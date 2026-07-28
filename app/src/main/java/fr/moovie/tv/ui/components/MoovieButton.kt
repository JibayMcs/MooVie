package fr.moovie.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon

/** Vert « actif/sélectionné » (outline), commun à tous les contrôles. */
val SELECTED_GREEN = Color(0xFF4CAF50)

private val BUTTON_SHAPE = RoundedCornerShape(10.dp)

/**
 * Bouton stylé de l'app : sombre au repos, accent rouge Movix au focus.
 * [selected] ajoute une outline verte (état actif/choisi) — remplace les
 * anciens marqueurs « ● » dans les libellés.
 */
@Composable
fun MoovieButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color(0xFFEDEDED),
            focusedContainerColor = Color(0xFFB5302C),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF8E2523),
            pressedContentColor = Color.White,
        ),
        shape = ButtonDefaults.shape(shape = BUTTON_SHAPE),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        border = selectedBorder(selected),
        content = content,
    )
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
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color(0xFFEDEDED),
            focusedContainerColor = Color(0xFFB5302C),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF8E2523),
            pressedContentColor = Color.White,
        ),
        shape = ButtonDefaults.shape(shape = BUTTON_SHAPE),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        border = selectedBorder(selected),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun selectedBorder(selected: Boolean) =
    if (selected) {
        ButtonDefaults.border(
            border = Border(BorderStroke(2.dp, SELECTED_GREEN), shape = BUTTON_SHAPE),
            focusedBorder = Border(BorderStroke(2.dp, SELECTED_GREEN), shape = BUTTON_SHAPE),
            pressedBorder = Border(BorderStroke(2.dp, SELECTED_GREEN), shape = BUTTON_SHAPE),
        )
    } else {
        ButtonDefaults.border()
    }
