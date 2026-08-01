package fr.moovie.tv.ui.home

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.WatchlistEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.catalog_open
import fr.moovie.tv.resources.common_loading
import fr.moovie.tv.resources.history_title
import fr.moovie.tv.resources.home_continue_watching
import fr.moovie.tv.core.format.formatDuration
import fr.moovie.tv.ui.components.MoovieProgressBar
import fr.moovie.tv.resources.home_in_progress
import fr.moovie.tv.resources.home_time_left
import fr.moovie.tv.resources.home_open_settings
import fr.moovie.tv.resources.home_search
import fr.moovie.tv.resources.home_settings
import fr.moovie.tv.resources.mark_watched
import fr.moovie.tv.resources.resume_remove
import fr.moovie.tv.resources.watchlist_add
import fr.moovie.tv.resources.watchlist_open
import fr.moovie.tv.resources.watchlist_remove
import fr.moovie.tv.resources.watchlist_row
import fr.moovie.tv.ui.components.LocalMoovieFocusMemory
import fr.moovie.tv.ui.components.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieMarqueeText
import fr.moovie.tv.ui.components.MoovieRail
import org.jetbrains.compose.resources.stringResource

/**
 * Écran d'accueil partagé TV + desktop : état hoisté (le ViewModel reste
 * côté plateforme tant que les repos DataStore vivent en androidMain).
 */
@Composable
fun HomeScreenContent(
    state: HomeState,
    resume: List<ResumeEntry>,
    watched: Set<String>,
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onResume: (ResumeEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCatalog: () -> Unit,
    onRemoveResume: (String) -> Unit,
    onMarkResumeWatched: (String) -> Unit,
    watchlist: List<WatchlistEntry> = emptyList(),
    onRemoveFromWatchlist: (String) -> Unit = {},
    onAddToWatchlist: (TmdbItem) -> Unit = {},
) {
    // Élément focalisé (D-pad) ou survolé → alimente le hero et le fond.
    var focused by remember { mutableStateOf<HeroTarget?>(null) }
    var watchlistMenuFor by remember { mutableStateOf<WatchlistEntry?>(null) }
    var catalogMenuFor by remember { mutableStateOf<TmdbItem?>(null) }
    // Clés déjà mises de côté : sert au badge des affiches du catalogue et au
    // libellé du menu (ajouter / retirer).
    val watchlistKeys = remember(watchlist) { watchlist.map { it.key }.toSet() }
    // Cible de descente depuis l'en-tête : sans ça, le focus reste bloqué sur
    // les boutons (les cartes sont hors du faisceau vertical du D-pad).
    val firstContentFocus = remember { FocusRequester() }
    // Entrée du rail dont le menu contextuel (appui long) est ouvert.
    var resumeMenuFor by remember { mutableStateOf<ResumeEntry?>(null) }
    // Rend le focus à la carte qui a ouvert le menu, plutôt que de le laisser
    // repartir sur le premier bouton de l'en-tête.
    val focusMemory = LocalMoovieFocusMemory.current

    val rows = (state as? HomeState.Ready)?.rows
    // Valeur par défaut = ce qui prendra le focus en premier. Sans ça le hero
    // décrivait un film en tendance alors que le focus arrive sur « Reprendre
    // la lecture » : titre et synopsis ne correspondaient pas à la carte visée.
    val fallback = remember(resume, rows) {
        resume.firstOrNull()?.let { HeroTarget.Resume(it) }
            ?: rows?.firstOrNull()?.items?.firstOrNull()?.let { HeroTarget.Catalog(it) }
    }
    val featured = focused ?: fallback

    // Opacité du hero pendant le défilement : 1 en haut de liste, 0 dès qu'il a
    // parcouru sa propre hauteur. `derivedStateOf` pour ne recomposer que le
    // hero, et pas toute la page à chaque pixel de scroll.
    val listState = rememberLazyListState()
    val heroPx = with(LocalDensity.current) { HERO_HEIGHT.toPx() }
    val heroAlpha by remember(heroPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                0f
            } else {
                (1f - listState.firstVisibleItemScrollOffset / heroPx).coerceIn(0f, 1f)
            }
        }
    }

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
                        contentDescription = stringResource(Res.string.home_search),
                        modifier = headerDown,
                    )
                    // Parcourir par genre : geste distinct de la recherche par
                    // titre, d'où un bouton à part plutôt qu'un onglet caché
                    // derrière le champ de saisie.
                    MoovieIconButton(
                        onClick = onOpenCatalog,
                        icon = Icons.Default.GridView,
                        contentDescription = stringResource(Res.string.catalog_open),
                        modifier = headerDown,
                    )
                    // Entre la loupe et l'engrenage : le focus par défaut de la
                    // barre reste sur la recherche, l'historique est à un cran.
                    MoovieIconButton(
                        onClick = onOpenHistory,
                        icon = Icons.Default.History,
                        contentDescription = stringResource(Res.string.history_title),
                        modifier = headerDown,
                    )
                    MoovieIconButton(
                        onClick = onOpenSettings,
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(Res.string.home_settings),
                        modifier = headerDown,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                HomeState.Loading -> Text(stringResource(Res.string.common_loading), modifier = Modifier.padding(horizontal = 32.dp))
                is HomeState.NeedsApiKey -> Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                    Text(s.reason)
                    Spacer(Modifier.height(16.dp))
                    MoovieButton(onClick = onOpenSettings) { Text(stringResource(Res.string.home_open_settings)) }
                }
                is HomeState.Ready -> LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(bottom = 48.dp),
                ) {
                    // Le hero s'efface au fil du défilement au lieu d'être
                    // tranché par le bord haut : sinon sa barre de progression,
                    // dernier élément de la colonne, restait seule à l'écran
                    // sous l'en-tête, sans le titre qu'elle décrit.
                    item { Hero(featured, modifier = Modifier.graphicsLayer { alpha = heroAlpha }) }
                    if (resume.isNotEmpty()) {
                        item {
                            ResumeRow(
                                entries = resume,
                                onResume = onResume,
                                onMenu = { resumeMenuFor = it },
                                onFocusEntry = { focused = HeroTarget.Resume(it) },
                                firstFocus = firstContentFocus,
                            )
                        }
                    }
                    // Juste après « Reprendre » : ce qu'on a mis de côté vient
                    // avant le catalogue, qui est de la découverte.
                    if (watchlist.isNotEmpty()) {
                        item {
                            WatchlistRow(
                                entries = watchlist,
                                onOpenTitle = onOpenTitle,
                                onMenu = { watchlistMenuFor = it },
                                firstFocus = if (resume.isEmpty()) firstContentFocus else null,
                            )
                        }
                    }
                    itemsIndexed(s.rows) { rowIndex, row ->
                        CatalogRow(
                            row = row,
                            watched = watched,
                            onOpenTitle = onOpenTitle,
                            onFocusItem = { focused = HeroTarget.Catalog(it) },
                            watchlistKeys = watchlistKeys,
                            onMenu = { catalogMenuFor = it },
                            // Sans rail Reprendre ni watchlist, la 1re rangée
                            // devient la cible de descente depuis l'en-tête.
                            firstFocus = if (resume.isEmpty() && watchlist.isEmpty() && rowIndex == 0) {
                                firstContentFocus
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        catalogMenuFor?.let { item ->
            val key = if (item.isTv) "tv:${item.id}" else "movie:${item.id}"
            CatalogMenuDialog(
                title = item.displayTitle,
                inWatchlist = key in watchlistKeys,
                onDismiss = { catalogMenuFor = null; focusMemory.restore() },
                onToggle = {
                    if (key in watchlistKeys) onRemoveFromWatchlist(key) else onAddToWatchlist(item)
                },
            )
        }

        watchlistMenuFor?.let { entry ->
            WatchlistMenuDialog(
                entry = entry,
                onDismiss = { watchlistMenuFor = null; focusMemory.restore() },
                onOpen = { onOpenTitle(entry.tmdbId, entry.isTv) },
                onRemove = { onRemoveFromWatchlist(entry.key) },
            )
        }

        resumeMenuFor?.let { entry ->
            ResumeMenuDialog(
                entry = entry,
                onDismiss = { resumeMenuFor = null; focusMemory.restore() },
                onRemove = { onRemoveResume(entry.key) },
                onMarkWatched = { onMarkResumeWatched(entry.key) },
            )
        }
    }
}

/**
 * Média décrit par le hero. Il suit l'élément réellement focalisé — y compris
 * une carte du rail « Reprendre la lecture », qui n'a rien à voir avec les
 * rangées de tendances.
 */
private sealed interface HeroTarget {
    data class Catalog(val item: TmdbItem) : HeroTarget
    data class Resume(val entry: ResumeEntry) : HeroTarget

    fun backdropUrl(): String? = when (this) {
        is Catalog -> item.backdropUrl()
        is Resume -> entry.imageUrl
    }
}

/**
 * Hauteur fixe : le hero est le 1er élément d'une LazyColumn, une hauteur
 * variable ferait sauter toutes les rangées au changement de focus.
 */
private val HERO_HEIGHT = 176.dp

@Composable
private fun Hero(target: HeroTarget?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .padding(horizontal = 32.dp, vertical = 8.dp),
    ) {
        when (target) {
            is HeroTarget.Catalog -> CatalogHero(target.item)
            is HeroTarget.Resume -> ResumeHero(target.entry)
            null -> Unit
        }
    }
}

@Composable
private fun CatalogHero(item: TmdbItem) {
    Column {
        Text(
            item.displayTitle,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

/** Hero d'une entrée « Reprendre » : progression plutôt que synopsis. */
@Composable
private fun ResumeHero(entry: ResumeEntry) {
    val remaining = formatDuration(((entry.durationMs - entry.positionMs) / 60_000).toInt())
    Column {
        Text(
            stringResource(Res.string.home_continue_watching),
            style = MaterialTheme.typography.titleMedium,
            color = MOOVIE_ACCENT,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            entry.title,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            entry.episodeLabel?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = Color(0xFFCCCCCC))
            }
            Text(
                if (entry.durationMs > 0 && remaining != null) {
                    stringResource(Res.string.home_time_left, remaining)
                } else {
                    stringResource(Res.string.home_in_progress)
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFCCCCCC),
            )
        }
        Spacer(Modifier.height(12.dp))
        MoovieProgressBar(
            progress = entry.progress,
            modifier = Modifier.fillMaxWidth(0.3f).height(4.dp),
        )
    }
}

/** Rail « Reprendre la lecture » : cartes paysage avec barre de progression. */
@Composable
private fun ResumeRow(
    entries: List<ResumeEntry>,
    onResume: (ResumeEntry) -> Unit,
    onMenu: (ResumeEntry) -> Unit,
    onFocusEntry: (ResumeEntry) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Column {
        Text(
            stringResource(Res.string.home_continue_watching),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(8.dp))
        val listState = rememberLazyListState()
        MoovieRail(listState) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.key }) { index, entry ->
                ResumeCard(
                    entry = entry,
                    onClick = { onResume(entry) },
                    onLongClick = { onMenu(entry) },
                    onFocusEntry = onFocusEntry,
                    modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
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
                Text(stringResource(Res.string.resume_remove))
            }
            MoovieButton(
                onClick = { onMarkWatched(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.mark_watched))
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstAction.requestFocus() } }
}

@Composable
private fun ResumeCard(
    entry: ResumeEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocusEntry: (ResumeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining = formatDuration(((entry.durationMs - entry.positionMs) / 60_000).toInt())
    MoovieCard(
        onClick = onClick,
        onLongClick = onLongClick,
        focusedScale = 1.08f,
        modifier = modifier
            .width(260.dp)
            .onFocusChanged { if (it.isFocused) onFocusEntry(entry) },
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
                MoovieProgressBar(
                    progress = entry.progress,
                    trackColor = Color(0x99000000),
                    modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                )
            }
            Column(modifier = Modifier.padding(8.dp)) {
                MoovieMarqueeText(
                    text = entry.title,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = listOfNotNull(
                        entry.episodeLabel,
                        if (entry.durationMs > 0 && remaining != null) {
                            stringResource(Res.string.home_time_left, remaining)
                        } else {
                            null
                        },
                    ).joinToString(" · ").ifBlank { stringResource(Res.string.home_in_progress) },
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
    watchlistKeys: Set<String> = emptySet(),
    onMenu: (TmdbItem) -> Unit = {},
    firstFocus: FocusRequester? = null,
) {
    Column {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(8.dp))
        val listState = rememberLazyListState()
        MoovieRail(listState) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
            ) {
                itemsIndexed(row.items) { index, item ->
                    PosterCard(
                        item = item,
                        // Badge ✓ seulement pour les films (une série n'a pas de clé unique).
                        isWatched = !item.isTv && "movie:${item.id}" in watched,
                        inWatchlist = (if (item.isTv) "tv:${item.id}" else "movie:${item.id}") in watchlistKeys,
                        onClick = { onOpenTitle(item.id, item.isTv) },
                        onLongClick = { onMenu(item) },
                        onFocusItem = onFocusItem,
                        modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterCard(
    item: TmdbItem,
    isWatched: Boolean,
    onClick: () -> Unit,
    onFocusItem: (TmdbItem) -> Unit,
    inWatchlist: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    MoovieCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(150.dp)
            .onFocusChanged { if (it.isFocused) onFocusItem(item) },
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
                // Signet en bas : l'ajout depuis une rangée doit se voir tout de
                // suite, sans quoi rien ne distingue une carte déjà mise de côté.
                if (inWatchlist) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC0A0A0A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = MOOVIE_ACCENT,
                            modifier = Modifier.size(13.dp),
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

/**
 * Rail « À regarder plus tard » : affiches, sans barre de progression — rien
 * n'a encore été lu, contrairement au rail « Reprendre » juste au-dessus.
 */
@Composable
private fun WatchlistRow(
    entries: List<WatchlistEntry>,
    onOpenTitle: (Int, Boolean) -> Unit,
    onMenu: (WatchlistEntry) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Column {
        Text(
            stringResource(Res.string.watchlist_row),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(8.dp))
        val listState = rememberLazyListState()
        MoovieRail(listState) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
            ) {
                itemsIndexed(entries, key = { _, e -> e.key }) { index, entry ->
                    WatchlistCard(
                        entry = entry,
                        onClick = { onOpenTitle(entry.tmdbId, entry.isTv) },
                        onLongClick = { onMenu(entry) },
                        modifier = if (index == 0 && firstFocus != null) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

/** Affiche d'un titre mis de côté, avec le signet qui rappelle d'où il vient. */
@Composable
private fun WatchlistCard(
    entry: WatchlistEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoovieCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(150.dp)) {
        Column {
            Box {
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC0A0A0A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MOOVIE_ACCENT,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            MoovieMarqueeText(
                text = entry.title,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Menu contextuel d'un titre mis de côté : ouvrir sa fiche ou le retirer. */
@Composable
private fun WatchlistMenuDialog(
    entry: WatchlistEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
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
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            MoovieButton(
                onClick = { onOpen(); onDismiss() },
                modifier = Modifier.fillMaxWidth().focusRequester(firstAction),
            ) {
                Text(stringResource(Res.string.watchlist_open))
            }
            MoovieButton(
                onClick = { onRemove(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.watchlist_remove))
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstAction.requestFocus() } }
}

/** Menu d'appui long sur une affiche du catalogue : mise de côté du titre. */
@Composable
private fun CatalogMenuDialog(
    title: String,
    inWatchlist: Boolean,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
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
            Text(title, style = MaterialTheme.typography.titleMedium)
            MoovieButton(
                onClick = { onToggle(); onDismiss() },
                modifier = Modifier.fillMaxWidth().focusRequester(firstAction),
                selected = inWatchlist,
            ) {
                Icon(
                    if (inWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(
                        if (inWatchlist) Res.string.watchlist_remove else Res.string.watchlist_add,
                    ),
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstAction.requestFocus() } }
}
