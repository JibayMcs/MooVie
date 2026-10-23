package fr.moovie.tv.ui.theme

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
 * Ces trois teintes ne servent qu'à **signaler** — l'option retenue, l'action
 * principale, un repère. Jamais à remplir un bouton ordinaire : au repos il n'a
 * pas de fond, seule son icône est visible, et c'est ce qui laisse les affiches
 * floutées derrière porter l'écran. Le focus, lui, n'est pas de cette palette :
 * il est blanc (voir [moovieSurface]).
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

/** Le fond de ce qu'on vise : blanc, opaque, sans ambiguïté possible. */
private val FOCUS_FILL = Color.White

/** Le même blanc, entamé le temps d'un appui : le bouton accuse le coup. */
private val FOCUS_PRESSED = Color.White.copy(alpha = 0.82f)

/** Retour d'un appui au doigt, sans focus : un voile, puisqu'il n'y a rien à viser. */
private val GLASS_PRESSED = Color.White.copy(alpha = 0.16f)

/**
 * Épaisseur du trait dégradé sous l'option retenue. Trois points : à deux, il
 * ne se voyait plus à trois mètres.
 */
private val UNDERLINE = 3.dp

/**
 * Habillage commun à tous les contrôles.
 *
 * ## Deux marques, deux sens, deux formes
 *
 * **Le focus est un aplat blanc.** Ce que la télécommande vise prend un fond
 * blanc opaque et son contenu passe en sombre : c'est la marque la plus visible
 * qu'un écran sombre puisse porter, et c'est celle de tous les lecteurs de
 * salon — personne n'a à l'apprendre.
 *
 * **La sélection est un trait dégradé sous le contrôle.** L'onglet ouvert,
 * l'option choisie dans un réglage : un état, pas un curseur. Il n'a rien de
 * commun avec le focus, ni la couleur ni la forme, et c'est le point. Les deux
 * peuvent se cumuler — l'onglet ouvert, visé — sans qu'on les confonde : un
 * aplat blanc avec un trait en bas.
 *
 * ## Ce que ça remplace
 *
 * Le focus était un verre blanc à 8 %, un halo magenta et le **même trait
 * dégradé** que la sélection, en plus franc ; et le bouton principal, rempli,
 * se cernait de sa propre couleur. Depuis le canapé, « Épisodes » souligné de
 * violet et « Lire » cerné de violet se lisaient pareil, et rien ne disait
 * lequel des deux répondrait à OK : impossible de savoir si c'est un bouton
 * ou un onglet. Le halo s'en va avec : derrière un aplat opaque, il ne se
 * voyait plus.
 *
 * @param active focus D-pad ou survol souris.
 * @param selected état retenu (onglet courant, option choisie).
 * @param pressed appui en cours. Avec le focus, le blanc s'entame ; sans — au
 *   doigt —, un simple voile.
 */
fun Modifier.moovieSurface(
    active: Boolean,
    selected: Boolean = false,
    pressed: Boolean = false,
): Modifier = drawBehind {
    when {
        active && pressed -> drawRect(color = FOCUS_PRESSED)
        active -> drawRect(color = FOCUS_FILL)
        pressed -> drawRect(color = GLASS_PRESSED)
    }
    if (selected) {
        val thickness = UNDERLINE.toPx()
        drawRect(
            brush = MoovieGradient,
            topLeft = Offset(0f, size.height - thickness),
            size = Size(size.width, thickness),
        )
    }
}
