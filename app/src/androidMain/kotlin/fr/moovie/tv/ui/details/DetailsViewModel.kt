package fr.moovie.tv.ui.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.R
import fr.moovie.tv.data.settings.LocaleManager
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.data.sources.EmbedLink
import fr.moovie.tv.data.sources.ExtractorRegistry
import fr.moovie.tv.data.sources.PlayableStream
import fr.moovie.tv.data.sources.ProviderRegistry
import fr.moovie.tv.data.tmdb.Episode
import fr.moovie.tv.data.tmdb.MovieDetails
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.data.tmdb.TvDetails
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.WatchProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Movie(val details: MovieDetails) : DetailsState
    data class Tv(val details: TvDetails, val season: Int, val episodes: List<Episode>) : DetailsState
    data class Error(val message: String) : DetailsState
}

/** Statut de chargement d'un provider donné. */
enum class ProviderStatus { LOADING, DONE, EMPTY, FAILED }

data class ProviderProgress(val name: String, val status: ProviderStatus)

/**
 * État du panneau de sources. Idle = fermé. Active = panneau ouvert (dès le clic),
 * avec les liens accumulés et la progression par provider, mis à jour en streaming.
 */
sealed interface SourcesState {
    data object Idle : SourcesState
    data class Active(
        val links: List<EmbedLink>,
        val providers: List<ProviderProgress>,
    ) : SourcesState {
        val anyLoading: Boolean get() = providers.any { it.status == ProviderStatus.LOADING }
    }
}

/** Délai max par provider avant de le marquer en échec (n'affecte que ce provider). */
private const val PROVIDER_TIMEOUT_MS = 12000L

/**
 * État de la « lecture rapide » : résolution automatique de la meilleure source
 * dans la langue préférée, sans passer par le panneau.
 */
sealed interface QuickPlayState {
    data object Idle : QuickPlayState
    /** [label] = descripteur technique ("VF" ou "S1E3 · VF"), formaté par l'UI. */
    data class Searching(val label: String) : QuickPlayState
    /** [lang] = langue manquante ("VF"…), formatée par l'UI. */
    data class Unavailable(val lang: String) : QuickPlayState
}

class DetailsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)

    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state

    private val _sources = MutableStateFlow<SourcesState>(SourcesState.Idle)
    val sources: StateFlow<SourcesState> = _sources

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

    private var quickPlayJob: Job? = null

    /** Langue de stream préférée de l'utilisateur (pour trier/prioriser les sources). */
    val streamLanguage: StateFlow<StreamLanguage> = settings.streamLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamLanguage.VF)

    private val watchRepo = WatchProgressRepository(app)

    /** Clés marquées vues (badge ✓ sur les épisodes / le film). */
    val watched: StateFlow<Set<String>> = watchRepo.watched
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Progression en cours par clé (mini-barre sur les épisodes commencés). */
    val resume: StateFlow<Map<String, ResumeEntry>> = watchRepo.continueWatching
        .map { list -> list.associateBy { it.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
        pendingMeta = null
        this.tmdbId = tmdbId
        this.isTv = isTv
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            if (apiKey.isBlank()) {
                _state.value = DetailsState.Error(str(R.string.details_needs_key))
                return@launch
            }
            val repo = TmdbRepository(LocaleManager.tmdbLanguage(getApplication()))
            runCatching {
                if (isTv) {
                    val d = repo.tvDetails(apiKey, tmdbId)
                    val firstSeason = d.seasons.map { it.seasonNumber }.filter { it > 0 }.minOrNull() ?: 1
                    val eps = repo.season(apiKey, tmdbId, firstSeason).episodes
                    DetailsState.Tv(d, firstSeason, eps)
                } else {
                    DetailsState.Movie(repo.movieDetails(apiKey, tmdbId))
                }
            }.onSuccess {
                _state.value = it
                // Fiche film : sources chargées immédiatement en arrière-plan
                // pour que le bouton « Lire » soit prêt sans ouvrir le panneau.
                if (it is DetailsState.Movie) loadMovieSources()
            }.onFailure { _state.value = DetailsState.Error(str(R.string.details_tmdb_error, it.message ?: "")) }
        }
    }

    fun selectSeason(season: Int) {
        val tv = _state.value as? DetailsState.Tv ?: return
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            val repo = TmdbRepository(LocaleManager.tmdbLanguage(getApplication()))
            runCatching { repo.season(apiKey, tmdbId, season).episodes }
                .onSuccess { _state.value = tv.copy(season = season, episodes = it) }
        }
    }

    /** Clé de reprise du contenu dont on charge les sources (film ou épisode). */
    var playbackKey: String = ""
        private set

    /** Charge les sources du film courant, en streaming par provider. */
    fun loadMovieSources() {
        val movie = _state.value as? DetailsState.Movie ?: return
        playbackKey = movieKey()
        pendingMeta = ResumeEntry(
            key = playbackKey,
            tmdbId = tmdbId,
            isTv = false,
            title = movie.details.title,
            imageUrl = movie.details.backdropUrl() ?: movie.details.posterUrl(),
        )
        startSourceLoad { it.movieSources(movie.details.title, movie.details.year) }
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
        val still = tv.episodes.takeIf { tv.season == season }
            ?.firstOrNull { it.episodeNumber == episode }?.stillUrl()
        pendingMeta = ResumeEntry(
            key = playbackKey,
            tmdbId = tmdbId,
            isTv = true,
            season = season,
            episode = episode,
            title = tv.details.name,
            imageUrl = still ?: tv.details.backdropUrl() ?: tv.details.posterUrl(),
        )
        startSourceLoad { it.tvSources(tv.details.name, tv.details.year, season, episode) }
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

    /**
     * Ouvre le panneau immédiatement (tous providers en LOADING) puis interroge
     * chaque provider en parallèle ; chaque résultat met à jour l'état de façon
     * atomique → les sources apparaissent au fil de l'eau. Un provider lent/mort
     * passe en FAILED après [PROVIDER_TIMEOUT_MS] sans bloquer les autres.
     */
    /** Génération de chargement : invalide les résultats des fiches précédentes. */
    private var loadGeneration = 0

    private fun startSourceLoad(query: suspend (fr.moovie.tv.data.sources.SourceProvider) -> List<EmbedLink>) {
        val generation = ++loadGeneration
        viewModelScope.launch {
            // Réglages utilisateur : providers désactivés + ordre de priorité.
            val disabled = settings.disabledProviders.first()
            val order = settings.providerOrder.first()
            val providers = ProviderRegistry.all
                .filter { it.name !in disabled }
                .sortedBy { p ->
                    order.indexOf(p.name).let { if (it == -1) order.size + ProviderRegistry.all.indexOf(p) else it }
                }
            val rank = providers.mapIndexed { i, p -> p.name to i }.toMap()
            if (generation != loadGeneration) return@launch
            _sources.value = SourcesState.Active(
                links = emptyList(),
                providers = providers.map { ProviderProgress(it.name, ProviderStatus.LOADING) },
            )
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
        quickPlayJob = viewModelScope.launch {
            val lang = settings.streamLanguage.first().name
            _quickPlay.value = QuickPlayState.Searching(if (label.isBlank()) lang else "$label · $lang")
            _resolveError.value = null
            val tried = mutableSetOf<String>()
            while (true) {
                val active = _sources.value as? SourcesState.Active
                if (active == null) {
                    _quickPlay.value = QuickPlayState.Idle
                    return@launch
                }
                val next = active.links.firstOrNull { it.language == lang && it.url !in tried }
                if (next != null) {
                    tried += next.url
                    val stream = runCatching { ExtractorRegistry.resolve(next) }.getOrNull()
                    if (gen != resolveGen) return@launch // titre changé entre-temps
                    if (stream != null && stream.url.isNotBlank()) {
                        pendingMeta?.let { watchRepo.register(it) }
                        _quickPlay.value = QuickPlayState.Idle
                        _resolved.value = stream
                        return@launch
                    }
                    continue
                }
                if (!active.anyLoading) {
                    when {
                        tried.isNotEmpty() -> {
                            _resolveError.value = str(R.string.details_no_player, lang)
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
            // Titre changé pendant la résolution : ne pas écraser la lecture courante.
            if (gen != resolveGen) return@launch
            if (stream != null) {
                // La lecture va démarrer : persiste les métadonnées pour le
                // rail « Reprendre » (la position suivra depuis le lecteur).
                pendingMeta?.let { watchRepo.register(it) }
                _resolved.value = stream
            } else {
                _resolveError.value = str(R.string.details_resolve_error, link.hoster)
            }
        }
    }

    fun consumeResolved() { _resolved.value = null }

    private fun str(resId: Int, vararg args: Any) = getApplication<Application>().getString(resId, *args)
}
