package fr.moovie.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import fr.moovie.tv.data.download.DownloadRepository
import fr.moovie.tv.data.search.SearchFilters
import fr.moovie.tv.resources.search_filters_scope
import fr.moovie.tv.data.download.TitleDownloads
import fr.moovie.tv.data.download.byTitle
import fr.moovie.tv.ui.download.DownloadPosterBadge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fr.moovie.tv.ui.theme.MoovieShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.search_clear_history
import fr.moovie.tv.resources.discovery_open
import fr.moovie.tv.resources.search_empty_hint
import fr.moovie.tv.resources.search_hint
import fr.moovie.tv.resources.search_hint_short
import fr.moovie.tv.resources.search_loading
import fr.moovie.tv.resources.search_needs_key
import fr.moovie.tv.resources.search_no_results
import fr.moovie.tv.resources.search_recent
import fr.moovie.tv.resources.search_recent_hint
import fr.moovie.tv.resources.search_remove_query
import fr.moovie.tv.resources.search_title
import fr.moovie.tv.resources.search_voice
import fr.moovie.tv.ui.adaptive.LocalUiFlavor
import fr.moovie.tv.ui.adaptive.isPointerUi
import fr.moovie.tv.ui.adaptive.isTouchUi
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.remote.remoteTypable
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MOOVIE_ORANGE
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.LocalMoovieCardActive
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieMarqueeText
import fr.moovie.tv.ui.components.MoovieRail
import fr.moovie.tv.ui.components.SkeletonGrid
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
import fr.moovie.tv.ui.theme.MOOVIE_ERROR
import fr.moovie.tv.ui.theme.MOOVIE_SCRIM
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE_HIGH
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_FAINT
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_MUTED
import fr.moovie.tv.ui.theme.MOOVIE_TEXT
import androidx.compose.material.icons.filled.Search
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE
import fr.moovie.tv.ui.theme.margePage

/** Résultats rapportés avant tri, à garder aligné sur `SearchViewModel.DEEP_PAGES`. */
private const val SEARCH_SCOPE = 60

/**
 * Marge horizontale de l'écran. 40 dp est un recul de salon ; sur les 448 dp
 * d'un téléphone, les deux côtés réunis mangeaient un cinquième de la largeur.
 */
@Composable
private fun searchHPad(): Dp = margePage()

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
    /**
     * Dictée vocale. Null quand la plateforme n'en propose pas — le bouton
     * disparaît alors au lieu d'être affiché et inerte. C'est le cas du
     * desktop, et d'un Android TV sans moteur de reconnaissance installé.
     */
    onVoiceSearch: (() -> Unit)? = null,
    /**
     * Entrée vers la page Découverte : une icône, à droite du micro.
     *
     * Null quand la plateforme la propose ailleurs — sur desktop et TV, elle a
     * son icône dans l'en-tête de l'accueil. Sur téléphone c'est le seul point
     * d'entrée, la barre basse étant pleine à six onglets (voir le pourquoi
     * dans `MoovieBottomBar`).
     */
    onOpenDiscovery: (() -> Unit)? = null,
    onBack: () -> Unit = {},
    /** Tri et filtres courants, et leur mise à jour. Conservés entre sessions. */
    filters: SearchFilters = SearchFilters.DEFAULT,
    onFiltersChange: (SearchFilters) -> Unit = {},
    // Desktop uniquement, comme pour l'historique : sur TV la télécommande a sa
    // touche Retour, et un bouton à l'écran ne ferait que voler le focus au
    // champ de saisie.
    showBackButton: Boolean = false,
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
                    .clip(MoovieShape)
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
        Row(
            modifier = Modifier.padding(horizontal = searchHPad()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showBackButton) {
                MoovieIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.common_back),
                )
            }
            Text(
                stringResource(Res.string.search_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.padding(horizontal = searchHPad()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                // Valider sur le clavier ferme l'IME et saute au 1er résultat.
                onSubmit = { runCatching { firstResultFocus.requestFocus() } },
                modifier = Modifier.weight(1f).focusRequester(fieldFocus),
            )
            // La télécommande a un micro ; l'app imposait un clavier virtuel,
            // lettre par lettre à la flèche. Le bouton est à droite du champ,
            // donc à un cran du focus d'arrivée.
            onVoiceSearch?.let { speak ->
                MoovieIconButton(
                    onClick = speak,
                    icon = Icons.Default.Mic,
                    contentDescription = stringResource(Res.string.search_voice),
                )
            }
            // Découverte, à droite du micro et sans libellé.
            //
            // Elle a d'abord été un bouton pleine largeur sous le champ : trop
            // long, trop lourd pour une entrée facultative, et il repoussait
            // l'historique de recherche sous la ligne de flottaison. Une icône
            // de plus dans la rangée du champ ne coûte rien et se trouve au
            // moment exact où l'on se demande quoi chercher.
            //
            // Teinte orange plutôt que le gris des autres : c'est le seul
            // bouton de cette rangée qui mène ailleurs que dans la recherche,
            // et une nuance de gris de plus ne l'aurait pas dit. Assez pour se
            // remarquer, pas assez pour crier.
            onOpenDiscovery?.let { ouvrir ->
                MoovieButton(
                    onClick = ouvrir,
                    // Mêmes marges que MoovieIconButton, sans quoi les deux
                    // boutons voisins n'auraient pas la même taille.
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = stringResource(Res.string.discovery_open),
                        tint = MOOVIE_ORANGE,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        // Sous le champ, au-dessus des résultats : c'est l'ordre dans lequel on
        // y pense, et le seul qui laisse la flèche du bas mener du champ aux
        // filtres puis aux résultats.
        if (query.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            SearchFilterBar(
                filters = filters,
                onChange = onFiltersChange,
                hPad = searchHPad(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))

        when {
            // Champ vide : l'historique de recherche. Parcourir par genre a
            // désormais sa propre page (Catalogue) — les deux gestes cohabitaient
            // mal ici, il fallait traverser le champ de saisie, donc le clavier
            // virtuel, pour atteindre les genres.
            query.isBlank() -> HistorySection(
                history = history,
                onPick = onQueryChange,
                onRemove = onRemoveHistory,
                onClear = onClearHistory,
                modifier = Modifier.padding(horizontal = searchHPad()),
            )
            results is SearchState.Loading -> SkeletonGrid(
                columns = if (useBottomNav) 3 else 6,
                modifier = Modifier.padding(horizontal = searchHPad()),
            )
            results is SearchState.NeedsKey -> Text(
                stringResource(Res.string.search_needs_key),
                color = MOOVIE_ERROR,
                modifier = Modifier.padding(horizontal = searchHPad()),
            )
            results is SearchState.Empty -> Text(stringResource(Res.string.search_no_results, query), color = MOOVIE_TEXT_MUTED, modifier = Modifier.padding(horizontal = searchHPad()))
            results is SearchState.Results -> ResultsGrid(
                filters = filters,
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

/**
 * Le champ de recherche, partagé avec la recherche hors ligne.
 *
 * `internal` plutôt que privé : la bibliothèque locale se cherche avec le même
 * champ que le catalogue, y compris sa saisie depuis le téléphone et sa sortie
 * de focus au D-pad. Deux champs auraient divergé sur le premier des deux.
 */
@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier
            // **Une surface, pas seulement un contour.**
            //
            // Le champ n'était qu'un rectangle tracé au trait sur le fond de la
            // page : sur un écran noir, un liseré gris est le contraire d'une
            // invitation à écrire. Un fond légèrement plus clair dit qu'il y a
            // là quelque chose à remplir, et c'est le même niveau de surface
            // que partout ailleurs dans l'application.
            .background(MOOVIE_SURFACE)
            .border(1.5.dp, MOOVIE_TEXT_FAINT, MoovieShape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La loupe **dans** le champ. Elle était au-dessus, dans le titre de la
        // page, où elle nommait l'écran sans désigner l'endroit où l'on tape.
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MOOVIE_TEXT_DIM,
            modifier = Modifier.size(22.dp),
        )
    Box(modifier = Modifier.weight(1f)) {
        if (value.isEmpty()) {
            Text(
                // Invite courte sur téléphone : la longue se repliait sur deux
                // lignes et étirait le champ d'autant, alors qu'il ne contiendra
                // jamais qu'une ligne de saisie.
                stringResource(
                    if (useBottomNav) Res.string.search_hint_short else Res.string.search_hint,
                ),
                color = MOOVIE_TEXT_DIM,
                // L'échelle du thème plutôt qu'un nombre : le champ de
                // recherche est un texte qu'on lit et qu'on saisit, donc du
                // corps, et il doit se resserrer sur un téléviseur comme le
                // reste. Dix-huit points en dur y étaient un cran trop gros.
                style = MaterialTheme.typography.bodyLarge,
                // Garde-fou : quelle que soit la traduction, le champ reste haut
                // d'une ligne.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            // Le champ et son texte d'invite partagent forcément le même
            // dessin : sinon le texte saute au premier caractère saisi.
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MOOVIE_TEXT),
            cursorBrush = SolidColor(MOOVIE_ACCENT),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                // Le champ que la télécommande sert le mieux : chercher un titre
                // à la croix directionnelle est le geste que l'appairage existe
                // pour supprimer. Le téléphone ouvre son clavier dès que ce
                // champ prend le focus.
                .remoteTypable(
                    label = stringResource(Res.string.search_hint),
                    value = value,
                    onValueChange = onValueChange,
                )
                // Sans ça, le champ avale le D-pad bas : impossible d'atteindre
                // l'historique/les résultats à la télécommande.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        // Le champ avale aussi la flèche droite pour déplacer
                        // son curseur : sans ça, le bouton micro posé à sa
                        // droite était tout simplement inatteignable à la
                        // télécommande. Le curseur se pilote de toute façon
                        // depuis le clavier virtuel, qui a le focus quand il
                        // est ouvert — ces touches ne nous parviennent alors pas.
                        Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                        else -> false
                    }
                },
        )
    }
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
        Text(stringResource(Res.string.search_empty_hint), color = MOOVIE_TEXT_DIM, modifier = modifier)
        return
    }
    // Croix de suppression visible en permanence dès qu'on désigne directement
    // — souris ou doigt. Elle n'apparaissait qu'au *focus*, ce qui n'existe pas
    // au tactile : sur téléphone, retirer une recherche ne passait plus que par
    // l'appui long, un geste que rien ne montre.
    val directRemove = LocalUiFlavor.current.isDirect
    Column(modifier = modifier) {
        if (isTouchUi) {
            // Le vidage remonte sur la ligne du titre, réduit à son icône : il
            // qualifie la section entière, et le libellé n'apprenait rien qu'une
            // corbeille à côté de « Recherches récentes » ne dise déjà.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(Res.string.search_recent),
                    style = MaterialTheme.typography.titleMedium,
                )
                MoovieIconButton(
                    onClick = onClear,
                    icon = Icons.Default.DeleteSweep,
                    contentDescription = stringResource(Res.string.search_clear_history),
                )
            }
        } else {
            Text(stringResource(Res.string.search_recent), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        // L'appui long ne se devine pas : on l'annonce une fois, discrètement,
        // plutôt que d'accoler un ✕ à chaque terme — c'est ce doublement de
        // cibles qui alourdissait la rangée au D-pad. Là où la croix est visible
        // et cliquable, elle se passe d'explication.
        if (!directRemove) {
            Text(
                stringResource(Res.string.search_recent_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MOOVIE_TEXT_DIM,
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            history.forEach { q ->
                // Un seul élément focalisable par terme : OK relance la
                // recherche, l'appui long la retire. Le ✕ n'apparaît que sur le
                // terme focalisé — un repère, pas une seconde cible.
                //
                // À la souris, cette économie n'a plus lieu d'être : un appui
                // long au pointeur ne se devine pas, alors que la croix est déjà
                // là et dit ce qu'elle fait. Elle devient donc cliquable, et
                // toujours visible. `detectTapGestures` plutôt que `clickable` :
                // ce dernier rendrait la croix focalisable, soit exactement la
                // seconde cible qu'on évite à la télécommande.
                MoovieButton(
                    onClick = { onPick(q) },
                    // Inutile là où la croix est déjà là et dit ce qu'elle fait —
                    // et sur un téléphone, l'appui long est surtout un geste
                    // qu'on déclenche par accident en faisant défiler.
                    onLongClick = if (directRemove) null else ({ onRemove(q) }),
                ) {
                    Text(q)
                    if (directRemove || LocalMoovieCardActive.current) {
                        Spacer(Modifier.width(4.dp))
                        RemoveQueryCross(query = q, onRemove = { onRemove(q) })
                    }
                }
            }
            // Au D-pad, le vidage reste dans la rangée de ce qu'il vide : on ne
            // l'atteint qu'en traversant les termes, donc jamais par accident.
            // Au doigt il est monté à côté du titre, hors de portée d'un appui
            // qui visait une recherche.
            if (!isTouchUi) {
                MoovieButton(onClick = onClear) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.search_clear_history))
                }
            }
        }
    }
}


/**
 * Croix de suppression d'un terme récent.
 *
 * Au pointeur elle est une vraie cible, elle a donc besoin de le dire : sans
 * retour au survol rien ne la distingue du reste du bouton, et cliquer dessus
 * revient à parier. Fond au survol, teinte qui s'éclaircit, curseur main.
 *
 * À la télécommande elle reste ce qu'elle était — un repère sur le terme
 * focalisé, ni cliquable ni teintée à part : le survol n'y existe pas et rien
 * ne doit changer.
 */
@Composable
private fun RemoveQueryCross(query: String, onRemove: () -> Unit) {
    val pointer = isPointerUi
    val direct = LocalUiFlavor.current.isDirect
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val label = stringResource(Res.string.search_remove_query, query)

    Box(
        contentAlignment = Alignment.Center,
        // Zone de clic élargie autour d'une icône de 14 dp : viser 14 dp à la
        // souris demande de la précision.
        modifier = Modifier
            .size(24.dp)
            .then(
                // Le survol n'existe qu'à la souris, mais **la croix doit être
                // cliquable au doigt aussi** : elle y était affichée sans
                // gestionnaire, si bien que la toucher relançait la recherche
                // au lieu de la supprimer — le geste du bouton parent.
                if (direct) {
                    Modifier
                        .then(
                            if (pointer) {
                                Modifier
                                    .hoverable(hoverSource)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .background(
                                        if (hovered) Color(0x33FFFFFF) else Color.Transparent,
                                        MoovieShape,
                                    )
                            } else {
                                Modifier
                            },
                        )
                        .pointerInput(query) { detectTapGestures { onRemove() } }
                } else {
                    Modifier
                },
            ),
    ) {
        if (direct) {
            Icon(
                Icons.Default.Close,
                contentDescription = label,
                tint = if (hovered) Color.White else MOOVIE_TEXT_DIM,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Icon(
                Icons.Default.Close,
                contentDescription = label,
                modifier = Modifier.size(14.dp),
            )
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
    filters: SearchFilters = SearchFilters.DEFAULT,
) {
    // Une fois pour toute la grille, et pas par carte : sans réseau, savoir ce
    // qui est disponible est la première chose qu'on cherche des yeux.
    val downloadsByTitle by remember { DownloadRepository().downloads }
        .collectAsState(initial = emptyList())
        .let { state -> remember(state.value) { mutableStateOf(state.value.byTitle()) } }

    // Dit ce que le tri recouvre vraiment. Sans cette ligne, « trier par note »
    // laisse croire à un classement de tout le catalogue, alors qu'il porte sur
    // ce que la recherche a rapporté — TMDB ne trie pas une recherche texte.
    if (filters.isActive) {
        Text(
            stringResource(Res.string.search_filters_scope, SEARCH_SCOPE),
            style = MaterialTheme.typography.labelSmall,
            color = MOOVIE_TEXT_DIM,
            modifier = Modifier.padding(horizontal = searchHPad(), vertical = 4.dp),
        )
    }
    LazyVerticalGrid(
        // 6 colonnes, c'est calibré sur les 960 dp d'un 1080p. Sur les 448 dp
        // d'un téléphone en portrait, chaque affiche tombait à 57 dp de large —
        // et son titre à « Dun… ».
        columns = GridCells.Fixed(if (useBottomNav) 3 else 6),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // Marges intérieures : la grille clippe à ses bords, les cartes
        // agrandies au focus ont besoin de cette réserve pour ne pas être rognées.
        // Même raison que le catalogue : la réserve de 40 dp protège les cartes
        // agrandies au focus, et il n'y a pas de focus au doigt. Sur un portrait
        // elle ne faisait que rétrécir les affiches et tronquer les titres.
        contentPadding = PaddingValues(
            horizontal = margePage(),
            vertical = 12.dp,
        ),
    ) {
        itemsIndexed(items, key = { _, it -> "${it.id}_${it.isTv}" }) { index, item ->
            ResultCard(
                inWatchlist = (if (item.isTv) "tv:${item.id}" else "movie:${item.id}") in watchlistKeys,
                downloads = downloadsByTitle[if (item.isTv) "tv:${item.id}" else "movie:${item.id}"],
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
    /** Ce que ce titre a hors ligne : la question qu'on se pose sans réseau. */
    downloads: TitleDownloads? = null,
    onLongClick: (() -> Unit)? = null,
) {
    MoovieCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth()) {
        Column {
            Box {
                MoovieAsyncImage(
                    model = item.posterUrl(),
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(MOOVIE_SURFACE_HIGH),
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
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
