package fr.moovie.tv.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp

/**
 * Identité visuelle de Moo-vie, relevée sur la bannière du dépôt : une diagonale
 * chaude → froide, orange → magenta → violet.
 *
 * Ces trois teintes ne servent qu'à **signaler** — focus, état actif, repère.
 * Jamais à remplir : un bouton au repos n'a pas de fond, seule son icône est
 * visible. C'est ce qui laisse les affiches floutées derrière porter l'écran.
 */
val MOOVIE_ORANGE = Color(0xFFFF9A2E)
val MOOVIE_MAGENTA = Color(0xFFE8214E)
val MOOVIE_VIOLET = Color(0xFF7B3FB0)

/** Teinte d'accent unique quand un dégradé n'a pas de sens (texte, séparateur). */
val MOOVIE_ACCENT = MOOVIE_MAGENTA

/** Le dégradé identité, dans le sens de lecture. */
val MoovieGradient = Brush.horizontalGradient(
    listOf(MOOVIE_ORANGE, MOOVIE_MAGENTA, MOOVIE_VIOLET),
)

/**
 * Angles droits partout. Les coins arrondis donnaient une interface molle et
 * générique ; l'app assume une géométrie franche, qui laisse aussi les affiches
 * (rectangulaires) s'aligner sans liseré parasite.
 */
val MoovieShape: Shape = RectangleShape

/** Verre translucide posé sur le focus. Volontairement discret. */
private val GLASS_FOCUSED = Color.White.copy(alpha = 0.08f)
private val GLASS_PRESSED = Color.White.copy(alpha = 0.16f)

/** Épaisseur du liseré d'accent sous un contrôle actif. */
private val UNDERLINE = 2.dp

/**
 * Habillage commun à tous les contrôles : rien au repos, verre + halo au focus,
 * liseré dégradé quand l'état est actif.
 *
 * Le halo est un dégradé radial dessiné *derrière* le contenu, pas un flou :
 * Compose n'offre pas de flou d'arrière-plan portable, et `Modifier.blur` ne
 * s'applique qu'à partir d'Android 12 — sur une box plus ancienne il ne ferait
 * rien du tout. Par-dessus un fond déjà flouté, le rendu « verre » est le même
 * et il tient sur toutes les versions.
 *
 * @param active focus D-pad ou survol souris.
 * @param selected état retenu (onglet courant, option choisie) : le liseré
 *   reste, mais sans le verre — sinon un écran de réglages ressemble à un
 *   damier de boutons allumés.
 */
fun Modifier.moovieSurface(
    active: Boolean,
    selected: Boolean = false,
    pressed: Boolean = false,
    glowAlpha: Float = 1f,
): Modifier = drawBehind {
    if (active || pressed) {
        drawRect(color = if (pressed) GLASS_PRESSED else GLASS_FOCUSED)
    }
    if (active && glowAlpha > 0f) {
        // Halo centré, éteint sur les bords : il éclaire le contrôle sans
        // dessiner une seconde forme par-dessus la première.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    MOOVIE_MAGENTA.copy(alpha = 0.34f * glowAlpha),
                    MOOVIE_VIOLET.copy(alpha = 0.16f * glowAlpha),
                    Color.Transparent,
                ),
                center = Offset(size.width / 2f, size.height),
                radius = size.maxDimension,
            ),
        )
    }
    if (active || selected) {
        val thickness = UNDERLINE.toPx()
        drawRect(
            brush = MoovieGradient,
            topLeft = Offset(0f, size.height - thickness),
            size = Size(size.width, thickness),
            alpha = if (active) 1f else 0.65f,
        )
    }
}

/**
 * Halo qui respire au focus. Une animation lente et de faible amplitude : sur
 * une TV allumée des heures, un effet marqué devient vite fatigant, et le focus
 * doit rester lisible d'un coup d'œil à trois mètres.
 */
@Composable
fun rememberGlow(active: Boolean): Float {
    return animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        label = "moovieGlow",
    ).value
}
