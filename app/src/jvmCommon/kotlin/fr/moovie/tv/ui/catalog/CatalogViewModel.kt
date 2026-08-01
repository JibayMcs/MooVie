package fr.moovie.tv.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.search.ExploreChoice
import fr.moovie.tv.data.search.SearchHistoryRepository
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.currentTmdbLanguage
import fr.moovie.tv.data.tmdb.Genre
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

/** Une entrée du volet gauche : un intitulé de section, ou un genre atteignable. */
sealed interface CatalogEntry {
    /** « Films » / « Séries » — décoratif, non focalisable. */
    data class Header(val isTv: Boolean) : CatalogEntry
    data class GenreEntry(val isTv: Boolean, val genre: Genre) : CatalogEntry
}

/** Genre affiché dans la grille de droite. */
data class CatalogSelection(val isTv: Boolean, val genreId: Int)

/**
 * Page « Catalogue » : parcourir TMDB par genre, films et séries séparés.
 *
 * Séparée de la recherche par texte à dessein — mélanger « je cherche un titre
 * précis » et « je regarde ce qui existe » dans un même écran obligeait à
 * traverser un champ de saisie (et son clavier virtuel) pour atteindre les
 * genres.
 *
 * Le volet gauche est **une seule liste** : intitulé « Films », ses genres,
 * intitulé « Séries », ses genres. Pas de mode à basculer — sur une télécommande,
 * remonter en haut d'un volet pour changer de type se paie à chaque aller-retour.
 */
class CatalogViewModel : ViewModel() {

    private val settings = SettingsRepository()
    private val watchRepo = WatchProgressRepository()
    private val historyRepo = SearchHistoryRepository()

    private val _entries = MutableStateFlow<List<CatalogEntry>>(emptyList())
    val entries: StateFlow<List<CatalogEntry>> = _entries

    private val _selection = MutableStateFlow<CatalogSelection?>(null)
    val selection: StateFlow<CatalogSelection?> = _selection

    private val _items = MutableStateFlow<List<TmdbItem>>(emptyList())
    val items: StateFlow<List<TmdbItem>> = _items

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val state: StateFlow<CatalogState> = _state

    /** Titres déjà vus : badge sur les affiches de la grille. */
    val watched: StateFlow<Set<String>> = watchRepo.watched
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Titres mis de côté : badge et libellé du menu d'appui long. */
    val watchlistKeys: StateFlow<Set<String>> = watchRepo.watchlist
        .map { list -> list.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Page TMDB déjà chargée pour la sélection courante. */
    private var page = 1

    /** Vrai tant que TMDB a encore des pages à donner pour ce genre. */
    private var hasMore = true

    /** Empêche deux chargements concurrents de la même page. */
    private var loading = false

    /**
     * Change de sélection à chaque fois qu'on la modifie : une réponse en
     * retard sur un genre qu'on vient de quitter ne doit pas remplir la grille
     * du genre courant.
     */
    private var generation = 0

    init {
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            if (apiKey.isBlank()) {
                _state.value = CatalogState.NeedsKey
                return@launch
            }
            val repo = TmdbRepository(currentTmdbLanguage())
            // Les deux listes de genres de TMDB diffèrent : « Action & Adventure »
            // n'existe que côté séries, « Aventure » que côté films.
            val movies = runCatching { repo.genres(apiKey, isTv = false) }.getOrDefault(emptyList())
            val series = runCatching { repo.genres(apiKey, isTv = true) }.getOrDefault(emptyList())

            _entries.value = buildList {
                if (movies.isNotEmpty()) {
                    add(CatalogEntry.Header(isTv = false))
                    movies.forEach { add(CatalogEntry.GenreEntry(isTv = false, genre = it)) }
                }
                if (series.isNotEmpty()) {
                    add(CatalogEntry.Header(isTv = true))
                    series.forEach { add(CatalogEntry.GenreEntry(isTv = true, genre = it)) }
                }
            }

            // Réouverture sur le dernier genre parcouru : refaire le chemin
            // jusqu'au sien à chaque session est pénible à la télécommande.
            // (Comportement hérité de l'ancienne page « explorer » de la
            // recherche — il aurait été perdu en la démontant.)
            historyRepo.lastExplore.first()?.let { select(it.isTv, it.genreId) }
        }
    }

    /** Appelé quand le focus atteint un genre du volet gauche. */
    fun select(isTv: Boolean, genreId: Int) {
        val next = CatalogSelection(isTv, genreId)
        if (_selection.value == next) return

        _selection.value = next
        generation++
        page = 1
        hasMore = true
        _items.value = emptyList()
        _state.value = CatalogState.Loading
        loadNextPage()
        viewModelScope.launch { historyRepo.setLastExplore(ExploreChoice(isTv, genreId)) }
    }

    /**
     * Charge la page suivante. Appelé quand la grille approche de sa fin —
     * charger d'un coup les vingt pages d'un genre populaire serait absurde,
     * et TMDB les pagine de toute façon par vingt titres.
     */
    fun loadMore() {
        if (loading || !hasMore || _selection.value == null) return
        loadNextPage()
    }

    private fun loadNextPage() {
        val selection = _selection.value ?: return
        val gen = generation
        loading = true
        viewModelScope.launch {
            val apiKey = settings.tmdbApiKey.first()
            if (apiKey.isBlank()) {
                _state.value = CatalogState.NeedsKey
                loading = false
                return@launch
            }
            val fetched = runCatching {
                TmdbRepository(currentTmdbLanguage())
                    .discover(apiKey, selection.isTv, selection.genreId, page)
            }.getOrDefault(emptyList())

            // Genre changé pendant la requête : ce lot ne concerne plus l'écran.
            if (gen != generation) return@launch

            // Une page vide (ou plus courte que prévu) marque la fin du genre :
            // sans ce garde-fou, la grille redemanderait la page suivante à
            // chaque défilement en bout de liste.
            hasMore = fetched.isNotEmpty()
            if (fetched.isNotEmpty()) page++

            // TMDB renvoie parfois le même titre sur deux pages : dédoublonner
            // évite des clés dupliquées dans la grille, qui plantent LazyGrid.
            val merged = (_items.value + fetched).distinctBy { it.id }
            _items.value = merged
            _state.value = if (merged.isEmpty()) CatalogState.Empty else CatalogState.Ready
            loading = false
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

sealed interface CatalogState {
    /** Aucun genre choisi : la grille invite à en prendre un. */
    data object Idle : CatalogState
    data object Loading : CatalogState
    data object NeedsKey : CatalogState
    data object Empty : CatalogState
    data object Ready : CatalogState
}
