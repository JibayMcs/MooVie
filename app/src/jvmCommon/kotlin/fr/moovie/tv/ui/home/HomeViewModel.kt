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
import fr.moovie.tv.resources.home_row_because
import fr.moovie.tv.resources.home_row_trending_movies
import fr.moovie.tv.resources.home_row_trending_tv
import fr.moovie.tv.resources.home_tmdb_error
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
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

    /**
     * Rangée « Parce que tu as regardé X », bâtie sur le dernier titre terminé.
     *
     * C'est la rangée qui joue avec la force de l'app plutôt que contre : elle
     * remonte des titres **proches de ce qu'on regarde déjà**, donc souvent plus
     * anciens — et ce sont eux qui ont des sources VF. Une rangée de nouveautés
     * ferait l'inverse : mesuré sur un film sorti quatre jours plus tôt, un seul
     * lien VF, et mort.
     *
     * Retourne null quand il n'y a pas d'historique ou rien à proposer : la
     * rangée disparaît plutôt que d'afficher un titre vide.
     */
    private suspend fun recommendationRow(repo: TmdbRepository, apiKey: String): HomeRow? {
        val seed = watchRepo.history.first().firstOrNull() ?: return null
        val items = runCatching { repo.recommendations(apiKey, seed.isTv, seed.tmdbId) }
            .getOrDefault(emptyList())
        if (items.isEmpty()) return null

        // Proposer ce qu'on a déjà vu, ou ce qu'on est en train de regarder,
        // n'a aucun intérêt — et le titre source lui-même reviendrait parfois.
        val seen = watchRepo.watched.first()
        val inProgress = watchRepo.continueWatching.first().map { it.key }.toSet()
        val fresh = items.filterNot { item ->
            val key = if (item.isTv) "tv:${item.id}" else "movie:${item.id}"
            key in seen || key in inProgress || (item.id == seed.tmdbId && item.isTv == seed.isTv)
        }
        if (fresh.isEmpty()) return null

        return HomeRow(getString(Res.string.home_row_because, seed.title), fresh)
    }

    private suspend fun loadRows(apiKey: String) {
        _state.value = HomeState.Loading
        val repo = TmdbRepository(currentTmdbLanguage())
        runCatching {
            listOfNotNull(
                // En tête, avant les tendances : c'est la seule rangée qui parle
                // de ce que *cet* utilisateur regarde.
                recommendationRow(repo, apiKey),
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
