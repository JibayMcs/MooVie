package fr.moovie.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.search_clear_history
import fr.moovie.tv.resources.search_empty_hint
import fr.moovie.tv.resources.search_hint
import fr.moovie.tv.resources.search_loading
import fr.moovie.tv.resources.search_needs_key
import fr.moovie.tv.resources.search_no_results
import fr.moovie.tv.resources.search_recent
import fr.moovie.tv.resources.search_remove_query
import fr.moovie.tv.resources.search_title
import fr.moovie.tv.ui.components.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieMarqueeText
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import fr.moovie.tv.resources.watchlist_add
import fr.moovie.tv.resources.watchlist_remove
import androidx.compose.foundation.shape.CircleShape
import fr.moovie.tv.resources.watchlist_added

/**
 * Écran de recherche partagé TV + desktop : état hoisté (le ViewModel reste
 * côté plateforme tant que les repos DataStore vivent en androidMain).
 */
@Composable
fun SearchScreenContent(
    query: String,
    results: SearchState,
    history: List<String>,
    onQueryChange: (String) -> Unit,
    onOpen: (TmdbItem) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    /** Clés « titre » déjà mises de côté, pour le badge et le libellé du menu. */
    watchlistKeys: Set<String> = emptySet(),
    onAddToWatchlist: (TmdbItem) -> Unit = {},
    onRemoveFromWatchlist: (String) -> Unit = {},
) {
    var menuFor by remember { mutableStateOf<TmdbItem?>(null) }
    val fieldFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }

    // Dialogue déclaré tôt dans le corps : un Dialog se dessine en surimpression
    // quelle que soit sa position dans la composition.
    menuFor?.let { item ->
        val key = if (item.isTv) "tv:${item.id}" else "movie:${item.id}"
        val inList = key in watchlistKeys
        val action = remember { FocusRequester() }
        Dialog(onDismissRequest = { menuFor = null }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF5161616))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(item.displayTitle, style = MaterialTheme.typography.titleMedium)
                MoovieButton(
                    onClick = {
                        if (inList) onRemoveFromWatchlist(key) else onAddToWatchlist(item)
                        menuFor = null
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(action),
                    selected = inList,
                ) {
                    Icon(
                        if (inList) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            if (inList) Res.string.watchlist_remove else Res.string.watchlist_add,
                        ),
                    )
                }
            }
        }
        LaunchedEffect(item) { runCatching { action.requestFocus() } }
    }

    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    // Marges horizontales portées par les enfants : la grille de résultats
    // s'étend jusqu'aux bords et ne rogne plus les cartes agrandies au focus.
    Column(modifier = Modifier.fillMaxSize().padding(vertical = 40.dp)) {
        Text(
            stringResource(Res.string.search_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
        Spacer(Modifier.height(16.dp))

        SearchField(
            value = query,
            onValueChange = onQueryChange,
            // Valider sur le clavier ferme l'IME et saute au 1er résultat.
            onSubmit = { runCatching { firstResultFocus.requestFocus() } },
            modifier = Modifier.padding(horizontal = 40.dp).focusRequester(fieldFocus),
        )
        Spacer(Modifier.height(24.dp))

        when {
            query.isBlank() -> HistorySection(
                history = history,
                onPick = onQueryChange,
                onRemove = onRemoveHistory,
                onClear = onClearHistory,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            results is SearchState.Loading -> Text(stringResource(Res.string.search_loading), color = Color(0xFFBBBBBB), modifier = Modifier.padding(horizontal = 40.dp))
            results is SearchState.NeedsKey -> Text(
                stringResource(Res.string.search_needs_key),
                color = Color(0xFFE0A0A0),
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            results is SearchState.Empty -> Text(stringResource(Res.string.search_no_results, query), color = Color(0xFFBBBBBB), modifier = Modifier.padding(horizontal = 40.dp))
            results is SearchState.Results -> ResultsGrid(
                items = results.items,
                firstFocus = firstResultFocus,
                onOpen = onOpen,
                watchlistKeys = watchlistKeys,
                onMenu = { menuFor = it },
            )
            else -> Unit
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = modifier
            .fillMaxWidth(0.7f)
            .border(1.5.dp, Color(0xFF555555), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(stringResource(Res.string.search_hint), color = Color(0xFF888888), fontSize = 18.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(MOOVIE_ACCENT),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                // Sans ça, le champ avale le D-pad bas : impossible d'atteindre
                // l'historique/les résultats à la télécommande.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    } else {
                        false
                    }
                },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySection(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) {
        Text(stringResource(Res.string.search_empty_hint), color = Color(0xFF888888), modifier = modifier)
        return
    }
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.search_recent), style = MaterialTheme.typography.titleMedium)
            MoovieIconButton(
                onClick = onClear,
                icon = Icons.Default.DeleteSweep,
                contentDescription = stringResource(Res.string.search_clear_history),
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            history.forEach { q ->
                // Chip = relancer la recherche ; ✕ accolé = supprimer cette entrée.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MoovieButton(onClick = { onPick(q) }) { Text(q) }
                    MoovieIconButton(
                        onClick = { onRemove(q) },
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.search_remove_query, q),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsGrid(
    items: List<TmdbItem>,
    firstFocus: FocusRequester,
    onOpen: (TmdbItem) -> Unit,
    watchlistKeys: Set<String> = emptySet(),
    onMenu: (TmdbItem) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // Marges intérieures : la grille clippe à ses bords, les cartes
        // agrandies au focus ont besoin de cette réserve pour ne pas être rognées.
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
    ) {
        itemsIndexed(items, key = { _, it -> "${it.id}_${it.isTv}" }) { index, item ->
            ResultCard(
                inWatchlist = (if (item.isTv) "tv:${item.id}" else "movie:${item.id}") in watchlistKeys,
                onLongClick = { onMenu(item) },
                item = item,
                onClick = { onOpen(item) },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun ResultCard(
    item: TmdbItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inWatchlist: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    MoovieCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth()) {
        Column {
            Box {
                AsyncImage(
                    model = item.posterUrl(),
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Color(0xFF222222)),
                )
                // Sans ce repère, rien ne distingue un titre déjà mis de côté :
                // on le rajoutait sans le savoir, autant de fois qu'on retombait
                // dessus dans les résultats.
                if (inWatchlist) {
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
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
