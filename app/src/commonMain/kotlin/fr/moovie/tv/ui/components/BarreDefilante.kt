package fr.moovie.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.moovie.tv.ui.theme.MOOVIE_BG
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM

/**
 * Une rangée de boutons qui défile horizontalement quand elle ne tient pas —
 * **et qui le dit**.
 *
 * ## Le problème qu'elle résout
 *
 * Les barres de l'application alignent quatre à six boutons : les onglets d'une
 * fiche, les filtres d'une recherche, les genres d'un catalogue. Sur un
 * téléviseur ils tiennent tous ; sur un téléphone en portrait, le dernier tombe
 * hors de l'écran. Un `Row` ne déborde pas, il tronque — et sans le moindre
 * signe. Le dernier onglet d'une fiche série (« En savoir plus ») emportait
 * ainsi le casting et la fiche technique de toutes les séries, sans que rien
 * n'indique qu'ils existaient encore.
 *
 * ## Pourquoi deux signes et non un
 *
 * Le rendre défilant ne suffit pas : le dernier bouton visible se termine
 * proprement avant le bord, exactement comme se termine une barre complète.
 *
 * Le dégradé posé sur le bord éteint ce qui court dessous au lieu de le couper
 * net, et cette coupure douce se lit comme « ça continue ». Mais il ne peut
 * éteindre que ce qui passe dessous : ce qui dépasse au bord est le plus
 * souvent le **rembourrage** du bouton suivant, pas son libellé, et le voile
 * s'applique alors à du noir. D'où le chevron, qui ne dépend de rien pour se
 * voir. Chacun ne paraît que du côté où il reste du chemin ; arrivé au bout, le
 * bord redevient franc et la barre dit qu'elle est finie. Sur un téléviseur, où
 * tout tient, aucun des deux ne s'allume jamais.
 *
 * @param marge marge de page, appliquée **dans** le défilement. Posée autour,
 *   elle arrêterait la zone défilante avant le bord de l'écran : le contenu y
 *   disparaîtrait trop tôt, et le voile n'aurait plus rien à éteindre. Elle
 *   rogne aussi les boutons agrandis au focus, qui débordent de leur case.
 */
@Composable
fun BarreDefilante(
    modifier: Modifier = Modifier,
    marge: Dp = 0.dp,
    espacement: Dp = 8.dp,
    contenu: @Composable RowScope.() -> Unit,
) {
    val defilement = rememberScrollState()
    val versLaGauche = defilement.value > 0
    val versLaDroite = defilement.value < defilement.maxValue
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            // Étroit à dessein : ce qui dépasse d'un bouton suivant se compte en
            // dizaines de points, et un voile plus large que ce dépassement
            // l'efface au lieu de l'éteindre — on retrouve alors un bord franc.
            val largeur = VOILE.toPx()
            if (versLaGauche) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(MOOVIE_BG, Color.Transparent),
                        startX = 0f,
                        endX = largeur,
                    ),
                    size = Size(largeur, size.height),
                )
            }
            if (versLaDroite) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, MOOVIE_BG),
                        startX = size.width - largeur,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - largeur, 0f),
                    size = Size(largeur, size.height),
                )
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(defilement),
            horizontalArrangement = Arrangement.spacedBy(espacement),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(marge))
            contenu()
            Spacer(Modifier.width(marge))
        }
        Chevron(Icons.AutoMirrored.Filled.KeyboardArrowLeft, versLaGauche, Alignment.CenterStart)
        Chevron(Icons.AutoMirrored.Filled.KeyboardArrowRight, versLaDroite, Alignment.CenterEnd)
    }
}

private val VOILE = 20.dp

/**
 * Le repère de défilement, à un bord de la barre.
 *
 * Il paraît et disparaît en fondu plutôt que d'un coup : la barre bouge sous le
 * doigt, et une icône qui s'allume sèchement pendant ce mouvement se lit comme
 * un élément de plus, pas comme un état de celui qu'on manipule.
 *
 * `MOOVIE_TEXT_DIM` et non la couleur des libellés : c'est un panneau, pas un
 * bouton, et deux blancs côte à côte les mettraient sur le même plan.
 * Décoratif : il montre le chemin, on ne l'appuie pas — le geste, c'est de
 * faire glisser la barre.
 */
@Composable
private fun BoxScope.Chevron(icone: ImageVector, visible: Boolean, cote: Alignment) {
    val opacite by animateFloatAsState(if (visible) 1f else 0f, label = "chevronBarre")
    if (opacite > 0f) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = MOOVIE_TEXT_DIM,
            modifier = Modifier.align(cote).size(20.dp).graphicsLayer { alpha = opacite },
        )
    }
}
