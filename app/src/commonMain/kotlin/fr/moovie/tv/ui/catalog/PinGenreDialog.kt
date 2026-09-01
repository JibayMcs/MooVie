package fr.moovie.tv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.moovie.tv.data.home.HomeLayoutEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.backup_cancel
import fr.moovie.tv.resources.pin_after
import fr.moovie.tv.resources.pin_at_end
import fr.moovie.tv.resources.pin_done
import fr.moovie.tv.resources.pin_first
import fr.moovie.tv.resources.pin_position
import fr.moovie.tv.resources.pin_remove
import fr.moovie.tv.resources.pin_title
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.home.homeRowLabel
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.stringResource

/**
 * Modale d'épinglage d'un genre, ouverte à l'appui long (TV, doigt) ou au clic
 * droit (souris).
 *
 * Elle demande **où** poser la rangée, pas seulement si on la veut : un accueil
 * personnalisable dont chaque ajout tombe en dernier oblige à passer par l'écran
 * de réorganisation à chaque épinglage. La question se pose ici, une fois.
 *
 * La position s'exprime par rapport à une rangée existante — « après Tendances »
 * — plutôt que par un numéro : personne ne sait de tête que sa rangée « Ma
 * liste » est la troisième, et ce numéro change dès qu'une rangée se vide.
 *
 * **Une seule liste, un seul geste.** Un premier jet demandait la rangée repère,
 * puis « avant » ou « après » dans un seul contrôle : deux libellés longs à se
 * partager 448 dp de large, tronqués et de largeurs inégales. Or la question
 * était de toute façon redondante — « avant X » est toujours « après la rangée
 * d'au-dessus », et le seul cas qui échappe à la règle est la première place,
 * qui a donc sa propre entrée. Rien n'a été perdu, et il n'y a plus qu'à choisir.
 *
 * Un genre déjà épinglé n'a qu'une action : le retirer. Rouvrir le choix de
 * position aurait fait de cette modale deux écrans en un, et il y a l'écran de
 * réorganisation pour ça.
 */
@Composable
fun PinGenreDialog(
    genreName: String,
    isPinned: Boolean,
    layout: List<HomeLayoutEntry>,
    onDismiss: () -> Unit,
    onPin: (anchorId: String?, after: Boolean) -> Unit,
    onUnpin: () -> Unit,
) {
    val slots = pinSlots(layout)
    // La fin de l'accueil par défaut : c'est le seul choix qui ne déplace
    // visuellement rien de ce qu'on connaît déjà.
    var chosen by remember(slots.size) { mutableStateOf(slots.lastIndex) }
    val firstAction = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                // Bornée, pas fixée : la même modale s'ouvre sur les 448 dp d'un
                // téléphone en portrait et sur les 960 d'une TV.
                .widthIn(max = 420.dp)
                .clip(MoovieShape)
                .background(Color(0xF5161616))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(Res.string.pin_title, genreName),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (isPinned) {
                MoovieButton(
                    onClick = { onUnpin(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().focusRequester(firstAction),
                ) { Text(stringResource(Res.string.pin_remove)) }
            } else {
                Text(
                    stringResource(Res.string.pin_position),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9A9A9A),
                )
                // Défilante, et **bornée bas** : la liste grandit à chaque
                // épinglage, et une modale qui déborde de l'écran n'a plus de
                // bouton valider. 240 dp est ce qui laisse « Épingler » et
                // « Annuler » à l'écran sur les 540 dp de haut d'une TV 1080p,
                // qui est le plus court des trois formats.
                //
                // Le recalage compte autant que la hauteur : le choix par défaut
                // est le dernier de la liste, donc sous le pli. Sans lui, la
                // modale s'ouvrait en désignant « En premier » alors que c'est
                // « À la fin » qui était retenu.
                val positions = rememberLazyListState()
                LaunchedEffect(Unit) { runCatching { positions.scrollToItem(chosen) } }
                LazyColumn(
                    state = positions,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(slots, key = { _, slot -> slot.key }) { index, slot ->
                        MoovieButton(
                            onClick = { chosen = index },
                            selected = index == chosen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (index == chosen) Modifier.focusRequester(firstAction)
                                    else Modifier,
                                ),
                        ) {
                            Text(
                                slot.label(),
                                // Une position par ligne, tronquée au besoin :
                                // deux libellés à se partager la largeur d'un
                                // portrait, c'est ce qui ne tenait pas.
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                MoovieButton(
                    onClick = {
                        slots.getOrNull(chosen)?.let { onPin(it.anchorId, it.after) }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(Res.string.pin_done)) }
            }

            MoovieButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.backup_cancel))
            }
        }
    }
    // Après le recalage de la liste : un `FocusRequester` posé sur une ligne que
    // la fenêtre n'a pas encore composée ne fait rien, et le focus retombait
    // alors sur la première ligne — l'écran désignait « En premier » alors que
    // c'est « À la fin » qui était retenu.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstAction.requestFocus() }
    }
}

/**
 * Une place possible dans l'accueil, telle qu'on la propose.
 *
 * [anchorId] et [after] sont ce que le dépôt attend ; [label] est ce que
 * l'utilisateur lit. Les deux ne coïncident pas : « À la fin » est un « après la
 * dernière rangée » qu'il vaut mieux ne pas nommer ainsi, et « En premier » est
 * le seul « avant » que la liste garde.
 */
private class PinSlot(
    val key: String,
    val anchorId: String?,
    val after: Boolean,
    val label: @Composable () -> String,
)

/**
 * Les places offertes, de haut en bas de l'accueil.
 *
 * Il y en a une de plus que de rangées : entre N rangées il y a N+1 interstices.
 * Les nommer tous par « après » sauf le premier évite au lecteur d'avoir à
 * traduire un choix en position — chaque ligne dit déjà où la rangée atterrira.
 */
@Composable
private fun pinSlots(layout: List<HomeLayoutEntry>): List<PinSlot> {
    val end = PinSlot("end", null, true) { stringResource(Res.string.pin_at_end) }
    if (layout.isEmpty()) return listOf(end)

    val first = layout.first()
    return buildList {
        add(PinSlot("first", first.id, after = false) { stringResource(Res.string.pin_first) })
        // La dernière rangée est écartée : « après elle » est déjà [end], et
        // proposer deux fois la même place ferait douter de la différence.
        layout.dropLast(1).forEach { entry ->
            val label = homeRowLabel(entry)
            add(PinSlot(entry.id, entry.id, after = true) {
                stringResource(Res.string.pin_after, label)
            })
        }
        add(end)
    }
}
