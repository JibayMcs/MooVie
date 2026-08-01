package fr.moovie.tv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.catalog_pick_genre
import fr.moovie.tv.resources.catalog_title
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.explore_movies
import fr.moovie.tv.resources.explore_no_results
import fr.moovie.tv.resources.explore_series
import fr.moovie.tv.resources.search_loading
import fr.moovie.tv.resources.search_needs_key
import fr.moovie.tv.resources.watchlist_added
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieMarqueeText
import org.jetbrains.compose.resources.stringResource

/**
 * Largeur du volet des genres. Reprend celle des réglages : en 1080p l'écran ne
 * fait que 960 dp de large, un volet plus large étranglerait la grille.
 */
private val NAV_WIDTH = 260.dp

/** Colonnes de la grille. Cinq, et non six comme la recherche : le volet mange 260 dp. */
private const val COLUMNS = 5

/**
 * Nombre de titres restants sous le dernier visible en dessous duquel on demande
 * la page suivante. Assez large pour que le chargement soit fini avant qu'on
 * n'arrive au bout, assez petit pour ne pas tirer des pages qu'on ne verra pas.
 */
private const val PREFETCH_AHEAD = 10

/**
 * Page « Catalogue » : les genres à gauche, la grille du genre choisi à droite.
 *
 * Séparée de la recherche par texte : chercher un titre précis et parcourir ce
 * qui existe sont deux gestes différents, et les mêler obligeait à traverser un
 * champ de saisie — donc un clavier virtuel sur TV — pour atteindre les genres.
 *
 * Le volet gauche est une **liste unique** : « Films », ses genres, « Séries »,
 * ses genres. Les intitulés de section ne sont pas focalisables, le D-pad les
 * traverse sans s'y arrêter. La grille se remplit au **focus** d'un genre, pas
 * à sa validation : parcourir la liste montre directement ce qu'elle contient.
 */
@Composable
fun CatalogScreenContent(
    entries: List<CatalogEntry>,
    selection: CatalogSelection?,
    state: CatalogState,
    items: List<TmdbItem>,
    watched: Set<String>,
    watchlistKeys: Set<String>,
    onSelectGenre: (isTv: Boolean, genreId: Int) -> Unit,
    onLoadMore: () -> Unit,
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onBack: () -> Unit = {},
    // Desktop uniquement : la télécommande a sa propre touche Retour.
    showBackButton: Boolean = false,
) {
    val firstGenreFocus = remember { FocusRequester() }
    // Le focus arrive sur le 1er genre, pas sur la grille : c'est le volet qui
    // pilote la page. Les genres arrivent après la 1re composition (appel TMDB),
    // on retente donc tant que la liste n'est pas posée.
    LaunchedEffect(entries.isNotEmpty()) {
        if (entries.isEmpty()) return@LaunchedEffect
        runCatching { firstGenreFocus.requestFocus() }
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        GenrePane(
            entries = entries,
            selection = selection,
            firstGenreFocus = firstGenreFocus,
            onSelectGenre = onSelectGenre,
            onBack = onBack,
            showBackButton = showBackButton,
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 40.dp)) {
            when {
                state is CatalogState.NeedsKey -> Message(stringResource(Res.string.search_needs_key))
                state is CatalogState.Idle -> Message(stringResource(Res.string.catalog_pick_genre))
                state is CatalogState.Empty -> Message(stringResource(Res.string.explore_no_results))
                items.isEmpty() && state is CatalogState.Loading ->
                    Message(stringResource(Res.string.search_loading))
                else -> ResultsGrid(
                    items = items,
                    watched = watched,
                    watchlistKeys = watchlistKeys,
                    onLoadMore = onLoadMore,
                    onOpenTitle = onOpenTitle,
                )
            }
        }
    }
}

@Composable
private fun GenrePane(
    entries: List<CatalogEntry>,
    selection: CatalogSelection?,
    firstGenreFocus: FocusRequester,
    onSelectGenre: (isTv: Boolean, genreId: Int) -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean,
) {
    // Volet défilant : films et séries réunis font une quarantaine d'entrées,
    // bien au-delà de ce qu'un écran affiche.
    LazyColumn(
        modifier = Modifier
            .width(NAV_WIDTH)
            .fillMaxHeight()
            .background(Color(0xFF141414)),
        contentPadding = PaddingValues(vertical = 40.dp, horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "title") {
            Column {
                Text(
                    stringResource(Res.string.catalog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
                if (showBackButton) {
                    MoovieButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.common_back), modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        itemsIndexed(
            items = entries,
            key = { _, entry ->
                when (entry) {
                    is CatalogEntry.Header -> "h-${entry.isTv}"
                    is CatalogEntry.GenreEntry -> "g-${entry.isTv}-${entry.genre.id}"
                }
            },
        ) { index, entry ->
            when (entry) {
                is CatalogEntry.Header -> Text(
                    stringResource(
                        if (entry.isTv) Res.string.explore_series else Res.string.explore_movies,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MOOVIE_ACCENT,
                    modifier = Modifier.padding(start = 8.dp, top = if (index == 0) 0.dp else 20.dp, bottom = 6.dp),
                )

                is CatalogEntry.GenreEntry -> {
                    val isSelected = selection?.isTv == entry.isTv && selection.genreId == entry.genre.id
                    // Premier genre de la liste : cible du focus à l'ouverture.
                    val isFirstGenre = entries.indexOfFirst { it is CatalogEntry.GenreEntry } == index
                    MoovieButton(
                        onClick = { onSelectGenre(entry.isTv, entry.genre.id) },
                        selected = isSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            // Comme dans les réglages : la grille suit le focus.
                            // Valider pour découvrir le contenu d'un genre
                            // imposerait un aller-retour par genre.
                            .onFocusChanged {
                                if (it.isFocused) onSelectGenre(entry.isTv, entry.genre.id)
                            }
                            .then(
                                if (isFirstGenre) Modifier.focusRequester(firstGenreFocus) else Modifier,
                            ),
                    ) {
                        Text(
                            entry.genre.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsGrid(
    items: List<TmdbItem>,
    watched: Set<String>,
    watchlistKeys: Set<String>,
    onLoadMore: () -> Unit,
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
) {
    val gridState = rememberLazyGridState()

    // Pagination : on demande la suite quand le dernier élément visible approche
    // de la fin de ce qui est chargé. `derivedStateOf` pour ne réagir qu'au
    // franchissement du seuil, pas à chaque pixel de défilement.
    val needsMore by remember(items.size) {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= items.size - PREFETCH_AHEAD
        }
    }
    LaunchedEffect(needsMore, items.size) {
        if (needsMore) onLoadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // Marges intérieures : la grille clippe à ses bords, et les cartes
        // agrandies au focus ont besoin de cette réserve pour ne pas être rognées.
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
    ) {
        itemsIndexed(items, key = { _, it -> "${it.id}_${it.isTv}" }) { _, item ->
            val key = if (item.isTv) "tv:${item.id}" else "movie:${item.id}"
            PosterCard(
                item = item,
                isWatched = key in watched,
                inWatchlist = key in watchlistKeys,
                onClick = { onOpenTitle(item.id, item.isTv) },
            )
        }
    }
}

@Composable
private fun PosterCard(
    item: TmdbItem,
    isWatched: Boolean,
    inWatchlist: Boolean,
    onClick: () -> Unit,
) {
    MoovieCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box {
                AsyncImage(
                    model = item.posterUrl(),
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(Color(0xFF222222)),
                )
                if (inWatchlist || isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC0A0A0A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = stringResource(Res.string.watchlist_added),
                            tint = MOOVIE_ACCENT,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            MoovieMarqueeText(
                text = item.displayTitle,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = Color(0xFF9A9A9A),
        modifier = Modifier.padding(horizontal = 40.dp),
    )
}
