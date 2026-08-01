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
import fr.moovie.tv.data.sources.ExtractorRegistry
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.data.sources.isStreamPlayable
import fr.moovie.tv.data.sources.streamQuality
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import fr.moovie.tv.data.sources.ProviderRegistry
import fr.moovie.tv.data.sources.SourceCacheRepository
import fr.moovie.tv.data.tmdb.Episode
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.data.tmdb.TvDetails
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.TitleMeta
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.watch.WatchlistEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.details_needs_key
import fr.moovie.tv.resources.details_no_player
import fr.moovie.tv.resources.details_resolve_error
import fr.moovie.tv.resources.details_tmdb_error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/** Délai max par provider avant de le marquer en échec (n'affecte que ce provider). */
private const val PROVIDER_TIMEOUT_MS = 12000L

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
        viewModelScope.launch {
            val label = runCatching {
                qualitySlots.withPermit {
                    ExtractorRegistry.resolve(link)?.let { streamQuality(it) }
                }
            }.getOrNull() ?: return@launch
            _qualities.value = _qualities.value + (link.url to label)
        }
    }

    /** Flux prêt à jouer (émis une fois qu'un extracteur a résolu un lien). */
    private val _resolved = MutableStateFlow<PlayableStream?>(null)
    val resolved: StateFlow<PlayableStream?> = _resolved

    /** Message transitoire si un lecteur choisi n'a pas pu être résolu. */
    private val _resolveError = MutableStateFlow<String?>(null)
    val resolveError: StateFlow<String?> = _resolveError

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
    private var playingLink: EmbedLink? = null

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

    fun movieKey() = "movie:$tmdbId"
    fun episodeKey(season: Int, episode: Int) = "tv:$tmdbId:s${season}e$episode"

    fun start(tmdbId: Int, isTv: Boolean) {
        if (this.tmdbId == tmdbId && _state.value !is DetailsState.Loading) return
        // Le ViewModel est partagé entre fiches (scope Activity) : purge l'état
        // de la fiche précédente (panneau sources, erreurs) avant de charger.
        quickPlayJob?.cancel()
        resolveGen++ // invalide toute résolution en vol de la fiche précédente
        _quickPlay.value = QuickPlayState.Idle
        _panelVisible.value = false
        _sources.value = SourcesState.Idle
        _resolveError.value = null
        _resolved.value = null
        _selectedEpisode.value = null
        pendingMeta = null
        rejectedLinks.clear()
        playingLink = null
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
                    val target = episodeToResume(
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
                    var eps = repo.season(apiKey, tmdbId, season).episodes
                    if (target.episode > eps.size) {
                        val next = seasons.firstOrNull { it > season }
                        if (next != null) {
                            season = next
                            eps = repo.season(apiKey, tmdbId, season).episodes
                        }
                    }
                    // Si la saison a changé en route, l'épisode visé redevient le
                    // premier de la nouvelle saison.
                    val focusEpisode =
                        if (season == target.season) target.episode.coerceAtMost(eps.size) else 1
                    resumeTarget = EpisodeRef(season, focusEpisode)
                    DetailsState.Tv(d, season, eps, resumeEpisode = focusEpisode)
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
            }.onFailure { _state.value = DetailsState.Error(getString(Res.string.details_tmdb_error, it.message ?: "")) }
        }
    }

    fun selectSeason(season: Int) {
        val tv = _state.value as? DetailsState.Tv ?: return
        _selectedEpisode.value = null
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            val repo = TmdbRepository(currentTmdbLanguage())
            runCatching { repo.season(apiKey, tmdbId, season).episodes }
                .onSuccess {
                    // Le repère ne vaut que pour la saison où l'on s'était
                    // arrêté : ailleurs, il désignerait un épisode au hasard.
                    val target = resumeTarget
                    _state.value = tv.copy(
                        season = season,
                        episodes = it,
                        resumeEpisode = if (target?.season == season) target.episode else 0,
                    )
                }
        }
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
            it.sourcesFor(MediaRef.Movie(tmdbId, movie.details.title, movie.details.year))
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
            it.sourcesFor(MediaRef.Episode(tmdbId, tv.details.name, tv.details.year, season, episode))
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
            // Réglages utilisateur : providers désactivés + ordre de priorité.
            // Lus AVANT le cache : une entrée n'est resservie que si elle a
            // interrogé tous les catalogues actifs aujourd'hui, sinon une mise à
            // jour qui en ajoute un resterait invisible sur les fiches déjà vues.
            val disabled = settings.disabledProviders.first()
            val order = settings.providerOrder.first()
            val providers = ProviderRegistry.all
                .filter { it.name !in disabled }
                .sortedBy { p ->
                    order.indexOf(p.name).let { if (it == -1) order.size + ProviderRegistry.all.indexOf(p) else it }
                }
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
        // Même filtre que la cascade : seule la langue préférée est enchaînée,
        // sinon on relancerait pour ne rien trouver.
        val lang = streamLanguage.value.name
        val hasAlternative = active.anyLoading ||
            active.links.any { it.language == lang && it.url !in rejectedLinks }
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
                val next = active.links.firstOrNull { it.language == lang && it.url !in tried }
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
                        _quickPlay.value = QuickPlayState.Idle
                        _resolved.value = stream
                        return@launch
                    }
                    // Écarté durablement : la sonde vient de le refuser, inutile
                    // d'y revenir si la cascade reprend plus tard.
                    rejectedLinks += next.url
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
            // Titre changé pendant la résolution : ne pas écraser la lecture courante.
            if (gen != resolveGen) return@launch
            if (stream != null) {
                // La lecture va démarrer : persiste les métadonnées pour le
                // rail « Reprendre » (la position suivra depuis le lecteur).
                pendingMeta?.let { watchRepo.register(it) }
                _resolved.value = stream
            } else {
                _resolveError.value = getString(Res.string.details_resolve_error, link.hoster)
            }
        }
    }

    fun consumeResolved() { _resolved.value = null }
}
