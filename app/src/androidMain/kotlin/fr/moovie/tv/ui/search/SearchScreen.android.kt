package fr.moovie.tv.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Wrapper Android : branche le [SearchViewModel] (repos DataStore androidMain)
 * sur l'écran partagé [SearchScreenContent] de jvmCommon.
 */
@Composable
fun SearchScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val watchlistKeys by viewModel.watchlistKeys.collectAsStateWithLifecycle()

    SearchScreenContent(
        query = query,
        results = results,
        history = history,
        onQueryChange = viewModel::setQuery,
        onOpen = { item ->
            viewModel.remember()
            onOpenTitle(item.id, item.isTv)
        },
        watchlistKeys = watchlistKeys,
        onAddToWatchlist = viewModel::addToWatchlist,
        onRemoveFromWatchlist = viewModel::removeFromWatchlist,
        onRemoveHistory = viewModel::removeHistory,
        onClearHistory = viewModel::clearHistory,
    )
}
