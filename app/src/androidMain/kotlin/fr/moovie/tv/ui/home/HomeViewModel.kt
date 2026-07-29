package fr.moovie.tv.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.R
import fr.moovie.tv.data.settings.LocaleManager
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.WatchProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// HomeRow / HomeState vivent désormais dans jvmCommon (ui/home/HomeState.kt),
// partagés avec l'écran d'accueil commun.

/**
 * Charge les rangées de l'accueil depuis TMDB. Sans clé API configurée,
 * renvoie NeedsApiKey pour renvoyer l'utilisateur vers les réglages.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository()
    private val watchRepo = WatchProgressRepository()
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state

    /** Contenus en cours → rail « Reprendre la lecture » (au-dessus des tendances). */
    val resume: StateFlow<List<ResumeEntry>> = watchRepo.continueWatching
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Clés vues → badge ✓ sur les affiches de films. */
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
                    _state.value = HomeState.NeedsApiKey(str(R.string.home_needs_key))
                } else {
                    loadRows(apiKey)
                }
            }
        }
    }

    private suspend fun loadRows(apiKey: String) {
        _state.value = HomeState.Loading
        val repo = TmdbRepository(LocaleManager.tmdbLanguage(getApplication()))
        runCatching {
            listOf(
                HomeRow(str(R.string.home_row_trending_movies), repo.trendingMovies(apiKey)),
                HomeRow(str(R.string.home_row_trending_tv), repo.trendingTv(apiKey)),
                HomeRow(str(R.string.home_row_top_movies), repo.topRatedMovies(apiKey)),
            ).filter { it.items.isNotEmpty() }
        }.onSuccess { rows ->
            _state.value =
                if (rows.isEmpty()) HomeState.NeedsApiKey(str(R.string.home_no_results_key))
                else HomeState.Ready(rows)
        }.onFailure {
            _state.value = HomeState.NeedsApiKey(str(R.string.home_tmdb_error, it.message ?: ""))
        }
    }

    private fun str(resId: Int, vararg args: Any) = getApplication<Application>().getString(resId, *args)
}
