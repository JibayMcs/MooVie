package fr.moovie.tv.ui.discovery

import fr.moovie.tv.shared.formaterDecimal
import kotlin.math.PI
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.moovie.tv.data.discovery.DiscoveryCard
import fr.moovie.tv.data.discovery.DiscoveryGroup
import fr.moovie.tv.data.discovery.DiscoveryKind
import fr.moovie.tv.data.discovery.DiscoveryState
import fr.moovie.tv.data.discovery.MoodAnswers
import fr.moovie.tv.data.discovery.MoodOption
import fr.moovie.tv.data.discovery.MoodQuestion
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.discovery_action_open
import fr.moovie.tv.resources.discovery_action_later
import fr.moovie.tv.resources.discovery_action_later_done
import fr.moovie.tv.resources.discovery_action_seen
import fr.moovie.tv.resources.discovery_cold_body
import fr.moovie.tv.resources.discovery_cold_start
import fr.moovie.tv.resources.discovery_cold_title
import fr.moovie.tv.resources.discovery_empty
import fr.moovie.tv.resources.discovery_mood_edit
import fr.moovie.tv.resources.discovery_more
import fr.moovie.tv.resources.discovery_needs_key
import fr.moovie.tv.resources.discovery_reload
import fr.moovie.tv.resources.discovery_saga_progress
import fr.moovie.tv.resources.discovery_see_more
import fr.moovie.tv.resources.discovery_title
import fr.moovie.tv.ui.adaptive.isTouchUi
import fr.moovie.tv.ui.adaptive.LocalUiFlavor
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieRail
import fr.moovie.tv.ui.theme.MOOVIE_MAGENTA
import fr.moovie.tv.ui.theme.MOOVIE_ORANGE
import fr.moovie.tv.ui.theme.MOOVIE_VIOLET
import fr.moovie.tv.ui.theme.MoovieGradient
import fr.moovie.tv.ui.theme.MoovieShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import fr.moovie.tv.ui.theme.MOOVIE_BG
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.margePage
import fr.moovie.tv.ui.components.CadreDefilant
import fr.moovie.tv.ui.components.MooviePageHeader
import fr.moovie.tv.ui.theme.ESPACE
import fr.moovie.tv.ui.theme.ESPACE_SERRE
import fr.moovie.tv.ui.theme.MOOVIE_TEXT
import fr.moovie.tv.ui.theme.MOOVIE_RATING

/**
 * Page Découverte : des mains de cartes, une par recette.
 *
 * ### Ce qui décide du rendu
 *
 * Trois contraintes, toutes vérifiées avant d'écrire une ligne :
 *
 * 1. **Aucun flou.** `Modifier.blur` repose sur `RenderEffect`, API 31, et la
 *    Mi Box est en 28 : il n'y planterait pas, il ne ferait *rien*. Le halo est
 *    donc dessiné en dégradé radial, exactement comme `moovieSurface`.
 * 2. **Une seule carte brille à la fois.** L'identité de l'application dit que
 *    les trois teintes ne servent qu'à *signaler* ; vingt cartes allumées, ce
 *    n'est plus un signal. Le goût et la performance disent ici la même chose,
 *    puisque c'est aussi un seul dégradé à dessiner au lieu de vingt.
 * 3. **Angles droits.** `MoovieShape` est rectangulaire, et une carte se
 *    reconnaît à sa pile et à son inclinaison, pas à ses coins arrondis.
 *
 * ### Le flottement
 *
 * Une **seule** `rememberInfiniteTransition` pour la page, déphasée par index,
 * appliquée dans `graphicsLayer { }` avec lambda : phase de dessin seulement,
 * jamais de recomposition. Une animation par carte qui recompose ferait tomber
 * la box à quelques images par seconde.
 *
 * L'amplitude reste faible, pour la raison écrite dans `rememberGlow` : sur un
 * téléviseur allumé des heures, un effet marqué devient vite fatigant.
 */
@Composable
fun DiscoveryScreenContent(
    state: DiscoveryState,
    mood: MoodAnswers,
    /** Cartes récoltées depuis l'ouverture, retirées sans reconstruire les groupes. */
    retirees: Set<String>,
    watchlistKeys: Set<String>,
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onMarkSeen: (DiscoveryCard) -> Unit,
    onToggleWatchlist: (DiscoveryCard) -> Unit,
    onAnswer: (MoodOption) -> Unit,
    onClearMood: () -> Unit = {},
    onReload: () -> Unit,
    onBack: () -> Unit = {},
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Le questionnaire est un mode de la page, pas une destination : en sortir
    // ne doit pas dépiler l'écran ni perdre les groupes déjà calculés.
    var quiz by remember { mutableStateOf<MoodQuestion?>(null) }

    Box(modifier = modifier.fillMaxSize().background(MOOVIE_BG)) {
        Column(Modifier.fillMaxSize()) {
            EnTete(
                showBackButton = showBackButton,
                onBack = onBack,
                onReload = onReload,
                mood = mood,
                onEditMood = { quiz = MoodQuestion.HUMEUR },
            )

            val q = quiz
            when {
                q != null -> MoodQuizContent(
                    question = q,
                    answers = mood,
                    onAnswer = { option ->
                        onAnswer(option)
                        // Question suivante, ou retour à la page quand la
                        // dernière est répondue.
                        quiz = MoodQuestion.entries.getOrNull(q.ordinal + 1)
                    },
                    onSkip = { quiz = null },
                    // Rien à effacer tant qu'aucune réponse n'existe : un
                    // bouton qui ne peut rien faire vaut moins que pas de
                    // bouton du tout.
                    onReset = if (mood.options.isNotEmpty()) {
                        { onClearMood(); quiz = MoodQuestion.HUMEUR }
                    } else {
                        null
                    },
                )

                state is DiscoveryState.Loading -> Message(stringResource(Res.string.discovery_reload))
                state is DiscoveryState.NeedsKey -> Message(stringResource(Res.string.discovery_needs_key))
                state is DiscoveryState.ColdStart -> DemarrageAFroid(
                    onStart = { quiz = MoodQuestion.HUMEUR },
                )

                state is DiscoveryState.Ready -> {
                    val groupes = state.groups
                        .map { g -> g.copy(cards = g.cards.filter { it.key !in retirees }) }
                        .filter { it.cards.isNotEmpty() }
                    if (groupes.isEmpty()) {
                        Message(stringResource(Res.string.discovery_empty))
                    } else {
                        Mains(
                            groupes = groupes,
                            mood = mood,
                            watchlistKeys = watchlistKeys,
                            onOpenTitle = onOpenTitle,
                            onMarkSeen = onMarkSeen,
                            onToggleWatchlist = onToggleWatchlist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnTete(
    showBackButton: Boolean,
    onBack: () -> Unit,
    onReload: () -> Unit,
    mood: MoodAnswers,
    onEditMood: () -> Unit,
) {
    /*
     * ### Pourquoi ça s'empile au doigt
     *
     * Une fois les questions répondues, la pastille d'humeur ne dit plus
     * « Régler l'humeur » mais « Détendue · Deux · Une vraie soirée ». Dans une
     * `Row`, un enfant sans contrainte est mesuré à sa largeur naturelle : la
     * pastille prenait donc toute la place et **poussait le bouton de
     * rechargement hors de l'écran**. Sur un téléphone, répondre au
     * questionnaire faisait disparaître le seul moyen de redistribuer.
     *
     * C'est le piège déjà consigné pour `SettingRow` et la rangée de
     * disposition de l'accueil : une rangée qui sépare un libellé et des
     * contrôles suppose une largeur qu'un portrait n'a pas. La réponse est la
     * même — on empile.
     *
     * Sur grand écran la rangée reste unique, mais la pastille y est bornée par
     * un poids : même dans une fenêtre étroite, elle rétrécit et s'abrège au
     * lieu de chasser ses voisines.
     */
    val empile = useBottomNav

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPad(), vertical = if (empile) 16.dp else 32.dp),
    ) {
        // L'en-tête commun aux pages secondaires. La pastille d'humeur et le
        // rechargement vont dans sa fente d'actions : ce sont bien deux gestes
        // qui portent sur la page entière, ce que cette place signifie.
        //
        // La borne de largeur sur la pastille reste. Le titre est le seul
        // enfant élastique de la rangée ; sans borne, un libellé d'humeur long
        // pousserait le rechargement hors du cadre dans une fenêtre étroite.
        MooviePageHeader(
            titre = stringResource(Res.string.discovery_title),
            onBack = onBack.takeIf { showBackButton },
            // La colonne parente porte déjà la marge de page.
            marge = 0.dp,
        ) {
            if (!empile) {
                PastilleHumeur(
                    mood = mood,
                    onClick = onEditMood,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }
            MoovieIconButton(
                onClick = onReload,
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(Res.string.discovery_reload),
            )
        }
        if (empile) {
            Spacer(Modifier.height(6.dp))
            PastilleHumeur(mood = mood, onClick = onEditMood)
        }
    }
}

/**
 * Les réponses au questionnaire, visibles et modifiables.
 *
 * Ce n'est pas un sas franchi une fois, c'est un réglage de la page : on doit
 * pouvoir lire ce qui oriente les propositions, et le changer d'un appui.
 */
@Composable
private fun PastilleHumeur(
    mood: MoodAnswers,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * Une icône, et le liseré d'état quand des réponses existent.
     *
     * Un `MoovieButton` au repos n'a **pas de fond** : c'est l'identité de
     * l'application, et elle tient parce que le focus ou le survol le
     * révèlent. Au doigt, rien ne le révèle jamais — posée seule sous le titre,
     * la pastille se lisait comme du texte égaré plutôt que comme un contrôle.
     * L'icône lui rend sa nature ; `selected` dit qu'une réponse est en place.
     */
    MoovieButton(onClick = onClick, modifier = modifier, selected = mood.isComplete) {
        Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (mood.isComplete) {
                // `map` est inline, `joinToString` ne l'est pas : un
                // `stringResource` ne peut vivre que dans le premier.
                mood.options.map { stringResource(optionLabel(it.id)) }
                    .joinToString(" · ")
            } else {
                stringResource(Res.string.discovery_mood_edit)
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Mains(
    groupes: List<DiscoveryGroup>,
    mood: MoodAnswers,
    watchlistKeys: Set<String>,
    onOpenTitle: (Int, Boolean) -> Unit,
    onMarkSeen: (DiscoveryCard) -> Unit,
    onToggleWatchlist: (DiscoveryCard) -> Unit,
) {
    // Une transition pour toute la page. Voir la note de tête : c'est ce qui
    // sépare 60 images par seconde d'un diaporama sur la box.
    val respiration = rememberInfiniteTransition(label = "flottement")
    val phase by respiration.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(PERIODE_MS),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        itemsIndexed(groupes, key = { _, g -> g.kind.name }) { index, groupe ->
            Main(
                groupe = groupe,
                mood = mood,
                phase = phase,
                teinte = teinteDe(groupe.kind),
                watchlistKeys = watchlistKeys,
                onOpenTitle = onOpenTitle,
                onMarkSeen = onMarkSeen,
                onToggleWatchlist = onToggleWatchlist,
            )
        }
    }
}

/** Un groupe : un titre, un éventail, et la ligne de contexte de la carte désignée. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Main(
    groupe: DiscoveryGroup,
    mood: MoodAnswers,
    phase: Float,
    teinte: Color,
    watchlistKeys: Set<String>,
    onOpenTitle: (Int, Boolean) -> Unit,
    onMarkSeen: (DiscoveryCard) -> Unit,
    onToggleWatchlist: (DiscoveryCard) -> Unit,
) {
    // Au doigt, il n'y a pas de focus : la carte désignée est la dernière
    // touchée, et la première par défaut — sinon la ligne d'actions serait vide
    // à l'ouverture et personne ne saurait qu'elle existe.
    var designee by remember(groupe.kind) { mutableStateOf(0) }
    val largeur = cardWidth()
    val tactile = isTouchUi

    /*
     * Position du halo, **en phase de dessin uniquement**.
     *
     * Ces deux valeurs sont écrites par `onGloballyPositioned`, donc à chaque
     * pixel de défilement. Lues en composition, elles recomposeraient le groupe
     * soixante fois par seconde ; lues seulement dans `drawBehind`, elles ne
     * provoquent qu'un redessin. C'est la même règle que le flottement.
     */
    val centreCarte = remember(groupe.kind) { mutableStateOf(Float.NaN) }
    val origineBoite = remember(groupe.kind) { mutableStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        val marge = hPad()
        val listState = rememberLazyListState()
        val flingBehavior = rememberSnapFlingBehavior(
            lazyListState = listState,
            snapPosition = SnapPosition.Center,
        )
        val entryFocus = remember { FocusRequester() }
        val scope = rememberCoroutineScope()
        // Doigt et souris n'ont pas de focus qui suive une carte quand la liste
        // s'allonge : c'est à eux seuls qu'il faut rendre la cible.
        val sansFocus = LocalUiFlavor.current.isDirect
        val capacite = cardsThatFit(maxWidth.value, largeur.value, marge.value)
        val tailleLot = if (groupe.cards.size <= capacite) {
            groupe.cards.size
        } else {
            (capacite - 1).coerceAtLeast(1)
        }
        var lotsVisibles by remember(groupe.kind) { mutableIntStateOf(1) }
        val cartesVisibles = groupe.cards.take(tailleLot * lotsVisibles)
        val restantes = groupe.cards.size - cartesVisibles.size
        val totalEventail = cartesVisibles.size + if (restantes > 0) 1 else 0

        LaunchedEffect(cartesVisibles.size) {
            if (designee >= cartesVisibles.size) {
                designee = (cartesVisibles.size - 1).coerceAtLeast(0)
            }
        }

        // Le doigt n'a ni focus ni survol. Pendant le glissement, la carte la
        // plus proche du centre devient donc la carte désignée, comme sur une
        // molette. Le flux suit aussi l'inertie et l'arrêt magnétique.
        LaunchedEffect(tactile, listState, cartesVisibles.size) {
            if (!tactile) return@LaunchedEffect
            snapshotFlow {
                val layout = listState.layoutInfo
                val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                layout.visibleItemsInfo
                    .asSequence()
                    .filter { it.index < cartesVisibles.size }
                    .minByOrNull { item -> abs(item.offset + item.size / 2 - viewportCenter) }
                    ?.index
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { designee = it }
        }

        Column {
            Text(
                text = titreDe(groupe, mood),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = marge),
            )
            Spacer(Modifier.height(6.dp))

            /*
             * Le halo est dessiné **ici**, derrière la rangée, et non dans la carte.
             *
             * Une `LazyRow` découpe son contenu : à l'horizontale, net à ses bords ;
             * à la verticale, avec une marge de 30 dp seulement. Un halo posé dans
             * la carte se faisait donc trancher au ras de la rangée — au lieu d'une
             * lueur, on voyait un rectangle coupé, ce qui est pire que pas de lueur
             * du tout. Dessiné derrière la rangée, il ne rencontre plus aucune
             * frontière.
             *
             * Son rayon vaut la demi-hauteur de la boîte : il s'éteint donc
             * exactement là où la rangée finit, sans jamais déborder sur le groupe
             * voisin ni sur son titre.
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { origineBoite.value = it.positionInRoot().x },
            ) {
                Box(
                    Modifier.matchParentSize().drawBehind {
                        val x = centreCarte.value - origineBoite.value
                        if (x.isNaN()) return@drawBehind
                        val r = size.height / 2f
                        val cy = size.height / 2f
                        drawRect(
                            brush = Brush.radialGradient(
                                // **Les arrêts sont calés sur l'anneau, pas sur le
                                // disque.** L'affiche est opaque et masque tout le
                                // cœur du dégradé ; seule la couronne qui dépasse
                                // d'elle se voit. Une décroissance classique depuis
                                // le centre dépense donc toute son intensité sous
                                // la carte, et il ne reste qu'un liseré fantôme.
                                0.0f to teinte.copy(alpha = 0.55f),
                                0.52f to teinte.copy(alpha = 0.50f),
                                0.78f to MOOVIE_VIOLET.copy(alpha = 0.22f),
                                1.0f to Color.Transparent,
                                center = Offset(x, cy),
                                radius = r,
                            ),
                            topLeft = Offset(x - r, cy - r),
                            size = Size(r * 2, r * 2),
                        )
                    },
                )

                // La main déborde toujours : c'est ce qui fait qu'on la lit
                // comme une main. Encore faut-il savoir de quel côté elle
                // continue — la carte « voir plus » vit en bout de course, et
                // sur un téléphone elle est hors de l'écran d'entrée de jeu.
                CadreDefilant(etat = listState, modifier = Modifier.fillMaxWidth()) {
                MoovieRail(
                    state = listState,
                    firstFocus = entryFocus,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LazyRow(
                        state = listState,
                        flingBehavior = flingBehavior,
                        // Chevauchement : c'est ce qui fait une **main** plutôt qu'une
                        // rangée. Les marges vont dans le contentPadding, jamais autour —
                        // la carte désignée grandit et se ferait rogner par le bord.
                        horizontalArrangement = Arrangement.spacedBy(-(largeur * CHEVAUCHEMENT)),
                        // Le supplément de gauche paie l'inclinaison : une carte penchée
                        // déborde de sa propre largeur, et sans lui la première de la
                        // main sortait coupée.
                        // Le vertical ne paie pas que le soulèvement : c'est lui qui
                        // fixe la hauteur de la boîte, donc le rayon du halo, donc la
                        // largeur de la couronne visible autour de l'affiche.
                        contentPadding = PaddingValues(
                            start = marge + MARGE_INCLINAISON_DP.dp,
                            end = marge,
                            top = 46.dp,
                            bottom = 42.dp,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        itemsIndexed(cartesVisibles, key = { _, c -> c.key }) { i, carte ->
                            Carte(
                                carte = carte,
                                index = i,
                                total = totalEventail,
                                active = i == designee,
                                phase = phase,
                                teinte = teinte,
                                largeur = largeur,
                                dansLaListe = carte.key in watchlistKeys,
                                onDesigner = { designee = i },
                                onCentre = { x -> if (i == designee) centreCarte.value = x },
                                // Sur mobile, le premier toucher désigne. La
                                // fiche ne s'ouvre que via l'action sous la main.
                                onOuvrir = {
                                    if (tactile) designee = i else ouvrir(carte, onOpenTitle)
                                },
                                modifier = if (i == 0) Modifier.focusRequester(entryFocus) else Modifier,
                            )
                        }
                        if (restantes > 0) {
                            item(key = "more:${groupe.kind.name}") {
                                VoirPlusCard(
                                    restantes = restantes,
                                    index = cartesVisibles.size,
                                    total = totalEventail,
                                    phase = phase,
                                    teinte = teinte,
                                    largeur = largeur,
                                    /*
                                     * Révéler un lot **et aller le voir**.
                                     *
                                     * Sans le défilement, la carte « voir plus »
                                     * est repoussée hors de l'écran par les
                                     * cartes qu'elle vient de révéler : le
                                     * deuxième appui tombe alors sur l'affiche
                                     * qui a pris sa place, et se contente de la
                                     * désigner. On croit à un chargement en
                                     * panne, alors que c'est la cible qui a
                                     * disparu.
                                     *
                                     * Au D-pad le défaut n'existe pas : le focus
                                     * reste sur la carte et Compose la ramène
                                     * seul. Il ne concerne donc que le doigt et
                                     * la souris, qui n'ont pas de focus à suivre
                                     * — d'où le garde-fou sur `isDirect`.
                                     *
                                     * On défile jusqu'à la **première carte
                                     * nouvellement révélée** plutôt que jusqu'à
                                     * la carte de queue : les nouveautés se
                                     * présentent à gauche, et « voir plus » se
                                     * retrouve au bord droit, là où le pouce
                                     * venait de la quitter.
                                     */
                                    onClick = {
                                        val premiereNouvelle = cartesVisibles.size
                                        lotsVisibles++
                                        if (sansFocus) {
                                            scope.launch {
                                                listState.animateScrollToItem(premiereNouvelle)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                }
            }

            cartesVisibles.getOrNull(designee)?.let { carte ->
                Contexte(
                    carte = carte,
                    kind = groupe.kind,
                    dansLaListe = carte.key in watchlistKeys,
                    onOuvrir = { ouvrir(carte, onOpenTitle) },
                    onListe = { onToggleWatchlist(carte) },
                    onVu = { onMarkSeen(carte) },
                )
            }
        }
    }
}

/** Carte de queue : même gabarit et même focus qu'une affiche, mais une action explicite. */
@Composable
private fun VoirPlusCard(
    restantes: Int,
    index: Int,
    total: Int,
    phase: Float,
    teinte: Color,
    largeur: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = focused || hovered || pressed
    val milieu = (total - 1) / 2f
    val rotation by animateFloatAsState(
        targetValue = if (active) 0f else ((index - milieu) / milieu.coerceAtLeast(1f)) * ANGLE,
        label = "inclinaison-voir-plus",
    )
    val echelle by animateFloatAsState(if (active) 1.08f else 1f, label = "echelle-voir-plus")
    val montee by animateFloatAsState(if (active) -18f else 0f, label = "montee-voir-plus")

    Box(
        modifier = Modifier
            .zIndex(if (active) 1f else 0f)
            .width(largeur)
            .aspectRatio(2f / 3f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val flottement = kotlin.math.sin(phase + index * 0.73f) * AMPLITUDE
                    translationY = montee + flottement
                    rotationZ = rotation
                    scaleX = echelle
                    scaleY = echelle
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.06f)
                }
                .background(
                    Brush.verticalGradient(
                        listOf(teinte.copy(alpha = 0.34f), Color(0xFF15151B), MOOVIE_BG),
                    ),
                )
                .border(
                    width = if (active) 2.dp else 1.dp,
                    color = teinte.copy(alpha = if (active) 0.95f else 0.45f),
                    shape = MoovieShape,
                ),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.discovery_more, restantes),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.discovery_see_more),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE8E8EF),
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = teinte,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(if (active) 3.dp else 2.dp)
                    .background(MoovieGradient),
            )
        }
    }
}

/**
 * Une carte de la main.
 *
 * ### Deux boîtes, et c'est tout l'enjeu
 *
 * La boîte **extérieure** porte le clic, le focus et le survol ; elle ne bouge
 * jamais. La boîte **intérieure** porte l'inclinaison, le redressement, le
 * soulèvement et l'échelle, toutes en `graphicsLayer` : dessin seulement, aucun
 * remesurage.
 *
 * Les mettre ensemble donnait un **scintillement aléatoire**, et la mécanique
 * en est instructive : la carte survolée se soulevait de 18 px, sortait de sous
 * le curseur, perdait le survol, redescendait, le retrouvait. Deux cartes
 * voisines se disputaient alors le premier plan à chaque image. Une cible de
 * pointage qui se déplace **parce qu'on la pointe** est une boucle, et elle
 * oscille toujours.
 */
@Composable
private fun Carte(
    carte: DiscoveryCard,
    index: Int,
    total: Int,
    active: Boolean,
    phase: Float,
    teinte: Color,
    largeur: Dp,
    dansLaListe: Boolean,
    onDesigner: () -> Unit,
    /** Centre horizontal de la carte, dans le repère racine, pour poser le halo. */
    onCentre: (Float) -> Unit,
    onOuvrir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // `MoovieCard` gère sa propre source d'interaction et son propre zoom : on
    // ne peut ni lire son focus depuis l'extérieur, ni empiler son échelle sur
    // celle de l'éventail. La carte est donc cliquable et focalisable ici, avec
    // la source d'interaction qu'on possède.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    // Le focus (télécommande) et le survol (souris) désignent la carte. Dans un
    // effet, jamais pendant la composition : écrire un état en composant est un
    // aller-retour dont Compose n'a aucune raison de sortir.
    LaunchedEffect(focused, hovered, pressed) {
        if (focused || hovered || pressed) onDesigner()
    }

    val milieu = (total - 1) / 2f
    val rotation by animateFloatAsState(
        targetValue = if (active) 0f else ((index - milieu) / milieu.coerceAtLeast(1f)) * ANGLE,
        label = "inclinaison",
    )
    val echelle by animateFloatAsState(if (active) 1.08f else 1f, label = "echelle")
    val montee by animateFloatAsState(if (active) -18f else 0f, label = "montee")

    Box(
        modifier = modifier
            .zIndex(if (active) 1f else 0f)
            // Cible de pointage **fixe** : elle donne la taille de la carte et
            // ne subit aucune transformation. Voir la note de tête.
            .width(largeur)
            .aspectRatio(2f / 3f)
            .onGloballyPositioned { onCentre(it.positionInRoot().x + it.size.width / 2f) }
            .clickable(
                interactionSource = interaction,
                // Pas d'ondulation : rien n'en utilise ailleurs dans
                // l'application, et elle déborderait d'une affiche.
                indication = null,
                onClick = onOuvrir,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Respiration : même transition pour toute la page, déphasée
                    // par index. Le pas est volontairement irrationnel, sinon
                    // les cartes finissent par battre ensemble et l'effet
                    // devient mécanique.
                    val flottement = kotlin.math.sin(phase + index * 0.73f) * AMPLITUDE
                    translationY = montee + flottement
                    rotationZ = rotation
                    scaleX = echelle
                    scaleY = echelle
                    // Pivot sous la carte : une main s'ouvre depuis le poignet,
                    // pas depuis son centre. **Pas trop bas pour autant** : à
                    // 1,25 le bras de levier déportait la carte de gauche d'une
                    // trentaine de dp et elle sortait rognée.
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.06f)
                },
        ) {
            Box(Modifier.fillMaxSize()) {
                // Une saga est une pile : deux cartes décalées derrière
                // l'affiche disent « plusieurs films » sans une ligne de
                // texte. Dessinées **avant** l'affiche, donc dessous.
                if (carte is DiscoveryCard.Saga) PileDeSaga(largeur)

                MoovieAsyncImage(
                    model = carte.posterUrl,
                    contentDescription = carte.title,
                    modifier = Modifier.fillMaxSize().clip(MoovieShape),
                )
                // Liseré de teinte : discret au repos, franc quand la carte
                // est désignée. C'est lui qui fait lire une main comme un
                // groupe et non comme cinq cartes indépendantes.
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, teinte.copy(alpha = if (active) 0.7f else 0.15f)),
                )
                if (carte is DiscoveryCard.Saga) {
                    Text(
                        text = "${carte.seen}/${carte.total}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MOOVIE_BG,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(MoovieGradient)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0xA6000000)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(carte.progress)
                                .height(3.dp)
                                .background(MoovieGradient),
                        )
                    }
                }
                // Marque « à voir ». **En bas à gauche, jamais à droite** :
                // les cartes se chevauchent, le bord droit de chacune est
                // caché par la suivante et une marque y serait invisible.
                if (dansLaListe) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MOOVIE_ORANGE,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color(0xE60A0A0A))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .size(14.dp),
                    )
                }
                if (active) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(MoovieGradient),
                    )
                }
            }
        }
    }
}

/** Les deux cartes décalées derrière une saga. */
@Composable
private fun PileDeSaga(largeur: Dp) {
    listOf(2.5f to 6f, 5f to 12f).forEach { (angle, decalage) ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = angle
                    translationX = decalage
                    translationY = -decalage
                }
                .background(Color(0xFF1B1B21))
                .border(1.dp, Color(0xFF2C2C36)),
        )
    }
}

/**
 * La ligne de contexte et ses trois actions.
 *
 * Elles sont **écrites, pas cachées derrière un appui long** : au doigt il n'y
 * a pas de focus, et un geste invisible n'existe pas. C'est aussi la seule
 * façon pour la page d'apprendre ce qui a été regardé **avant**
 * l'application — sans « Déjà vu », la découverte proposerait indéfiniment des
 * films vus il y a dix ans.
 */
@Composable
private fun Contexte(
    carte: DiscoveryCard,
    kind: DiscoveryKind,
    dansLaListe: Boolean,
    onOuvrir: () -> Unit,
    onListe: () -> Unit,
    onVu: () -> Unit,
) {
    // **Le titre désigné est le sujet de la page, pas une légende.**
    //
    // Il était en `bodyMedium`, à la taille d'un synopsis, sous une main de
    // cartes qui prend la moitié de l'écran : à trois mètres, on voyait
    // parfaitement quelle carte était choisie et pas du tout laquelle c'était.
    // Le rang de titre le remet à sa place, et l'espace au-dessus le décolle
    // des cartes au lieu de l'y coller.
    Column(
        modifier = Modifier.padding(horizontal = hPad()),
        verticalArrangement = Arrangement.spacedBy(ESPACE_SERRE),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ESPACE),
        ) {
            Text(
                carte.title,
                style = MaterialTheme.typography.titleLarge,
                color = MOOVIE_TEXT,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            when (carte) {
                is DiscoveryCard.Title -> {
                    carte.year?.let { Puce(it) }
                    // L'étoile, comme partout ailleurs : cette note était le
                    // seul nombre de l'application à s'annoncer tout seul.
                    if (carte.rating > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MOOVIE_RATING,
                                modifier = Modifier.size(14.dp),
                            )
                            Puce(formaterDecimal(carte.rating, 1), MOOVIE_RATING)
                        }
                    }
                }
                is DiscoveryCard.Saga -> Puce(
                    stringResource(Res.string.discovery_saga_progress, carte.seen, carte.total),
                )
            }
        }
        Text(
            stringResource(groupWhy(kind)),
            style = MaterialTheme.typography.bodySmall,
            color = MOOVIE_TEXT_DIM,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(ESPACE_SERRE))
        Row(horizontalArrangement = Arrangement.spacedBy(ESPACE_SERRE)) {
            MoovieButton(onClick = onOuvrir) {
                Text(stringResource(Res.string.discovery_action_open))
            }
            MoovieButton(onClick = onListe, selected = dansLaListe) {
                Text(
                    stringResource(
                        if (dansLaListe) Res.string.discovery_action_later_done
                        else Res.string.discovery_action_later,
                    ),
                )
            }
            MoovieButton(onClick = onVu) {
                Text(stringResource(Res.string.discovery_action_seen))
            }
        }
    }
}

@Composable
private fun Puce(texte: String, couleur: Color = MOOVIE_TEXT_DIM) {
    Text(
        texte,
        style = MaterialTheme.typography.labelSmall,
        color = couleur,
        modifier = Modifier.padding(start = 10.dp),
    )
}

/**
 * Démarrage à froid : ni historique, ni réponses.
 *
 * C'est le seul endroit où le questionnaire n'est pas facultatif — sans lui la
 * page n'aurait littéralement rien à montrer. Partout ailleurs il ne fait
 * qu'ajouter un groupe.
 */
@Composable
private fun DemarrageAFroid(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = hPad()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.discovery_cold_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.discovery_cold_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MOOVIE_TEXT_DIM,
        )
        Spacer(Modifier.height(20.dp))
        MoovieButton(onClick = onStart) {
            Text(stringResource(Res.string.discovery_cold_start))
        }
    }
}

@Composable
private fun Message(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.bodyMedium,
        color = MOOVIE_TEXT_DIM,
        modifier = Modifier.padding(horizontal = hPad(), vertical = 24.dp),
    )
}

private fun ouvrir(carte: DiscoveryCard, onOpenTitle: (Int, Boolean) -> Unit) {
    when (carte) {
        is DiscoveryCard.Title -> onOpenTitle(carte.tmdbId, carte.isTv)
        // Une saga ouvre le film qui vient ensuite : la question « lequel
        // maintenant » a une seule réponse raisonnable, et un écran
        // intermédiaire pour la poser serait un écran de trop.
        is DiscoveryCard.Saga -> carte.next?.let { onOpenTitle(it.id, false) }
    }
}

@Composable
private fun titreDe(groupe: DiscoveryGroup, mood: MoodAnswers): String = when (groupe.kind) {
    // Le titre nomme les graines : c'est la différence entre une
    // recommandation et une recommandation qu'on comprend.
    DiscoveryKind.RECOUPEMENT -> stringResource(
        groupTitle(groupe.kind),
        groupe.seeds.joinToString(", "),
    )
    DiscoveryKind.HUMEUR -> stringResource(
        groupTitle(groupe.kind),
        mood.options.map { stringResource(optionLabel(it.id)) }.joinToString(", "),
    )
    else -> stringResource(groupTitle(groupe.kind))
}

/**
 * La teinte d'un groupe, **prise dans le dégradé de l'application**.
 *
 * Aucune couleur nouvelle : chaque groupe se pose quelque part sur orange →
 * magenta → violet. La page paraît variée alors que tout ce qu'elle affiche
 * appartient déjà à la marque.
 */
private fun teinteDe(kind: DiscoveryKind): Color = when (kind) {
    DiscoveryKind.HUMEUR -> MOOVIE_ORANGE
    DiscoveryKind.RECOUPEMENT -> MOOVIE_MAGENTA
    DiscoveryKind.REVOIR -> MOOVIE_ORANGE
    DiscoveryKind.SAGAS -> MOOVIE_VIOLET
    DiscoveryKind.PEPITES -> MOOVIE_MAGENTA
}

/** Marge horizontale : 40 dp est un recul de salon, trop sur un téléphone. */
@Composable
private fun hPad(): Dp = margePage()

/**
 * Largeur d'affiche.
 *
 * Aucune valeur fixe au-delà de 300 dp : c'est la faute que ce portage a payée
 * à chaque écran. 118 dp en portrait laissent voir trois cartes et demie d'une
 * main de six, ce qui suffit à comprendre qu'il y en a d'autres.
 */
@Composable
private fun cardWidth(): Dp = if (useBottomNav) 118.dp else 152.dp

/** Nombre de cartes entières qui tiennent dans une main, chevauchement compris. */
internal fun cardsThatFit(
    availableWidthDp: Float,
    cardWidthDp: Float,
    horizontalPaddingDp: Float,
): Int {
    val usable = availableWidthDp - horizontalPaddingDp * 2 - MARGE_INCLINAISON_DP
    val step = cardWidthDp * (1f - CHEVAUCHEMENT)
    val additionalCards = ((usable - cardWidthDp).coerceAtLeast(0f) / step).toInt()
    // Une affiche et la carte d'action restent accessibles, même dans une fenêtre très étroite.
    return (1 + additionalCards).coerceAtLeast(2)
}

/** Chevauchement des cartes, en fraction de leur largeur. */
private const val CHEVAUCHEMENT = 0.34f

/** Place payée à gauche pour qu'une carte inclinée ne soit pas rognée. */
private const val MARGE_INCLINAISON_DP = 14f

/** Inclinaison maximale d'une carte au bord de l'éventail, en degrés. */
private const val ANGLE = 6f

/** Amplitude du flottement, en pixels. Volontairement faible : voir la note de tête. */
private const val AMPLITUDE = 4f

/** Période de la respiration. Lente, pour ne pas fatiguer un salon. */
private const val PERIODE_MS = 7400
