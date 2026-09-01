package fr.moovie.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_close
import fr.moovie.tv.resources.player_pause
import fr.moovie.tv.resources.player_play
import fr.moovie.tv.resources.trailer_mute
import fr.moovie.tv.resources.trailer_unmute
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieProgressBar
import fr.moovie.tv.ui.player.MooviePlayerController
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Les contrôles de la bande-annonce, posés sur l'aperçu qui joue déjà.
 *
 * ## Pourquoi ils vivent ici et non dans l'écran du lecteur
 *
 * Il n'y a **qu'un seul lecteur** pour une bande-annonce : celui du fond de la
 * fiche. Appuyer sur le bouton ne crée rien et ne change pas d'écran — il
 * découvre ces contrôles par-dessus une lecture déjà en cours. On garde donc la
 * position, on évite un second flux vers le même CDN (ce qui nous avait valu
 * des 403), et l'ouverture est instantanée puisqu'il n'y a rien à charger.
 *
 * ## Pourquoi si peu de boutons
 *
 * La chrome du lecteur principal sert un film : pistes audio, sous-titres,
 * vitesse, veille, téléchargement, épisode suivant. Sur deux minutes de
 * promotion, presque tout cela n'a pas d'objet, et la réutiliser aurait couplé
 * la fiche à mille trois cents lignes bâties autour de la reprise de lecture.
 * Lecture/pause, position, son, fermeture : c'est ce qu'on fait d'une
 * bande-annonce.
 *
 * Les contrôles restent **visibles tant qu'on est là**. Un lecteur de film les
 * replie parce qu'ils masqueraient l'image pendant deux heures ; ici on vient
 * de demander explicitement à les voir, et les faire disparaître obligerait à
 * les rappeler sans cesse.
 */
@Composable
fun TrailerControls(
    controller: MooviePlayerController?,
    title: String,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sondage plutôt qu'observation : ni ExoPlayer ni libVLC ne poussent leur
    // position, et c'est déjà ainsi que le lecteur principal la suit.
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedMs by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                positionMs = it.positionMs()
                durationMs = it.durationMs()
                bufferedMs = it.bufferedMs()
                playing = it.isPlaying
            }
            delay(POLL_MS)
        }
    }

    // Le focus arrive sur Lecture/pause : à la télécommande, c'est l'action
    // qu'on cherche, et sans point d'entrée explicite le focus resterait sur la
    // fiche invisible derrière.
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
            )
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title.isNotBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedMs = bufferedMs,
            onSeek = { controller?.seekTo(it) },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoovieIconButton(
                onClick = { controller?.togglePause() },
                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (playing) Res.string.player_pause else Res.string.player_play,
                ),
                modifier = Modifier.focusRequester(playFocus),
            )
            MoovieIconButton(
                onClick = onToggleMute,
                icon = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = stringResource(
                    if (muted) Res.string.trailer_unmute else Res.string.trailer_mute,
                ),
                selected = !muted,
            )
            Text(
                "${formatClock(positionMs)} / ${formatClock(durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFBBBBBB),
            )
            Box(Modifier.weight(1f))
            MoovieIconButton(
                onClick = onClose,
                icon = Icons.Default.Close,
                contentDescription = stringResource(Res.string.common_close),
            )
        }
    }
}

/**
 * Barre de progression cliquable, avec la portion déjà tamponnée en second
 * plan.
 *
 * La piste de tampon dit **jusqu'où un saut est gratuit** : au-delà, le
 * lecteur doit retélécharger — et sur une bande-annonce googlevideo servie au
 * compte-gouttes, c'est la différence entre un saut instantané et une attente.
 * Sans elle, un seek qui attend ressemble à un seek cassé.
 *
 * La largeur mesurée est indispensable : sans elle on ne sait pas convertir
 * l'abscisse d'un clic en instant du média, et la barre ne serait qu'un témoin.
 */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, bufferedMs: Long, onSeek: (Long) -> Unit) {
    var widthPx by remember { mutableStateOf(0) }
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val buffered = if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Zone de saisie plus haute que le trait : viser trois pixels au
            // doigt ou à la souris est une épreuve, et la barre en fait quatre.
            .height(24.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(durationMs, widthPx) {
                detectTapGestures { offset ->
                    if (durationMs > 0 && widthPx > 0) {
                        onSeek((durationMs * (offset.x / widthPx)).toLong().coerceIn(0, durationMs))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
            Box(modifier = Modifier.matchParentSize().background(Color(0x33FFFFFF)))
            // Le tampon sous l'avancement : un voile plus clair que la piste,
            // moins que la bannière — un repère, pas une seconde barre criarde.
            if (buffered > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(buffered.coerceIn(0f, 1f))
                        .background(Color(0x59FFFFFF)),
                )
            }
            MoovieProgressBar(
                progress = progress,
                trackColor = Color.Transparent,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** `1:47`, ou `1:02:30` au-delà de l'heure — une bande-annonce n'y arrive jamais. */
private fun formatClock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val s = total % 60
    val m = (total / 60) % 60
    val h = total / 3600
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

private const val POLL_MS = 300L
