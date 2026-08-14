package fr.moovie.tv.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.data.settings.currentTmdbLanguage
import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.watch.EpisodeRef
import fr.moovie.tv.core.watch.episodeToResume
import fr.moovie.tv.core.watch.parseEpisodeKey
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.usecase.UNKNOWN_HEIGHT
import fr.moovie.tv.core.sources.usecase.nextLinkFor
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.download.DownloadRepository
import fr.moovie.tv.data.download.DownloadQueue
import fr.moovie.tv.data.download.localStream
import fr.moovie.tv.data.sources.ExtractorRegistry
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.data.sources.isStreamPlayable
import fr.moovie.tv.core.sources.usecase.qualityLabel
import fr.moovie.tv.data.sources.streamHeights
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import fr.moovie.tv.data.sources.ProviderRegistry
import fr.moovie.tv.data.sources.SourceCacheRepository
import fr.moovie.tv.ui.format.upcomingDate
import fr.moovie.tv.ui.navigation.AltSource
import fr.moovie.tv.data.tmdb.Episode
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.data.tmdb.TvDetails
import fr.moovie.tv.data.trailer.YoutubeTrailerExtractor
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.TitleMeta
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.watch.WatchlistEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.details_lang_unavailable
import fr.moovie.tv.resources.details_needs_key
import fr.moovie.tv.resources.details_no_player
import fr.moovie.tv.resources.details_resolve_error
import fr.moovie.tv.resources.details_tmdb_error
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import fr.moovie.tv.core.sources.usecase.HosterTrust
import fr.moovie.tv.data.sources.HosterTrustRepository

/** Délai max par provider avant de le marquer en échec (n'affecte que ce provider). */
private const val PROVIDER_TIMEOUT_MS = 12000L

/**
 * Langue dans laquelle on interroge les catalogues.
 *
 * Constante et non paramétrable : elle décrit les sites visés — tous
 * francophones — et non l'utilisateur. La rendre configurable reviendrait à
 * offrir un réglage dont toutes les valeurs sauf une cassent la recherche.
 */
private const val CATALOG_LANGUAGE = "fr-FR"

class DetailsViewModel : ViewModel() {

    private val settings = SettingsRepository()

    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state

    private val _sources = MutableStateFlow<SourcesState>(SourcesState.Idle)
    val sources: StateFlow<SourcesState> = _sources

    /**
     * Qualité vidéo par URL d'embed, remplie au fil de l'eau.
     *
     * Aucun catalogue ne l'annonce en listant ses liens : il faut résoudre
     * l'embed puis lire la master playlist. C'est trop lent pour bloquer
     * l'ouverture du panneau, d'où une mesure en arrière-plan dont le résultat
     * arrive quand il arrive. Le cache est volontairement à vie de session : une
     * même source ne doit pas être re-résolue à chaque va-et-vient dans la fiche.
     */
    private val _qualities = MutableStateFlow<Map<String, String>>(emptyMap())
    val qualities: StateFlow<Map<String, String>> = _qualities

    /**
     * Hauteur d'image mesurée, par URL de lien.
     *
     * Doublon apparent de [_qualities], qui n'en est pas un : celui-ci porte un
     * libellé fait pour être lu, celui-là un nombre fait pour être comparé. Le
     * classement des sources au lancement s'appuie sur le second.
     */
    private val _heights = MutableStateFlow<Map<String, Int>>(emptyMap())
    val heights: StateFlow<Map<String, Int>> = _heights

    /**
     * Toutes les définitions d'un lien, pas seulement la meilleure : c'est ce
     * que le menu « Qualité » du lecteur propose, en croisant les sources.
     */
    private val _variants = MutableStateFlow<Map<String, List<Int>>>(emptyMap())
    val variants: StateFlow<Map<String, List<Int>>> = _variants

    /**
     * Verdict de la sonde, par URL d'embed.
     *
     * L'information existait déjà : mesurer la qualité oblige à résoudre le
     * lien, donc à savoir s'il répond. On la jetait — un échec sortait
     * silencieusement et la ligne restait indiscernable d'une source valide.
     * D'où le reproche légitime : le panneau listait des sources qui
     * n'ouvraient pas, et il fallait en essayer deux ou trois pour tomber sur
     * la bonne.
     */
    private val _linkStatus = MutableStateFlow<Map<String, LinkStatus>>(emptyMap())
    val linkStatus: StateFlow<Map<String, LinkStatus>> = _linkStatus

    /** URLs déjà mesurées ou en cours, pour ne jamais lancer deux fois le même travail. */
    private val qualityRequested = mutableSetOf<String>()

    /**
     * Limite le nombre de mesures simultanées. Sans elle, ouvrir un panneau de
     * dix-sept sources déclencherait dix-sept résolutions d'un coup : de quoi
     * saturer une TV d'entrée de gamme et se faire remarquer des hébergeurs,
     * pour un simple libellé.
     */
    private val qualitySlots = Semaphore(3)

    /**
     * Demande la qualité d'un lien. Sans effet si elle est déjà connue ou en
     * cours de mesure. Un échec est silencieux : la ligne garde son libellé de
     * repli plutôt que d'afficher une erreur pour une information d'appoint.
     */
    fun requestQuality(link: EmbedLink) {
        if (!qualityRequested.add(link.url)) return
        _linkStatus.value = _linkStatus.value + (link.url to LinkStatus.CHECKING)
        viewModelScope.launch {
            val outcome = runCatching {
                qualitySlots.withPermit {
                    val stream = ExtractorRegistry.resolve(link)
                    // Le même verdict que celui de la lecture rapide, et pour
                    // la même raison : une URL bien formée ne veut pas dire un
                    // flux servi. Le mesurer ici plutôt qu'après le choix, c'est
                    // toute la différence entre « voir » et « essayer ».
                    if (stream == null || !isStreamPlayable(stream, playbackMinutes)) {
                        null
                    } else {
                        stream to streamHeights(stream)
                    }
                }
            }.getOrNull()

            if (outcome == null) {
                // Une source morte est **signalée, pas masquée**. La sonde a des
                // faux négatifs connus : la durée ne se mesure que sur HLS, et
                // certains hôtes refusent un HEAD venu d'un contexte inhabituel.
                // Cacher rendrait injoignable ce qui aurait peut-être marché ;
                // griser laisse le choix tout en disant lequel prendre.
                _linkStatus.value = _linkStatus.value + (link.url to LinkStatus.DEAD)
                return@launch
            }
            _linkStatus.value = _linkStatus.value + (link.url to LinkStatus.OK)
            // La hauteur **et** le libellé : la première sert au classement des
            // sources et au menu du lecteur, le second à l'affichage. Déduire
            // l'une de l'autre après coup, c'est trier « 1080p » et « 720p »
            // comme des chaînes de caractères.
            val hauteurs = outcome.second
            hauteurs.firstOrNull()?.let { haut ->
                _heights.value = _heights.value + (link.url to haut)
                qualityLabel(haut)?.let { _qualities.value = _qualities.value + (link.url to it) }
            }
            if (hauteurs.isNotEmpty()) {
                _variants.value = _variants.value + (link.url to hauteurs)
            }
        }
    }

    /** Flux prêt à jouer (émis une fois qu'un extracteur a résolu un lien). */
    private val _resolved = MutableStateFlow<PlayableStream?>(null)
    val resolved: StateFlow<PlayableStream?> = _resolved

    /** Message transitoire si un lecteur choisi n'a pas pu être résolu. */
    private val _resolveError = MutableStateFlow<String?>(null)
    val resolveError: StateFlow<String?> = _resolveError

    // ── Bande-annonce ────────────────────────────────────────────────────────

    private val trailerExtractor = YoutubeTrailerExtractor(ExtractorRegistry.gateway)

    private val _trailer = MutableStateFlow<TrailerState>(TrailerState.None)
    val trailer: StateFlow<TrailerState> = _trailer

    /**
     * La bande-annonce est-elle passée au premier plan.
     *
     * Elle ne change pas d'écran et ne crée aucun lecteur : c'est **celui qui
     * joue déjà en fond** qui reçoit les contrôles. D'où un simple booléen
     * plutôt qu'un flux à consommer — il n'y a rien à transporter ailleurs,
     * seulement une vue à découvrir.
     *
     * Dans le ViewModel et non dans l'écran parce que le bouton Retour doit
     * pouvoir la refermer, et que le retour est géré par plateforme.
     */
    private val _trailerExpanded = MutableStateFlow(false)
    val trailerExpanded: StateFlow<Boolean> = _trailerExpanded


    /** Réglage : l'aperçu du hero se lance-t-il tout seul. */
    val trailerAutoplay: StateFlow<Boolean> = settings.trailerAutoplay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Réglage : le son de l'aperçu monte-t-il quand l'interface s'efface. */
    val trailerSound: StateFlow<Boolean> = settings.trailerSound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var trailerJob: Job? = null

    /**
     * URL de la source en cours de résolution, ou null.
     *
     * Extraire un lien d'embed prend une à trois secondes — on interroge
     * l'hébergeur, on désobfusque, puis on vérifie que le flux est réellement
     * servi. Sans rien à l'écran pendant ce temps, l'appui semblait n'avoir rien
     * fait, et le lecteur s'ouvrait « tout seul » quelques secondes plus tard.
     *
     * On retient **l'URL** plutôt qu'un simple booléen : le panneau doit poser
     * l'indicateur sur la ligne effectivement choisie, pas sur toutes.
     */
    private val _resolving = MutableStateFlow<String?>(null)
    val resolving: StateFlow<String?> = _resolving

    /** Visibilité du panneau des sources (découplée du chargement, qui est en fond). */
    private val _panelVisible = MutableStateFlow(false)
    val panelVisible: StateFlow<Boolean> = _panelVisible

    /** État de la lecture rapide (loader du bouton Lire / bannière épisode). */
    private val _quickPlay = MutableStateFlow<QuickPlayState>(QuickPlayState.Idle)
    val quickPlay: StateFlow<QuickPlayState> = _quickPlay

    /**
     * Épisode ouvert en fiche détaillée. Non nul = la fiche série affiche
     * l'épisode (visuel, synopsis complet, Lire / Sources / Marquer vu) au lieu
     * de la liste — même logique que la fiche d'un film.
     */
    private val _selectedEpisode = MutableStateFlow<EpisodeSelection?>(null)
    val selectedEpisode: StateFlow<EpisodeSelection?> = _selectedEpisode

    private var quickPlayJob: Job? = null

    /**
     * Liens écartés pour le contenu en cours : sonde négative, ou lecture qui a
     * cassé une fois le lecteur ouvert. La cascade les saute au lieu de
     * reproposer un hébergeur dont on sait déjà qu'il ne passe pas.
     */
    private val rejectedLinks = mutableSetOf<String>()

    /** Lien à l'origine du flux en cours de lecture, pour pouvoir l'écarter. */
    /**
     * Le lien d'embed derrière la lecture en cours.
     *
     * Deux usages, et c'est pour ça qu'il n'y en a qu'un : rendre la main à la
     * cascade quand le flux casse en route, et dire au lecteur quoi mettre en
     * file de téléchargement. Null quand la lecture vient du disque — il n'y a
     * alors ni source à réessayer, ni rien à télécharger.
     */
    var playingLink: EmbedLink? = null
        private set

    /** Libellé de la lecture rapide en cours, réutilisé si la cascade reprend. */
    private var quickPlayLabel = ""

    /** Langue de stream préférée de l'utilisateur (pour trier/prioriser les sources). */
    val streamLanguage: StateFlow<StreamLanguage> = settings.streamLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamLanguage.VF)

    private val watchRepo = WatchProgressRepository()
    private val sourceCache = SourceCacheRepository()

    /** Clés marquées vues (badge ✓ sur les épisodes / le film). */
    val watched: StateFlow<Set<String>> = watchRepo.watched
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Titre courant présent dans « À regarder plus tard ».
     *
     * Dérivé du flux du dépôt plutôt que d'un booléen local : le titre peut en
     * sortir tout seul quand il est marqué vu, et le bouton doit suivre.
     */
    val inWatchlist: StateFlow<Boolean> = watchRepo.watchlist
        .map { list -> list.any { it.key == watchlistKey() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Progression en cours par clé (mini-barre sur les épisodes commencés). */
    val resume: StateFlow<Map<String, ResumeEntry>> = watchRepo.continueWatching
        .map { list -> list.associateBy { it.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Épisode à reprendre pour la série affichée. Conservé au-delà du
     * chargement initial : changer de saison à la main ne doit pas déplacer le
     * repère sur un épisode qui n'a rien à voir, ni le faire disparaître quand
     * on revient sur la bonne saison.
     */
    private var resumeTarget: EpisodeRef? = null

    /** Métadonnées du contenu en cours de résolution, persistées quand la lecture démarre. */
    private var pendingMeta: ResumeEntry? = null

    /**
     * Génération de résolution : incrémentée à chaque changement de titre ou
     * nouvelle demande de lecture. Une résolution en vol (panneau/lecture rapide)
     * n'émet son flux que si sa génération est toujours courante — sinon un
     * « Film B » résolu tardivement écraserait la lecture de « Série A ».
     */
    private var resolveGen = 0

    private var tmdbId = 0
    private var isTv = false

    /** Épisode explicitement demandé à l'ouverture (voir [start]). */
    private var requestedResume: EpisodeRef? = null

    /**
     * Titre français du média courant, mémorisé le temps de la fiche.
     *
     * C'est un `Deferred` et non une valeur parce que les catalogues sont
     * interrogés en parallèle : sans ça, dix providers déclencheraient dix fois
     * la même requête TMDB. Réinitialisé à chaque fiche (voir [start]).
     */
    private var catalogTitleAsync: Deferred<String>? = null
    private val catalogTitleLock = Mutex()

    /**
     * Titre à envoyer aux catalogues, **toujours en français**.
     *
     * Le titre affiché suit la langue de l'interface ; les catalogues, eux, sont
     * francophones et ne connaissent que le titre français. Les confondre avait
     * un effet radical : interface en anglais (ou, sur desktop, simple locale
     * système non française) et plus **aucune** source ne remontait, puisque
     * seul frembed se repère à l'identifiant TMDB — tous les autres cherchent au
     * titre.
     *
     * Une requête TMDB de plus, et seulement quand l'interface n'est pas déjà en
     * français. En cas d'échec on retombe sur le titre affiché : mieux vaut une
     * recherche approximative que pas de recherche du tout.
     */
    private suspend fun catalogTitle(displayed: String): String {
        if (currentTmdbLanguage() == CATALOG_LANGUAGE) return displayed
        val pending = catalogTitleLock.withLock {
            catalogTitleAsync ?: viewModelScope.async(Dispatchers.IO) {
                // Tout est capturé : une exception qui s'échapperait d'un `async`
                // emporterait le scope du ViewModel avec elle, et donc la fiche.
                runCatching {
                    val apiKey = settings.tmdbApiKey.first()
                    val repo = TmdbRepository(CATALOG_LANGUAGE)
                    if (isTv) repo.tvDetails(apiKey, tmdbId).name
                    else repo.movieDetails(apiKey, tmdbId).title
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: displayed
            }.also { catalogTitleAsync = it }
        }
        return pending.await()
    }

    fun movieKey() = "movie:$tmdbId"
    fun episodeKey(season: Int, episode: Int) = "tv:$tmdbId:s${season}e$episode"

    /**
     * Ouvre une fiche.
     *
     * [resumeSeason]/[resumeEpisode] désignent l'épisode à mettre en avant quand
     * l'appelant le connaît — typiquement une carte de « Reprendre la lecture ».
     * Ils priment sur la déduction faite depuis l'historique : la carte cliquée
     * dit sans ambiguïté où l'utilisateur veut aller, là où la déduction doit
     * arbitrer entre plusieurs épisodes en cours et peut désigner l'autre.
     * À zéro, on retombe sur la déduction.
     */
    fun start(tmdbId: Int, isTv: Boolean, resumeSeason: Int = 0, resumeEpisode: Int = 0) {
        if (this.tmdbId == tmdbId && _state.value !is DetailsState.Loading) {
            // Même titre : on ne recharge pas TMDB pour rien, mais on **revient
            // à la vue d'ensemble**.
            //
            // La fiche d'épisode et le panneau des sources sont des sous-vues,
            // pas l'état de la fiche. Ce ViewModel vivant à l'échelle de la
            // fenêtre, elles survivaient à la sortie de l'écran : rouvrir la
            // série depuis la recherche rendait la fiche du dernier épisode
            // regardé, jamais la liste des épisodes qu'on venait chercher.
            _selectedEpisode.value = null
            _panelVisible.value = false
            // Un épisode explicitement demandé (carte « Reprendre ») désigne
            // aussi sa saison : sans ça on rouvrait sur celle qui était affichée.
            if (resumeSeason > 0) selectSeason(resumeSeason)
            return
        }
        requestedResume = if (resumeSeason > 0 && resumeEpisode > 0) {
            EpisodeRef(resumeSeason, resumeEpisode)
        } else {
            null
        }
        // Le ViewModel est partagé entre fiches (scope Activity) : purge l'état
        // de la fiche précédente (panneau sources, erreurs) avant de charger.
        quickPlayJob?.cancel()
        // Même raison que pour la lecture : le ViewModel vit à l'échelle de la
        // fenêtre, donc la bande-annonce du titre précédent resterait affichée
        // sous le hero du nouveau — et son bouton la lancerait.
        trailerJob?.cancel()
        _trailer.value = TrailerState.None
        _trailerExpanded.value = false
        resolveGen++ // invalide toute résolution en vol de la fiche précédente
        _quickPlay.value = QuickPlayState.Idle
        _panelVisible.value = false
        _sources.value = SourcesState.Idle
        _resolveError.value = null
        // Sans ça, une résolution laissée en vol sur la fiche précédente
        // rendrait la main sans jamais effacer son témoin (elle sort par le
        // garde-fou de génération) : la fiche suivante s'ouvrirait avec une
        // source qui tourne indéfiniment.
        _resolving.value = null
        _resolved.value = null
        _selectedEpisode.value = null
        pendingMeta = null
        rejectedLinks.clear()
        playingLink = null
        catalogTitleAsync = null
        this.tmdbId = tmdbId
        this.isTv = isTv
        resumeTarget = null
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            if (apiKey.isBlank()) {
                _state.value = DetailsState.Error(getString(Res.string.details_needs_key))
                return@launch
            }
            val repo = TmdbRepository(currentTmdbLanguage())
            runCatching {
                if (isTv) {
                    val d = repo.tvDetails(apiKey, tmdbId)
                    val seasons = d.seasons.map { it.seasonNumber }.filter { it > 0 }.sorted()
                    val firstSeason = seasons.firstOrNull() ?: 1

                    // Reprendre là où on en est plutôt que systématiquement à la
                    // saison 1 : sur une série suivie depuis des semaines, c'est
                    // le seul endroit où l'on ne veut pas atterrir.
                    val target = requestedResume ?: episodeToResume(
                        inProgress = watchRepo.continueWatching.first()
                            .firstOrNull { it.isTv && it.tmdbId == tmdbId && it.season > 0 }
                            ?.let { EpisodeRef(it.season, it.episode) },
                        watched = watchRepo.watched.first()
                            .mapNotNull { key -> parseEpisodeKey(key, tmdbId) }
                            .toSet(),
                        firstSeason = firstSeason,
                    )

                    // La règle peut désigner un épisode au-delà de la saison
                    // (saison terminée) : c'est ici, et seulement ici, qu'on
                    // connaît la longueur réelle des saisons pour basculer sur
                    // la suivante.
                    var season = if (target.season in seasons) target.season else firstSeason
                    var seasonDetails = repo.season(apiKey, tmdbId, season)
                    if (target.episode > seasonDetails.episodes.size) {
                        val next = seasons.firstOrNull { it > season }
                        if (next != null) {
                            season = next
                            seasonDetails = repo.season(apiKey, tmdbId, season)
                        }
                    }
                    val eps = seasonDetails.episodes
                    // Si la saison a changé en route, l'épisode visé redevient le
                    // premier de la nouvelle saison.
                    val focusEpisode =
                        if (season == target.season) target.episode.coerceAtMost(eps.size) else 1
                    resumeTarget = EpisodeRef(season, focusEpisode)
                    DetailsState.Tv(
                        d,
                        season,
                        eps,
                        resumeEpisode = focusEpisode,
                        seasonOverview = seasonDetails.overview,
                        seasonYear = seasonDetails.year,
                        seasonAirDate = seasonDetails.airDate,
                    )
                } else {
                    DetailsState.Movie(repo.movieDetails(apiKey, tmdbId))
                }
            }.onSuccess {
                _state.value = it
                // Une série mise de côté depuis une affiche du catalogue n'a pas
                // de total d'épisodes : sans lui elle ne sortirait jamais seule
                // de la liste. On le complète dès qu'on connaît ses saisons.
                if (it is DetailsState.Tv) completeWatchlistEntry(it)
                // Nom, image et genres relevés ici une fois pour toutes :
                // l'historique en a besoin, et ni un épisode marqué vu depuis le
                // lecteur ni un titre coché sans lecture n'ont de fiche sous la
                // main au moment où la ligne s'écrit.
                when (it) {
                    is DetailsState.Movie -> watchRepo.rememberTitle(
                        "movie:$tmdbId",
                        TitleMeta(
                            title = it.details.title,
                            imageUrl = it.details.backdropUrl() ?: it.details.posterUrl(),
                            genres = it.details.genres.map { g -> g.name },
                        ),
                    )
                    is DetailsState.Tv -> watchRepo.rememberTitle(
                        "tv:$tmdbId",
                        TitleMeta(
                            title = it.details.name,
                            imageUrl = it.details.backdropUrl() ?: it.details.posterUrl(),
                            genres = it.details.genres.map { g -> g.name },
                        ),
                    )
                    else -> Unit
                }
                // Fiche film : sources chargées immédiatement en arrière-plan
                // pour que le bouton « Lire » soit prêt sans ouvrir le panneau.
                if (it is DetailsState.Movie) loadMovieSources()
                loadTrailer(repo, apiKey, it)
            }.onFailure { _state.value = DetailsState.Error(getString(Res.string.details_tmdb_error, it.message ?: "")) }
        }
    }

    fun selectSeason(season: Int) {
        val tv = _state.value as? DetailsState.Tv ?: return
        _selectedEpisode.value = null
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            val repo = TmdbRepository(currentTmdbLanguage())
            runCatching { repo.season(apiKey, tmdbId, season) }
                .onSuccess { details ->
                    // Le repère ne vaut que pour la saison où l'on s'était
                    // arrêté : ailleurs, il désignerait un épisode au hasard.
                    val target = resumeTarget
                    _state.value = tv.copy(
                        season = season,
                        episodes = details.episodes,
                        resumeEpisode = if (target?.season == season) target.episode else 0,
                        seasonOverview = details.overview,
                        seasonYear = details.year,
                        seasonAirDate = details.airDate,
                    )
                }
        }
    }

    // ── Bande-annonce ────────────────────────────────────────────────────────

    /**
     * Trouve la bande-annonce **et résout son flux**, en tâche de fond.
     *
     * La résolution est faite ici plutôt qu'au clic parce que c'est elle qui
     * décide de l'affichage : ni le bouton ni l'aperçu du hero ne doivent
     * apparaître sur une bande-annonce dont on ne sait pas encore si elle est
     * jouable. Voir [TrailerState] pour ce que ce choix coûte et rapporte.
     */
    private fun loadTrailer(repo: TmdbRepository, apiKey: String, state: DetailsState) {
        val (id, isTv, videos) = when (state) {
            is DetailsState.Movie ->
                Triple(state.details.id, false, state.details.videos?.results.orEmpty())
            is DetailsState.Tv ->
                Triple(state.details.id, true, state.details.videos?.results.orEmpty())
            else -> return
        }
        val gen = resolveGen
        trailerJob = viewModelScope.launch {
            val best = runCatching { repo.bestTrailer(apiKey, id, isTv, videos) }.getOrNull()
                ?: return@launch
            // Le titre a pu changer pendant la requête : sans ce garde-fou, la
            // bande-annonce du film précédent s'afficherait sur la fiche
            // suivante. Même règle que `resolveGen` pour les sources.
            if (gen != resolveGen) return@launch

            val langue = currentTmdbLanguage().take(2).lowercase()
            val resolved = runCatching { trailerExtractor.resolveDetailed(best.key, langue) }
                .getOrNull() ?: return@launch
            if (gen != resolveGen) return@launch

            _trailer.value = TrailerState.Ready(
                video = best,
                stream = resolved.stream,
                durationSeconds = resolved.durationSeconds,
            )
        }
    }

    /**
     * Passe la bande-annonce au premier plan.
     *
     * Rien à résoudre ni à lancer : le flux est déjà là — sans quoi le bouton
     * ne serait pas affiché — et l'aperçu joue déjà, ou démarrera à l'instant
     * puisque la demande est explicite. L'appui est donc instantané, et la
     * lecture ne repart pas de zéro.
     */
    fun openTrailer() {
        if (_trailer.value !is TrailerState.Ready) return
        _trailerExpanded.value = true
    }

    /** Referme les contrôles ; l'aperçu, lui, continue en fond. */
    fun closeTrailer() {
        _trailerExpanded.value = false
    }

    /** Ouvre la fiche détaillée d'un épisode (clic / OK sur sa carte). */
    fun openEpisode(season: Int, episode: Episode) {
        _selectedEpisode.value = EpisodeSelection(season, episode)
    }

    /** Referme la fiche d'épisode et revient à la liste (Retour / Échap). */
    fun closeEpisode() {
        _selectedEpisode.value = null
    }

    /** Panneau des sources d'un épisode (bouton « Sources » de sa fiche). */
    fun openEpisodePanel(season: Int, episode: Int) {
        loadEpisodeSourcesAt(season, episode)
        _panelVisible.value = true
    }

    /** Clé de reprise du contenu dont on charge les sources (film ou épisode). */
    var playbackKey: String = ""
        private set

    /** Titre affiché par le lecteur (film ou série). */
    var playbackTitle: String = ""
        private set

    /**
     * Durée annoncée par TMDB pour le contenu en cours de résolution, en minutes.
     *
     * Sert de garde-fou dans la cascade : une source qui rend un flux de quelques
     * secondes ne sert pas le média demandé (logo, bande-annonce, message
     * d'indisponibilité). Null quand TMDB ne l'annonce pas — le contrôle est
     * alors simplement ignoré.
     */
    var playbackMinutes: Int? = null
        private set

    /** Sous-titre du lecteur : année (film) ou « S1 · E3 — Nom » (épisode). */
    var playbackSubtitle: String = ""
        private set

    /**
     * Épisode à enchaîner en fin de lecture (saison, numéro), null s'il n'y en
     * a pas : film, ou dernier épisode de la dernière saison. Calculé ici car
     * le lecteur ne connaît que la clé du média, pas le catalogue TMDB.
     */
    var playbackNext: Pair<Int, Int>? = null
        private set

    /** Affiche du titre en lecture, pour l'écran de veille. */
    var playbackPoster: String = ""
        private set

    /**
     * Les autres sources de la langue en cours, pour le menu « Qualité » du
     * lecteur — celle qui joue exclue, elle n'est pas une alternative à
     * elle-même.
     *
     * Calculé à la demande plutôt que mémorisé : les mesures de définition
     * continuent d'arriver pendant qu'on regarde la fiche, et une liste figée à
     * l'ouverture serait plus pauvre que ce qu'on sait au moment de lancer.
     */
    fun playbackAlternatives(): List<AltSource> {
        val active = _sources.value as? SourcesState.Active ?: return emptyList()
        val lang = playingLink?.language ?: return emptyList()
        val hauteurs = _heights.value
        return active.links
            .filter { it.language == lang && it.url != playingLink?.url }
            .map { AltSource(url = it.url, hoster = it.hoster, height = hauteurs[it.url] ?: 0) }
            // La meilleure d'abord : c'est l'ordre dans lequel le menu les
            // proposera, et celui dans lequel on veut les lire.
            .sortedByDescending { it.height }
    }

    /**
     * Épisode suivant : le numéro d'après dans la saison courante, sinon le
     * premier épisode de la saison suivante si elle existe. On se fie à
     * `episodeCount` de TMDB, disponible pour toutes les saisons — la liste
     * `episodes` chargée ne concerne que la saison affichée.
     */
    private fun nextEpisodeAfter(tv: TvDetails, season: Int, episode: Int): Pair<Int, Int>? {
        val current = tv.seasons.firstOrNull { it.seasonNumber == season }
        if (current != null && episode < current.episodeCount) return season to (episode + 1)
        val next = tv.seasons.firstOrNull { it.seasonNumber == season + 1 && it.episodeCount > 0 }
        return next?.let { it.seasonNumber to 1 }
    }

    /** Charge les sources du film courant, en streaming par provider. */
    fun loadMovieSources() {
        val movie = _state.value as? DetailsState.Movie ?: return
        playbackKey = movieKey()
        playbackTitle = movie.details.title
        playbackMinutes = movie.details.runtime
        playbackSubtitle = movie.details.year.orEmpty()
        playbackNext = null
        playbackPoster = movie.details.posterUrl() ?: movie.details.backdropUrl().orEmpty()
        pendingMeta = ResumeEntry(
            key = playbackKey,
            tmdbId = tmdbId,
            isTv = false,
            title = movie.details.title,
            imageUrl = movie.details.backdropUrl() ?: movie.details.posterUrl(),
        )
        startSourceLoad {
            it.sourcesFor(
                MediaRef.Movie(tmdbId, catalogTitle(movie.details.title), movie.details.year),
            )
        }
    }

    /** Charge les sources d'un épisode de la saison affichée. */
    fun loadEpisodeSources(episode: Int) {
        val tv = _state.value as? DetailsState.Tv ?: return
        loadEpisodeSourcesAt(tv.season, episode)
    }

    /**
     * Charge les sources d'un épisode d'une saison explicite (reprise depuis
     * l'accueil : la saison affichée peut ne pas être encore la bonne).
     */
    fun loadEpisodeSourcesAt(season: Int, episode: Int) {
        val tv = _state.value as? DetailsState.Tv ?: return
        playbackKey = episodeKey(season, episode)
        val ep = tv.episodes.takeIf { tv.season == season }
            ?.firstOrNull { it.episodeNumber == episode }
        val still = ep?.stillUrl()
        playbackTitle = tv.details.name
        // Durée de l'épisode et non de la série : TMDB ne l'annonce pas toujours,
        // auquel cas le garde-fou de durée est simplement inactif.
        playbackMinutes = ep?.runtime
        playbackSubtitle = "S$season · E$episode" + (ep?.name?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
        playbackNext = nextEpisodeAfter(tv.details, season, episode)
        playbackPoster = tv.details.posterUrl() ?: tv.details.backdropUrl().orEmpty()
        pendingMeta = ResumeEntry(
            key = playbackKey,
            tmdbId = tmdbId,
            isTv = true,
            season = season,
            episode = episode,
            title = tv.details.name,
            imageUrl = still ?: tv.details.backdropUrl() ?: tv.details.posterUrl(),
        )
        startSourceLoad {
            it.sourcesFor(
                MediaRef.Episode(
                    tmdbId,
                    catalogTitle(tv.details.name),
                    tv.details.year,
                    season,
                    episode,
                ),
            )
        }
    }

    /** Bascule vu/non vu pour une clé (épisode ou film). */
    fun toggleWatched(key: String) {
        viewModelScope.launch {
            watchRepo.setWatched(key, !watched.value.contains(key))
        }
    }

    /**
     * Marque toute la saison affichée : vue si au moins un épisode ne l'est
     * pas encore, sinon non vue (bascule globale).
     */
    fun toggleSeasonWatched() {
        val tv = _state.value as? DetailsState.Tv ?: return
        val keys = tv.episodes.map { episodeKey(tv.season, it.episodeNumber) }
        if (keys.isEmpty()) return
        val markWatched = keys.any { it !in watched.value }
        viewModelScope.launch { watchRepo.setAllWatched(keys, markWatched) }
    }

    /** Génération de chargement : invalide les résultats des fiches précédentes. */
    private var loadGeneration = 0

    /** Vrai si les sources affichées viennent du cache et non d'une recherche. */
    private var servedFromCache = false

    /** Dernière requête de recherche, pour pouvoir la rejouer sans le cache. */
    /**
     * Ce que l'appareil a appris des hébergeurs. Voir [HosterTrustRepository] :
     * un hébergeur qui n'a jamais rien rendu de jouable cesse de passer devant
     * ceux qui marchent.
     */
    private val hosterTrust = HosterTrustRepository()

    /**
     * Verdicts en mémoire, relus en continu.
     *
     * Une carte tenue à jour plutôt qu'une lecture par tour de cascade : la
     * boucle tourne toutes les 250 ms, et lire le magasin à chaque tour
     * mettrait un accès disque sur le chemin le plus sensible de
     * l'application.
     */
    private var trustNow: Map<String, HosterTrust> = emptyMap()

    init {
        // **Après** la déclaration du dépôt, et ce n'est pas cosmétique : un
        // `init` placé en tête du corps s'exécute avant que `hosterTrust` n'ait
        // sa valeur. Le code compilait — l'accès est différé dans une lambda —
        // et ne tenait qu'à l'ordonnancement de la coroutine.
        viewModelScope.launch { hosterTrust.trust.collect { trustNow = it } }
    }

    private var lastSourceQuery: (suspend (SourceProvider) -> List<EmbedLink>)? = null

    /**
     * Ouvre le panneau immédiatement (tous providers en LOADING) puis interroge
     * chaque provider en parallèle ; chaque résultat met à jour l'état de façon
     * atomique → les sources apparaissent au fil de l'eau. Un provider lent/mort
     * passe en FAILED après [PROVIDER_TIMEOUT_MS] sans bloquer les autres.
     */
    private fun startSourceLoad(
        skipCache: Boolean = false,
        query: suspend (SourceProvider) -> List<EmbedLink>,
    ) {
        val generation = ++loadGeneration
        val cacheKey = playbackKey
        lastSourceQuery = query
        servedFromCache = false
        // Bascule en Active AVANT toute suspension : la lecture rapide lit
        // `_sources` juste après cet appel et repartait sur l'ancien `Idle`
        // (la liste des providers n'arrive qu'après lecture des réglages), d'où
        // l'obligation d'appuyer deux fois sur OK pour lancer un épisode.
        _sources.value = SourcesState.Active(links = emptyList(), providers = emptyList())
        viewModelScope.launch {
            // Lus AVANT le cache : une entrée n'est resservie que si elle a
            // interrogé tous les catalogues actifs aujourd'hui, sinon une mise à
            // jour qui en ajoute un resterait invisible sur les fiches déjà vues.
            val providers = activeProviders()
            val rank = providers.mapIndexed { i, p -> p.name to i }.toMap()
            val expected = providers.map { it.name }.toSet()

            // Fiche déjà consultée : on ressert les liens connus au lieu de
            // réinterroger les providers (plusieurs secondes). Le flux jouable,
            // lui, sera de toute façon ré-extrait au moment de lire.
            if (!skipCache) {
                val cached = sourceCache.get(cacheKey, expected)
                val cachedProviders = cached?.mapNotNull { it.provider }?.distinct().orEmpty()
                if (cached != null && cachedProviders.isNotEmpty()) {
                    if (generation != loadGeneration) return@launch
                    servedFromCache = true
                    _sources.value = SourcesState.Active(
                        links = cached,
                        providers = cachedProviders.map { ProviderProgress(it, ProviderStatus.DONE) },
                    )
                    return@launch
                }
            }
            if (generation != loadGeneration) return@launch
            _sources.value = SourcesState.Active(
                links = emptyList(),
                providers = providers.map { ProviderProgress(it.name, ProviderStatus.LOADING) },
            )
            // coroutineScope : suspend jusqu'à ce que tous les providers aient
            // répondu, pour ne mettre en cache qu'une liste complète.
            coroutineScope {
                providers.forEach { provider ->
                    launch(Dispatchers.IO) {
                        val result = runCatching {
                            withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { query(provider) }
                        }
                        if (generation != loadGeneration) return@launch
                        _sources.update { st ->
                            val active = st as? SourcesState.Active ?: return@update st
                            val links = result.getOrNull().orEmpty().map { it.copy(provider = provider.name) }
                            val status = when {
                                result.isFailure || result.getOrNull() == null -> ProviderStatus.FAILED
                                links.isEmpty() -> ProviderStatus.EMPTY
                                else -> ProviderStatus.DONE
                            }
                            active.copy(
                                // Tri stable par priorité de provider (l'ordre d'arrivée
                                // est conservé au sein d'un même provider).
                                links = (active.links + links)
                                    .distinctBy { it.url }
                                    .sortedBy { rank[it.provider] ?: Int.MAX_VALUE },
                                providers = active.providers.map {
                                    if (it.name == provider.name) it.copy(status = status) else it
                                },
                            )
                        }
                    }
                }
            }

            if (generation != loadGeneration) return@launch
            val active = _sources.value as? SourcesState.Active
            // Seuls les catalogues qui ont *répondu* comptent comme interrogés :
            // enregistrer un provider tombé en panne (timeout, domaine mort)
            // figerait son absence dans le cache, et la fiche resterait amputée
            // pendant six heures alors qu'il est peut-être déjà revenu.
            val answered = active?.providers.orEmpty()
                .filter { it.status == ProviderStatus.DONE || it.status == ProviderStatus.EMPTY }
                .map { it.name }.toSet()
            sourceCache.put(cacheKey, active?.links.orEmpty(), answered)
        }
    }

    /**
     * Catalogues actifs, dans l'ordre de priorité de l'utilisateur.
     *
     * Un seul point de vérité : le chargement d'une fiche et le préchargement de
     * l'épisode suivant doivent interroger exactement le même ensemble, sinon le
     * cache serait rempli par un ensemble et jugé incomplet par l'autre — et
     * rejeté à chaque lecture.
     */
    private suspend fun activeProviders(): List<SourceProvider> {
        val disabled = settings.disabledProviders.first()
        val order = settings.providerOrder.first()
        return ProviderRegistry.all
            .filter { it.name !in disabled }
            .sortedBy { p ->
                order.indexOf(p.name).let { if (it == -1) order.size + ProviderRegistry.all.indexOf(p) else it }
            }
    }

    /**
     * Interroge à l'avance les catalogues pour un épisode, et range le résultat
     * dans le cache des sources.
     *
     * Appelé par le lecteur quand l'épisode en cours approche de sa fin : les
     * requêtes catalogues prennent plusieurs secondes, et jusqu'ici l'enchaînement
     * automatique les payait *après* le générique, écran noir à l'appui. Faites
     * pendant que l'épisode joue encore, elles ne coûtent rien de visible.
     *
     * **On ne précharge que la liste des liens, jamais le flux résolu.** Une URL
     * extraite expire souvent en moins de deux heures et se lie parfois à l'IP :
     * la mettre en cache reviendrait à préparer un lien mort. La résolution reste
     * donc au moment de lire.
     */
    fun prefetchEpisodeSources(season: Int, episode: Int) {
        val tv = _state.value as? DetailsState.Tv ?: return
        if (season <= 0 || episode <= 0) return
        val key = episodeKey(season, episode)
        if (!prefetched.add(key)) return

        viewModelScope.launch {
            val providers = activeProviders()
            // Déjà en cache et complet : rien à faire, et surtout pas de trafic
            // vers les hébergeurs pour un résultat qu'on a déjà.
            if (sourceCache.get(key, providers.map { it.name }.toSet()) != null) return@launch

            val media = MediaRef.Episode(
                tmdbId,
                catalogTitle(tv.details.name),
                tv.details.year,
                season,
                episode,
            )
            val answered = mutableSetOf<String>()
            val links = coroutineScope {
                providers.map { provider ->
                    async(Dispatchers.IO) {
                        val result = runCatching {
                            withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { provider.sourcesFor(media) }
                        }.getOrNull()
                        // Un catalogue en panne n'est pas « interrogé » : sans ça
                        // son absence serait figée dans le cache pour six heures.
                        if (result != null) answered += provider.name
                        result.orEmpty().map { it.copy(provider = provider.name) }
                    }
                }.awaitAll().flatten()
            }.distinctBy { it.url }

            if (links.isNotEmpty()) sourceCache.put(key, links, answered)
        }
    }

    /** Épisodes déjà préchargés dans cette session, pour ne pas le refaire. */
    private val prefetched = mutableSetOf<String>()

    /** Ouvre le panneau des sources (choix manuel). Recharge si nécessaire. */
    fun openPanel() {
        if (_sources.value is SourcesState.Idle) loadMovieSources()
        _panelVisible.value = true
    }

    /** Ferme le panneau des sources (les liens chargés restent en cache). */
    fun closePanel() {
        _panelVisible.value = false
    }

    /**
     * Le lecteur a renoncé sur le flux en cours : écarte sa source et relance la
     * cascade sur la suivante.
     *
     * La sonde ne valide qu'un accès au premier octet : un lien peut répondre
     * correctement puis casser à l'ouverture (manifeste HLS vide, segments en
     * 403, codec refusé). Sans cette reprise, l'utilisateur retombait sur un
     * écran d'erreur alors que d'autres hébergeurs restaient à essayer.
     *
     * Renvoie false si plus rien n'est à tenter — à l'appelant de traiter
     * l'échec (retour à la fiche).
     */
    fun retryAfterPlaybackFailure(): Boolean {
        val failed = playingLink ?: return false
        rejectedLinks += failed.url
        playingLink = null
        _resolved.value = null
        val active = _sources.value as? SourcesState.Active ?: return false
        // Même filtre que la cascade — repli de langue compris, sinon on
        // conclurait « plus rien à tenter » devant des liens qu'elle jouerait.
        val lang = streamLanguage.value.name
        val hasAlternative = active.anyLoading ||
            nextLinkFor(active.links, preferred = lang, excluded = rejectedLinks, heights = _heights.value) != null
        if (!hasAlternative) {
            // Plus rien à tenter : sans ce retour, l'utilisateur revenait à la
            // fiche sans la moindre explication. On ouvre le panneau, qui liste
            // les autres langues et hébergeurs restants.
            viewModelScope.launch {
                _resolveError.value = getString(Res.string.details_no_player, lang)
                _panelVisible.value = true
            }
            return false
        }
        // La cascade repart de zéro : le job précédent est terminé (il rend la
        // main dès qu'il a émis un flux), rejectedLinks fait le reste.
        startQuickPlay(quickPlayLabel)
        return true
    }

    /** Renseigne le total d'épisodes d'une série déjà en liste, s'il manque. */
    private suspend fun completeWatchlistEntry(tv: DetailsState.Tv) {
        val key = WatchlistEntry.tvKey(tmdbId)
        val existing = watchRepo.watchlist.first().firstOrNull { it.key == key } ?: return
        if (existing.totalEpisodes > 0) return
        val total = tv.details.seasons.filter { it.seasonNumber > 0 }.sumOf { it.episodeCount }
        if (total > 0) watchRepo.addToWatchlist(existing.copy(totalEpisodes = total))
    }

    /** Clé « titre » de la watchlist : sans saison ni épisode. */
    private fun watchlistKey() =
        if (isTv) WatchlistEntry.tvKey(tmdbId) else WatchlistEntry.movieKey(tmdbId)

    /** Ajoute ou retire le titre courant de « À regarder plus tard ». */
    fun toggleWatchlist() {
        val key = watchlistKey()
        viewModelScope.launch {
            if (inWatchlist.value) {
                watchRepo.removeFromWatchlist(key)
                return@launch
            }
            val entry = when (val st = _state.value) {
                is DetailsState.Movie -> WatchlistEntry(
                    key = key,
                    tmdbId = tmdbId,
                    isTv = false,
                    title = st.details.title,
                    imageUrl = st.details.posterUrl() ?: st.details.backdropUrl(),
                )
                is DetailsState.Tv -> WatchlistEntry(
                    key = key,
                    tmdbId = tmdbId,
                    isTv = true,
                    title = st.details.name,
                    imageUrl = st.details.posterUrl() ?: st.details.backdropUrl(),
                    // Total d'épisodes su au moment de l'ajout : c'est lui qui
                    // permettra de sortir la série de la liste une fois vue.
                    totalEpisodes = st.details.seasons
                        .filter { it.seasonNumber > 0 }
                        .sumOf { it.episodeCount },
                )
                else -> return@launch
            }
            watchRepo.addToWatchlist(entry)
        }
    }

    /** Efface la bannière « indisponible » de la lecture rapide. */
    fun dismissQuickPlay() {
        if (_quickPlay.value is QuickPlayState.Unavailable) _quickPlay.value = QuickPlayState.Idle
    }

    /** Lecture rapide du film courant (sources déjà en cours de chargement). */
    fun quickPlayMovie() {
        if (_sources.value is SourcesState.Idle) loadMovieSources()
        startQuickPlay(label = "")
    }

    /** Lecture rapide d'un épisode : charge ses sources puis résout la meilleure. */
    fun quickPlayEpisode(season: Int, episode: Int) {
        if (quickPlayJob?.isActive == true) return
        loadEpisodeSourcesAt(season, episode)
        startQuickPlay(label = "S${season}E$episode")
    }

    /**
     * Boucle de lecture rapide : dès qu'un lien dans la langue préférée arrive,
     * tente de le résoudre ; en échec, passe au suivant. Quand tous les
     * providers ont fini sans succès : ouvre le panneau s'il existe d'autres
     * langues ou si des lecteurs ont échoué, sinon bannière « indisponible ».
     */
    private fun startQuickPlay(label: String) {
        if (quickPlayJob?.isActive == true) return
        val gen = ++resolveGen
        quickPlayLabel = label
        quickPlayJob = viewModelScope.launch {
            // Le fichier local passe **avant toute résolution**, et pas seulement
            // hors ligne : un épisode déjà sur le disque se lit instantanément,
            // sans jeton qui expire, sans hébergeur qui répond 403, sans les
            // deux à trois secondes de cascade. Interroger le réseau pour
            // ignorer ensuite sa réponse n'aurait servi à rien.
            //
            // C'est aussi ce qui rend le mode hors ligne automatique : il n'y a
            // pas de détection de réseau à faire, la présence du fichier suffit
            // à trancher.
            pendingMeta?.key?.let { key ->
                localStream(key)?.let { local ->
                    pendingMeta?.let { watchRepo.register(it) }
                    _quickPlay.value = QuickPlayState.Idle
                    _resolved.value = local
                    return@launch
                }
            }
            val lang = settings.streamLanguage.first().name
            _quickPlay.value = QuickPlayState.Searching(if (label.isBlank()) lang else "$label · $lang")
            _resolveError.value = null
            // Repart des liens déjà écartés : une reprise après échec de lecture
            // ne doit pas reproposer l'hébergeur qui vient de casser.
            val tried = rejectedLinks.toMutableSet()
            // Tours passés à attendre que la liste des providers soit publiée.
            var startupWaits = 0
            // Une seule reprise autorisée après purge d'un cache périmé.
            var retriedWithoutCache = false
            while (true) {
                val active = _sources.value as? SourcesState.Active
                if (active == null) {
                    _quickPlay.value = QuickPlayState.Idle
                    return@launch
                }
                val next = nextLinkFor(
                    active.links,
                    preferred = lang,
                    excluded = tried,
                    heights = _heights.value,
                    trust = trustNow,
                )
                if (next != null) {
                    tried += next.url
                    // Affiche l'hébergeur en cours d'essai : la cascade devient
                    // visible au lieu de laisser l'utilisateur devant un écran
                    // d'attente muet.
                    _quickPlay.value = QuickPlayState.Searching(next.hoster, hoster = next.hoster)
                    val stream = runCatching { ExtractorRegistry.resolve(next) }.getOrNull()
                    if (gen != resolveGen) return@launch // titre changé entre-temps
                    // Une URL extraite ne suffit pas : ces hébergeurs signent des
                    // liens à durée de vie courte et répondent 403 derrière. Sans
                    // cette sonde, la cascade s'arrêtait sur un lien mort et le
                    // lecteur s'ouvrait sur « lecture impossible ».
                    if (stream != null && stream.url.isNotBlank() &&
                        isStreamPlayable(stream, playbackMinutes)
                    ) {
                        if (gen != resolveGen) return@launch
                        pendingMeta?.let { watchRepo.register(it) }
                        playingLink = next
                        // Ce qui vient de jouer ici rejouera probablement.
                        hosterTrust.recordSuccess(next.hoster)
                        _quickPlay.value = QuickPlayState.Idle
                        _resolved.value = stream
                        return@launch
                    }
                    // Écarté durablement : la sonde vient de le refuser, inutile
                    // d'y revenir si la cascade reprend plus tard.
                    rejectedLinks += next.url
                    // Et retenu **au-delà de ce titre** : c'est le lien qui est
                    // écarté ici, l'hébergeur qui l'est là. Un échec ne condamne
                    // rien — voir SEUIL_ECHECS.
                    hosterTrust.recordFailure(next.hoster)
                    continue
                }
                if (!active.anyLoading) {
                    // Aucun lien du cache n'a pu être lu : ils sont probablement
                    // tous morts. On purge et on refait une vraie recherche
                    // plutôt que de renvoyer l'utilisateur sur un échec — sans
                    // ce garde-fou, le cache dégraderait le comportement.
                    if (tried.isNotEmpty() && servedFromCache && !retriedWithoutCache) {
                        retriedWithoutCache = true
                        sourceCache.invalidate(playbackKey)
                        val replay = lastSourceQuery
                        if (replay != null) {
                            startSourceLoad(skipCache = true, query = replay)
                            tried.clear()
                            startupWaits = 0
                            delay(250)
                            continue
                        }
                    }
                    when {
                        tried.isNotEmpty() -> {
                            _resolveError.value = getString(Res.string.details_no_player, lang)
                            _panelVisible.value = true
                        }
                        active.links.isNotEmpty() -> _panelVisible.value = true
                        else -> {
                            _quickPlay.value = QuickPlayState.Unavailable(lang)
                            return@launch
                        }
                    }
                    _quickPlay.value = QuickPlayState.Idle
                    return@launch
                }
                // Garde-fou : si la liste des providers n'est jamais publiée
                // (lecture des réglages en échec), ne pas boucler sans fin.
                if (active.providers.isEmpty() && ++startupWaits > 40) {
                    _quickPlay.value = QuickPlayState.Unavailable(lang)
                    return@launch
                }
                delay(250)
            }
        }
    }

    /** Résout un lien d'embed en flux jouable via les extracteurs. */
    fun play(link: EmbedLink) {
        _resolveError.value = null
        // Un titre téléchargé se lit depuis le disque, sans toucher au réseau —
        // ni pour résoudre, ni pour vérifier que le flux répond. C'est ce qui
        // fait qu'« hors ligne » veut dire hors ligne, et non « plus rapide ».
        pendingMeta?.key?.let { key ->
            localStream(key)?.let { local ->
                viewModelScope.launch { pendingMeta?.let { watchRepo.register(it) } }
                playingLink = null
                _resolved.value = local
                return
            }
        }
        _resolving.value = link.url
        val gen = ++resolveGen
        viewModelScope.launch {
            val stream = runCatching { ExtractorRegistry.resolve(link) }.getOrNull()
                // Une URL bien formée ne veut pas dire un flux servi. Vidzy rend
                // par exemple une playlist signée que son CDN refuse ensuite en
                // 403 : le lecteur s'ouvrait, échouait, et renvoyait aussitôt sur
                // la fiche sans rien expliquer. La lecture rapide faisait déjà
                // cette vérification, le choix manuel non — d'où un comportement
                // différent selon le chemin emprunté pour la même source.
                ?.takeIf { runCatching { isStreamPlayable(it, playbackMinutes) }.getOrDefault(false) }
            // Titre changé pendant la résolution, ou autre source choisie entre
            // temps : ni le flux ni l'indicateur ne concernent plus l'écran —
            // c'est la résolution la plus récente qui les porte.
            if (gen != resolveGen) return@launch
            _resolving.value = null
            if (stream != null) {
                // La lecture va démarrer : persiste les métadonnées pour le
                // rail « Reprendre » (la position suivra depuis le lecteur).
                pendingMeta?.let { watchRepo.register(it) }
                // Renseigné ici aussi, et pas seulement par la lecture
                // rapide : sans quoi le bouton de téléchargement manquait sur
                // le chemin le plus courant, et la reprise après flux cassé ne
                // savait pas quelle source venait d'échouer.
                playingLink = link
                _resolved.value = stream
            } else {
                _resolveError.value = getString(Res.string.details_resolve_error, link.hoster)
            }
        }
    }

    /**
     * Met une source en file de téléchargement.
     *
     * On résout **ici** plutôt que de laisser la file le faire : c'est le seul
     * moment où l'on sait dire à l'utilisateur que la source est morte, pendant
     * qu'il la regarde. Une file qui échoue en silence deux minutes plus tard
     * n'aide personne.
     *
     * Le lien d'embed voyage avec le téléchargement : c'est lui qu'on rejouera
     * quand le jeton du flux aura expiré, pas cette URL-ci.
     */
    fun download(link: EmbedLink) {
        val meta = pendingMeta ?: return
        _resolveError.value = null
        _resolving.value = link.url
        viewModelScope.launch {
            val stream = runCatching { ExtractorRegistry.resolve(link) }.getOrNull()
            _resolving.value = null
            if (stream == null) {
                _resolveError.value = getString(Res.string.details_resolve_error, link.hoster)
                return@launch
            }
            DownloadQueue.enqueue(
                Download(
                    key = meta.key,
                    title = meta.title,
                    subtitle = meta.episodeLabel.orEmpty(),
                    imageUrl = meta.imageUrl,
                    tmdbId = meta.tmdbId,
                    isTv = meta.isTv,
                    createdAt = System.currentTimeMillis(),
                    sourceUrl = link.url,
                    hoster = link.hoster,
                    language = link.language.orEmpty(),
                ),
                stream,
            )
        }
    }

    /**
     * Avancement du téléchargement d'une saison entière.
     *
     * `checked` compte les épisodes déjà examinés, pas ceux téléchargés : la
     * partie longue est la recherche d'une source qui marche, la mise en file
     * est instantanée.
     */
    data class SeasonDownload(
        val season: Int,
        val checked: Int,
        val total: Int,
        val queued: Int,
        val failed: Int,
    ) {
        val done: Boolean get() = checked >= total
    }

    private val _seasonDownload = MutableStateFlow<SeasonDownload?>(null)
    val seasonDownload: StateFlow<SeasonDownload?> = _seasonDownload

    private var seasonJob: Job? = null

    /**
     * Cherche-t-on en ce moment la meilleure source à télécharger.
     *
     * Un booléen et non une URL, contrairement à [_resolving] : ici on ne
     * désigne aucune ligne du panneau, on cherche parmi toutes. Le bouton s'en
     * sert pour tourner — la recherche prend plusieurs secondes, et sans rien à
     * l'écran l'appui semble n'avoir rien fait.
     */
    private val _downloadSearching = MutableStateFlow(false)
    val downloadSearching: StateFlow<Boolean> = _downloadSearching

    /**
     * Télécharge le film ou l'épisode affiché, **dans la meilleure définition
     * trouvable**.
     *
     * C'est le pendant de « Télécharger la saison » pour un titre seul, et il
     * comblait un trou : jusqu'ici un film ne pouvait être téléchargé qu'en
     * désignant une source à la main, ou en lançant la lecture — deux chemins
     * qui donnent ce qu'on a sous la main, pas le meilleur. Une saison était
     * donc mieux servie qu'un film.
     *
     * Sans effet si les sources ne sont pas encore chargées : il n'y aurait rien
     * à départager.
     */
    fun downloadBest() {
        if (_downloadSearching.value) return
        val active = _sources.value as? SourcesState.Active ?: return
        val meta = pendingMeta ?: return
        val minutes = _selectedEpisode.value?.episode?.runtime ?: playbackMinutes

        _resolveError.value = null
        _downloadSearching.value = true
        viewModelScope.launch {
            val lang = settings.streamLanguage.first().name
            val retenu = runCatching { bestDownloadCandidate(active.links, lang, minutes) }.getOrNull()
            _downloadSearching.value = false
            if (retenu == null) {
                // Aucune source jouable : on le dit, plutôt que de laisser la
                // file échouer en silence deux minutes plus tard.
                _resolveError.value = getString(Res.string.details_lang_unavailable, lang)
                return@launch
            }
            val (lien, flux) = retenu
            DownloadQueue.enqueue(
                Download(
                    key = meta.key,
                    title = meta.title,
                    subtitle = meta.episodeLabel.orEmpty(),
                    imageUrl = meta.imageUrl,
                    tmdbId = meta.tmdbId,
                    isTv = meta.isTv,
                    createdAt = System.currentTimeMillis(),
                    sourceUrl = lien.url,
                    hoster = lien.hoster,
                    language = lien.language.orEmpty(),
                ),
                flux,
            )
        }
    }

    /**
     * La meilleure source téléchargeable parmi les candidats, ou null.
     *
     * ### Pourquoi on ne s'arrête pas au premier qui marche
     *
     * Un téléchargement se garde : sa définition compte davantage que la
     * seconde qu'on met à la choisir — c'est l'inverse du compromis de la
     * lecture, où l'on part vite avec ce qu'on sait déjà. On descend donc les
     * candidats, on les résout, on les sonde, **et on lit leur définition au
     * passage** : le flux étant déjà résolu et sondé, cela ne coûte qu'une
     * requête de plus, et c'est ce qui permet de garder le meilleur plutôt que
     * le premier venu.
     *
     * Deux bornes empêchent l'emballement : on s'arrête dès [ENOUGH_HEIGHT],
     * qu'on ne battra pas sur ces catalogues, et le budget de
     * [MAX_TRIES_PER_EPISODE] plafonne le reste. Un flux non mesurable — MP4
     * progressif, qui n'annonce rien avant d'être ouvert — vaut le même pivot
     * qu'ailleurs, voir [UNKNOWN_HEIGHT].
     *
     * @param expectedMinutes durée attendue, qui écarte les leurres : un flux
     *   de vingt secondes là où l'épisode en fait quarante-deux minutes n'est
     *   pas l'épisode.
     */
    private suspend fun bestDownloadCandidate(
        links: List<EmbedLink>,
        lang: String,
        expectedMinutes: Int?,
    ): Pair<EmbedLink, PlayableStream>? {
        val tried = mutableSetOf<String>()
        var best: Triple<EmbedLink, PlayableStream, Int>? = null
        repeat(MAX_TRIES_PER_EPISODE) {
            if ((best?.third ?: 0) >= ENOUGH_HEIGHT) return@repeat
            val next = nextLinkFor(links, preferred = lang, excluded = tried, heights = _heights.value)
                ?: return@repeat
            tried += next.url
            val stream = runCatching { ExtractorRegistry.resolve(next) }.getOrNull()
            // Le même verdict que le lecteur, durée comprise.
            if (stream == null || stream.url.isBlank() || !isStreamPlayable(stream, expectedMinutes)) {
                return@repeat
            }
            val hauteur = streamHeights(stream).firstOrNull() ?: UNKNOWN_HEIGHT
            if (hauteur > (best?.third ?: -1)) best = Triple(next, stream, hauteur)
        }
        return best?.let { it.first to it.second }
    }

    /**
     * Télécharge toute la saison affichée, en choisissant pour chaque épisode
     * **une source que le lecteur saurait vraiment lire**.
     *
     * ### Pourquoi ce n'est pas « prendre la première source »
     *
     * Le panneau des sources en propose vingt-cinq dont la moitié répond 403,
     * sert une page d'erreur, ou renvoie l'URL leurre de vingt secondes. Aller
     * dans chaque fiche, ouvrir le panneau, faire un appui long et tomber sur
     * une source morte — c'est exactement le geste que cette fonction remplace.
     *
     * On rejoue donc la cascade de la lecture rapide, à l'identique :
     * [nextLinkFor] pour l'ordre (préférence de langue puis rang du
     * catalogue), résolution, puis **[isStreamPlayable] avec la durée attendue
     * de l'épisode**. C'est ce dernier contrôle qui écarte les leurres : un
     * flux qui dure vingt secondes là où l'épisode en fait quarante-deux
     * minutes n'est pas l'épisode.
     *
     * ### Ce qui borne le coût
     *
     * Chaque tentative est une résolution plus une ou deux requêtes de sonde.
     * On s'arrête donc à [MAX_TRIES_PER_EPISODE] essais par épisode : au-delà,
     * l'épisode est déclaré introuvable et on passe au suivant plutôt que de
     * marteler dix hébergeurs. Un épisode déjà téléchargé est sauté sans
     * aucune requête.
     *
     * La mise en file, elle, ne parallélise rien : [DownloadQueue] tient déjà
     * un titre à la fois, et c'est ce qui rend le premier épisode regardable
     * pendant que le deuxième se charge.
     */
    fun downloadSeason(season: Int) {
        if (seasonJob?.isActive == true) return
        val tv = _state.value as? DetailsState.Tv ?: return
        if (tv.season != season) return
        // Les épisodes non diffusés sont exclus : aucune source n'existe pour eux,
        // et les inclure faisait passer la cascade complète — six tentatives par
        // épisode — sur des numéros qui n'ont encore rien à offrir. La saison
        // semblait alors « échouer » alors qu'elle était simplement en avance.
        val episodes = tv.episodes.filter { it.episodeNumber > 0 && upcomingDate(it.airDate) == null }
        if (episodes.isEmpty()) return

        seasonJob = viewModelScope.launch {
            val lang = settings.streamLanguage.first().name
            val already = DownloadRepository().downloads.first().map { it.key }.toSet()
            var checked = 0
            var queued = 0
            var failed = 0
            _seasonDownload.value = SeasonDownload(season, 0, episodes.size, 0, 0)

            for (ep in episodes) {
                val key = episodeKey(season, ep.episodeNumber)
                // Déjà sur le disque : ni requête, ni file.
                if (key in already) {
                    checked++
                    _seasonDownload.value = SeasonDownload(season, checked, episodes.size, queued, failed)
                    continue
                }

                val links = seasonLinks(tv, season, ep.episodeNumber)
                val retenu = bestDownloadCandidate(links, lang, ep.runtime)
                if (retenu != null) {
                    val (lien, flux) = retenu
                    DownloadQueue.enqueue(
                        Download(
                            key = key,
                            title = tv.details.name,
                            subtitle = "S$season · E${ep.episodeNumber} — ${ep.name}",
                            imageUrl = ep.stillUrl() ?: tv.details.posterUrl(),
                            tmdbId = tmdbId,
                            isTv = true,
                            createdAt = System.currentTimeMillis(),
                            sourceUrl = lien.url,
                            hoster = lien.hoster,
                            language = lien.language.orEmpty(),
                        ),
                        flux,
                    )
                    queued++
                } else {
                    failed++
                }
                checked++
                _seasonDownload.value = SeasonDownload(season, checked, episodes.size, queued, failed)
            }
        }
    }

    /** Annule la recherche en cours. Les téléchargements déjà en file continuent. */
    fun cancelSeasonDownload() {
        seasonJob?.cancel()
        _seasonDownload.value = null
    }

    /**
     * Les liens d'un épisode : cache d'abord, interrogation des catalogues
     * sinon. Même chemin que [prefetchEpisodeSources], dont c'est la moitié
     * utile — sans le garde-fou « une seule fois », puisqu'ici on veut la
     * réponse et pas seulement la préchauffe.
     */
    private suspend fun seasonLinks(
        tv: DetailsState.Tv,
        season: Int,
        episode: Int,
    ): List<EmbedLink> {
        val providers = activeProviders()
        val key = episodeKey(season, episode)
        sourceCache.get(key, providers.map { it.name }.toSet())?.let { return it }

        val media = MediaRef.Episode(
            tmdbId,
            catalogTitle(tv.details.name),
            tv.details.year,
            season,
            episode,
        )
        val answered = mutableSetOf<String>()
        val links = coroutineScope {
            providers.map { provider ->
                async(Dispatchers.IO) {
                    val result = runCatching {
                        withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { provider.sourcesFor(media) }
                    }.getOrNull()
                    if (result != null) answered += provider.name
                    result.orEmpty().map { it.copy(provider = provider.name) }
                }
            }.awaitAll().flatten()
        }.distinctBy { it.url }
        if (links.isNotEmpty()) sourceCache.put(key, links, answered)
        return links
    }

    fun consumeResolved() { _resolved.value = null }
}

/**
 * Ce que la sonde a conclu d'une source, avant qu'on la choisisse.
 *
 * [UNKNOWN] n'est pas « douteux » : c'est « pas encore regardé ». Les mesures
 * partent trois par trois, sur les seules lignes visibles — une source non
 * sondée ne doit donc rien laisser croire.
 */
enum class LinkStatus { UNKNOWN, CHECKING, OK, DEAD }

/** Essais de sources par épisode avant d'abandonner. Voir DetailsViewModel.downloadSeason. */
private const val MAX_TRIES_PER_EPISODE = 6

/**
 * Définition au-delà de laquelle chercher mieux ne vaut plus les requêtes.
 *
 * Un téléchargement se garde : sa qualité compte davantage que la seconde qu'on
 * met à la choisir, et c'est pourquoi la recherche ne s'arrête plus au premier
 * flux jouable. Elle doit tout de même s'arrêter — 1080p est le plafond réel de
 * ces catalogues, et le budget de [MAX_TRIES_PER_EPISODE] borne le reste.
 */
private const val ENOUGH_HEIGHT = 1080
