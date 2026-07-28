package fr.moovie.tv.ui.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    /** Langue de stream préférée de l'utilisateur (pour trier/prioriser les sources). */
    val streamLanguage: StateFlow<StreamLanguage> = settings.streamLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamLanguage.VF)

    private var tmdbId = 0
    private var isTv = false

    fun start(tmdbId: Int, isTv: Boolean) {
        if (this.tmdbId == tmdbId && _state.value !is DetailsState.Loading) return
        this.tmdbId = tmdbId
        this.isTv = isTv
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            if (apiKey.isBlank()) {
                _state.value = DetailsState.Error("Clé TMDB manquante (Réglages).")
                return@launch
            }
            val repo = TmdbRepository(settings.uiLanguage.first())
            runCatching {
                if (isTv) {
                    val d = repo.tvDetails(apiKey, tmdbId)
                    val firstSeason = d.seasons.map { it.seasonNumber }.filter { it > 0 }.minOrNull() ?: 1
                    val eps = repo.season(apiKey, tmdbId, firstSeason).episodes
                    DetailsState.Tv(d, firstSeason, eps)
                } else {
                    DetailsState.Movie(repo.movieDetails(apiKey, tmdbId))
                }
            }.onSuccess { _state.value = it }
                .onFailure { _state.value = DetailsState.Error("Échec TMDB : ${it.message}") }
        }
    }

    fun selectSeason(season: Int) {
        val tv = _state.value as? DetailsState.Tv ?: return
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            val repo = TmdbRepository(settings.uiLanguage.first())
            runCatching { repo.season(apiKey, tmdbId, season).episodes }
                .onSuccess { _state.value = tv.copy(season = season, episodes = it) }
        }
    }

    /** Charge les sources du film courant, en streaming par provider. */
    fun loadMovieSources() {
        val movie = _state.value as? DetailsState.Movie ?: return
        startSourceLoad { it.movieSources(movie.details.title, movie.details.year) }
    }

    /** Charge les sources d'un épisode, en streaming par provider. */
    fun loadEpisodeSources(episode: Int) {
        val tv = _state.value as? DetailsState.Tv ?: return
        startSourceLoad { it.tvSources(tv.details.name, tv.details.year, tv.season, episode) }
    }

    /**
     * Ouvre le panneau immédiatement (tous providers en LOADING) puis interroge
     * chaque provider en parallèle ; chaque résultat met à jour l'état de façon
     * atomique → les sources apparaissent au fil de l'eau. Un provider lent/mort
     * passe en FAILED après [PROVIDER_TIMEOUT_MS] sans bloquer les autres.
     */
    private fun startSourceLoad(query: suspend (fr.moovie.tv.data.sources.SourceProvider) -> List<EmbedLink>) {
        val providers = ProviderRegistry.all
        _sources.value = SourcesState.Active(
            links = emptyList(),
            providers = providers.map { ProviderProgress(it.name, ProviderStatus.LOADING) },
        )
        providers.forEach { provider ->
            viewModelScope.launch(Dispatchers.IO) {
                val result = runCatching {
                    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { query(provider) }
                }
                _sources.update { st ->
                    val active = st as? SourcesState.Active ?: return@update st
                    val links = result.getOrNull().orEmpty()
                    val status = when {
                        result.isFailure || result.getOrNull() == null -> ProviderStatus.FAILED
                        links.isEmpty() -> ProviderStatus.EMPTY
                        else -> ProviderStatus.DONE
                    }
                    active.copy(
                        links = (active.links + links).distinctBy { it.url },
                        providers = active.providers.map {
                            if (it.name == provider.name) it.copy(status = status) else it
                        },
                    )
                }
            }
        }
    }

    /** Ferme le panneau des sources (retour à l'état inactif). */
    fun clearSources() {
        _sources.value = SourcesState.Idle
    }

    /** Résout un lien d'embed en flux jouable via les extracteurs. */
    fun play(link: EmbedLink) {
        _resolveError.value = null
        viewModelScope.launch {
            val stream = runCatching { ExtractorRegistry.resolve(link) }.getOrNull()
            if (stream != null) _resolved.value = stream
            else _resolveError.value = "Impossible de lire « ${link.hoster} ». Essaie un autre lecteur."
        }
    }

    fun consumeResolved() { _resolved.value = null }
}
