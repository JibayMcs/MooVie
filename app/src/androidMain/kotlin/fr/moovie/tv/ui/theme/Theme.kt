package fr.moovie.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val MooVieColors = darkColorScheme(
    primary = Color(0xFFB5302C),
    onPrimary = Color.White,
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFEDEDED),
)

// Thème tv-material résiduel : englobe le thème material3 partagé tant que des
// écrans tv-material subsistent (PlayerScreen). À supprimer avec eux.
@Composable
fun MooVieTvMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MooVieColors, content = content)
}
