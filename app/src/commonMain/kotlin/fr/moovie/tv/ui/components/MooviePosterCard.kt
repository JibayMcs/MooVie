package fr.moovie.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.moovie.tv.shared.formaterDecimal
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MOOVIE_RATING
import fr.moovie.tv.ui.theme.MOOVIE_READY
import fr.moovie.tv.ui.theme.MOOVIE_SCRIM
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE_HIGH
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM

/**
 * L'affiche d'un titre, partout dans l'application.
 *
 * ## Ce qu'elle remplace
 *
 * Trois copies presque identiques — catalogue, filmographie, accueil — nées
 * chacune sur son écran. Elles divergeaient déjà : le fond d'attente n'était
 * pas le même, la pastille de liste non plus, et le libellé s'abrégeait à une
 * ligne ici et deux là. Une affiche est pourtant la même chose partout, et
 * c'est l'objet qu'on voit le plus souvent dans cette application.
 *
 * ## Le dépliage au focus
 *
 * Au repos, l'affiche et son titre. Quand la carte est visée, elle **révèle ce
 * qu'on allait chercher** : la note et l'année. C'est ce qui permet de trancher
 * sans ouvrir la fiche — parcourir une rangée devient une lecture au lieu d'une
 * suite d'allers-retours.
 *
 * Ces deux lignes ne sont pas affichées en permanence, et c'est délibéré : sur
 * une rangée de huit affiches, seize valeurs supplémentaires font du bruit et
 * personne ne les lit. Sur celle qu'on regarde, elles sont exactement ce qui
 * manquait.
 *
 * @param surAffiche ce que l'appelant pose **sur** l'affiche : la pastille
 *   « disponible hors ligne », par exemple. Une fente plutôt qu'un paramètre,
 *   parce qu'une affiche n'a pas à connaître l'état de téléchargement d'un
 *   titre — elle sait seulement qu'il y a un coin où poser une marque.
 *
 * **Pas d'action dans la carte.** La maquette en proposait ; sur une
 * télécommande, atteindre un bouton *dans* une carte demande un second niveau
 * de focus, et une rangée qu'on parcourt à la flèche deviendrait un labyrinthe.
 * L'application a déjà sa réponse : OK ouvre, OK long ouvre le menu d'actions
 * (voir [MoovieCard]). Le dépliage informe, il n'agit pas.
 */
@Composable
fun MooviePosterCard(
    posterUrl: String?,
    titre: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** Note TMDB sur dix, zéro pour aucune. */
    note: Double = 0.0,
    annee: String? = null,
    inWatchlist: Boolean = false,
    isWatched: Boolean = false,
    surAffiche: (@Composable BoxScope.() -> Unit)? = null,
) {
    MoovieCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth()) {
        Column {
            Box {
                MoovieAsyncImage(
                    model = posterUrl,
                    contentDescription = titre,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(MOOVIE_SURFACE_HIGH),
                )
                // **Deux états, deux coins.** Un titre peut être à la fois vu
                // et mis de côté, et c'est même le cas le plus courant sur une
                // série qu'on suit. Trois écrans sur quatre n'en montraient
                // qu'un, celui qui gagnait un `if` — on croyait alors avoir
                // retiré de la liste un titre qu'on venait de marquer vu.
                //
                // Dans un rond opaque : une icône blanche sur une affiche
                // claire n'existe pas.
                if (isWatched) PastilleAffiche(Alignment.TopEnd, Icons.Default.Check, MOOVIE_READY)
                if (inWatchlist) {
                    PastilleAffiche(Alignment.BottomEnd, Icons.Default.Bookmark, MOOVIE_ACCENT)
                }
                surAffiche?.invoke(this)
            }
            MoovieMarqueeText(
                text = titre,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            // Révélé au focus. `expandVertically` et non un simple fondu : la
            // carte doit **grandir**, sinon les deux lignes se superposeraient
            // au titre ou déborderaient hors du cadre.
            AnimatedVisibility(
                visible = LocalMoovieCardActive.current && (note > 0 || annee != null),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (note > 0) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MOOVIE_RATING,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            formaterDecimal(note, 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MOOVIE_RATING,
                        )
                    }
                    if (annee != null) {
                        Text(
                            annee,
                            style = MaterialTheme.typography.labelSmall,
                            color = MOOVIE_TEXT_DIM,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Le rond opaque qui porte une marque d'état sur une affiche. */
@Composable
private fun BoxScope.PastilleAffiche(
    coin: Alignment,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    teinte: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .align(coin)
            .padding(6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(MOOVIE_SCRIM),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = null, tint = teinte, modifier = Modifier.size(13.dp))
    }
}
