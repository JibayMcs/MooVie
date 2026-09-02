package fr.moovie.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.moovie.tv.ui.theme.ESPACE_SERRE
import androidx.compose.foundation.shape.CircleShape
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MOOVIE_BG
import fr.moovie.tv.ui.theme.margePage

/**
 * La barre de navigation haute, pleine largeur.
 *
 * ## Ce qu'elle remplace
 *
 * Six icônes sans libellé, groupées dans le coin haut droit et **posées sur le
 * héros**. Trois défauts, dont deux qu'on ne voit qu'en s'en servant :
 *
 * 1. À la télécommande, y aller demande de remonter d'une rangée puis de
 *    traverser tout l'écran vers la droite. Le geste le plus fréquent de
 *    l'accueil — aller chercher un titre — était le plus long.
 * 2. Posées sur l'image, elles obligeaient le héros à leur réserver 250 points
 *    de largeur, qui manquaient aux titres longs.
 * 3. Sans libellé, une grille et un carré d'étoiles ne disent pas « catalogue »
 *    et « découverte ». On les apprend par essai.
 *
 * En barre, elles sont sur le trajet naturel du Haut, elles ne recouvrent plus
 * rien, et elles portent leur nom.
 *
 * ## Ce qu'elle coûte
 *
 * Une bande horizontale sur les 540 points d'un téléviseur. C'est le prix
 * assumé de la lisibilité : la barre est aussi courte que possible — la hauteur
 * d'un bouton et rien de plus — et le héros commence juste dessous.
 */
@Composable
fun MoovieTopNav(
    modifier: Modifier = Modifier,
    contenu: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HAUTEUR_NAV)
            // Opaque, et c'est le point : posée sur l'image elle en dépendait
            // pour être lisible, ce qui n'est jamais vrai deux affiches de
            // suite. Séparée du contenu, elle ne dépend plus de rien.
            .background(MOOVIE_BG)
            .padding(horizontal = margePage()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ESPACE_SERRE),
        content = contenu,
    )
}

/** Sépare les destinations de ce qui se pose au bout — les réglages. */
@Composable
fun RowScope.MoovieNavSpacer() {
    Spacer(modifier = Modifier.weight(1f))
}

/**
 * Une destination : son icône **et** son nom.
 *
 * Le libellé n'est pas décoratif. Une loupe se devine, une grille et quatre
 * étoiles non — « Catalogue » et « Découverte » répondent à deux questions
 * différentes (« montre-moi de la science-fiction » contre « je ne sais pas
 * quoi regarder ») que rien dans les deux pictogrammes ne distingue.
 *
 * @param badge nombre à poser sur l'icône, zéro pour aucun. C'est le compte des
 *   téléchargements en cours : la seule information de cette barre qui change
 *   sans qu'on ait rien fait, et donc la seule qui ait à s'annoncer.
 */
@Composable
fun MoovieNavItem(
    icone: ImageVector,
    libelle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
) {
    MoovieButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp,
            vertical = 10.dp,
        ),
    ) {
        Box {
            Icon(
                icone,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            if (badge > 0) {
                // La pastille se pose sur l'icône, pas à côté du libellé : c'est
                // l'icône qu'on repère du coin de l'œil.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .background(MOOVIE_ACCENT, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(ESPACE_SERRE))
        Text(
            libelle,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/**
 * Hauteur de la barre.
 *
 * Celle d'un bouton et de sa marge, pas un point de plus : chaque point pris
 * ici est pris au héros, et sur un téléviseur de 540 points le budget vertical
 * est la ressource rare.
 */
val HAUTEUR_NAV: Dp = 56.dp
