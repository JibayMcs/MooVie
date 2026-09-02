package fr.moovie.tv.ui.home

import fr.moovie.tv.shared.formaterDecimal
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.text.font.FontWeight
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
import fr.moovie.tv.resources.app_name
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
import fr.moovie.tv.ui.theme.MOOVIE_RATING
import fr.moovie.tv.ui.theme.MOOVIE_READY
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
import fr.moovie.tv.resources.moovie_icon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.theme.MOOVIE_BG
import fr.moovie.tv.ui.theme.MOOVIE_SCRIM
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE_HIGH
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_MUTED
import fr.moovie.tv.ui.theme.MoovieGradient
import fr.moovie.tv.ui.theme.margePage
import fr.moovie.tv.ui.components.MoovieTopNav
import fr.moovie.tv.ui.components.MoovieNavItem
import fr.moovie.tv.ui.components.MoovieNavSpacer
import fr.moovie.tv.ui.adaptive.LocalWindowHeight
import fr.moovie.tv.ui.components.HAUTEUR_NAV
import androidx.compose.foundation.layout.BoxWithConstraints
import fr.moovie.tv.ui.components.MooviePosterCard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.clickable

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
    // La boîte du héros, barre comprise : c'est elle que l'image et le voile
    // doivent couvrir. Le dégradé la reçoit en fraction de la page parce qu'un
    // `Brush` ne connaît que des fractions de la surface qu'il peint.
    ProvideTitleDownloads {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MOOVIE_BG)) {
        // **La place réellement offerte, pas la fenêtre.**
        //
        // `LocalWindowHeight` mesure la fenêtre entière, bandeau de mise à jour
        // compris. Le héros calé dessus débordait donc de la hauteur du
        // bandeau, et mangeait exactement l'amorce censée montrer la première
        // rangée : on arrivait sur une image pleine page sans rien dessous.
        val hauteurDispo = maxHeight
        val auDoigtIci = useBottomNav
        // **Deux hauteurs de héros, parce que deux formats.**
        //
        // En paysage, le héros prend ce qui reste une fois une rangée entière
        // réservée. En portrait ce calcul n'a pas de sens : l'écran est haut et
        // étroit, une rangée d'affiches y tient de toute façon sous n'importe
        // quel héros, et « ce qui reste » ferait un cadre de six cents points
        // pour une image de deux cent trente. Le portrait se cale donc sur
        // l'image elle-même — un 16:9 pleine largeur — plus la place du bloc de
        // texte sous elle.
        val hauteurHeros = if (auDoigtIci) {
            maxWidth / 16f * 9f + BLOC_TEXTE_HEROS
        } else {
            (hauteurDispo - HAUTEUR_NAV - BLOC_RANGEE).coerceIn(200.dp, 620.dp)
        }
        // La boîte que l'image et le voile doivent couvrir : le héros, barre
        // comprise. Le dégradé la reçoit en fraction, un `Brush` ne connaissant
        // que des fractions de la surface qu'il peint.
        val hauteurFond = if (auDoigtIci) hauteurHeros else HAUTEUR_NAV + hauteurHeros
        val fondFrac = (hauteurFond / hauteurDispo).coerceIn(0.05f, 1f)
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
                    // **Nette, et seulement en haut.**
                    //
                    // Elle était floutée à 28 points sur toute la page, puis
                    // noyée sous un aplat à 60 % : autant dire une texture. La
                    // fiche de détails a montré l'inverse — une image qu'on
                    // voit vaut mieux qu'une ambiance qu'on devine —, et la
                    // même image sert ici de fond au titre mis en avant.
                    //
                    // Elle ne couvre plus que la moitié haute : en dessous
                    // commencent les rangées d'affiches, et une image derrière
                    // des affiches, c'est deux images l'une sur l'autre.
                    modifier = Modifier
                        .fillMaxWidth()
                        // Exactement la boîte du héros, barre comprise. Une
                        // fraction de la page ne marche plus depuis que le
                        // héros prend l'écran entier : l'image s'arrêtait au
                        // milieu de lui, et sa moitié basse — celle qui porte
                        // le titre — retombait sur du noir.
                        .height(hauteurFond)
                        .align(Alignment.TopCenter),
                )
            }
        }
        // **Deux dégradés, comme sur la fiche.**
        //
        // Le latéral protège la colonne de gauche, celle qui porte le titre :
        // sans lui, un backdrop clair de ce côté rend un titre blanc illisible.
        // Le vertical raccorde l'image aux rangées et l'éteint avant qu'elles ne
        // commencent — c'est ce qui évite qu'une affiche se découpe sur un ciel.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to MOOVIE_BG,
                    0.55f to Color(0x000A0A0A),
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    // Le voile s'ancre sur le bas de l'image, comme sur la
                    // fiche : le bloc de texte du héros y a la même hauteur
                    // physique quel que soit l'écran, et une fraction le
                    // couvrirait trop ou pas assez selon la taille.
                    0f to Color(0x000A0A0A),
                    (fondFrac - 0.30f).coerceAtLeast(0.05f) to Color(0x1A0A0A0A),
                    (fondFrac - 0.12f).coerceAtLeast(0.10f) to Color(0x990A0A0A),
                    fondFrac to MOOVIE_BG,
                ),
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
                .padding(top = 0.dp, bottom = 16.dp),
        ) {
            // Hissé : `useBottomNav` est un accesseur composable, et le corps
            // d'une liste paresseuse ne l'est pas.
            val auDoigt = useBottomNav
            // Le héros est un élément de la liste sur grand écran, et pas au
            // doigt : tout ce qui raisonne en indices de liste doit le savoir.
            val decalageHeros = if (auDoigt) 0 else 1

            // **Descente depuis la barre, avec un filet.**
            //
            // La première carte est hors du faisceau vertical de la barre : la
            // recherche de focus native n'y arrive pas, d'où la visée explicite.
            // Mais la cible n'est attachée qu'à la **première rangée**, et
            // celle-ci n'existe pas toujours — « Reprendre » et « À regarder
            // plus tard » sont vides sur un profil neuf, et le
            // `FocusRequester` posé dessus ne correspond alors à aucun nœud.
            // La demande échouait en silence, et Bas ne faisait rien du tout :
            // on restait prisonnier de la barre, ce qu'on a constaté sur
            // l'émulateur.
            //
            // Le repli est la recherche native. Elle ne trouve pas toujours la
            // *bonne* carte, mais elle sort toujours de la barre — et sortir de
            // la barre est le minimum qu'on doive à quelqu'un qui appuie sur
            // Bas.
            // Ce que fait un appui sur le héros. Les trois natures de cible
            // mènent à trois gestes différents : reprendre une lecture en
            // cours, ouvrir un titre mis de côté, ouvrir un titre proposé.
            val ouvrirCible: (HeroTarget) -> Unit = { cible ->
                when (cible) {
                    is HeroTarget.Resume -> onResume(cible.entry)
                    is HeroTarget.Watchlist -> onOpenTitle(cible.entry.tmdbId, cible.entry.isTv)
                    is HeroTarget.Catalog -> onOpenTitle(cible.item.id, cible.item.isTv)
                }
            }
            val gestionnaireFocus = LocalFocusManager.current
            // **Revenir sur la barre, c'est revenir en haut.**
            //
            // Le héros est le premier élément de la liste : une fois qu'on est
            // descendu de deux rangées, il est hors écran, et remonter jusqu'à
            // la barre ne le ramenait pas — on se retrouvait avec une barre
            // posée sur des rangées, sans plus aucun moyen de revoir le titre
            // mis en avant. Même correctif que sur la fiche de détails, où
            // atteindre le bouton principal ramène la page en haut.
            val retourEnHaut = Modifier.onFocusChanged { etat ->
                if (etat.isFocused) rowsScope.launch { rowsState.animateScrollToItem(0) }
            }
            val navDown = retourEnHaut.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) {
                    return@onPreviewKeyEvent false
                }
                runCatching { firstContentFocus.requestFocus() }.isSuccess ||
                    gestionnaireFocus.moveFocus(FocusDirection.Down)
            }
            // **Le nom, et seulement au doigt.**
            //
            // Le wordmark a été retiré pour rendre ~56 dp à une rangée
            // d'affiches, et l'argument tient toujours sur un téléviseur : 540 dp
            // de haut en tout, et l'on sait à trois mètres dans quelle
            // application on est. Un téléphone n'a ni ce budget contraint ni ce
            // contexte — l'accueil s'ouvrait directement sur une rangée, sans
            // rien pour dire où l'on est, là où le haut de l'écran est
            // précisément l'endroit où toute application mobile pose son nom.
            //
            // **Le nom seul, et non l'icône de lancement.** Elle a d'abord été
            // posée à côté : c'était le dessin qu'on venait de toucher sur la
            // page d'accueil du téléphone, donc la même chose reconnue deux
            // fois. Mais une icône de lancement est dessinée pour vivre dans sa
            // tuile — fond plein, coins arrondis, marges internes calculées pour
            // une grille d'icônes. Réduite à 28 points et posée sur le noir de
            // la page, elle y garde son carré, qui ne correspond à rien : c'est
            // la seule forme arrondie d'une interface où tout est à angle droit,
            // et une deuxième couleur de fond au-dessus du fond.
            //
            // Le dégradé de l'identité passe donc dans les lettres elles-mêmes.
            // Il dit ce que l'icône disait — orange, magenta, violet — sans
            // rapporter la tuile avec lui.
            //
            // **Le bandeau se remplit quand la page défile.**
            //
            // Le nom est posé au-dessus de la liste, donc fixe, et ce qu'on
            // voyait derrière lui était l'affiche floutée du héros. Tant qu'on
            // est en haut de page c'est ce qu'il faut — l'image monte jusqu'au
            // bord et le nom s'y pose. Mais une fois descendu, les rangées
            // passaient sous une bande restée translucide : un rectangle du
            // haut de l'écran où le fond n'était pas celui de la page et où les
            // affiches défilaient à moitié effacées.
            //
            // Il prend donc le fond de la page dès le premier point de
            // défilement, et le rend dès qu'on revient en haut. En fondu, parce
            // que c'est un fond qui change, pas un élément qui apparaît.
            if (useBottomNav) {
                val defile by remember {
                    derivedStateOf {
                        rowsState.firstVisibleItemIndex > 0 ||
                            rowsState.firstVisibleItemScrollOffset > 0
                    }
                }
                val fondBandeau by animateColorAsState(
                    if (defile) MOOVIE_BG else Color.Transparent,
                    label = "fondNom",
                )
                // Le fond au conteneur, le dégradé au texte. Posés sur le même
                // nœud, `fillMaxWidth` étirait le dégradé sur toute la largeur
                // de l'écran : les lettres, qui n'en occupent qu'un cinquième,
                // n'attrapaient plus que son orange, et l'identité à trois
                // teintes se lisait comme une teinte unique.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(fondBandeau)
                        .padding(horizontal = margePage(), vertical = 6.dp),
                ) {
                    Text(
                        stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.headlineSmall
                            .copy(brush = MoovieGradient),
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                // **La barre de navigation, en haut et pleine largeur.**
                //
                // Elle était six icônes muettes dans le coin droit du héros. À
                // la télécommande, y aller demandait de remonter d'une rangée
                // puis de traverser l'écran — le geste le plus fréquent de
                // l'accueil était le plus long. Ici, elle est sur le trajet
                // naturel du Haut, elle ne recouvre plus l'image, et chaque
                // destination porte son nom.
                MoovieTopNav {
                    MoovieNavItem(
                        icone = Icons.Default.Search,
                        libelle = stringResource(Res.string.home_search),
                        onClick = onOpenSearch,
                        modifier = navDown,
                    )
                    MoovieNavItem(
                        icone = Icons.Default.AutoAwesome,
                        libelle = stringResource(Res.string.discovery_open),
                        onClick = onOpenDiscovery,
                        modifier = navDown,
                    )
                    MoovieNavItem(
                        icone = Icons.Default.GridView,
                        libelle = stringResource(Res.string.catalog_open),
                        onClick = onOpenCatalog,
                        modifier = navDown,
                    )
                    MoovieNavItem(
                        icone = Icons.Default.History,
                        libelle = stringResource(Res.string.history_title),
                        onClick = onOpenHistory,
                        modifier = navDown,
                    )
                    MoovieNavItem(
                        icone = Icons.Default.Download,
                        libelle = stringResource(Res.string.settings_cat_downloads),
                        onClick = onOpenDownloads,
                        badge = rememberActiveDownloadCount(),
                        modifier = navDown,
                    )
                    // Les réglages au bout, séparés des destinations : on n'y
                    // va pas pour regarder quelque chose.
                    MoovieNavSpacer()
                    if (onOpenRemote != null) {
                        MoovieNavItem(
                            icone = Icons.Default.SettingsRemote,
                            libelle = stringResource(Res.string.remote_title),
                            onClick = onOpenRemote,
                            modifier = navDown,
                        )
                    }
                    MoovieNavItem(
                        icone = Icons.Default.Settings,
                        libelle = stringResource(Res.string.home_settings),
                        onClick = onOpenSettings,
                        modifier = navDown,
                    )
                }
            }
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
            // Le héros ne vit plus ici : il est devenu le premier élément de
            // la liste défilante, en pleine hauteur. Voir plus bas.
            if (useBottomNav) Spacer(Modifier.height(16.dp))

            when (val s = state) {
                // Deux rangées fantômes, à la largeur réelle des cartes : rien
                // ne se réorganise quand les vraies affiches arrivent. C'est ce
                // qu'un « Chargement… » centré ne peut pas faire.
                HomeState.Loading -> Column(
                    modifier = Modifier.padding(horizontal = margePage()),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    repeat(2) { SkeletonRail(posterWidth = POSTER_WIDTH) }
                }
                is HomeState.NeedsApiKey -> Column(modifier = Modifier.padding(horizontal = margePage())) {
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
                    // **Les rangées s'éteignent en haut au lieu d'être coupées.**
                    //
                    // La liste est bornée par le bas de la barre de navigation :
                    // ce qui la traverse est tranché net, et une rangée à demi
                    // sortie s'arrête sur une ligne droite en travers des
                    // affiches. On lit une image tronquée, pas un défilement.
                    //
                    // Un rectangle noir posé par-dessus ne conviendrait pas : ce
                    // qu'il y a derrière la liste n'est pas noir mais l'affiche
                    // floutée de la page, et il ferait une bande sombre là où
                    // l'on veut une transition. On efface donc les pixels de la
                    // liste plutôt que de les recouvrir — `DstIn` avec un
                    // dégradé d'opacité — et c'est le fond qui reparaît dessous.
                    //
                    // `compositingStrategy` est indispensable : sans couche
                    // hors écran, le mélange s'appliquerait à tout ce qui est
                    // déjà dessiné et effacerait la page entière.
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black),
                                    startY = 0f,
                                    endY = FONDU_HAUT.toPx(),
                                ),
                                size = Size(size.width, FONDU_HAUT.toPx()),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    // **Le héros prend l'écran, et les rangées commencent
                    // dessous.**
                    //
                    // Il faisait 148 points — un quart d'un téléviseur — pour
                    // porter l'image la plus forte de l'application, pendant
                    // que la fiche de détails, elle, allait jusqu'au bord. Deux
                    // langages pour la même chose dans la même application.
                    //
                    // En premier élément de la liste plutôt qu'en bloc fixe :
                    // c'est ce qui lui permet de prendre toute la hauteur sans
                    // condamner les rangées. On le quitte en descendant, comme
                    // on quitte le hero d'une fiche.
                    //
                    // L'amorce sous lui n'est pas décorative : sans elle, rien
                    // ne dit qu'il y a des rangées plus bas, et l'accueil
                    // ressemble à une affiche.
                    item(key = "heros") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(hauteurHeros)
                                // **Tout le cadre, image comprise.**
                                //
                                // L'image est peinte à la racine de la page, en
                                // fond ; seul le bloc de texte est un enfant du
                                // héros. Poser l'appui sur ce bloc ne rendait
                                // donc cliquable que les trois lignes de texte,
                                // et taper l'affiche — le geste évident — ne
                                // faisait rien.
                                .then(
                                    if (auDoigt && featured != null) {
                                        Modifier.clickable { ouvrirCible(featured) }
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            // **Le héros descend au doigt, et devient cliquable.**
                            //
                            // Il en était absent parce qu'il décrivait l'élément
                            // *focalisé* — et il n'y a pas de focus au tactile :
                            // il restait figé sur le premier titre, à décrire
                            // quelque chose que personne n'avait désigné. On
                            // l'avait donc retiré, et l'accueil s'ouvrait
                            // directement sur une rangée d'affiches.
                            //
                            // Ce n'est plus vrai depuis qu'il porte une action :
                            // il ne décrit plus une visée, il **propose** un
                            // titre, et l'on tape dessus pour l'ouvrir. C'est ce
                            // que fait toute application de streaming sur
                            // téléphone, et c'est ce qui donne à l'accueil un
                            // sujet au lieu d'une liste.
                            Hero(featured, modifier = Modifier.align(Alignment.BottomStart))
                        }
                    }
                    itemsIndexed(slots, key = { _, slot -> slot.id }) { index, slot ->
                        // La cible de descente depuis l'en-tête est toujours le
                        // premier créneau affiché, quel qu'il soit : la 1re carte
                        // est hors du faisceau vertical du D-pad.
                        val entry = if (index == 0) firstContentFocus else null
                        // **L'indice de la liste, pas celui du créneau.** Le
                        // héros occupe désormais l'élément 0 : sans ce décalage,
                        // prendre la première rangée en visée recalait la liste
                        // sur le héros, la rangée sortait de l'écran et le focus
                        // s'en allait avec elle. On restait alors prisonnier de
                        // la barre de navigation, Bas ne faisant rien du tout.
                        RowSlot(index + decalageHeros, rowsState, rowsScope) {
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
 * Part de la hauteur que l'image de fond occupe.
 *
 * En dessous commencent les rangées d'affiches. Une image qui descendrait plus
 * bas se retrouverait derrière elles — deux images l'une sur l'autre, dont
 * aucune ne se lit. La valeur cadre le héros et la première rangée, ce qui est
 * exactement la zone qu'on regarde en arrivant.
 */
/**
 * La hauteur d'une rangée complète : son titre, ses affiches, leurs libellés.
 *
 * C'est **elle** qui dimensionne le héros, et non l'inverse. Un héros pleine
 * hauteur laissait sous lui une amorce de quelques points : l'accueil
 * s'ouvrait sur une affiche géante, et il fallait descendre pour découvrir
 * qu'il y avait un catalogue derrière. Trop d'image tue l'image — on venait
 * chercher quoi regarder, on tombait sur un seul titre.
 *
 * En réservant une rangée entière, la première est lisible d'emblée, et le
 * héros prend tout ce qui reste. La conséquence est juste sur les deux
 * appareils : une fenêtre de bureau haute de 1 045 points lui en laisse près de
 * sept cents, un téléviseur 1080p — 540 points en tout — beaucoup moins, ce qui
 * est exactement ce que ce téléviseur peut se permettre.
 *
 * La valeur vient du relevé fait pour [POSTER_WIDTH] : 310 points pour le bloc
 * complet en 1080p.
 */
/**
 * Hauteur du fondu en haut des rangées. Assez pour qu'une affiche s'éteigne au
 * lieu de se couper, assez peu pour ne pas manger le titre de la rangée qui
 * arrive.
 */
private val FONDU_HAUT = 28.dp

private val BLOC_RANGEE = 310.dp

/**
 * Place du titre, de la méta et du bouton sous l'image du héros, en portrait.
 *
 * En paysage le texte se pose **sur** l'image, qui est large et dont la moitié
 * gauche est libre. En portrait il n'y a pas de moitié libre : l'image est un
 * 16:9 pleine largeur, et lui superposer trois lignes plus un bouton la
 * couvrirait aux deux tiers. Le texte passe donc dessous, et cette valeur est
 * la hauteur qu'il réclame.
 */
private val BLOC_TEXTE_HEROS = 150.dp

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
 * Part de la largeur que le héros occupe.
 *
 * Elle correspond à la zone que le dégradé latéral protège. Au-delà, le fond
 * est l'image elle-même, et rien ne garantit qu'elle soit sombre.
 *
 * Le retrait de 250 dp qui réservait le coin haut droit aux boutons de la barre
 * disparaît avec ça : le titre ne va plus jusque-là, et un titre long — « Star
 * Wars, épisode III - La Revanche des Sith » — cesse d'être amputé de moitié
 * pour une collision qui ne peut plus se produire.
 */
private const val LARGEUR_HEROS = 0.55f

@Composable
private fun Hero(target: HeroTarget?, modifier: Modifier = Modifier) {
    val auDoigt = useBottomNav
    Box(
        modifier = modifier
            // **Borné à la moitié gauche en paysage, pleine largeur en portrait.**
            //
            // Le héros prenait toute la largeur partout, ce qui se tenait sur un
            // fond flouté et uniforme : il n'y avait rien derrière. Sur une
            // image nette, la moitié droite d'un écran large est claire une fois
            // sur deux et un synopsis blanc y disparaît — d'où la borne. En
            // portrait la question ne se pose pas : le texte est **sous**
            // l'image, pas dessus, et il a droit à toute la largeur.
            .fillMaxWidth(if (auDoigt) 1f else LARGEUR_HEROS)
            .then(if (auDoigt) Modifier else Modifier.height(HERO_HEIGHT))
            .padding(horizontal = margePage(), vertical = 8.dp),
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
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            item.year?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MOOVIE_TEXT_MUTED) }
            if (item.voteAverage > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MOOVIE_RATING,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        formaterDecimal(item.voteAverage, 1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MOOVIE_RATING,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Pleine largeur : à 60 % le synopsis se coupait au milieu d'une phrase
        // alors que la moitié droite de l'écran était vide.
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                item.overview.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MOOVIE_TEXT_MUTED,
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

        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            entry.episodeLabel?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MOOVIE_TEXT_MUTED)
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
                color = MOOVIE_TEXT_MUTED,
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
            color = MOOVIE_TEXT_MUTED,
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
            modifier = Modifier.padding(horizontal = margePage()),
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
            contentPadding = PaddingValues(horizontal = margePage(), vertical = 12.dp),
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
                    .background(MOOVIE_SURFACE_HIGH),
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
                    color = MOOVIE_TEXT_DIM,
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
            modifier = Modifier.padding(horizontal = margePage()),
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
                contentPadding = PaddingValues(horizontal = margePage(), vertical = 12.dp),
            ) {
                itemsIndexed(row.items) { index, item ->
                    MooviePosterCard(
                        posterUrl = item.posterUrl(),
                        titre = item.displayTitle,
                        note = item.voteAverage,
                        annee = item.year,
                        // Marque « vu » seulement pour les films : une série
                        // n'a pas de clé unique, elle se suit épisode par
                        // épisode.
                        isWatched = !item.isTv && "movie:${item.id}" in watched,
                        inWatchlist = (if (item.isTv) "tv:${item.id}" else "movie:${item.id}") in watchlistKeys,
                        onClick = { onOpenTitle(item.id, item.isTv) },
                        onLongClick = { onMenu(item) },
                        surAffiche = { DownloadPosterBadge(titleKeyOf(item.id, item.isTv)) },
                        modifier = Modifier
                            .width(POSTER_WIDTH)
                            // C'est cette carte qui alimente le héros de la
                            // page : la prendre en visée change ce qu'il
                            // montre.
                            .heroSubject { onFocusItem(item) }
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(entryFocus)
                                } else {
                                    Modifier
                                },
                            ),
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
            modifier = Modifier.padding(horizontal = margePage()),
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
                contentPadding = PaddingValues(horizontal = margePage(), vertical = 12.dp),
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
                        .background(MOOVIE_SCRIM),
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
