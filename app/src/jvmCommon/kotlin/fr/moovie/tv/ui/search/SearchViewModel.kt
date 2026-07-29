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
