package fr.moovie.tv.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.moovie.tv.data.remote.PlayRequest
import fr.moovie.tv.data.remote.RemoteClient
import fr.moovie.tv.data.remote.RemotePresence
import fr.moovie.tv.data.remote.RemoteStatus
import fr.moovie.tv.data.remote.RemoteTarget
import fr.moovie.tv.data.remote.RemoteTargetRepository
import fr.moovie.tv.data.sync.SyncSettingsRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_cancel
import fr.moovie.tv.resources.remote_play_busy_body
import fr.moovie.tv.resources.remote_play_busy_confirm
import fr.moovie.tv.resources.remote_play_busy_title
import fr.moovie.tv.resources.remote_play_failed
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.theme.MoovieShape
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Envoyer un titre au téléviseur du salon, depuis le téléphone.
 *
 * ## Ce qui décide de l'existence du bouton
 *
 * [RemotePresence], et non la simple mémoire d'un appairage. Une cible
 * enregistrée survit à un téléviseur débranché ; proposer « lire sur la TV »
 * dans ce cas donnerait un bouton qui ne fait rien, et qui ne dit pas pourquoi —
 * une commande perdue est silencieuse par construction ([RemoteClient]).
 *
 * ## Pourquoi une confirmation, et seulement dans ce cas
 *
 * Quelqu'un regarde peut-être. Envoyer un titre remplace ce qui joue, sans
 * préavis, sur un écran qu'on ne tient pas dans la main : c'est le seul geste de
 * l'application dont les conséquences se produisent dans une autre pièce. On
 * demande donc — mais **uniquement si la TV joue vraiment**, sinon la
 * confirmation devient un réflexe qu'on n'a plus qu'à traverser.
 *
 * L'état joué est déjà connu : le téléphone l'interroge pour son mini-lecteur
 * ([RemoteClient.status]). On le relit au moment du geste plutôt que de se fier
 * au dernier relevé, qui peut avoir une seconde de retard — assez pour demander
 * confirmation d'un remplacement qui n'a plus lieu d'être.
 */
class TvSender internal constructor(
    internal val target: RemoteTarget?,
    private val send: (PlayRequest, Boolean) -> Unit,
) {
    /** Vrai quand un téléviseur répond : c'est la condition d'affichage du bouton. */
    val available: Boolean get() = target != null

    /** Demande la lecture, en passant par la confirmation si la TV est occupée. */
    fun ask(request: PlayRequest) = send(request, false)

    internal fun force(request: PlayRequest) = send(request, true)
}

/**
 * Prépare l'envoi et **rend le composant de confirmation à poser dans l'arbre**.
 *
 * Le dialogue est rendu par [TvSenderDialog] plutôt qu'ici : un `Dialog` posé
 * dans un `remember` se retrouverait dans la composition de l'appelant à un
 * endroit qu'il ne choisit pas, et sa position dans l'arbre décide de ce qu'il
 * recouvre.
 *
 * @param onSent appelé après un envoi accepté. C'est là que l'appelant bascule
 *   sur la télécommande : le geste continue sur l'écran qui montre ce que la TV
 *   fait, plutôt que de laisser le téléphone sur une fiche devenue sans objet.
 */
@Composable
fun rememberTvSender(onSent: () -> Unit): TvSender {
    val repo = remember { RemoteTargetRepository() }
    val sync = remember { SyncSettingsRepository() }
    val target by repo.target.collectAsStateWithLifecycle(initialValue = null)
    val reachable by RemotePresence.found.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var pending by remember { mutableStateOf<PlayRequest?>(null) }
    var busyTitle by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }

    val live = target?.takeIf { reachable }

    val sender = remember(live, onSent) {
        TvSender(live) { request, forced ->
            val destination = live ?: return@TvSender
            scope.launch {
                val client = RemoteClient(destination)
                // Relu au moment du geste : le dernier relevé peut dater d'une
                // seconde, assez pour demander confirmation d'un remplacement
                // qui n'a plus lieu d'être.
                val state = (client.status() as? RemoteStatus.Known)?.state
                val playing = if (forced) null else state?.now
                if (playing != null) {
                    busyTitle = playing.title
                    pending = request
                    return@launch
                }
                pending = null
                // Le téléviseur n'enregistre que si on peut **prouver** que les
                // deux appareils écrivent au même endroit : deux empreintes non
                // vides et identiques. Sinon il n'est qu'un écran, et tout reste
                // ici. Voir PlayRequest.record.
                val mine = runCatching { sync.syncFingerprint() }.getOrDefault("")
                val theirs = state?.syncFingerprint.orEmpty()
                val sameDestination = mine.isNotEmpty() && mine == theirs
                if (client.play(request.copy(record = sameDestination))) {
                    onSent()
                } else {
                    failed = true
                }
            }
        }
    }

    TvSenderDialog(
        request = pending,
        busyTitle = busyTitle,
        failed = failed,
        onDismiss = { pending = null; failed = false },
        onConfirm = { request -> pending = null; sender.force(request) },
    )

    return sender
}

/**
 * La modale de remplacement, et le seul message d'échec de tout le parcours.
 *
 * Un `Dialog` et non une bannière : remplacer ce que quelqu'un regarde est une
 * décision, pas une notification. Elle doit interrompre.
 */
@Composable
private fun TvSenderDialog(
    request: PlayRequest?,
    busyTitle: String,
    failed: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PlayRequest) -> Unit,
) {
    if (request == null && !failed) return

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(MoovieShape)
                .background(Color(0xFF1C1C1C))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (failed) {
                Text(
                    stringResource(Res.string.remote_play_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFE0A0A0),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    MoovieButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
                }
                return@Column
            }

            val pending = request ?: return@Column
            Text(
                stringResource(Res.string.remote_play_busy_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(Res.string.remote_play_busy_body, busyTitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCCCCCC),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                MoovieButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
                MoovieButton(onClick = { onConfirm(pending) }) {
                    Text(stringResource(Res.string.remote_play_busy_confirm))
                }
            }
        }
    }
}
