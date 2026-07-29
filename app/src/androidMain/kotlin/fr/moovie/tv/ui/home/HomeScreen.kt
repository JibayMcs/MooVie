package fr.moovie.tv.ui.home

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import fr.moovie.tv.R
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton

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
    // Élément survolé (focus D-pad) → alimente le hero et le fond dynamique.
    var focused by remember { mutableStateOf<TmdbItem?>(null) }
    // Cible de descente depuis l'en-tête : sans ça, le focus reste bloqué sur
    // les boutons (les cartes sont hors du faisceau vertical du D-pad).
    val firstContentFocus = remember { FocusRequester() }
    // Entrée du rail dont le menu contextuel (appui long) est ouvert.
    var resumeMenuFor by remember { mutableStateOf<ResumeEntry?>(null) }

    val rows = (state as? HomeState.Ready)?.rows
    val featured = focused ?: rows?.firstOrNull()?.items?.firstOrNull()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        // Backdrop dynamique avec fondu enchaîné quand l'élément focalisé change.
        Crossfade(
            targetState = featured?.backdropUrl(),
            label = "homeBackdrop",
            modifier = Modifier.fillMaxSize(),
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(28.dp),
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x990A0A0A), Color(0xF20A0A0A))),
            ),
        )

        // Marges horizontales portées par les enfants (contentPadding pour les
        // rangées) : les conteneurs scrollables s'étendent jusqu'aux bords de
        // l'écran et ne rognent plus les cartes/boutons agrandis par le focus.
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Moo-vie", style = MaterialTheme.typography.headlineMedium)
                // Descente explicite depuis l'en-tête : la 1re carte est hors du
                // faisceau vertical du D-pad, la recherche de focus native échoue.
                val headerDown = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        runCatching { firstContentFocus.requestFocus() }.isSuccess
                    } else {
                        false
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoovieIconButton(
                        onClick = onOpenSearch,
                        icon = Icons.Default.Search,
                        contentDescription = stringResource(R.string.home_search),
                        modifier = headerDown,
                    )
                    MoovieIconButton(
                        onClick = onOpenSettings,
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.home_settings),
                        modifier = headerDown,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                HomeState.Loading -> Text(stringResource(R.string.common_loading), modifier = Modifier.padding(horizontal = 32.dp))
                is HomeState.NeedsApiKey -> Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                    Text(s.reason)
                    Spacer(Modifier.height(16.dp))
                    MoovieButton(onClick = onOpenSettings) { Text(stringResource(R.string.home_open_settings)) }
                }
                is HomeState.Ready -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(bottom = 48.dp),
                ) {
                    item { Hero(featured) }
                    if (resume.isNotEmpty()) {
                        item {
                            ResumeRow(
                                entries = resume,
                                onResume = onResume,
                                onMenu = { resumeMenuFor = it },
                                firstFocus = firstContentFocus,
                            )
                        }
                    }
                    itemsIndexed(s.rows) { rowIndex, row ->
                        CatalogRow(
                            row = row,
                            watched = watched,
                            onOpenTitle = onOpenTitle,
                            onFocusItem = { focused = it },
                            // Sans rail Reprendre, la 1re rangée devient la cible
                            // de descente depuis l'en-tête.
                            firstFocus = if (resume.isEmpty() && rowIndex == 0) firstContentFocus else null,
                        )
                    }
                }
            }
        }

        resumeMenuFor?.let { entry ->
            ResumeMenuDialog(
                entry = entry,
                onDismiss = { resumeMenuFor = null },
                onRemove = { viewModel.removeResume(entry.key) },
                onMarkWatched = { viewModel.markResumeWatched(entry.key) },
            )
        }
    }
}

@Composable
private fun Hero(item: TmdbItem?) {
    if (item == null) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)) {
        Text(item.displayTitle, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            item.year?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Color(0xFFCCCCCC)) }
            if (item.voteAverage > 0) {
                Text("★ %.1f".format(item.voteAverage), style = MaterialTheme.typography.titleMedium, color = Color(0xFFE6B800))
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth(0.6f)) {
            Text(
                item.overview.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFDDDDDD),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Rail « Reprendre la lecture » : cartes paysage avec barre de progression. */
@Composable
private fun ResumeRow(
    entries: List<ResumeEntry>,
    onResume: (ResumeEntry) -> Unit,
    onMenu: (ResumeEntry) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Column {
        Text(
            stringResource(R.string.home_continue_watching),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.key }) { index, entry ->
                ResumeCard(
                    entry = entry,
                    onClick = { onResume(entry) },
                    onLongClick = { onMenu(entry) },
                    modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
}

/** Menu contextuel d'une entrée du rail : retirer de la liste ou marquer vu. */
@Composable
private fun ResumeMenuDialog(
    entry: ResumeEntry,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onMarkWatched: () -> Unit,
) {
    val firstAction = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF5161616))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                entry.title + (entry.episodeLabel?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
            MoovieButton(
                onClick = { onRemove(); onDismiss() },
                modifier = Modifier.fillMaxWidth().focusRequester(firstAction),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.resume_remove))
            }
            MoovieButton(
                onClick = { onMarkWatched(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.mark_watched))
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstAction.requestFocus() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResumeCard(
    entry: ResumeEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainMin = ((entry.durationMs - entry.positionMs) / 60_000).coerceAtLeast(0)
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(260.dp),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color(0xFFB5302C)),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF222222)),
            ) {
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                LinearProgressIndicator(
                    progress = entry.progress,
                    color = Color(0xFFB5302C),
                    trackColor = Color(0x99000000),
                    modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                )
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = listOfNotNull(
                        entry.episodeLabel,
                        if (entry.durationMs > 0) stringResource(R.string.home_minutes_left, remainMin) else null,
                    ).joinToString(" · ").ifBlank { stringResource(R.string.home_in_progress) },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9A9A9A),
                )
            }
        }
    }
}

@Composable
private fun CatalogRow(
    row: HomeRow,
    watched: Set<String>,
    onOpenTitle: (Int, Boolean) -> Unit,
    onFocusItem: (TmdbItem) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Column {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
        ) {
            itemsIndexed(row.items) { index, item ->
                PosterCard(
                    item = item,
                    // Badge ✓ seulement pour les films (une série n'a pas de clé unique).
                    isWatched = !item.isTv && "movie:${item.id}" in watched,
                    onClick = { onOpenTitle(item.id, item.isTv) },
                    onFocusItem = onFocusItem,
                    modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(
    item: TmdbItem,
    isWatched: Boolean,
    onClick: () -> Unit,
    onFocusItem: (TmdbItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(150.dp)
            .onFocusChanged { if (it.isFocused) onFocusItem(item) },
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color(0xFFB5302C)),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = item.posterUrl(),
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )
                if (isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC0A0A0A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", color = Color(0xFF5FD98A), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
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
