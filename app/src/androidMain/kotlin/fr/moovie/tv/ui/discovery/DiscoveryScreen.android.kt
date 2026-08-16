package fr.moovie.tv.ui.discovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Enveloppe Android : branche le [DiscoveryViewModel] sur l'écran partagé.
 *
 * Même découpage que la recherche et l'historique — l'écran vit dans
 * `jvmCommon`, seule la fabrique de ViewModel diffère d'une plateforme à
 * l'autre.
 */
@Composable
fun DiscoveryScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = false,
    viewModel: DiscoveryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    val retirees by viewModel.retirees.collectAsStateWithLifecycle()
    val watchlistKeys by viewModel.watchlistKeys.collectAsStateWithLifecycle()

    DiscoveryScreenContent(
        state = state,
        mood = mood,
        retirees = retirees,
        watchlistKeys = watchlistKeys,
        onOpenTitle = onOpenTitle,
        onMarkSeen = viewModel::markSeen,
        onToggleWatchlist = viewModel::toggleWatchlist,
        onAnswer = viewModel::answer,
        onClearMood = viewModel::clearMood,
        onReload = viewModel::reload,
        onBack = onBack,
        showBackButton = showBackButton,
    )
}
