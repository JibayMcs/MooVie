package fr.moovie.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Vert « actif/sélectionné » (outline), commun à tous les contrôles. */
val SELECTED_GREEN = Color(0xFF4CAF50)

/** Rouge accent de l'app (focus/hover). */
val MOOVIE_ACCENT = Color(0xFFB5302C)

private val BUTTON_SHAPE = RoundedCornerShape(10.dp)
private val REST_BG = Color(0xFF1E1E1E)
private val REST_FG = Color(0xFFEDEDED)
private val PRESSED_BG = Color(0xFF8E2523)

/**
 * Bouton stylé de l'app, 100 % foundation (aucune dépendance tv-material) :
 * sombre au repos, accent rouge au focus D-pad ou au survol souris, [selected]
 * ajoute une outline verte (état actif/choisi).
 */
@Composable
fun MoovieButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = focused || hovered
    val scale by animateFloatAsState(if (active) 1.05f else 1f)

    val bg = when {
        pressed -> PRESSED_BG
        active -> MOOVIE_ACCENT
        else -> REST_BG
    }
    val fg = if (active || pressed) Color.White else REST_FG

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(BUTTON_SHAPE)
            .background(bg)
            .then(if (selected) Modifier.border(2.dp, SELECTED_GREEN, BUTTON_SHAPE) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}

/**
 * Bouton icône compact (actions secondaires : réglages, tri, vu/non vu…).
 * Même langage visuel que [MoovieButton], avec outline verte si [selected].
 */
@Composable
fun MoovieIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    MoovieButton(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Carte cliquable (affiches, épisodes…) : zoom + bordure accent au focus/survol.
 * Remplace tv-material Card dans les écrans partagés. [onLongClick] ouvre un
 * menu contextuel (appui long OK sur TV, clic long/droit à la souris).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoovieCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusedScale: Float = 1.1f,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = focused || hovered
    val scale by animateFloatAsState(if (active) focusedScale else 1f)
    val shape = RoundedCornerShape(10.dp)

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Color(0xFF181818))
            .then(if (active) Modifier.border(3.dp, MOOVIE_ACCENT, shape) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        content()
    }
}
