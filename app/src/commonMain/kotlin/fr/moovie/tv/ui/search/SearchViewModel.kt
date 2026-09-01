package fr.moovie.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.search.SearchFilters
import fr.moovie.tv.data.search.SearchFiltersRepository
import fr.moovie.tv.data.search.SearchHistoryRepository
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.currentTmdbLanguage
import fr.moovie.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

    private val filtersRepo = SearchFiltersRepository()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** Filtres retenus d'une session à l'autre. */
    val filters: StateFlow<SearchFilters> = filtersRepo.filters
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchFilters.DEFAULT)

    fun setFilters(value: SearchFilters) {
        viewModelScope.launch { filtersRepo.set(value) }
    }

    fun resetFilters() {
        viewModelScope.launch { filtersRepo.reset() }
    }

    private val _results = MutableStateFlow<SearchState>(SearchState.Idle)
    val results: StateFlow<SearchState> = _results

    val history: StateFlow<List<String>> =
        historyRepo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Recherche live : débounce la saisie, annule la requête précédente.
        viewModelScope.launch {
            // Les filtres relancent la recherche au même titre que la saisie :
            // ils changent ce qui est demandé au service (`include_adult`) et
            // combien de pages il faut rapporter avant de classer.
            combine(_query.debounce(350), filters) { q, f -> q to f }
                .distinctUntilChanged()
                .collectLatest { (q, activeFilters) ->
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
                    // Une page suffit tant qu'on ne réordonne rien : c'est le
                    // cas courant, et en payer trois coûterait deux requêtes
                    // sur chaque frappe.
                    val pages = if (activeFilters.isActive) DEEP_PAGES else 1
                    runCatching { repo.search(apiKey, term, activeFilters, pages) }
                        .onSuccess {
                            _results.value =
                                if (it.isEmpty()) SearchState.Empty else SearchState.Results(it)
                        }
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

    private companion object {
        /**
         * Pages rapportées quand un tri ou un filtre est posé. Soixante titres
         * : assez pour que « les mieux notés » veuille dire quelque chose, pas
         * au point de faire trois allers-retours sur une recherche ordinaire.
         */
        const val DEEP_PAGES = 3
    }
}
