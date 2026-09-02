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

/**
 * Les deux couleurs d'**état**, hors identité.
 *
 * La palette ci-dessus signale l'attention : ce qui est visé, sélectionné, en
 * cours. Elle ne sait pas dire « c'est prêt » ni « voici la note », et l'y
 * forcer reviendrait à peindre trois choses différentes de la même couleur.
 *
 * Elles vivent ici plutôt que dans l'écran qui les a introduites, parce que
 * quatre écrans les emploient déjà — accueil, découverte, fiche, épisodes — et
 * que deux verts voisins se lisent comme deux états différents.
 */
val MOOVIE_READY = Color(0xFF5FD98A)
val MOOVIE_RATING = Color(0xFFE6B800)

/** Ce qui demande une décision sans être une panne : un téléchargement partiel. */
val MOOVIE_WARN = Color(0xFFE0B057)

/** Ce qui a échoué. Rouge désaturé : l'écran est sombre, un rouge pur y vibre. */
val MOOVIE_ERROR = Color(0xFFE08585)

/**
 * ## L'échelle de gris, et pourquoi il en fallait une
 *
 * L'interface en comptait **six pour le texte** — `CCCCCC`, `DDDDDD`, `BBBBBB`,
 * `9A9A9A`, `888888`, `777777` — et autant pour les fonds. Aucun de ces écarts
 * n'était une décision : chacun est né sur un écran, à un moment, sans savoir
 * ce que le voisin avait choisi. À l'usage, deux textes de même rang y prennent
 * deux gris différents, et l'œil lit une hiérarchie là où il n'y en a pas.
 *
 * Quatre valeurs suffisent, et quatre valeurs se retiennent :
 *
 * - [MOOVIE_TEXT] — ce qu'on lit. Titres, libellés, contenu.
 * - [MOOVIE_TEXT_MUTED] — ce qui accompagne. Synopsis, descriptions, aide.
 * - [MOOVIE_TEXT_DIM] — ce qui qualifie. Métadonnées, rôles, unités.
 * - [MOOVIE_TEXT_FAINT] — ce qui est indisponible. Rien d'autre.
 *
 * Le pas entre deux niveaux est net (environ 25 % de luminance) : c'est ce qui
 * permet de les distinguer à trois mètres, là où six gris rapprochés se
 * ressemblaient tous.
 */
val MOOVIE_TEXT = Color(0xFFF2F2F2)
val MOOVIE_TEXT_MUTED = Color(0xFFB8B8B8)
val MOOVIE_TEXT_DIM = Color(0xFF8C8C8C)
val MOOVIE_TEXT_FAINT = Color(0xFF5E5E5E)

/**
 * Les fonds, même raisonnement.
 *
 * [MOOVIE_BG] est celui de la page — la même valeur que `background` du thème,
 * nommée ici parce que des dizaines d'appels la réécrivaient à la main.
 * [MOOVIE_SURFACE] est celui d'un bloc posé dessus, [MOOVIE_SURFACE_HIGH] celui
 * d'un bloc posé sur un bloc. Au-delà de trois niveaux, on n'empile plus, on
 * s'égare.
 *
 * [MOOVIE_SCRIM] est le voile qu'on passe **sur une image** pour y poser du
 * texte. Il est noir et opaque à 80 % : moins, le texte devient illisible sur
 * une affiche claire ; plus, l'image ne sert plus à rien.
 */
val MOOVIE_BG = Color(0xFF0A0A0A)
val MOOVIE_SURFACE = Color(0xFF161616)
val MOOVIE_SURFACE_HIGH = Color(0xFF232323)
val MOOVIE_SCRIM = Color(0xCC0A0A0A)

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
