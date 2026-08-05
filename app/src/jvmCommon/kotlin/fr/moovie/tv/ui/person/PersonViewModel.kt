package fr.moovie.tv.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.currentTmdbLanguage
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.watch.WatchlistEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ce que l'écran d'une personne peut montrer. */
sealed interface PersonState {
    data object Loading : PersonState

    /** Filmographie trouvée, la plus récente en tête. */
    data class Ready(val credits: List<TmdbItem>) : PersonState

    /** Rien à montrer : personne sans rôle référencé, ou appel en échec. */
    data class Empty(val reason: String) : PersonState
}

/**
 * Filmographie d'une personne.
 *
 * Un ViewModel par écran, comme partout ailleurs — et non une extension de
 * `DetailsViewModel` : celui-ci est partagé à l'échelle de l'activité et porte
 * déjà l'état d'une fiche en cours de lecture. Y greffer une seconde recherche
 * aurait mêlé deux durées de vie sans rapport.
 */
class PersonViewModel : ViewModel() {

    private val settings = SettingsRepository()
    private val watchRepo = WatchProgressRepository()

    private val _state = MutableStateFlow<PersonState>(PersonState.Loading)
    val state: StateFlow<PersonState> = _state

    /** Titres déjà vus : badge sur les affiches, comme dans le catalogue. */
    val watched: StateFlow<Set<String>> = watchRepo.watched
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val watchlistKeys: StateFlow<Set<String>> = watchRepo.watchlist
        .map { list -> list.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Personne actuellement chargée : évite de tout refaire à chaque recomposition. */
    private var loadedId: Int? = null

    fun load(personId: Int, emptyMessage: String, errorMessage: String) {
        if (loadedId == personId) return
        loadedId = personId
        _state.value = PersonState.Loading
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            if (apiKey.isBlank()) {
                _state.value = PersonState.Empty(errorMessage)
                return@launch
            }
            val repo = TmdbRepository(currentTmdbLanguage())
            runCatching { repo.personCredits(apiKey, personId) }
                .onSuccess { credits ->
                    _state.value =
                        if (credits.isEmpty()) PersonState.Empty(emptyMessage)
                        else PersonState.Ready(credits)
                }
                // Une filmographie vide et un réseau en échec ne se ressemblent
                // pas : l'un est une réponse, l'autre un problème à réessayer.
                .onFailure {
                    loadedId = null
                    _state.value = PersonState.Empty(errorMessage)
                }
        }
    }

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

    fun removeFromWatchlist(key: String) {
        viewModelScope.launch { watchRepo.removeFromWatchlist(key) }
    }
}
