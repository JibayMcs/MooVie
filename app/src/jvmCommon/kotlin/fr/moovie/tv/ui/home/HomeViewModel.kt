package fr.moovie.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.home.HomeLayoutEntry
import fr.moovie.tv.data.home.HomeLayoutRepository
import fr.moovie.tv.data.home.HomeRowKind
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.currentTmdbLanguage
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.ui.catalog.CatalogSelection
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.oneCardPerSeries
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Charge les rangées de l'accueil depuis TMDB. Sans clé API configurée,
 * renvoie NeedsApiKey pour renvoyer l'utilisateur vers les réglages.
 */
class HomeViewModel : ViewModel() {

    private val settings = SettingsRepository()
    private val watchRepo = WatchProgressRepository()
    private val layoutRepo = HomeLayoutRepository()
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state

    /**
     * Contenus en cours → rail « Reprendre la lecture » (au-dessus des tendances).
     *
     * Regroupé par série : le rail répond à « où j'en suis », pas à « tout ce que
     * j'ai entamé ». L'historique, lui, garde le détail épisode par épisode.
     */
    val resume: StateFlow<List<ResumeEntry>> = watchRepo.continueWatching
        .map { it.oneCardPerSeries() }
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
        // Réactif sur les deux entrées : la clé TMDB (saisie dans les réglages ou
        // injectée par adb) *et* la disposition. Épingler un genre doit redessiner
        // l'accueil sur-le-champ ; le recharger au seul démarrage donnerait une
        // fonctionnalité qui a l'air de ne pas marcher.
        //
        // `collectLatest` : un épinglage pendant le chargement annule le lot en
        // cours plutôt que de le laisser écraser le suivant.
        viewModelScope.launch {
            combine(settings.tmdbApiKey, layoutRepo.layout, ::Pair)
                .collectLatest { (apiKey, layout) ->
                    if (apiKey.isBlank()) {
                        _state.value = HomeState.NeedsApiKey(getString(Res.string.home_needs_key))
                    } else {
                        loadSlots(apiKey, layout)
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
    private suspend fun recommendationRow(repo: TmdbRepository, apiKey: String, id: String): HomeRow? {
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

        return HomeRow(id, getString(Res.string.home_row_because, seed.title), fresh)
    }

    /**
     * Construit les créneaux dans l'ordre de la disposition.
     *
     * Les rangées se chargent **en parallèle** : elles étaient quatre et écrites
     * en dur, elles peuvent maintenant être quinze si l'utilisateur épingle. En
     * série, chaque genre ajouté aurait rallongé l'ouverture de l'accueil d'un
     * aller-retour TMDB.
     *
     * Chaque rangée encaisse son propre échec et rend une liste vide plutôt que
     * de faire tomber l'accueil entier : un genre que TMDB refuse ne doit pas
     * effacer les tendances.
     */
    private suspend fun loadSlots(apiKey: String, layout: List<HomeLayoutEntry>) {
        _state.value = HomeState.Loading
        val repo = TmdbRepository(currentTmdbLanguage())
        var failure: Throwable? = null

        val slots = coroutineScope {
            layout.filter { it.visible }
                .map { entry -> async { slotFor(entry, repo, apiKey) { failure = failure ?: it } } }
                .awaitAll()
                .filterNotNull()
        }

        // Une rangée vide disparaît ; « Reprendre » et « Ma liste » restent, leur
        // contenu n'étant pas connu ici — c'est l'écran qui les écarte s'il n'a
        // rien à y mettre.
        val kept = slots.filterNot { it is HomeSlot.Catalog && it.row.items.isEmpty() }

        _state.value = when {
            kept.any { it is HomeSlot.Catalog } -> HomeState.Ready(kept)
            failure != null ->
                HomeState.NeedsApiKey(getString(Res.string.home_tmdb_error, failure?.message ?: ""))
            else -> HomeState.NeedsApiKey(getString(Res.string.home_no_results_key))
        }
    }

    private suspend fun slotFor(
        entry: HomeLayoutEntry,
        repo: TmdbRepository,
        apiKey: String,
        onFailure: (Throwable) -> Unit,
    ): HomeSlot? {
        fun row(title: String, items: List<TmdbItem>, open: CatalogSelection? = null) =
            HomeSlot.Catalog(HomeRow(entry.id, title, items, open))

        suspend fun fetch(block: suspend () -> List<TmdbItem>): List<TmdbItem> =
            runCatching { block() }.onFailure(onFailure).getOrDefault(emptyList())

        return when (entry.kind) {
            HomeRowKind.RESUME -> HomeSlot.Resume
            HomeRowKind.WATCHLIST -> HomeSlot.Watchlist
            HomeRowKind.RECOMMENDATIONS ->
                runCatching { recommendationRow(repo, apiKey, entry.id) }
                    .onFailure(onFailure)
                    .getOrNull()
                    ?.let { HomeSlot.Catalog(it) }

            HomeRowKind.TRENDING_MOVIES -> row(
                getString(Res.string.home_row_trending_movies),
                fetch { repo.trendingMovies(apiKey) },
            )

            HomeRowKind.TRENDING_TV -> row(
                getString(Res.string.home_row_trending_tv),
                fetch { repo.trendingTv(apiKey) },
            )

            HomeRowKind.TOP_MOVIES -> row(
                getString(Res.string.home_row_top_movies),
                fetch { repo.topRatedMovies(apiKey) },
            )

            // Le titre vient du genre stocké, pas d'un appel TMDB : la rangée
            // s'affiche nommée dès la première image, et même hors ligne.
            HomeRowKind.GENRE -> entry.genre?.let { genre ->
                row(
                    genre.name,
                    fetch { repo.discover(apiKey, genre.isTv, genre.genreId) },
                    open = CatalogSelection(genre.isTv, genre.genreId),
                )
            }
        }
    }
}
