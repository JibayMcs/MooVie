package fr.moovie.tv.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Wrapper Android : branche le [CatalogViewModel] sur l'écran partagé
 * [CatalogScreenContent] de jvmCommon. Pas de bouton retour à l'écran — la
 * télécommande a sa touche dédiée, et un bouton de plus volerait le focus au
 * premier genre.
 */
@Composable
fun CatalogScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    /** Genre à ouvrir d'emblée (voir Screen.Catalog). */
    select: CatalogSelection? = null,
    viewModel: CatalogViewModel = viewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    val watchlistKeys by viewModel.watchlistKeys.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val pinnedKeys by viewModel.pinnedKeys.collectAsStateWithLifecycle()

    // Arrivée par « En voir plus » : le genre demandé prime sur le dernier
    // parcouru, que l'init du ViewModel restaure sinon.
    LaunchedEffect(select) { select?.let(viewModel::openAt) }

    CatalogScreenContent(
        filters = filters,
        onFiltersChange = viewModel::setFilters,
        entries = entries,
        selection = selection,
        state = state,
        items = items,
        watched = watched,
        watchlistKeys = watchlistKeys,
        onSelectGenre = viewModel::select,
        onLoadMore = viewModel::loadMore,
        onOpenTitle = onOpenTitle,
        layout = layout,
        pinnedKeys = pinnedKeys,
        onPin = viewModel::pin,
        onUnpin = viewModel::unpin,
    )
}
