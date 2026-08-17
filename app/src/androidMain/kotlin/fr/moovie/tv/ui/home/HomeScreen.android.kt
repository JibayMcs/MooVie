package fr.moovie.tv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.ui.catalog.CatalogSelection

/**
 * Wrapper Android : branche le [HomeViewModel] (repos DataStore androidMain)
 * sur l'écran partagé [HomeScreenContent] de jvmCommon.
 */
@Composable
fun HomeScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onResume: (ResumeEntry) -> Unit,
    /** Diffuser une reprise sur le téléviseur, null s'il n'y en a pas à portée. */
    onSendResumeToTv: ((ResumeEntry) -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDiscovery: () -> Unit = {},
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    /** Voir HomeScreenContent : null quand la télécommande n'a pas lieu d'être. */
    onOpenRemote: (() -> Unit)? = null,
    onOpenCatalog: () -> Unit,
    onOpenCatalogGenre: (CatalogSelection) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()

    HomeScreenContent(
        state = state,
        resume = resume,
        watched = watched,
        onOpenTitle = onOpenTitle,
        onResume = onResume,
        onSendResumeToTv = onSendResumeToTv,
        onOpenSettings = onOpenSettings,
        onOpenSearch = onOpenSearch,
        onOpenDiscovery = onOpenDiscovery,
        onOpenHistory = onOpenHistory,
        onOpenDownloads = onOpenDownloads,
        onOpenRemote = onOpenRemote,
        onOpenCatalog = onOpenCatalog,
        onOpenCatalogGenre = onOpenCatalogGenre,
        onRemoveResume = viewModel::removeResume,
        onMarkResumeWatched = viewModel::markResumeWatched,
        watchlist = watchlist,
        onRemoveFromWatchlist = viewModel::removeFromWatchlist,
        onAddToWatchlist = viewModel::addToWatchlist,
    )
}
