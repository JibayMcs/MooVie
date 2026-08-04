package fr.moovie.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import fr.moovie.tv.ui.adaptive.isPointerUi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

/**
 * Empêche le déplacement du focus **dans** une rangée de faire défiler la page
 * qui la contient.
 *
 * Quand une carte prend le focus, Compose demande à tous les conteneurs
 * défilants au-dessus d'elle de la rendre visible. La `LazyRow` fait son travail
 * en défilant horizontalement — mais la demande continuait de remonter jusqu'à
 * la `LazyColumn` de la page, qui ajustait aussi la position verticale. Mesuré
 * sur l'accueil : **11 px de dérive après quatre appuis vers la droite**, d'où
 * l'impression que les rangées du dessus « sautent » pendant qu'on parcourt
 * celle du dessous.
 *
 * On intercepte donc la demande et on ne transmet plus au parent le rectangle de
 * l'élément focalisé, mais **celui du bloc entier**. Bloc déjà visible → le
 * parent n'a rien à faire, donc plus aucun mouvement vertical. Bloc
 * partiellement hors écran (on descend d'une rangée à l'autre) → le parent
 * défile pour le montrer, ce qui est exactement le comportement voulu. C'est
 * pour ça qu'on ne se contente pas d'ignorer la demande.
 *
 * À poser aussi **autour du titre + rangée**, pas seulement autour de la
 * `LazyRow` : sinon le parent ne connaît que les cartes, et sous un en-tête fixe
 * le titre de la rangée se retrouve rogné juste au-dessus du bord.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.scrollAsWholeBlock(): Modifier {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val responder = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect =
                Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())

            // La rangée n'est pas elle-même défilante : la `LazyRow` qu'elle
            // contient a déjà fait le nécessaire avant que la demande n'arrive ici.
            override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
        }
    }
    return onSizeChanged { size = it }.bringIntoViewResponder(responder)
}

/**
 * Ramène le focus sur la première carte quand la rangée est atteinte depuis
 * l'extérieur — et seulement à ce moment-là.
 *
 * La bascule se fait sur la **transition** « la rangée n'avait pas le focus →
 * elle l'a » : se déplacer de carte en carte à l'intérieur ne la déclenche donc
 * jamais, sans quoi on serait cloué sur la première.
 */
@Composable
private fun rememberRailEntryFocus(firstFocus: FocusRequester?): Modifier {
    if (firstFocus == null) return Modifier
    var hadFocus by remember { mutableStateOf(false) }
    return Modifier.onFocusChanged { state ->
        if (state.hasFocus && !hadFocus) {
            // Peut échouer si la première carte n'est pas encore attachée
            // (rangée recyclée par la liste) : dans ce cas on laisse le focus
            // où le système l'a mis plutôt que de le perdre.
            runCatching { firstFocus.requestFocus() }
        }
        hadFocus = state.hasFocus
    }
}

/** Fraction de la largeur visible parcourue par un « coup » de défilement. */
private const val PAGE_FRACTION = 0.8f

/** Multiplicateur appliqué au delta d'une molette horizontale. */
private const val WHEEL_STEP = 80f

/**
 * Enveloppe une rangée horizontale (`LazyRow`) pour la rendre atteignable à la
 * souris et au clavier.
 *
 * Sur TV le problème ne se pose pas : le D-pad déplace le focus de carte en
 * carte et la `LazyRow` défile toute seule. À la souris en revanche, rien ne
 * permettait d'atteindre les éléments hors écran — d'où les flèches affichées
 * au survol, la molette horizontale / Maj+molette, et les touches gauche/droite.
 *
 * Tout cela est **inactif sur Android TV** ([isPointerUi] faux) pour ne pas
 * intercepter les touches directionnelles de la télécommande.
 */
@Composable
fun MoovieRail(
    state: LazyListState,
    modifier: Modifier = Modifier,
    /**
     * Première carte de la rangée. Fournie, le focus y est ramené **chaque fois
     * que la rangée est atteinte depuis l'extérieur**.
     *
     * Sans ça, descendre d'une rangée à l'autre conservait la colonne : parti de
     * la 3ᵉ affiche, on arrivait sur la 3ᵉ de la rangée suivante, en ayant sauté
     * les deux premières sans les voir. Or les rangées sont ordonnées par
     * pertinence — leur début est ce qu'on a de mieux à montrer, et c'est aussi
     * ce que l'œil cherche en arrivant.
     */
    firstFocus: FocusRequester? = null,
    row: @Composable () -> Unit,
) {
    val focusFirst = rememberRailEntryFocus(firstFocus)
    if (!isPointerUi) {
        Box(modifier = modifier.scrollAsWholeBlock().then(focusFirst)) { row() }
        return
    }

    val scope = rememberCoroutineScope()
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val canScrollBack by remember { derivedStateOf { state.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { state.canScrollForward } }

    fun page(direction: Int) {
        scope.launch {
            val viewport = state.layoutInfo.viewportSize.width.toFloat()
            if (viewport > 0f) state.animateScrollBy(direction * viewport * PAGE_FRACTION)
        }
    }

    Box(
        modifier = modifier
            .scrollAsWholeBlock()
            .hoverable(hoverSource)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> if (canScrollBack) { page(-1); true } else false
                    Key.DirectionRight -> if (canScrollForward) { page(1); true } else false
                    else -> false
                }
            }
            // Molette horizontale (trackpad) et Maj+molette : la molette
            // verticale n'est jamais consommée, la page continue de défiler.
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val shift = event.keyboardModifiers.isShiftPressed
                        val delta = event.changes.fold(0f) { acc, change ->
                            acc + change.scrollDelta.x + if (shift) change.scrollDelta.y else 0f
                        }
                        if (delta == 0f) continue
                        event.changes.forEach { it.consume() }
                        scope.launch { state.animateScrollBy(delta * WHEEL_STEP) }
                    }
                }
            },
    ) {
        row()

        AnimatedVisibility(
            visible = hovered && canScrollBack,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
        ) {
            MoovieIconButton(
                onClick = { page(-1) },
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
            )
        }
        AnimatedVisibility(
            visible = hovered && canScrollForward,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        ) {
            MoovieIconButton(
                onClick = { page(1) },
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}
