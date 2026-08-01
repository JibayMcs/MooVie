package fr.moovie.tv.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Thème partagé TV + desktop (Material3 multiplateforme). Sur TV, il est
// englobé par le thème tv-material tant que des écrans tv-material subsistent.
private val MooVieColors = darkColorScheme(
    primary = MOOVIE_MAGENTA,
    onPrimary = Color.White,
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFEDEDED),
)

@Composable
fun MooVieTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MooVieColors) {
        // Couleur de contenu par défaut : sans Surface parent, les Text libres
        // hériteraient du noir par défaut → invisibles sur le fond sombre.
        CompositionLocalProvider(LocalContentColor provides MooVieColors.onBackground, content = content)
    }
}
