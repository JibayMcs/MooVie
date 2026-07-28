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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Movie(val details: MovieDetails) : DetailsState
    data class Tv(val details: TvDetails, val season: Int, val episodes: List<Episode>) : DetailsState
    data class Error(val message: String) : DetailsState
}

/** Liens d'embed résolus par le provider, ou état de résolution/erreur. */
sealed interface SourcesState {
    data object Idle : SourcesState
    data object Loading : SourcesState
    data class Loaded(val links: List<EmbedLink>) : SourcesState
    /** Aucune source trouvée (site ok mais pas de lecteur). */
    data object Empty : SourcesState
    data class Error(val message: String) : SourcesState
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

    /** Charge les liens d'embed pour le film courant. */
    fun loadMovieSources() {
        val movie = _state.value as? DetailsState.Movie ?: return
        _sources.value = SourcesState.Loading
        viewModelScope.launch {
            runCatching { ProviderRegistry.fstream.movieSources(movie.details.title, movie.details.year) }
                .onSuccess { emitSources(it) }
                .onFailure { _sources.value = SourcesState.Error(it.message ?: "erreur") }
        }
    }

    /** Charge les liens d'embed pour un épisode. */
    fun loadEpisodeSources(episode: Int) {
        val tv = _state.value as? DetailsState.Tv ?: return
        _sources.value = SourcesState.Loading
        viewModelScope.launch {
            runCatching {
                ProviderRegistry.fstream.tvSources(tv.details.name, tv.details.year, tv.season, episode)
            }.onSuccess { emitSources(it) }
                .onFailure { _sources.value = SourcesState.Error(it.message ?: "erreur") }
        }
    }

    private fun emitSources(links: List<EmbedLink>) {
        _sources.value = if (links.isEmpty()) SourcesState.Empty else SourcesState.Loaded(links)
    }

    /** Ferme le panneau des sources (retour à l'état inactif). */
    fun clearSources() {
        _sources.value = SourcesState.Idle
    }

    /** Résout un lien d'embed en flux jouable via les extracteurs. */
    fun play(link: EmbedLink) {
        viewModelScope.launch {
            val stream = runCatching { ExtractorRegistry.resolve(link) }.getOrNull()
            if (stream != null) _resolved.value = stream
            else _sources.value =
                SourcesState.Error("Impossible de résoudre « ${link.hoster} » (extracteur manquant ?).")
        }
    }

    fun consumeResolved() { _resolved.value = null }
}
