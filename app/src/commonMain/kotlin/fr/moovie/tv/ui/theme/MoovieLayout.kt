package fr.moovie.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.moovie.tv.ui.adaptive.LocalWindowWidth
import fr.moovie.tv.ui.adaptive.useBottomNav

/**
 * ## La marge de page, une seule pour toute l'application
 *
 * Elle en avait quatre : 32 points sur l'accueil, 40 sur le catalogue et les
 * filmographies, 48 sur la fiche et le lecteur, 16 au doigt. Aucune n'était
 * fausse prise isolément ; ensemble elles donnaient une application dont le
 * bord gauche bougeait à chaque écran, ce qui se voit immédiatement quand on
 * passe de l'un à l'autre — le contenu tressaute latéralement.
 *
 * ## Pourquoi une proportion et non un nombre
 *
 * Un nombre fixe suppose une largeur. Les nôtres vont de 448 points (téléphone
 * en portrait) à plus de 1 700 (fenêtre de bureau ouverte en grand) : 48 points
 * y sont successivement le dixième de l'écran et son quarantième. Dans le
 * premier cas la marge écrase le contenu, dans le second elle disparaît et le
 * texte vient toucher le bord.
 *
 * Un dixième de la largeur, c'est la mesure de la maquette de référence, et
 * c'est aussi ce qui donne au regard un point d'entrée stable quelle que soit
 * la taille. Le plancher tient le cas de la fenêtre étroite, où un dixième ne
 * serait plus rien.
 *
 * Au doigt, rien de tout cela : l'écran est déjà étroit, la marge n'y sert qu'à
 * décoller le texte du bord, et seize points suffisent.
 */
@Composable
fun margePage(): Dp = if (useBottomNav) {
    16.dp
} else {
    (LocalWindowWidth.current * 0.09f).coerceAtLeast(32.dp)
}

/**
 * ## Le rythme vertical
 *
 * Quatre valeurs, et elles ne se choisissent pas au jugé : chacune répond à une
 * question sur ce qui sépare deux choses.
 *
 * - [ESPACE_SERRE] — deux lignes du même objet. Un titre et sa date, une carte
 *   et son libellé. Elles se lisent d'un bloc.
 * - [ESPACE] — deux objets d'une même liste. Deux réglages, deux épisodes.
 * - [ESPACE_LARGE] — deux groupes. Une rangée et la suivante, un bloc de
 *   formulaire et le suivant.
 * - [ESPACE_SECTION] — deux parties de la page. Ce qui mériterait un titre.
 *
 * L'écart entre deux valeurs est un facteur d'environ 1,6 : sous ce rapport,
 * deux espacements voisins se ressemblent, et on ne lit plus un groupement mais
 * une suite indifférenciée.
 */
val ESPACE_SERRE: Dp = 6.dp
val ESPACE: Dp = 12.dp
val ESPACE_LARGE: Dp = 20.dp
val ESPACE_SECTION: Dp = 32.dp
