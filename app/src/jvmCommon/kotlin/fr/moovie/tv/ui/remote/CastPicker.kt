package fr.moovie.tv.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.moovie.tv.data.cast.CastDevice
import fr.moovie.tv.data.remote.RemoteTarget
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.cast_pick_chromecast_help
import fr.moovie.tv.resources.cast_pick_moovie_help
import fr.moovie.tv.resources.cast_pick_title
import fr.moovie.tv.resources.common_cancel
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.stringResource

/** Où l'on peut envoyer un titre. */
sealed interface CastTarget {

    /** Un téléviseur qui fait tourner Moo-vie : il résoudra la source lui-même. */
    data class Moovie(val target: RemoteTarget) : CastTarget

    /** Un Chromecast : c'est **ce téléphone** qui résoudra et servira le flux. */
    data class Chromecast(val device: CastDevice) : CastTarget
}

/**
 * Le choix de la destination, quand il y en a plusieurs.
 *
 * ## Pourquoi une modale, et pourquoi seulement parfois
 *
 * Une seule destination ne se choisit pas : l'appelant part directement, et
 * demander confirmation ferait un écran de plus à traverser pour rien. Le
 * dialogue n'apparaît qu'à partir de deux — c'est la même règle que la modale de
 * remplacement de [TvSender], qui ne s'ouvre que si la TV joue vraiment.
 *
 * ## Les deux ne se valent pas, et ça se dit
 *
 * Envoyer vers une Moo-vie et vers un Chromecast n'ont pas les mêmes
 * conséquences : le téléviseur résout tout seul et le téléphone peut partir,
 * là où un Chromecast oblige ce téléphone à rester allumé et sur le réseau tout
 * le temps du film. Deux lignes d'aide le disent, plutôt que de laisser
 * découvrir la différence quand la lecture s'arrête.
 */
@Composable
fun CastTargetDialog(
    targets: List<CastTarget>,
    onPick: (CastTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    if (targets.size < 2) return

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(MoovieShape)
                .background(Color(0xFF1A1A1F))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(Res.string.cast_pick_title),
                style = MaterialTheme.typography.titleMedium,
            )
            targets.forEach { cible ->
                CastTargetRow(cible) { onPick(cible) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                MoovieButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.common_cancel))
                }
            }
        }
    }
}

@Composable
private fun CastTargetRow(cible: CastTarget, onClick: () -> Unit) {
    val (nom, aide, icone) = when (cible) {
        is CastTarget.Moovie -> Triple(
            cible.target.name,
            stringResource(Res.string.cast_pick_moovie_help),
            Icons.Default.Tv,
        )
        is CastTarget.Chromecast -> Triple(
            cible.device.name,
            stringResource(Res.string.cast_pick_chromecast_help),
            Icons.Default.Cast,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MoovieShape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icone, contentDescription = null, tint = Color(0xFFCCCCCC))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nom, style = MaterialTheme.typography.bodyLarge)
            Text(
                aide,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9A9A9A),
            )
        }
    }
}
