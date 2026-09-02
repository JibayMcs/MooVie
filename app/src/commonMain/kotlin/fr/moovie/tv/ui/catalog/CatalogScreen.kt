package fr.moovie.tv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import fr.moovie.tv.data.download.DownloadRepository
import fr.moovie.tv.data.download.TitleDownloads
import fr.moovie.tv.data.download.byTitle
import fr.moovie.tv.ui.download.DownloadPosterBadge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import fr.moovie.tv.data.home.HomeLayoutEntry
import fr.moovie.tv.data.home.pinnedGenreKey
import fr.moovie.tv.data.tmdb.Genre
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
import fr.moovie.tv.resources.pin_action
import fr.moovie.tv.resources.watchlist_added
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.components.LocalMoovieFocusMemory
import fr.moovie.tv.ui.components.SkeletonGrid
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieMarqueeText
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.search.SearchFilterBar
import fr.moovie.tv.data.search.SearchFilters
import androidx.compose.foundation.lazy.grid.GridItemSpan
import fr.moovie.tv.ui.theme.MOOVIE_BG
import fr.moovie.tv.ui.theme.MOOVIE_SCRIM
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE_HIGH
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.margePage

/**
 * Largeur du volet des genres. Reprend celle des réglages : en 1080p l'écran ne
 * fait que 960 dp de large, un volet plus large étranglerait la grille.
 */
private val NAV_WIDTH = 260.dp

/** Colonnes de la grille. Cinq, et non six comme la recherche : le volet mange 260 dp. */
private const val COLUMNS = 5

/** Colonnes en portrait sur téléphone : trois affiches d'environ 140 dp. */
private const val COMPACT_COLUMNS = 3

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
    /** Tri et filtres du catalogue, conservés entre sessions. */
    filters: SearchFilters = SearchFilters.DEFAULT,
    onFiltersChange: (SearchFilters) -> Unit = {},
    /** Disposition de l'accueil : les repères proposés par la modale d'épinglage. */
    layout: List<HomeLayoutEntry> = emptyList(),
    /** Clés (`movie:28`) des genres déjà épinglés — pastille et action inverse. */
    pinnedKeys: Set<String> = emptySet(),
    onPin: (isTv: Boolean, genre: Genre, anchorId: String?, after: Boolean) -> Unit = { _, _, _, _ -> },
    onUnpin: (isTv: Boolean, genreId: Int) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    // Desktop uniquement : la télécommande a sa propre touche Retour.
    showBackButton: Boolean = false,
) {
    val firstGenreFocus = remember { FocusRequester() }
    // Genre dont la modale d'épinglage est ouverte.
    var pinFor by remember { mutableStateOf<CatalogEntry.GenreEntry?>(null) }
    val focusMemory = LocalMoovieFocusMemory.current

    /**
     * Genre qui prend le focus **à l'ouverture** : celui qui est sélectionné à
     * cet instant, à défaut le premier de la liste.
     *
     * Viser le premier quoi qu'il arrive était un piège : le volet sélectionne
     * *au focus*, si bien qu'arriver par « En voir plus » posait le focus sur
     * « Action », qui réécrasait aussitôt le genre demandé. La page s'ouvrait
     * alors sur autre chose que ce qu'on venait de cliquer.
     *
     * Mais c'est une cible d'**arrivée**, pas un suivi : la clé de `remember`
     * est la liste des genres, volontairement pas la sélection. Recalculer à
     * chaque sélection ferait sauter le volet et redemanderait le focus à chaque
     * cran de D-pad — or la sélection *suit* le focus ici, on se battrait contre
     * l'utilisateur en train de parcourir la liste.
     */
    @Suppress("ProduceStateDoesNotAssignValue")
    val focusIndex = remember(entries) {
        entries
            .indexOfFirst {
                it is CatalogEntry.GenreEntry &&
                    it.isTv == selection?.isTv &&
                    it.genre.id == selection.genreId
            }
            .takeIf { it >= 0 }
            ?: entries.indexOfFirst { it is CatalogEntry.GenreEntry }
    }

    // Le focus arrive sur le volet, pas sur la grille : c'est lui qui pilote la
    // page. Les genres arrivent après la 1re composition (appel TMDB), on
    // retente donc tant que la liste n'est pas posée.
    LaunchedEffect(focusIndex) {
        if (entries.isEmpty() || focusIndex < 0) return@LaunchedEffect
        runCatching { firstGenreFocus.requestFocus() }
    }

    val results = @Composable {
        when {
            state is CatalogState.NeedsKey -> Message(stringResource(Res.string.search_needs_key))
            state is CatalogState.Idle -> Message(stringResource(Res.string.catalog_pick_genre))
            state is CatalogState.Empty -> Message(stringResource(Res.string.explore_no_results))
            items.isEmpty() && state is CatalogState.Loading ->
                // Grille fantôme au même nombre de colonnes que la vraie : la
                // page ne se réorganise pas quand les affiches arrivent.
                SkeletonGrid(
                    columns = if (useBottomNav) COMPACT_COLUMNS else COLUMNS,
                    modifier = Modifier.padding(horizontal = margePage()),
                )
            else -> ResultsGrid(
                items = items,
                watched = watched,
                watchlistKeys = watchlistKeys,
                onLoadMore = onLoadMore,
                filters = filters,
                onFiltersChange = onFiltersChange,
                onOpenTitle = onOpenTitle,
            )
        }
    }

    // Sur téléphone, le volet vertical devient une rangée de puces horizontale.
    //
    // Pas un maître-détail comme les réglages : ici on compare, on hésite, on
    // saute d'un genre à l'autre. Cacher la grille derrière une liste imposerait
    // un aller-retour à chaque essai, alors qu'une rangée de puces garde le
    // choix et son résultat visibles ensemble — et ne coûte que sa hauteur.
    // Rendue dans les deux dispositions : c'est le même geste et la même
    // question, que les genres soient en volet ou en puces.
    val pinDialog = @Composable {
        pinFor?.let { entry ->
            val key = pinnedGenreKey(entry.isTv, entry.genre.id)
            PinGenreDialog(
                genreName = entry.genre.name,
                isPinned = key in pinnedKeys,
                layout = layout,
                onDismiss = { pinFor = null; focusMemory.restore() },
                onPin = { anchorId, after -> onPin(entry.isTv, entry.genre, anchorId, after) },
                onUnpin = { onUnpin(entry.isTv, entry.genre.id) },
            )
        }
    }

    if (useBottomNav) {
        Column(modifier = Modifier.fillMaxSize().background(MOOVIE_BG)) {
            GenreChipRow(
                entries = entries,
                selection = selection,
                pinnedKeys = pinnedKeys,
                focusIndex = focusIndex,
                onSelectGenre = onSelectGenre,
                onPinRequest = { pinFor = it },
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { results() }
        }
        pinDialog()
        return
    }

    Row(modifier = Modifier.fillMaxSize().background(MOOVIE_BG)) {
        GenrePane(
            entries = entries,
            selection = selection,
            pinnedKeys = pinnedKeys,
            firstGenreFocus = firstGenreFocus,
            focusIndex = focusIndex,
            onSelectGenre = onSelectGenre,
            onPinRequest = { pinFor = it },
            onBack = onBack,
            showBackButton = showBackButton,
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 40.dp)) {
            results()
        }
    }
    pinDialog()
}

/**
 * Genres en rangée horizontale, pour le portrait.
 *
 * Les intitulés « Films » / « Séries » restent, mais deviennent des séparateurs
 * dans le fil : sans eux, « Action » de film et « Action » de série se
 * suivraient sans qu'on puisse les distinguer.
 */
@Composable
private fun GenreChipRow(
    entries: List<CatalogEntry>,
    selection: CatalogSelection?,
    pinnedKeys: Set<String>,
    focusIndex: Int,
    onSelectGenre: (isTv: Boolean, genreId: Int) -> Unit,
    onPinRequest: (CatalogEntry.GenreEntry) -> Unit,
) {
    // Sans ce recalage, une arrivée par « En voir plus » montrait la rangée de
    // puces au début, avec le genre sélectionné hors écran : le résultat
    // s'affichait sans qu'on voie ce qui l'avait produit.
    val chipsState = rememberLazyListState()
    LaunchedEffect(focusIndex, entries.size) {
        if (focusIndex > 0 && focusIndex <= entries.lastIndex) {
            runCatching { chipsState.scrollToItem(focusIndex) }
        }
    }

    LazyRow(
        state = chipsState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = entries,
            key = { entry ->
                when (entry) {
                    is CatalogEntry.Header -> "h-${entry.isTv}"
                    is CatalogEntry.GenreEntry -> "g-${entry.isTv}-${entry.genre.id}"
                }
            },
        ) { entry ->
            when (entry) {
                is CatalogEntry.Header -> Text(
                    stringResource(
                        if (entry.isTv) Res.string.explore_series else Res.string.explore_movies,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MOOVIE_ACCENT,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                is CatalogEntry.GenreEntry -> MoovieButton(
                    onClick = { onSelectGenre(entry.isTv, entry.genre.id) },
                    onLongClick = { onPinRequest(entry) },
                    selected = selection?.isTv == entry.isTv &&
                        selection.genreId == entry.genre.id,
                ) {
                    Text(entry.genre.name, maxLines = 1)
                    PinnedMark(pinnedGenreKey(entry.isTv, entry.genre.id) in pinnedKeys)
                }
            }
        }
    }
}

@Composable
private fun GenrePane(
    entries: List<CatalogEntry>,
    selection: CatalogSelection?,
    pinnedKeys: Set<String>,
    firstGenreFocus: FocusRequester,
    focusIndex: Int,
    onSelectGenre: (isTv: Boolean, genreId: Int) -> Unit,
    onPinRequest: (CatalogEntry.GenreEntry) -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean,
) {
    // Le volet fait une quarantaine d'entrées : le genre visé est souvent hors
    // de la fenêtre composée, et un `FocusRequester` posé sur un nœud qui
    // n'existe pas encore ne fait rien. On l'amène donc d'abord à l'écran.
    // `+ 1` : l'entrée 0 de la liste est le titre du volet.
    val paneState = rememberLazyListState()
    LaunchedEffect(focusIndex, entries.size) {
        if (focusIndex > 0 && focusIndex <= entries.lastIndex) {
            runCatching { paneState.scrollToItem(focusIndex + 1) }
        }
    }

    // Volet défilant : films et séries réunis font une quarantaine d'entrées,
    // bien au-delà de ce qu'un écran affiche.
    LazyColumn(
        state = paneState,
        modifier = Modifier
            .width(NAV_WIDTH)
            .fillMaxHeight()
            .background(MOOVIE_SURFACE),
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
                    val isFirstGenre = index == focusIndex
                    MoovieButton(
                        onClick = { onSelectGenre(entry.isTv, entry.genre.id) },
                        // Appui long OK à la télécommande, clic droit à la souris.
                        onLongClick = { onPinRequest(entry) },
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
                        PinnedMark(pinnedGenreKey(entry.isTv, entry.genre.id) in pinnedKeys)
                    }
                }
            }
        }
    }
}

/**
 * Punaise sur un genre déjà épinglé.
 *
 * Sans elle, rien ne distingue un genre qui est sur l'accueil d'un autre, et le
 * geste d'appui long n'a aucun état visible à annoncer — on épingle deux fois
 * sans le savoir. Elle ne prend la place que quand elle a quelque chose à dire.
 */
@Composable
private fun RowScope.PinnedMark(pinned: Boolean) {
    if (!pinned) return
    Spacer(Modifier.width(6.dp))
    Icon(
        Icons.Default.PushPin,
        contentDescription = stringResource(Res.string.pin_action),
        tint = MOOVIE_ACCENT,
        modifier = Modifier.size(14.dp),
    )
}

@Composable
private fun ResultsGrid(
    items: List<TmdbItem>,
    watched: Set<String>,
    watchlistKeys: Set<String>,
    onLoadMore: () -> Unit,
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    /**
     * Tri et filtres, **sans valeur par défaut** : ce composable en a besoin
     * pour rendre la barre, et un défaut ici laissait l'écran oublier de les
     * transmettre sans que rien ne le signale. Les boutons affichaient alors
     * l'état d'origine et leurs clics partaient dans une lambda vide.
     */
    filters: SearchFilters,
    onFiltersChange: (SearchFilters) -> Unit,
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

    // Résumé des téléchargements par titre, calculé une fois pour toute la
    // grille : chaque affiche n'a qu'à s'y indexer.
    val downloadsByTitle by remember { DownloadRepository().downloads }
        .collectAsState(initial = emptyList())
        .let { state -> remember(state.value) { mutableStateOf(state.value.byTitle()) } }

    LazyVerticalGrid(
        state = gridState,
        // 5 colonnes tiennent sur les 960 dp d'un 1080p, pas sur les 448 dp d'un
        // téléphone en portrait : les affiches y feraient 80 dp de large, soit
        // moins qu'une vignette de contact.
        columns = GridCells.Fixed(if (useBottomNav) COMPACT_COLUMNS else COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // Marges intérieures : la grille clippe à ses bords, et les cartes
        // agrandies au focus ont besoin de cette réserve pour ne pas être rognées.
        //
        // **Rien à réserver au doigt** : il n'y a pas de focus, donc aucune carte
        // ne déborde. Les 40 dp de la TV — proportionnés sur 960 dp — prenaient
        // 18 % de la largeur d'un portrait de 441 dp et rognaient les titres.
        // La grille fantôme, elle, faisait déjà la distinction : les deux se
        // contredisaient à l'écran, l'une chassant l'autre à l'arrivée des
        // affiches.
        contentPadding = PaddingValues(
            horizontal = margePage(),
            vertical = 12.dp,
        ),
    ) {
        // La barre de filtres occupe une ligne entière, **dans** la grille.
        //
        // Posée au-dessus, elle aurait demandé de restructurer la page ; posée
        // ici elle défile avec les affiches, ce qui la fait disparaître dès
        // qu'on descend — et au D-pad, elle se retrouve naturellement au-dessus
        // de la première rangée, donc atteignable d'une flèche vers le haut.
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchFilterBar(
                filters = filters,
                onChange = onFiltersChange,
                // Les marges sont déjà portées par le `contentPadding` de la
                // grille : les cumuler décalerait la barre par rapport aux
                // affiches qu'elle surplombe.
                hPad = 0.dp,
                // Le type est déjà décidé par le genre choisi, et la pertinence
                // n'existe pas sans texte à comparer.
                showMedia = false,
                allowRelevance = false,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        itemsIndexed(items, key = { _, it -> "${it.id}_${it.isTv}" }) { _, item ->
            val key = if (item.isTv) "tv:${item.id}" else "movie:${item.id}"
            PosterCard(
                item = item,
                isWatched = key in watched,
                inWatchlist = key in watchlistKeys,
                downloads = downloadsByTitle[key],
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
    /** Ce que ce titre a hors ligne, null s'il n'a rien. */
    downloads: TitleDownloads?,
    onClick: () -> Unit,
) {
    MoovieCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box {
                MoovieAsyncImage(
                    model = item.posterUrl(),
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(MOOVIE_SURFACE_HIGH),
                )
                if (inWatchlist || isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MOOVIE_SCRIM),
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
                DownloadPosterBadge(downloads)
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
        color = MOOVIE_TEXT_DIM,
        modifier = Modifier.padding(horizontal = margePage()),
    )
}
