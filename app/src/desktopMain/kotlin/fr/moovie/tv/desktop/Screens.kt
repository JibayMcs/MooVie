package fr.moovie.tv.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.ui.details.DetailsScreenContent
import fr.moovie.tv.ui.details.DetailsState
import fr.moovie.tv.ui.details.DetailsViewModel
import fr.moovie.tv.ui.catalog.CatalogScreenContent
import fr.moovie.tv.ui.catalog.CatalogViewModel
import fr.moovie.tv.ui.history.HistoryScreenContent
import fr.moovie.tv.ui.history.HistoryViewModel
import fr.moovie.tv.ui.home.HomeScreenContent
import fr.moovie.tv.ui.home.HomeViewModel
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.search.SearchScreenContent
import fr.moovie.tv.ui.search.SearchViewModel
import fr.moovie.tv.ui.settings.SettingsScreenContent
import fr.moovie.tv.ui.settings.SettingsViewModel

/**
 * ViewModels à l'échelle de la fenêtre (équivalent du scope Activity sur
 * Android) : survivent à la navigation, le process les emporte en quittant.
 */
internal object Vm {
    val home by lazy { HomeViewModel() }
    val search by lazy { SearchViewModel() }
    val history by lazy { HistoryViewModel() }
    val settings by lazy { SettingsViewModel() }
    val details by lazy { DetailsViewModel() }
    val catalog by lazy { CatalogViewModel() }
}

@Composable
internal fun DesktopHomeScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onResume: (ResumeEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCatalog: () -> Unit,
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
        onOpenTitle = onOpenTitle,
        onResume = onResume,
        onOpenSettings = onOpenSettings,
        onOpenSearch = onOpenSearch,
        onOpenHistory = onOpenHistory,
        onOpenCatalog = onOpenCatalog,
        onRemoveResume = vm::removeResume,
        onMarkResumeWatched = vm::markResumeWatched,
        watchlist = watchlist,
        onRemoveFromWatchlist = vm::removeFromWatchlist,
        onAddToWatchlist = vm::addToWatchlist,
    )
}

@Composable
internal fun DesktopSearchScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
) {
    val vm = Vm.search
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val history by vm.history.collectAsState()
    val watchlistKeys by vm.watchlistKeys.collectAsState()

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
    )
}

@Composable
internal fun DesktopHistoryScreen(
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
internal fun DesktopSettingsScreen(onBack: () -> Unit) {
    val vm = Vm.settings
    val apiKey by vm.tmdbApiKey.collectAsState()
    val streamLang by vm.streamLanguage.collectAsState()
    val providers by vm.providers.collectAsState()
    val dohEnabled by vm.dohEnabled.collectAsState()
    val dohProvider by vm.dohProvider.collectAsState()
    val skipIntroOutro by vm.skipIntroOutro.collectAsState()
    val autoPlayNext by vm.autoPlayNext.collectAsState()
    val hideHistoryWidgets by vm.hideHistoryWidgets.collectAsState()
    val updateInterval by vm.updateInterval.collectAsState()
    val screensaverDelay by vm.screensaverDelay.collectAsState()

    SettingsScreenContent(
        apiKey = apiKey,
        streamLang = streamLang,
        skipIntroOutro = skipIntroOutro,
        autoPlayNext = autoPlayNext,
        hideHistoryWidgets = hideHistoryWidgets,
        updateInterval = updateInterval,
        screensaverDelay = screensaverDelay,
        dohEnabled = dohEnabled,
        dohProvider = dohProvider,
        providers = providers,
        onSetApiKey = vm::setTmdbApiKey,
        onSetStreamLanguage = vm::setStreamLanguage,
        onSetSkipIntroOutro = vm::setSkipIntroOutro,
        onSetAutoPlayNext = vm::setAutoPlayNext,
        onSetHideHistoryWidgets = vm::setHideHistoryWidgets,
        onSetUpdateInterval = vm::setUpdateInterval,
        onSetScreensaverDelay = vm::setScreensaverDelay,
        onSetDohEnabled = vm::setDohEnabled,
        onSetDohProvider = vm::setDohProvider,
        onToggleProvider = vm::toggleProvider,
        onMoveProviderUp = vm::moveProviderUp,
        onMoveProviderDown = vm::moveProviderDown,
        onBack = onBack,
        languageSelector = {
            // Desktop : locale système pour l'instant (pas de sélecteur).
            Text("Langue du système", color = Color(0xFF9A9A9A))
        },
    )
}

@Composable
internal fun DesktopDetailsScreen(
    params: Screen.Details,
    onPlay: (Screen.Player) -> Unit,
    onBack: () -> Unit,
    onRegisterBack: ((() -> Unit)?) -> Unit,
) {
    val vm = Vm.details
    val state by vm.state.collectAsState()
    val sources by vm.sources.collectAsState()
    val resolved by vm.resolved.collectAsState()
    val inWatchlist by vm.inWatchlist.collectAsState()
    val resolveError by vm.resolveError.collectAsState()
    val sourceQualities by vm.qualities.collectAsState()
    val streamLang by vm.streamLanguage.collectAsState()
    val watched by vm.watched.collectAsState()
    val resume by vm.resume.collectAsState()
    val quickPlay by vm.quickPlay.collectAsState()
    val panelVisible by vm.panelVisible.collectAsState()
    val selectedEpisode by vm.selectedEpisode.collectAsState()
    // Reprise depuis l'accueil : lance la lecture directe une seule fois.
    val autoConsumed = remember { mutableStateOf(false) }

    LaunchedEffect(params.tmdbId, params.isTv) { vm.start(params.tmdbId, params.isTv) }
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
                    ),
                )
            }
            vm.consumeResolved()
        }
    }
    // Échap ferme d'abord le panneau des sources, puis la fiche d'épisode.
    // Quand il n'y a plus rien à fermer on enregistre `null` : la fenêtre dépile
    // alors la pile de navigation. Enregistrer `onBack` ici masquait la pile et
    // ramenait à l'accueil en sautant la fiche de la série.
    DisposableEffect(panelVisible, selectedEpisode) {
        onRegisterBack(
            when {
                panelVisible -> vm::closePanel
                selectedEpisode != null -> vm::closeEpisode
                else -> null
            },
        )
        // Sans ce nettoyage, le retour interne de la fiche resterait actif après
        // l'avoir quittée — c'est ce qui faisait dérailler les retours imbriqués.
        onDispose { onRegisterBack(null) }
    }

    DetailsScreenContent(
        state = state,
        sources = sources,
        resolveError = resolveError,
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
        sourceQualities = sourceQualities,
        onRequestQuality = vm::requestQuality,
        onDismissQuickPlay = vm::dismissQuickPlay,
        onBack = onBack,
        showBackButton = true,
    )
}

@Composable
internal fun DesktopCatalogScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val vm = Vm.catalog
    val entries by vm.entries.collectAsState()
    val selection by vm.selection.collectAsState()
    val state by vm.state.collectAsState()
    val items by vm.items.collectAsState()
    val watched by vm.watched.collectAsState()
    val watchlistKeys by vm.watchlistKeys.collectAsState()

    CatalogScreenContent(
        entries = entries,
        selection = selection,
        state = state,
        items = items,
        watched = watched,
        watchlistKeys = watchlistKeys,
        onSelectGenre = vm::select,
        onLoadMore = vm::loadMore,
        onOpenTitle = onOpenTitle,
        onBack = onBack,
        showBackButton = true,
    )
}
