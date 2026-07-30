package fr.moovie.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.currentTmdbLanguage
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.watch.WatchlistEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.home_needs_key
import fr.moovie.tv.resources.home_no_results_key
import fr.moovie.tv.resources.home_row_top_movies
import fr.moovie.tv.resources.home_row_trending_movies
import fr.moovie.tv.resources.home_row_trending_tv
import fr.moovie.tv.resources.home_tmdb_error
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Charge les rangées de l'accueil depuis TMDB. Sans clé API configurée,
 * renvoie NeedsApiKey pour renvoyer l'utilisateur vers les réglages.
 */
class HomeViewModel : ViewModel() {

    private val settings = SettingsRepository()
    private val watchRepo = WatchProgressRepository()
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state

    /** Contenus en cours → rail « Reprendre la lecture » (au-dessus des tendances). */
    val resume: StateFlow<List<ResumeEntry>> = watchRepo.continueWatching
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Clés vues → badge ✓ sur les affiches de films. */
    /** Titres mis de côté, alimentant le rail « À regarder plus tard ». */
    val watchlist: StateFlow<List<WatchlistEntry>> = watchRepo.watchlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Met un titre de côté depuis une affiche du catalogue.
     *
     * `totalEpisodes` reste à 0 : une carte TMDB ne porte pas le nombre
     * d'épisodes. La série ne sortira donc pas d'elle-même de la liste tant
     * qu'on n'aura pas ouvert sa fiche, qui complète l'entrée.
     */
    fun addToWatchlist(item: TmdbItem) {
        viewModelScope.launch {
            watchRepo.addToWatchlist(
                WatchlistEntry(
                    key = if (item.isTv) WatchlistEntry.tvKey(item.id) else WatchlistEntry.movieKey(item.id),
                    tmdbId = item.id,
                    isTv = item.isTv,
                    title = item.displayTitle,
                    imageUrl = item.posterUrl(),
                ),
            )
        }
    }

    /** Retire un titre de la watchlist (menu d'appui long du rail). */
    fun removeFromWatchlist(key: String) {
        viewModelScope.launch { watchRepo.removeFromWatchlist(key) }
    }

    val watched: StateFlow<Set<String>> = watchRepo.watched
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Retire une entrée du rail « Reprendre » (progression remise à zéro). */
    fun removeResume(key: String) {
        viewModelScope.launch { watchRepo.remove(key) }
    }

    /** Marque le contenu comme vu (le retire aussi du rail « Reprendre »). */
    fun markResumeWatched(key: String) {
        viewModelScope.launch { watchRepo.setWatched(key, true) }
    }

    init {
        // Réactif : recharge automatiquement dès que la clé TMDB change
        // (saisie dans les réglages ou injectée par adb).
        viewModelScope.launch {
            settings.tmdbApiKey.collect { apiKey ->
                if (apiKey.isBlank()) {
                    _state.value = HomeState.NeedsApiKey(getString(Res.string.home_needs_key))
                } else {
                    loadRows(apiKey)
                }
            }
        }
    }

    private suspend fun loadRows(apiKey: String) {
        _state.value = HomeState.Loading
        val repo = TmdbRepository(currentTmdbLanguage())
        runCatching {
            listOf(
                HomeRow(getString(Res.string.home_row_trending_movies), repo.trendingMovies(apiKey)),
                HomeRow(getString(Res.string.home_row_trending_tv), repo.trendingTv(apiKey)),
                HomeRow(getString(Res.string.home_row_top_movies), repo.topRatedMovies(apiKey)),
            ).filter { it.items.isNotEmpty() }
        }.onSuccess { rows ->
            _state.value =
                if (rows.isEmpty()) HomeState.NeedsApiKey(getString(Res.string.home_no_results_key))
                else HomeState.Ready(rows)
        }.onFailure {
            _state.value = HomeState.NeedsApiKey(getString(Res.string.home_tmdb_error, it.message ?: ""))
        }
    }
}
