package fr.moovie.tv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.data.watch.ResumeEntry

/**
 * Wrapper Android : branche le [HomeViewModel] (repos DataStore androidMain)
 * sur l'écran partagé [HomeScreenContent] de jvmCommon.
 */
@Composable
fun HomeScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onResume: (ResumeEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val watched by viewModel.watched.collectAsStateWithLifecycle()

    HomeScreenContent(
        state = state,
        resume = resume,
        watched = watched,
        onOpenTitle = onOpenTitle,
        onResume = onResume,
        onOpenSettings = onOpenSettings,
        onOpenSearch = onOpenSearch,
        onRemoveResume = viewModel::removeResume,
        onMarkResumeWatched = viewModel::markResumeWatched,
    )
}
