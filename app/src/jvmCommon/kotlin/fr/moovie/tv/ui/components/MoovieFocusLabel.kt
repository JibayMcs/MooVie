package fr.moovie.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.moovie.tv.ui.adaptive.LocalUiFlavor

/**
 * Le nom d'un bouton, écrit sous lui tant qu'il a le focus. **Téléviseur seul.**
 *
 * ### Pourquoi seulement là
 *
 * Une icône seule se devine tant qu'on en a trois ; l'en-tête de l'accueil en
 * aligne six, et à trois mètres de recul on hésite. Au pointeur, l'infobulle de
 * `MoovieIconButton` répond déjà à la question ; au doigt, il n'y a ni survol ni
 * focus, donc rien à révéler et le libellé serait figé sur une icône que
 * personne n'a désignée. La télécommande est le seul cas où un curseur existe
 * en permanence — et c'est justement l'appareil où l'on est le plus loin.
 *
 * ### Pourquoi pas une infobulle
 *
 * Une bulle a un fond, un cadre et une ombre : trois choses de plus à dessiner
 * par-dessus une affiche, pour un mot. Ici c'est du texte blanc, posé sous
 * l'icône, qui apparaît et disparaît avec le focus.
 *
 * ### Deux précautions de mise en page
 *
 * La hauteur du libellé est **réservée en permanence** : la faire apparaître
 * décalerait toute la rangée d'icônes vers le haut au moment du focus. Et le
 * texte est mesuré **sans borne de largeur**, faute de quoi un mot plus large
 * que son icône écarterait ses voisines dès qu'on le survole.
 */
@Composable
fun MoovieFocusLabel(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val surTelecommande = LocalUiFlavor.current.isDpad
    if (!surTelecommande) {
        content()
        return
    }

    // `hasFocus` et non `isFocused` : le focus vit sur le bouton, qui est un
    // enfant de cette colonne. `isFocused` ne serait vrai que si la colonne
    // elle-même était focalisable, ce qu'elle n'a aucune raison d'être.
    var focalise by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.onFocusChanged { focalise = it.hasFocus },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
        // Respiration entre l'icône et son nom. Réservée elle aussi en
        // permanence : c'est la hauteur totale qui doit rester constante, pas
        // seulement celle du texte.
        Spacer(Modifier.height(ECART))
        // **Largeur nulle, texte débordant.** Une boîte qui `fillMaxWidth` prend
        // toute la largeur offerte par la rangée : la colonne du premier bouton
        // avalait alors la ligne entière et les cinq autres icônes sortaient de
        // l'écran. Ici la boîte ne mesure rien, et le libellé est mesuré sans
        // borne puis centré — il déborde de part et d'autre sans jamais
        // déplacer une icône.
        Box(
            modifier = Modifier.height(HAUTEUR_LIBELLE).width(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (focalise) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.wrapContentSize(unbounded = true),
                )
            }
        }
    }
}

/** Assez pour une ligne de `labelSmall`, et réservée même quand rien n'est écrit. */
private val HAUTEUR_LIBELLE = 18.dp

/**
 * Écart entre l'icône et son nom.
 *
 * Collé, le libellé se lisait comme une partie du bouton plutôt que comme sa
 * légende. Le décoller le rattache à l'icône sans s'y confondre.
 */
private val ECART = 8.dp
