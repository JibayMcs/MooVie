package fr.moovie.tv.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import fr.moovie.tv.data.download.DownloadRepository
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.settings.tmdbCountry
import fr.moovie.tv.ui.details.DetailsScreenContent
import fr.moovie.tv.ui.details.DetailsState
import fr.moovie.tv.ui.details.DetailsViewModel
import fr.moovie.tv.ui.catalog.CatalogSelection
import fr.moovie.tv.ui.catalog.CatalogScreenContent
import fr.moovie.tv.ui.catalog.CatalogViewModel
import fr.moovie.tv.ui.discovery.DiscoveryScreenContent
import fr.moovie.tv.ui.discovery.DiscoveryViewModel
import fr.moovie.tv.ui.history.HistoryScreenContent
import fr.moovie.tv.ui.history.HistoryViewModel
import fr.moovie.tv.ui.home.HomeScreenContent
import fr.moovie.tv.ui.home.HomeViewModel
import fr.moovie.tv.ui.person.PersonScreenContent
import fr.moovie.tv.ui.person.PersonViewModel
import fr.moovie.tv.data.remote.PlayRequest
import fr.moovie.tv.ui.remote.rememberTvSender
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.search.SearchScreenContent
import fr.moovie.tv.ui.search.SearchViewModel
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.ui.settings.RemoteSection
import fr.moovie.tv.ui.pairing.PairingDialog
import fr.moovie.tv.ui.settings.SettingsScreenContent
import fr.moovie.tv.ui.settings.SettingsViewModel
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.person_credits_empty
import fr.moovie.tv.resources.person_credits_error
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.data.settings.DesktopLanguage
import fr.moovie.tv.data.settings.DesktopLocale
import fr.moovie.tv.ui.components.MoovieSelect
import fr.moovie.tv.resources.settings_language
import fr.moovie.tv.resources.language_system
import fr.moovie.tv.resources.language_fr
import fr.moovie.tv.resources.language_en
import fr.moovie.tv.resources.language_es

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
    val person by lazy { PersonViewModel() }
    val discovery by lazy { DiscoveryViewModel() }

    /** Partagé entre la bannière et le bouton « Vérifier maintenant ». */
    val update by lazy { DesktopUpdateViewModel() }
}

@Composable
internal fun DesktopHomeScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onResume: (ResumeEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDiscovery: () -> Unit = {},
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit = {},
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
        watchlist = watchlist,
        onRemoveFromWatchlist = vm::removeFromWatchlist,
        onAddToWatchlist = vm::addToWatchlist,
    )
}

@Composable
internal fun DesktopSearchScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
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
        filters = filters,
        onFiltersChange = vm::setFilters,
        onBack = onBack,
        showBackButton = true,
    )
}

/**
 * Enveloppe desktop de la page Découverte.
 *
 * Bouton Retour toujours visible, comme les autres écrans du poste de travail :
 * il n'y a pas de touche Retour sur un clavier de bureau.
 */
@Composable
internal fun DesktopDiscoveryScreen(
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
internal fun DesktopSettingsScreen(
    onBack: () -> Unit,
    onPlayDownload: (Download) -> Unit = {},
) {
    val vm = Vm.settings
    val apiKey by vm.tmdbApiKey.collectAsState()
    val introDbKey by vm.introDbApiKey.collectAsState()
    val streamLang by vm.streamLanguage.collectAsState()
    val providers by vm.providers.collectAsState()
    val dohEnabled by vm.dohEnabled.collectAsState()
    val dohProvider by vm.dohProvider.collectAsState()
    val skipIntroOutro by vm.skipIntroOutro.collectAsState()
    val autoPlayNext by vm.autoPlayNext.collectAsState()
    val playerClock by vm.playerClock.collectAsState()
    val settingsTrailerAutoplay by vm.trailerAutoplay.collectAsState()
    val settingsTrailerSound by vm.trailerSound.collectAsState()
    val updatePrereleases by vm.updatePrereleases.collectAsState()
    val hideHistoryWidgets by vm.hideHistoryWidgets.collectAsState()
    val splashAnimation by vm.splashAnimation.collectAsState()
    val updateCheck by Vm.update.checkStatus.collectAsState()
    val updateInterval by vm.updateInterval.collectAsState()
    val screensaverDelay by vm.screensaverDelay.collectAsState()

    SettingsScreenContent(
        apiKey = apiKey,
        introDbKey = introDbKey,
        streamLang = streamLang,
        skipIntroOutro = skipIntroOutro,
        autoPlayNext = autoPlayNext,
        playerClock = playerClock,
        trailerAutoplay = settingsTrailerAutoplay,
        trailerSound = settingsTrailerSound,
        hideHistoryWidgets = hideHistoryWidgets,
        splashAnimation = splashAnimation,
        updateCheck = updateCheck,
        updateInterval = updateInterval,
        screensaverDelay = screensaverDelay,
        dohEnabled = dohEnabled,
        dohProvider = dohProvider,
        providers = providers,
        onSetApiKey = vm::setTmdbApiKey,
        onSetIntroDbKey = vm::setIntroDbApiKey,
        onSetStreamLanguage = vm::setStreamLanguage,
        onSetSkipIntroOutro = vm::setSkipIntroOutro,
        onSetAutoPlayNext = vm::setAutoPlayNext,
        onSetPlayerClock = vm::setPlayerClock,
        onSetTrailerAutoplay = vm::setTrailerAutoplay,
        onSetTrailerSound = vm::setTrailerSound,
        updatePrereleases = updatePrereleases,
        onSetUpdatePrereleases = vm::setUpdatePrereleases,
        onSetHideHistoryWidgets = vm::setHideHistoryWidgets,
        onSetSplashAnimation = vm::setSplashAnimation,
        onCheckUpdates = Vm.update::checkNow,
        onSetUpdateInterval = vm::setUpdateInterval,
        onSetScreensaverDelay = vm::setScreensaverDelay,
        onSetDohEnabled = vm::setDohEnabled,
        onSetDohProvider = vm::setDohProvider,
        onToggleProvider = vm::toggleProvider,
        onMoveProviderUp = vm::moveProviderUp,
        onMoveProviderDown = vm::moveProviderDown,
        onBack = onBack,
        onPlayDownload = onPlayDownload,
        languageSelector = {
            // Un vrai choix, et non plus un libellé inerte : voir DesktopLocale.
            // Les chaînes de l'interface et les formats de date suivent tout de
            // suite ; les métadonnées TMDB déjà chargées, elles, restent dans
            // l'ancienne langue jusqu'au prochain lancement — leur rangée a été
            // demandée avant le changement.
            MoovieSelect(
                title = stringResource(Res.string.settings_language),
                options = DesktopLanguage.entries,
                selected = DesktopLocale.current(),
                label = { langueLabel(it) },
                onSelect = { DesktopLocale.set(it) },
            )
        },
        // Les deux sections qui parlent à un autre appareil. Passées en
        // paramètre parce qu'elles s'adossent aux sockets de `data.cast` et
        // `data.remote` : l'écran, lui, est commun aux quatre plateformes
        // depuis le portage iOS. Voir RemoteSection.kt.
        remoteSection = { onPair -> RemoteSection(onPair) },
        pairingDialog = { onDismiss -> PairingDialog(onDismiss = onDismiss) },
    )
}

@Composable
internal fun DesktopDetailsScreen(
    params: Screen.Details,
    onPlay: (Screen.Player) -> Unit,
    onBack: () -> Unit,
    onOpenPerson: (personId: Int, name: String) -> Unit,
    onRegisterBack: ((() -> Unit)?) -> Unit,
    /**
     * Ouvre la télécommande après un envoi accepté. Le geste continue sur
     * l'écran qui montre ce que la TV fait, plutôt que de laisser le poste sur
     * une fiche devenue sans objet — exactement comme sur le téléphone.
     */
    onOpenRemote: () -> Unit = {},
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
    // Indexés par le lien d'embed dont ils sont partis : c'est la seule façon
    // de savoir **quelle ligne** allumer dans le panneau des sources.
    val seasonDownload by vm.seasonDownload.collectAsState(initial = null)
    val downloadsBySource by remember { DownloadRepository().downloads }
        .collectAsState(initial = emptyList())
    val watched by vm.watched.collectAsState()
    val resume by vm.resume.collectAsState()
    val quickPlay by vm.quickPlay.collectAsState()
    val panelVisible by vm.panelVisible.collectAsState()
    val selectedEpisode by vm.selectedEpisode.collectAsState()
    val trailer by vm.trailer.collectAsState()

    // Envoi vers le téléviseur du salon. Le composant porte lui-même sa modale
    // de confirmation ; il n'expose ici qu'un « peut-on envoyer ».
    val tvSender = rememberTvSender(onSent = onOpenRemote)
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
    // Échap ferme d'abord le panneau des sources, puis la fiche d'épisode.
    // Quand il n'y a plus rien à fermer on enregistre `null` : la fenêtre dépile
    // alors la pile de navigation. Enregistrer `onBack` ici masquait la pile et
    // ramenait à l'accueil en sautant la fiche de la série.
    DisposableEffect(panelVisible, selectedEpisode, trailerExpanded) {
        onRegisterBack(
            when {
                // La bande-annonce d'abord : elle recouvre tout le reste, donc
                // c'est elle qu'Échap doit refermer en premier.
                trailerExpanded -> vm::closeTrailer
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
        // Null quand aucun téléviseur ne répond : pas de bouton plutôt qu'un
        // bouton inerte. Voir TvSender. Le raisonnement et le calcul de la clé
        // sont ceux du téléphone — c'est le même geste, sur le même protocole.
        onSendToTv = if (!tvSender.available) null else {
            {
                val episode = selectedEpisode
                val season = episode?.season ?: params.resumeSeason
                val number = episode?.episode?.episodeNumber ?: params.resumeEpisode
                // La reprise **de ce poste**, pour que la TV continue là où on
                // s'est arrêté plutôt qu'au début. La clé se recalcule au lieu
                // d'être devinée : c'est la même que celle du magasin.
                val key = if (params.isTv) {
                    "tv:${params.tmdbId}:s${season}e$number"
                } else {
                    "movie:${params.tmdbId}"
                }
                val here = resume[key]
                tvSender.ask(
                    PlayRequest(
                        tmdbId = params.tmdbId,
                        isTv = params.isTv,
                        season = season,
                        episode = number,
                        title = vm.playbackTitle,
                        subtitle = vm.playbackSubtitle,
                        artwork = vm.playbackPoster.orEmpty(),
                        positionMs = here?.positionMs ?: 0,
                        durationMs = here?.durationMs ?: 0,
                    ),
                )
            }
        },
        onRequestQuality = vm::requestQuality,
        trailer = trailer,
        onPlayTrailer = vm::openTrailer,
        trailerExpanded = trailerExpanded,
        onCloseTrailer = vm::closeTrailer,
        onDownloadBest = vm::downloadBest,
        downloadSearching = downloadSearching,
        trailerPreview = { stream, vol, onCtl, mod -> TrailerPreview(stream, vol, onCtl, mod) },
        trailerAutoplay = trailerAutoplay,
        trailerSound = trailerSound,
        country = tmdbCountry(),
        onDismissQuickPlay = vm::dismissQuickPlay,
        onBack = onBack,
        showBackButton = true,
    )
}

@Composable
internal fun DesktopCatalogScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    /** Genre à ouvrir d'emblée (voir Screen.Catalog). */
    select: CatalogSelection? = null,
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

    // Arrivée par « En voir plus » : le genre demandé prime sur le dernier
    // parcouru, que l'init du ViewModel restaure sinon.
    LaunchedEffect(select) { select?.let(vm::openAt) }

    val catalogFilters by vm.filters.collectAsState()
    CatalogScreenContent(
        filters = catalogFilters,
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
internal fun DesktopPersonScreen(
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

/** Libellé d'une langue, dans les chaînes partagées avec Android. */
@Composable
private fun langueLabel(language: DesktopLanguage): String = stringResource(
    when (language) {
        DesktopLanguage.SYSTEM -> Res.string.language_system
        DesktopLanguage.FRENCH -> Res.string.language_fr
        DesktopLanguage.ENGLISH -> Res.string.language_en
        DesktopLanguage.SPANISH -> Res.string.language_es
    },
)
