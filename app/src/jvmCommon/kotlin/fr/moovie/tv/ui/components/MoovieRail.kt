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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import fr.moovie.tv.shared.isPointerUi
import kotlinx.coroutines.launch

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
    row: @Composable () -> Unit,
) {
    if (!isPointerUi) {
        Box(modifier = modifier) { row() }
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
