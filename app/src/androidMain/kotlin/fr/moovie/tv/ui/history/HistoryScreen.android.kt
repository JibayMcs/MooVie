package fr.moovie.tv.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Wrapper Android : branche le [HistoryViewModel] (repos DataStore androidMain)
 * sur l'écran partagé [HistoryScreenContent] de jvmCommon.
 */
@Composable
fun HistoryScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    HistoryScreenContent(
        days = days,
        stats = stats,
        onOpenTitle = onOpenTitle,
        onRemove = viewModel::remove,
        onMarkUnwatched = viewModel::markUnwatched,
    )
}
