package fr.moovie.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import fr.moovie.tv.resources.settings_cat_downloads
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fr.moovie.tv.ui.theme.MoovieShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.WatchlistEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.catalog_open
import fr.moovie.tv.resources.common_loading
import fr.moovie.tv.resources.history_episodes
import fr.moovie.tv.resources.history_title
import fr.moovie.tv.resources.home_continue_watching
import fr.moovie.tv.core.format.formatDuration
import fr.moovie.tv.ui.components.MoovieProgressBar
import fr.moovie.tv.resources.home_in_progress
import fr.moovie.tv.resources.home_next_up
import fr.moovie.tv.resources.home_time_left
import fr.moovie.tv.resources.home_open_settings
import fr.moovie.tv.resources.discovery_open
import fr.moovie.tv.resources.home_search
import fr.moovie.tv.resources.home_see_more
import fr.moovie.tv.resources.home_settings
import fr.moovie.tv.resources.remote_title
import fr.moovie.tv.resources.media_movie
import fr.moovie.tv.resources.media_series
import fr.moovie.tv.resources.mark_watched
import fr.moovie.tv.resources.details_send_to_tv
import fr.moovie.tv.resources.resume_remove
import fr.moovie.tv.resources.watchlist_add
import fr.moovie.tv.resources.watchlist_open
import fr.moovie.tv.resources.watchlist_remove
import fr.moovie.tv.resources.watchlist_row
import fr.moovie.tv.ui.components.LocalMoovieFocusMemory
import fr.moovie.tv.ui.catalog.CatalogSelection
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.data.download.titleKeyOf
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.download.DownloadPosterBadge
import fr.moovie.tv.ui.download.ProvideTitleDownloads
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.components.MoovieFocusLabel
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.download.DownloadCountBadge
import fr.moovie.tv.ui.download.rememberActiveDownloadCount
import fr.moovie.tv.ui.components.MoovieMarqueeText
import fr.moovie.tv.ui.components.MoovieRail
import fr.moovie.tv.ui.components.SkeletonRail
import fr.moovie.tv.ui.components.scrollAsWholeBlock
import org.jetbrains.compose.resources.pluralStringResource
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
    /** Diffuser une reprise sur le téléviseur, null s'il n'y en a pas à portée. */
    onSendResumeToTv: ((ResumeEntry) -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    /**
     * Page Découverte. Vide par défaut pour ne pas casser les appels
     * existants ; l'icône reste affichée, car un bouton qui apparaît selon la
     * plateforme déplacerait le focus de ses voisines.
     */
    onOpenDiscovery: () -> Unit = {},
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    /**
     * Télécommande virtuelle. Null quand elle n'a pas lieu d'être : sur un
     * téléviseur, ou tant qu'aucun téléviseur n'a été appairé.
     */
    onOpenRemote: (() -> Unit)? = null,
    onOpenCatalog: () -> Unit,
    /** « En voir plus » d'une rangée épinglée : rouvre le genre exact. */
    onOpenCatalogGenre: (CatalogSelection) -> Unit,
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
    // Voir RowSlot : la liste doit pouvoir être recalée à la main.
    val rowsState = rememberLazyListState()
    val rowsScope = rememberCoroutineScope()

    // Les créneaux réellement affichés, dans l'ordre voulu par l'utilisateur.
    // « Reprendre » et « Ma liste » gardent leur place dans la disposition, mais
    // disparaissent quand elles n'ont rien à montrer : leur contenu est ici, pas
    // dans l'état, et une rangée vide vaut une rangée absente.
    val slots = remember(state, resume, watchlist) {
        (state as? HomeState.Ready)?.slots.orEmpty().filter { slot ->
            when (slot) {
                HomeSlot.Resume -> resume.isNotEmpty()
                HomeSlot.Watchlist -> watchlist.isNotEmpty()
                is HomeSlot.Catalog -> true
            }
        }
    }
    // Valeur par défaut = ce qui prendra le focus en premier. Sans ça le hero
    // décrivait un film en tendance alors que le focus arrive sur « Reprendre
    // la lecture » : titre et synopsis ne correspondaient pas à la carte visée.
    // Il suit donc le **premier créneau**, quel qu'il soit maintenant qu'ils se
    // réordonnent — le figer sur « Reprendre » le désaccorderait de nouveau.
    val fallback = remember(slots, resume, watchlist) {
        when (val first = slots.firstOrNull()) {
            HomeSlot.Resume -> resume.firstOrNull()?.let { HeroTarget.Resume(it) }
            HomeSlot.Watchlist -> watchlist.firstOrNull()?.let { HeroTarget.Watchlist(it) }
            is HomeSlot.Catalog -> first.row.items.firstOrNull()?.let { HeroTarget.Catalog(it) }
            null -> null
        }
    }
    val featured = focused ?: fallback

    // Abonnement unique de l'écran : les trois sortes de cartes y puisent
    // leur pastille « hors ligne » sans que les rangées aient à la convoyer.
    ProvideTitleDownloads {
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
        // Marges verticales resserrées : chaque dp repris ici va au bloc
        // « titre + rangée », qui doit tenir en entier sous un héros fixe.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp, bottom = 16.dp),
        ) {
            // Le wordmark « Moo-vie » et sa rangée dédiée sont partis : ils
            // coûtaient ~56 dp de haut pour une information qu'on connaît déjà
            // (on est dans l'app). Récupérés, ils laissent enfin passer une
            // rangée d'affiches entière sous un héros qui, lui, reste fixe.
            // Les boutons se posent en surimpression du héros, dans sa moitié
            // droite restée vide.
            // Rien de tout cela au doigt : le héros décrivait l'élément
            // *focalisé*, et il n'y a pas de focus au tactile — il restait donc
            // figé sur le premier titre, à décrire quelque chose que personne
            // n'avait désigné. Sa hauteur revient aux rangées, qui en manquent.
            // Le fond flou, lui, reste : il ne prétend rien, il habille.
            if (!useBottomNav) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (state is HomeState.Ready) Hero(featured)
                // Descente explicite depuis l'en-tête : la 1re carte est hors du
                // faisceau vertical du D-pad, la recherche de focus native échoue.
                val headerDown = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        runCatching { firstContentFocus.requestFocus() }.isSuccess
                    } else {
                        false
                    }
                }
                // Sur tactile, ces quatre icônes vivent dans la barre basse : les
                // répéter en haut du héros les mettrait hors de portée du pouce
                // tout en volant la largeur du titre.
                if (!useBottomNav) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MoovieFocusLabel(stringResource(Res.string.home_search)) {
                        MoovieIconButton(
                            onClick = onOpenSearch,
                            icon = Icons.Default.Search,
                            contentDescription = stringResource(Res.string.home_search),
                            modifier = headerDown,
                        )
                    }
                    // Découverte : troisième geste, encore différent des deux
                    // autres. La recherche répond à « je cherche ce titre », le
                    // catalogue à « montre-moi de la science-fiction », et
                    // celui-ci à « je ne sais pas quoi regarder » — la seule
                    // question qui n'a rien à formuler.
                    MoovieFocusLabel(stringResource(Res.string.discovery_open)) {
                        MoovieIconButton(
                            onClick = onOpenDiscovery,
                            icon = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(Res.string.discovery_open),
                            modifier = headerDown,
                        )
                    }
                    // Parcourir par genre : geste distinct de la recherche par
                    // titre, d'où un bouton à part plutôt qu'un onglet caché
                    // derrière le champ de saisie.
                    MoovieFocusLabel(stringResource(Res.string.catalog_open)) {
                        MoovieIconButton(
                            onClick = onOpenCatalog,
                            icon = Icons.Default.GridView,
                            contentDescription = stringResource(Res.string.catalog_open),
                            modifier = headerDown,
                        )
                    }
                    // Entre la loupe et l'engrenage : le focus par défaut de la
                    // barre reste sur la recherche, l'historique est à un cran.
                    MoovieFocusLabel(stringResource(Res.string.history_title)) {
                        MoovieIconButton(
                            onClick = onOpenHistory,
                            icon = Icons.Default.History,
                            contentDescription = stringResource(Res.string.history_title),
                            modifier = headerDown,
                        )
                    }
                    // Les téléchargements se surveillent, ils ne se règlent
                    // pas : leur place est ici, à côté de l'historique, et non
                    // à trois niveaux dans les réglages. Toujours visible même
                    // à vide — la faire apparaître déplacerait le focus des
                    // voisines au moment le plus inattendu.
                    // La pastille du nombre de téléchargements en cours, comme
                    // sur la barre basse : c'est la même icône, elle doit dire
                    // la même chose. Sans elle, un téléchargement lancé depuis
                    // la fiche ne se voyait nulle part sur cet écran.
                    MoovieFocusLabel(stringResource(Res.string.settings_cat_downloads)) {
                        DownloadCountBadge(rememberActiveDownloadCount()) {
                            MoovieIconButton(
                                onClick = onOpenDownloads,
                                icon = Icons.Default.Download,
                                contentDescription = stringResource(Res.string.settings_cat_downloads),
                                modifier = headerDown,
                            )
                        }
                    }
                    // Télécommande : **seulement au doigt, et seulement si un
                    // téléviseur a été appairé**. Sur un téléviseur, se piloter
                    // soi-même n'a pas de sens ; sans cible, le bouton ouvrirait
                    // un écran vide. Les deux conditions valent mieux qu'un
                    // bouton qui explique pourquoi il ne sert à rien.
                    if (onOpenRemote != null) {
                        MoovieFocusLabel(stringResource(Res.string.remote_title)) {
                            MoovieIconButton(
                                onClick = onOpenRemote,
                                icon = Icons.Default.SettingsRemote,
                                contentDescription = stringResource(Res.string.remote_title),
                                modifier = headerDown,
                            )
                        }
                    }
                    MoovieFocusLabel(stringResource(Res.string.home_settings)) {
                        MoovieIconButton(
                            onClick = onOpenSettings,
                            icon = Icons.Default.Settings,
                            contentDescription = stringResource(Res.string.home_settings),
                            modifier = headerDown,
                        )
                    }
                }
                }
            }
            }
            Spacer(Modifier.height(if (state is HomeState.Ready) 12.dp else 16.dp))

            when (val s = state) {
                // Deux rangées fantômes, à la largeur réelle des cartes : rien
                // ne se réorganise quand les vraies affiches arrivent. C'est ce
                // qu'un « Chargement… » centré ne peut pas faire.
                HomeState.Loading -> Column(
                    modifier = Modifier.padding(horizontal = if (useBottomNav) 16.dp else 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    repeat(2) { SkeletonRail(posterWidth = POSTER_WIDTH) }
                }
                is HomeState.NeedsApiKey -> Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                    Text(s.reason)
                    Spacer(Modifier.height(16.dp))
                    MoovieButton(onClick = onOpenSettings) { Text(stringResource(Res.string.home_open_settings)) }
                }
                is HomeState.Ready -> LazyColumn(
                    state = rowsState,
                    // 16 et non 24 : voir POSTER_WIDTH — ces huit points, plus
                    // ceux gagnés sur les affiches, sont ce qui fait tenir le
                    // titre de la rangée suivante en bas d'écran.
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 48.dp),
                ) {
                    itemsIndexed(slots, key = { _, slot -> slot.id }) { index, slot ->
                        // La cible de descente depuis l'en-tête est toujours le
                        // premier créneau affiché, quel qu'il soit : la 1re carte
                        // est hors du faisceau vertical du D-pad.
                        val entry = if (index == 0) firstContentFocus else null
                        RowSlot(index, rowsState, rowsScope) {
                            when (slot) {
                                HomeSlot.Resume -> ResumeRow(
                                    entries = resume,
                                    onResume = onResume,
                                    onMenu = { resumeMenuFor = it },
                                    onFocusEntry = { focused = HeroTarget.Resume(it) },
                                    firstFocus = entry,
                                )

                                HomeSlot.Watchlist -> WatchlistRow(
                                    entries = watchlist,
                                    onOpenTitle = onOpenTitle,
                                    onMenu = { watchlistMenuFor = it },
                                    onFocusEntry = { focused = HeroTarget.Watchlist(it) },
                                    firstFocus = entry,
                                )

                                is HomeSlot.Catalog -> CatalogRow(
                                    row = slot.row,
                                    watched = watched,
                                    onOpenTitle = onOpenTitle,
                                    onFocusItem = { focused = HeroTarget.Catalog(it) },
                                    watchlistKeys = watchlistKeys,
                                    onMenu = { catalogMenuFor = it },
                                    onSeeMore = onOpenCatalogGenre,
                                    firstFocus = entry,
                                )
                            }
                        }
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
                onSendToTv = onSendResumeToTv?.let { send -> { send(entry) } },
            )
        }
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
    data class Watchlist(val entry: WatchlistEntry) : HeroTarget

    fun backdropUrl(): String? = when (this) {
        is Catalog -> item.backdropUrl()
        is Resume -> entry.imageUrl
        // L'affiche fait office de fond : une entrée mise de côté ne porte pas
        // d'image large. Floutée et rognée, elle ne sert de toute façon que de
        // nappe de couleur.
        is Watchlist -> entry.imageUrl
    }
}

/**
 * Hauteur du héros. Calibrée pour qu'une rangée d'affiches entière tienne
 * dessous en 1080p (540 dp de haut) : héros + marges + rangée = l'écran. La
 * rallonger recoupe les affiches en bas, ce qui était le défaut d'origine.
 */
private val HERO_HEIGHT = 148.dp

/**
 * Cale une rangée en haut de la liste dès qu'elle prend le focus.
 *
 * Le défilement automatique du focus se contente d'amener la rangée *quelque
 * part* dans le cadre : elle se retrouve alors collée en bas, la précédente
 * réduite à une bande d'affiches tronquées au-dessus, et il ne reste rien sous
 * elle. En l'alignant explicitement, tout l'espace gagné passe en bas — là où
 * le titre de la rangée suivante doit apparaître.
 */
@Composable
private fun RowSlot(
    index: Int,
    state: LazyListState,
    scope: CoroutineScope,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.onFocusChanged {
            // Le délai n'est pas décoratif : le `bringIntoView` du système part
            // sur la même prise de focus et s'exécute après nous. Sans ce
            // décalage il écrase l'alignement, et la rangée retombe collée en
            // bas du cadre — c'est exactement ce qu'on cherche à éviter.
            if (it.hasFocus) scope.launch {
                delay(80)
                state.animateScrollToItem(index)
            }
        },
    ) {
        content()
    }
}

/**
 * Largeur des affiches 2:3 des rangées.
 *
 * Calibrée pour qu'une rangée focalisée laisse voir le **titre de la rangée
 * suivante** en bas d'écran. Sans ce repère, rien ne dit qu'il y a autre chose
 * plus bas : la rangée occupe toute la hauteur restante et l'écran paraît
 * terminé. Quelqu'un qui n'a jamais utilisé d'application de streaming n'a
 * aucune raison de tenter le Bas.
 *
 * Mesuré en 1080p / 320 dpi : le bloc d'une rangée fait 310 dp pour 345 dp
 * disponibles sous le héros, et un titre de section en réclame 28 de plus. Les
 * ~20 dp manquants viennent d'ici, le reste de l'espacement entre rangées.
 * Élargir ces affiches referme l'invite.
 */
private val POSTER_WIDTH = 138.dp

/**
 * Place réservée au coin haut droit, où les boutons de la barre sont posés en
 * surimpression du héros depuis le retrait du wordmark. Sans elle, un titre
 * long passe **dessous** — constaté sur « Star Wars, épisode III - La Revanche
 * des Sith ». Ne s'applique qu'au titre : le synopsis, plus bas, garde toute la
 * largeur, qu'on était justement allé chercher.
 */
private val HERO_TITLE_END_INSET = 250.dp

/**
 * Le retrait ne vaut que là où les boutons sont réellement posés sur le héros.
 * Sur tactile ils sont partis dans la barre basse, et réserver 250 dp sur les
 * 448 dp d'un téléphone en portrait — plus de la moitié de l'écran — ne laissait
 * lire que « Spider… » d'un titre pourtant court.
 */
private val heroTitleEndInset: Dp
    @Composable get() = if (useBottomNav) 0.dp else HERO_TITLE_END_INSET

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
            is HeroTarget.Watchlist -> WatchlistHero(target.entry)
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
            modifier = Modifier.padding(end = heroTitleEndInset),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            item.year?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Color(0xFFCCCCCC)) }
            if (item.voteAverage > 0) {
                Text("★ %.1f".format(item.voteAverage), style = MaterialTheme.typography.titleMedium, color = Color(0xFFE6B800))
            }
        }
        Spacer(Modifier.height(10.dp))
        // Pleine largeur : à 60 % le synopsis se coupait au milieu d'une phrase
        // alors que la moitié droite de l'écran était vide.
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                item.overview.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFDDDDDD),
                // Deux lignes sur toute la largeur portent plus de texte que
                // trois sur 60 % : on ne perd rien, et la rangée d'affiches
                // récupère la hauteur qui lui manquait.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Hero d'une entrée « Reprendre » : progression plutôt que synopsis. */
@Composable
private fun ResumeHero(entry: ResumeEntry) {
    val remaining = formatDuration(((entry.durationMs - entry.positionMs) / 60_000).toInt())
    Column(modifier = Modifier.scrollAsWholeBlock()) {
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
            modifier = Modifier.padding(end = heroTitleEndInset),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            entry.episodeLabel?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = Color(0xFFCCCCCC))
            }
            Text(
                when {
                    entry.durationMs > 0 && remaining != null ->
                        stringResource(Res.string.home_time_left, remaining)
                    // Épisode posé là parce qu'on vient de finir le précédent :
                    // annoncer « En cours » sur ce qu'on n'a pas commencé
                    // laisserait croire à une position perdue.
                    entry.queued -> stringResource(Res.string.home_next_up)
                    else -> stringResource(Res.string.home_in_progress)
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

/**
 * Hero d'une entrée « À regarder plus tard ».
 *
 * Volontairement sobre : une entrée mise de côté ne porte ni synopsis, ni note,
 * ni année — seulement ce qu'il fallait pour dessiner sa carte sans requête
 * TMDB. Inventer une ligne de plus demanderait d'aller la chercher au moment
 * même où le focus se déplace, pour un bandeau qu'on quitte aussitôt.
 */
@Composable
private fun WatchlistHero(entry: WatchlistEntry) {
    Column {
        Text(
            stringResource(Res.string.watchlist_row),
            style = MaterialTheme.typography.titleMedium,
            color = MOOVIE_ACCENT,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            entry.title,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = heroTitleEndInset),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(
                stringResource(
                    if (entry.isTv) Res.string.media_series else Res.string.media_movie,
                ),
                entry.totalEpisodes.takeIf { entry.isTv && it > 0 }?.let {
                    pluralStringResource(Res.plurals.history_episodes, it, it)
                },
            ).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFCCCCCC),
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
        // Repli local : même sans cible fournie par l'appelant, la rangée doit
        // pouvoir ramener le focus sur sa première carte quand on l'atteint.
        val entryFocus = firstFocus ?: remember { FocusRequester() }
        MoovieRail(listState, firstFocus = entryFocus) {
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
                    modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
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
    /**
     * Diffuser sur le téléviseur, ou null s'il n'y en a pas à portée.
     *
     * Dans ce menu plutôt que sur la carte : reprendre une lecture est le geste
     * courant, et l'appui long enseigne déjà le reste. Ajouter un bouton visible
     * sur chaque vignette encombrerait une rangée qu'on parcourt à la
     * télécommande pour une action qu'on fait rarement.
     */
    onSendToTv: (() -> Unit)? = null,
) {
    val firstAction = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(MoovieShape)
                .background(Color(0xF5161616))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                entry.title + (entry.episodeLabel?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
            onSendToTv?.let { send ->
                MoovieButton(
                    onClick = { send(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().focusRequester(firstAction),
                ) {
                    Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(Res.string.details_send_to_tv))
                }
            }
            MoovieButton(
                onClick = { onRemove(); onDismiss() },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onSendToTv == null) Modifier.focusRequester(firstAction) else Modifier),
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

/**
 * Désigne la carte comme sujet du hero, au focus **et au survol**.
 *
 * Le focus seul suffisait tant que l'app se pilotait à la télécommande. À la
 * souris, survoler une carte ne lui donne pas le focus : le hero restait donc
 * figé sur la première carte, et la seule façon d'en désigner une autre était de
 * cliquer — ce qui ouvre la fiche au lieu de la présenter.
 *
 * Le survol ne *prend* pas le focus, il ne fait que renseigner le hero : lui
 * confier le focus ferait défiler la rangée sous le curseur au moindre passage.
 * Sans pointeur (Android TV), rien ne se déclenche jamais.
 */
@Composable
private fun Modifier.heroSubject(onActive: () -> Unit): Modifier {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onActive() }
    return this
        .onFocusChanged { if (it.isFocused) onActive() }
        .hoverable(hoverSource)
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
            .heroSubject { onFocusEntry(entry) },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF222222)),
            ) {
                MoovieAsyncImage(
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
                DownloadPosterBadge(titleKeyOf(entry.tmdbId, entry.isTv), bar = false)
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
    onSeeMore: (CatalogSelection) -> Unit = {},
    firstFocus: FocusRequester? = null,
) {
    Column(modifier = Modifier.scrollAsWholeBlock()) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(8.dp))
        val listState = rememberLazyListState()
        // Repli local : même sans cible fournie par l'appelant, la rangée doit
        // pouvoir ramener le focus sur sa première carte quand on l'atteint.
        val entryFocus = firstFocus ?: remember { FocusRequester() }
        MoovieRail(listState, firstFocus = entryFocus) {
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
                        modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
                    )
                }
                // Seules les rangées épinglées en ont une : elles viennent d'un
                // genre du catalogue, donc elles savent où renvoyer. Une rangée
                // de tendances n'a aucune page équivalente à rouvrir.
                row.open?.let { selection ->
                    item(key = "more") { SeeMoreCard(onClick = { onSeeMore(selection) }) }
                }
            }
        }
    }
}

/**
 * Fin d'une rangée épinglée : rouvre le genre dans le catalogue.
 *
 * Même gabarit que les affiches, sans image. Une carte plus étroite ou plus
 * courte aurait cassé l'alignement de la rangée et, sur TV, désaxé le focus qui
 * la traverse — c'est le genre de détail qui ne se voit qu'à la télécommande.
 */
@Composable
private fun SeeMoreCard(onClick: () -> Unit) {
    MoovieCard(onClick = onClick, modifier = Modifier.width(POSTER_WIDTH)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color(0xFF1C1C1C)),
                contentAlignment = Alignment.Center,
            ) {
                // Icône **et** libellé au centre, l'un sur l'autre.
                //
                // Le libellé était posé en bas, à la place où toutes les autres
                // cartes portent leur titre : « En voir plus » se lisait alors
                // comme le nom d'un film. Au centre, il ne peut plus être pris
                // pour autre chose qu'une action.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Une flèche, et non la grille du catalogue : celle-ci dit
                    // où l'on arrive, pas ce que fait la carte. Ce qu'on promet
                    // ici, c'est d'aller voir la suite.
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MOOVIE_ACCENT,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        stringResource(Res.string.home_see_more),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            // Bande de titre vide, de la hauteur exacte de celle des affiches
            // voisines : sans elle, la carte serait plus courte et le bas de la
            // rangée se déchirerait. Un `Text` vide plutôt qu'une hauteur en dur
            // — il suit le style, une valeur figée en divergerait.
            Text(
                "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.padding(8.dp),
            )
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
            .width(POSTER_WIDTH)
            .heroSubject { onFocusItem(item) },
    ) {
        Column {
            Box {
                MoovieAsyncImage(
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
                DownloadPosterBadge(titleKeyOf(item.id, item.isTv))
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
    onFocusEntry: (WatchlistEntry) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Column(modifier = Modifier.scrollAsWholeBlock()) {
        Text(
            stringResource(Res.string.watchlist_row),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(8.dp))
        val listState = rememberLazyListState()
        // Repli local : même sans cible fournie par l'appelant, la rangée doit
        // pouvoir ramener le focus sur sa première carte quand on l'atteint.
        val entryFocus = firstFocus ?: remember { FocusRequester() }
        MoovieRail(listState, firstFocus = entryFocus) {
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
                        modifier = Modifier
                            // Le hero décrit la carte focalisée : ce rappel
                            // manquait ici, et le bandeau restait figé sur la
                            // dernière carte de la rangée précédente.
                            .heroSubject { onFocusEntry(entry) }
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(entryFocus)
                                } else {
                                    Modifier
                                },
                            ),
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
    MoovieCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(POSTER_WIDTH)) {
        Column {
            Box {
                MoovieAsyncImage(
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
                DownloadPosterBadge(titleKeyOf(entry.tmdbId, entry.isTv))
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
                .clip(MoovieShape)
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
                .clip(MoovieShape)
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
