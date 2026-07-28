package fr.moovie.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton

private val ACCENT = Color(0xFFB5302C)

@Composable
fun SearchScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    // Marges horizontales portées par les enfants : la grille de résultats
    // s'étend jusqu'aux bords et ne rogne plus les cartes agrandies au focus.
    Column(modifier = Modifier.fillMaxSize().padding(vertical = 40.dp)) {
        Text(
            "Recherche",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
        Spacer(Modifier.height(16.dp))

        SearchField(
            value = query,
            onValueChange = viewModel::setQuery,
            // Valider sur le clavier ferme l'IME et saute au 1er résultat.
            onSubmit = { runCatching { firstResultFocus.requestFocus() } },
            modifier = Modifier.padding(horizontal = 40.dp).focusRequester(fieldFocus),
        )
        Spacer(Modifier.height(24.dp))

        when {
            query.isBlank() -> HistorySection(
                history = history,
                onPick = viewModel::setQuery,
                onRemove = viewModel::removeHistory,
                onClear = viewModel::clearHistory,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            results is SearchState.Loading -> Text("Recherche…", color = Color(0xFFBBBBBB), modifier = Modifier.padding(horizontal = 40.dp))
            results is SearchState.NeedsKey -> Text(
                "Clé TMDB manquante — renseigne-la dans les réglages.",
                color = Color(0xFFE0A0A0),
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            results is SearchState.Empty -> Text("Aucun résultat pour « $query ».", color = Color(0xFFBBBBBB), modifier = Modifier.padding(horizontal = 40.dp))
            results is SearchState.Results -> ResultsGrid(
                items = (results as SearchState.Results).items,
                firstFocus = firstResultFocus,
                onOpen = { item ->
                    viewModel.remember()
                    onOpenTitle(item.id, item.isTv)
                },
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
            Text("Rechercher un film, une série, un anime…", color = Color(0xFF888888), fontSize = 18.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(ACCENT),
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
        Text("Tape pour rechercher. Ton historique apparaîtra ici.", color = Color(0xFF888888), modifier = modifier)
        return
    }
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Recherches récentes", style = MaterialTheme.typography.titleMedium)
            MoovieIconButton(
                onClick = onClear,
                icon = Icons.Default.DeleteSweep,
                contentDescription = "Vider l'historique",
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
                        contentDescription = "Supprimer « $q »",
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsGrid(items: List<TmdbItem>, firstFocus: FocusRequester, onOpen: (TmdbItem) -> Unit) {
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
                item = item,
                onClick = { onOpen(item) },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResultCard(item: TmdbItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, ACCENT), shape = RoundedCornerShape(10.dp)),
        ),
    ) {
        Column {
            AsyncImage(
                model = item.posterUrl(),
                contentDescription = item.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Color(0xFF222222)),
            )
            Text(
                text = item.displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
