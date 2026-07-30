package fr.moovie.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.search.ExploreChoice
import fr.moovie.tv.data.search.SearchHistoryRepository
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.currentTmdbLanguage
import fr.moovie.tv.data.tmdb.Genre
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

    // ── Page « explorer » : genres TMDB + grille /discover ──────────────────
    // Le champ vide n'affichait que l'historique de recherche : la même page
    // sert maintenant à « je cherche un titre » et « je cherche quoi voir »,
    // sans rien ajouter à la topbar (le focus D-pad y reste sur la recherche).

    private val _exploreIsTv = MutableStateFlow(false)

    /** Type exploré : films (false) ou séries (true). */
    val exploreIsTv: StateFlow<Boolean> = _exploreIsTv

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres

    private val _selectedGenre = MutableStateFlow<Int?>(null)
    val selectedGenre: StateFlow<Int?> = _selectedGenre

    private val _discover = MutableStateFlow<SearchState>(SearchState.Idle)

    /** Grille du genre choisi (Idle tant qu'aucun genre n'est sélectionné). */
    val discover: StateFlow<SearchState> = _discover

    /** L'utilisateur a touché au sélecteur ou aux genres depuis l'ouverture. */
    private var exploreTouched = false

    init {
        // Réouverture sur le dernier genre exploré : sur une télécommande,
        // refaire le chemin jusqu'à son genre à chaque session est pénible.
        viewModelScope.launch {
            val saved = historyRepo.lastExplore.first() ?: return@launch
            // Le disque peut répondre après un premier appui : dans ce cas la
            // restauration écraserait le choix qu'on vient de faire.
            if (exploreTouched) return@launch
            _exploreIsTv.value = saved.isTv
            _selectedGenre.value = saved.genreId
        }
        // Les deux listes de genres TMDB diffèrent (film / série) : on recharge
        // à chaque changement de type.
        viewModelScope.launch {
            _exploreIsTv.collectLatest { isTv ->
                val apiKey = settings.tmdbApiKey.first()
                _genres.value = if (apiKey.isBlank()) {
                    emptyList()
                } else {
                    runCatching { TmdbRepository(currentTmdbLanguage()).genres(apiKey, isTv) }
                        .getOrDefault(emptyList())
                }
            }
        }
        viewModelScope.launch {
            combine(_exploreIsTv, _selectedGenre) { isTv, genre -> isTv to genre }
                .distinctUntilChanged()
                .collectLatest { (isTv, genre) ->
                    if (genre == null) {
                        _discover.value = SearchState.Idle
                        return@collectLatest
                    }
                    _discover.value = SearchState.Loading
                    val apiKey = settings.tmdbApiKey.first()
                    if (apiKey.isBlank()) {
                        _discover.value = SearchState.NeedsKey
                        return@collectLatest
                    }
                    runCatching { TmdbRepository(currentTmdbLanguage()).discover(apiKey, isTv, genre) }
                        .onSuccess {
                            _discover.value =
                                if (it.isEmpty()) SearchState.Empty else SearchState.Results(it)
                        }
                        .onFailure { _discover.value = SearchState.Empty }
                }
        }
    }

    /**
     * Bascule films / séries. La sélection retombe à zéro : les identifiants de
     * genre ne se recoupent pas d'une liste à l'autre (18 = Drame côté film,
     * mais 18 n'existe pas côté série).
     */
    fun setExploreType(isTv: Boolean) {
        exploreTouched = true
        if (_exploreIsTv.value == isTv) return
        _exploreIsTv.value = isTv
        selectGenre(null)
    }

    /** Choisit un genre (ou le déselectionne en le rappuyant). */
    fun selectGenre(genreId: Int?) {
        exploreTouched = true
        val next = if (genreId == _selectedGenre.value) null else genreId
        _selectedGenre.value = next
        viewModelScope.launch {
            historyRepo.setLastExplore(next?.let { ExploreChoice(_exploreIsTv.value, it) })
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
