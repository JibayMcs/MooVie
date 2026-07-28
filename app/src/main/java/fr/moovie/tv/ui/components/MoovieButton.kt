package fr.moovie.tv.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults

/**
 * Bouton stylé de l'app : sombre au repos, accent rouge Movix au focus, coins
 * arrondis et léger agrandissement au focus. Remplace le pill clair par défaut
 * de tv-material3 partout dans l'UI.
 */
@Composable
fun MoovieButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        content = content,
    )
}
