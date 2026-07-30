package fr.moovie.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.search.SearchHistoryRepository
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.currentTmdbLanguage
import fr.moovie.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.watch.WatchlistEntry
import kotlinx.coroutines.flow.map
import fr.moovie.tv.data.tmdb.TmdbItem

@OptIn(FlowPreview::class)
class SearchViewModel : ViewModel() {

    private val settings = SettingsRepository()
    private val historyRepo = SearchHistoryRepository()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<SearchState>(SearchState.Idle)
    val results: StateFlow<SearchState> = _results

    val history: StateFlow<List<String>> =
        historyRepo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Recherche live : débounce la saisie, annule la requête précédente.
        viewModelScope.launch {
            _query.debounce(350).distinctUntilChanged().collectLatest { q ->
                val term = q.trim()
                if (term.isBlank()) {
                    _results.value = SearchState.Idle
                    return@collectLatest
                }
                _results.value = SearchState.Loading
                val apiKey = settings.tmdbApiKey.first()
                if (apiKey.isBlank()) {
                    _results.value = SearchState.NeedsKey
                    return@collectLatest
                }
                val repo = TmdbRepository(currentTmdbLanguage())
                runCatching { repo.search(apiKey, term) }
                    .onSuccess { _results.value = if (it.isEmpty()) SearchState.Empty else SearchState.Results(it) }
                    .onFailure { _results.value = SearchState.Empty }
            }
        }
    }

    private val watchRepo = WatchProgressRepository()

    /** Clés des titres déjà mis de côté (badge + libellé du menu d'appui long). */
    val watchlistKeys: StateFlow<Set<String>> = watchRepo.watchlist
        .map { list -> list.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Met un résultat de recherche de côté. */
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

    /** Retire un titre de la liste. */
    fun removeFromWatchlist(key: String) {
        viewModelScope.launch { watchRepo.removeFromWatchlist(key) }
    }

    fun setQuery(q: String) { _query.value = q }

    /** Mémorise la requête dans l'historique (appelé à l'ouverture d'un résultat). */
    fun remember() {
        viewModelScope.launch { historyRepo.add(_query.value) }
    }

    fun removeHistory(q: String) {
        viewModelScope.launch { historyRepo.remove(q) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepo.clear() }
    }
}
