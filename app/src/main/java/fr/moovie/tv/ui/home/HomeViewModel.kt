package fr.moovie.tv.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeRow(val title: String, val items: List<TmdbItem>)

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val rows: List<HomeRow>) : HomeState
    data class NeedsApiKey(val reason: String) : HomeState
}

/**
 * Charge les rangées de l'accueil depuis TMDB. Sans clé API configurée,
 * renvoie NeedsApiKey pour renvoyer l'utilisateur vers les réglages.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            if (apiKey.isBlank()) {
                _state.value = HomeState.NeedsApiKey("Renseigne ta clé API TMDB dans les réglages.")
                return@launch
            }
            _state.value = HomeState.Loading
            val language = settings.uiLanguage.first()
            val repo = TmdbRepository(language)
            runCatching {
                val trendingMovies = repo.trendingMovies(apiKey)
                val trendingTv = repo.trendingTv(apiKey)
                val topRated = repo.topRatedMovies(apiKey)
                listOf(
                    HomeRow("Films tendances", trendingMovies),
                    HomeRow("Séries tendances", trendingTv),
                    HomeRow("Films les mieux notés", topRated),
                ).filter { it.items.isNotEmpty() }
            }.onSuccess { rows ->
                _state.value =
                    if (rows.isEmpty()) HomeState.NeedsApiKey("Aucun résultat — vérifie ta clé TMDB.")
                    else HomeState.Ready(rows)
            }.onFailure {
                _state.value = HomeState.NeedsApiKey("Échec du chargement TMDB : ${it.message}")
            }
        }
    }
}
