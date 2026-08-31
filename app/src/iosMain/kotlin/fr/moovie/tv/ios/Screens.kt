package fr.moovie.tv.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import fr.moovie.tv.data.download.DownloadRepository
import fr.moovie.tv.data.settings.tmdbCountry
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.person_credits_empty
import fr.moovie.tv.resources.person_credits_error
import fr.moovie.tv.ui.catalog.CatalogScreenContent
import fr.moovie.tv.ui.catalog.CatalogSelection
import fr.moovie.tv.ui.catalog.CatalogViewModel
import fr.moovie.tv.ui.details.DetailsScreenContent
import fr.moovie.tv.ui.details.DetailsState
import fr.moovie.tv.ui.details.DetailsViewModel
import fr.moovie.tv.ui.discovery.DiscoveryScreenContent
import fr.moovie.tv.ui.discovery.DiscoveryViewModel
import fr.moovie.tv.ui.history.HistoryScreenContent
import fr.moovie.tv.ui.history.HistoryViewModel
import fr.moovie.tv.ui.home.HomeScreenContent
import fr.moovie.tv.ui.home.HomeViewModel
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.person.PersonScreenContent
import fr.moovie.tv.ui.person.PersonViewModel
import fr.moovie.tv.ui.search.SearchScreenContent
import fr.moovie.tv.ui.search.SearchViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Portée « application » des ViewModels, comme le `Vm` du desktop et comme le
 * scope Activity d'Android.
 *
 * Ils survivent à la navigation : revenir sur l'accueil ne redemande pas ses
 * rangées à TMDB, et la fiche garde ses sources résolues le temps qu'on en
 * sorte pour lancer le lecteur. `lazy`, parce que construire un ViewModel ouvre
 * ses dépôts — inutile d'ouvrir celui des téléchargements pour quelqu'un qui
 * n'ira jamais sur cet écran.
 */
internal object Vm {
    val home by lazy { HomeViewModel() }
    val search by lazy { SearchViewModel() }
    val history by lazy { HistoryViewModel() }
    val details by lazy { DetailsViewModel() }
    val catalog by lazy { CatalogViewModel() }
    val person by lazy { PersonViewModel() }
    val discovery by lazy { DiscoveryViewModel() }
}

/*
 * Les emballages iOS : chacun branche un ViewModel sur l'écran partagé
 * correspondant, et ne dessine rien lui-même.
 *
 * Le projet range chaque écran en deux morceaux : un `XxxScreenContent`, qui est
 * l'interface et vit dans `commonMain`, et un emballage par plateforme qui lui
 * passe les flux de son ViewModel. Android a `SettingsScreen.android.kt`, le
 * desktop a `desktop/Screens.kt` ; voici celui d'iOS. Ce que l'utilisateur voit
 * vient donc du même Compose que sur Android — mêmes cartes, mêmes marges,
 * mêmes couleurs — sans qu'une ligne d'interface soit écrite deux fois.
 *
 * Deux écarts avec `desktop/Screens.kt`, tous deux mécaniques :
 *
 * - `collectAsState` plutôt que `collectAsStateWithLifecycle`, qui vient d'un
 *   artefact déclaré côté Android seulement. Les flux concernés viennent de
 *   DataStore et de TMDB : ils n'émettent pas d'eux-mêmes quand l'écran n'est
 *   pas visible, la différence reste sans effet.
 * - Tous posent `showBackButton = true`, comme le desktop et pour la raison
 *   inverse d'Android : celui-ci s'en passe parce qu'il a une touche Retour
 *   matérielle. iOS n'en a pas — c'est le balayage depuis le bord qui en tient
 *   lieu, et Compose Multiplatform ne le relaie pas jusqu'à la pile. Sans ce
 *   bouton, chaque écran serait un cul-de-sac.
 */

@Composable
internal fun IosHomeScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onResume: (ResumeEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDiscovery: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenCatalogGenre: (CatalogSelection) -> Unit,
) {
    val vm = Vm.home
    val state by vm.state.collectAsState()
    val resume by vm.resume.collectAsState()
    val watched by vm.watched.collectAsState()
    val watchlist by vm.watchlist.collectAsState()

    HomeScreenContent(
        state = state,
        resume = resume,
        watched = watched,
        watchlist = watchlist,
        onOpenTitle = onOpenTitle,
        onResume = onResume,
        onOpenSettings = onOpenSettings,
        onOpenSearch = onOpenSearch,
        onOpenDiscovery = onOpenDiscovery,
        onOpenHistory = onOpenHistory,
        onOpenDownloads = onOpenDownloads,
        onOpenCatalog = onOpenCatalog,
        onOpenCatalogGenre = onOpenCatalogGenre,
        onRemoveResume = vm::removeResume,
        onMarkResumeWatched = vm::markResumeWatched,
        onRemoveFromWatchlist = vm::removeFromWatchlist,
        onAddToWatchlist = vm::addToWatchlist,
        // Nuls et non vides : leur contrat est « null s'il n'y a pas de
        // téléviseur à portée », et il n'y en aura jamais — le portage a écarté
        // la diffusion Cast. Les boutons ne s'affichent pas, au lieu de
        // s'afficher sans effet.
        onSendResumeToTv = null,
        onOpenRemote = null,
    )
}

@Composable
internal fun IosSearchScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onOpenDiscovery: () -> Unit,
    onBack: () -> Unit,
) {
    val vm = Vm.search
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val history by vm.history.collectAsState()
    val watchlistKeys by vm.watchlistKeys.collectAsState()
    val filters by vm.filters.collectAsState()

    SearchScreenContent(
        query = query,
        results = results,
        history = history,
        onQueryChange = vm::setQuery,
        onOpen = { item ->
            vm.remember()
            onOpenTitle(item.id, item.isTv)
        },
        watchlistKeys = watchlistKeys,
        onAddToWatchlist = vm::addToWatchlist,
        onRemoveFromWatchlist = vm::removeFromWatchlist,
        onRemoveHistory = vm::removeHistory,
        onClearHistory = vm::clearHistory,
        onOpenDiscovery = onOpenDiscovery,
        filters = filters,
        onFiltersChange = vm::setFilters,
        onBack = onBack,
        showBackButton = true,
    )
}

@Composable
internal fun IosDiscoveryScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val vm = Vm.discovery
    val state by vm.state.collectAsState()
    val mood by vm.mood.collectAsState()
    val retirees by vm.retirees.collectAsState()
    val watchlistKeys by vm.watchlistKeys.collectAsState()

    DiscoveryScreenContent(
        state = state,
        mood = mood,
        retirees = retirees,
        watchlistKeys = watchlistKeys,
        onOpenTitle = onOpenTitle,
        onMarkSeen = vm::markSeen,
        onToggleWatchlist = vm::toggleWatchlist,
        onAnswer = vm::answer,
        onClearMood = vm::clearMood,
        onReload = vm::reload,
        onBack = onBack,
        showBackButton = true,
    )
}

@Composable
internal fun IosHistoryScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val vm = Vm.history
    val days by vm.days.collectAsState()
    val stats by vm.stats.collectAsState()

    HistoryScreenContent(
        days = days,
        stats = stats,
        onOpenTitle = onOpenTitle,
        onRemove = vm::remove,
        onMarkUnwatched = vm::markUnwatched,
        onBack = onBack,
        showBackButton = true,
    )
}

@Composable
internal fun IosCatalogScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    select: CatalogSelection?,
    onBack: () -> Unit,
) {
    val vm = Vm.catalog
    val entries by vm.entries.collectAsState()
    val selection by vm.selection.collectAsState()
    val state by vm.state.collectAsState()
    val items by vm.items.collectAsState()
    val watched by vm.watched.collectAsState()
    val watchlistKeys by vm.watchlistKeys.collectAsState()
    val layout by vm.layout.collectAsState()
    val pinnedKeys by vm.pinnedKeys.collectAsState()
    val filters by vm.filters.collectAsState()

    // Arrivée par « En voir plus » : le genre demandé prime sur le dernier
    // parcouru, que l'init du ViewModel restaure sinon.
    LaunchedEffect(select) { select?.let(vm::openAt) }

    CatalogScreenContent(
        filters = filters,
        onFiltersChange = vm::setFilters,
        entries = entries,
        selection = selection,
        state = state,
        items = items,
        watched = watched,
        watchlistKeys = watchlistKeys,
        onSelectGenre = vm::select,
        onLoadMore = vm::loadMore,
        onOpenTitle = onOpenTitle,
        layout = layout,
        pinnedKeys = pinnedKeys,
        onPin = vm::pin,
        onUnpin = vm::unpin,
        onBack = onBack,
        showBackButton = true,
    )
}

@Composable
internal fun IosPersonScreen(
    params: Screen.Person,
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val vm = Vm.person
    val state by vm.state.collectAsState()
    val watched by vm.watched.collectAsState()
    val watchlistKeys by vm.watchlistKeys.collectAsState()

    // Résolus ici : le ViewModel n'a pas de contexte de composition.
    val empty = stringResource(Res.string.person_credits_empty)
    val error = stringResource(Res.string.person_credits_error)
    LaunchedEffect(params.personId) { vm.load(params.personId, empty, error) }

    PersonScreenContent(
        name = params.name,
        state = state,
        watched = watched,
        watchlistKeys = watchlistKeys,
        onOpenTitle = onOpenTitle,
        onBack = onBack,
        showBackButton = true,
    )
}

/**
 * La fiche d'un titre.
 *
 * ## Le retour y a plusieurs sens, et l'ordre compte
 *
 * La fiche empile ses propres couches : la bande-annonce plein cadre, le panneau
 * des sources, la fiche d'épisode. Un retour doit refermer la plus haute avant
 * de songer à quitter l'écran. Le desktop confie cette cascade à la touche Échap
 * via `onRegisterBack`, Android à son `BackHandler` ; iOS n'a ni l'une ni
 * l'autre, si bien qu'elle vit ici, dans le seul `onBack` que déclenche le
 * bouton de l'écran.
 *
 * L'ordre est celui des deux autres plateformes : la bande-annonce d'abord —
 * elle recouvre tout le reste —, puis le panneau, puis l'épisode, et seulement
 * alors on dépile.
 */
@Composable
internal fun IosDetailsScreen(
    params: Screen.Details,
    onPlay: (Screen.Player) -> Unit,
    onOpenPerson: (personId: Int, name: String) -> Unit,
    onBack: () -> Unit,
) {
    val vm = Vm.details
    val state by vm.state.collectAsState()
    val sources by vm.sources.collectAsState()
    val resolved by vm.resolved.collectAsState()
    val inWatchlist by vm.inWatchlist.collectAsState()
    val resolveError by vm.resolveError.collectAsState()
    val resolvingUrl by vm.resolving.collectAsState()
    val sourceQualities by vm.qualities.collectAsState()
    val sourceHeights by vm.heights.collectAsState()
    val sourceStatuses by vm.linkStatus.collectAsState()
    val streamLang by vm.streamLanguage.collectAsState()
    val seasonDownload by vm.seasonDownload.collectAsState(initial = null)
    val downloadsBySource by remember { DownloadRepository().downloads }
        .collectAsState(initial = emptyList())
    val watched by vm.watched.collectAsState()
    val resume by vm.resume.collectAsState()
    val quickPlay by vm.quickPlay.collectAsState()
    val panelVisible by vm.panelVisible.collectAsState()
    val selectedEpisode by vm.selectedEpisode.collectAsState()
    val trailer by vm.trailer.collectAsState()
    val trailerExpanded by vm.trailerExpanded.collectAsState()
    val downloadSearching by vm.downloadSearching.collectAsState()
    val trailerAutoplay by vm.trailerAutoplay.collectAsState()
    val trailerSound by vm.trailerSound.collectAsState()

    // Reprise depuis l'accueil : lance la lecture directe une seule fois.
    val autoConsumed = remember { mutableStateOf(false) }

    LaunchedEffect(params.tmdbId, params.isTv) {
        vm.start(params.tmdbId, params.isTv, params.resumeSeason, params.resumeEpisode)
    }
    LaunchedEffect(state) {
        if (params.autoSources && !autoConsumed.value) {
            when (state) {
                is DetailsState.Movie -> {
                    autoConsumed.value = true
                    vm.quickPlayMovie()
                }
                is DetailsState.Tv -> {
                    autoConsumed.value = true
                    vm.selectSeason(params.resumeSeason)
                    vm.quickPlayEpisode(params.resumeSeason, params.resumeEpisode)
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(resolved) {
        resolved?.let { s ->
            if (s.url.isNotBlank()) {
                vm.closePanel()
                onPlay(
                    Screen.Player(
                        streamUrl = s.url,
                        headers = s.headers,
                        mediaKey = vm.playbackKey,
                        subtitles = s.subtitleUrls,
                        title = vm.playbackTitle,
                        subtitle = vm.playbackSubtitle,
                        nextSeason = vm.playbackNext?.first ?: 0,
                        nextEpisode = vm.playbackNext?.second ?: 0,
                        posterUrl = vm.playbackPoster,
                        expectedMinutes = vm.playbackMinutes ?: 0,
                        sourceUrl = vm.playingLink?.url.orEmpty(),
                        hoster = vm.playingLink?.hoster.orEmpty(),
                        language = vm.playingLink?.language.orEmpty(),
                        alternatives = vm.playbackAlternatives(),
                    ),
                )
            }
            vm.consumeResolved()
        }
    }

    DetailsScreenContent(
        state = state,
        sources = sources,
        resolveError = resolveError,
        resolvingUrl = resolvingUrl,
        onOpenPerson = { onOpenPerson(it.id, it.name) },
        streamLang = streamLang,
        watched = watched,
        resume = resume,
        quickPlay = quickPlay,
        panelVisible = panelVisible,
        selectedEpisode = selectedEpisode,
        movieKey = vm.movieKey(),
        episodeKey = vm::episodeKey,
        onQuickPlayMovie = vm::quickPlayMovie,
        onQuickPlayEpisode = vm::quickPlayEpisode,
        onSelectSeason = vm::selectSeason,
        onOpenEpisode = vm::openEpisode,
        onOpenEpisodePanel = vm::openEpisodePanel,
        onToggleWatched = vm::toggleWatched,
        onToggleSeasonWatched = vm::toggleSeasonWatched,
        inWatchlist = inWatchlist,
        onToggleWatchlist = vm::toggleWatchlist,
        onOpenPanel = vm::openPanel,
        onClosePanel = vm::closePanel,
        onPickSource = vm::play,
        onDownloadSource = vm::download,
        sourceStatuses = sourceStatuses,
        onDownloadSeason = { season -> vm.downloadSeason(season) },
        seasonDownload = seasonDownload,
        downloads = downloadsBySource.associateBy { it.sourceUrl },
        downloadList = downloadsBySource,
        sourceQualities = sourceQualities,
        sourceHeights = sourceHeights,
        // Pas de téléviseur à qui envoyer : voir IosHomeScreen.
        onSendToTv = null,
        onRequestQuality = vm::requestQuality,
        trailer = trailer,
        onPlayTrailer = vm::openTrailer,
        trailerExpanded = trailerExpanded,
        onCloseTrailer = vm::closeTrailer,
        onDownloadBest = vm::downloadBest,
        downloadSearching = downloadSearching,
        // L'aperçu joue vraiment. Il était nul, et le bouton « Bande-annonce »
        // ne faisait alors rien du tout : `TrailerButton` ne dépend que de
        // `TrailerState.Ready`, jamais de ce paramètre. Voir TrailerPreview.kt.
        trailerPreview = { flux, volume, surControleur, mod ->
            TrailerPreviewIos(flux, volume, surControleur, mod, plein = trailerExpanded)
        },
        trailerAutoplay = trailerAutoplay,
        trailerSound = trailerSound,
        country = tmdbCountry(),
        onDismissQuickPlay = vm::dismissQuickPlay,
        // La cascade : voir le KDoc.
        onBack = {
            when {
                trailerExpanded -> vm.closeTrailer()
                panelVisible -> vm.closePanel()
                selectedEpisode != null -> vm.closeEpisode()
                else -> onBack()
            }
        },
        showBackButton = true,
    )
}
