package fr.moovie.tv.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import fr.moovie.tv.data.cast.CastNow
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.cast_stop
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.player.PlayerControlBar
import fr.moovie.tv.ui.player.PlayerTitleOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Pilote une diffusion Chromecast, sous la forme d'un lecteur.
 *
 * ## La même chrome que le reste
 *
 * [PlayerControlBar] et [PlayerTitleOverlay] viennent du vrai lecteur, comme
 * pour la télécommande au pointeur : la jaquette prend la place du flux, la
 * barre se clique. C'est la troisième surface à s'en servir, et c'est le signe
 * que la découpe était la bonne — elles ne manipulent que des primitives.
 *
 * ## Ce qui diffère d'une télécommande Moo-vie
 *
 * Il n'y a **pas de pavé directionnel**, et il n'y en aura pas : un Chromecast
 * n'a pas de menus à parcourir. Il joue ce qu'on lui a donné, et la seule chose
 * qu'on puisse faire est de la transporter. En revanche il y a un bouton
 * d'arrêt explicite, parce que fermer cet écran ne coupe rien — la diffusion
 * continue, et c'est voulu.
 *
 * ## La position avance entre deux nouvelles
 *
 * Le récepteur envoie un `MEDIA_STATUS` quand il en a envie, pas à cadence
 * fixe. Sans interpolation, la barre resterait figée puis sauterait de plusieurs
 * secondes — le même à-coup que la télécommande corrige déjà de la même façon.
 */
@Composable
fun CastPlayerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val playback by CastNow.playback.collectAsState()
    val encours = playback ?: return

    val session = CastNow.session
    val status by (session?.status ?: return).collectAsState()
    val scope = rememberCoroutineScope()

    /** Position affichée : celle du récepteur, puis l'écoulement du temps. */
    var affichee by remember { mutableLongStateOf(0L) }
    LaunchedEffect(status.positionMs) { affichee = status.positionMs }
    LaunchedEffect(status.playing) {
        if (!status.playing) return@LaunchedEffect
        while (true) {
            delay(TICK_MS)
            affichee += TICK_MS
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (encours.artwork.isNotBlank()) {
            MoovieAsyncImage(
                model = encours.artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        PlayerTitleOverlay(
            title = encours.title,
            subtitle = encours.subtitle,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Le nom de l'appareil, parce qu'on ne voit pas ce qu'il fait : cet
            // écran est la seule chose qui dise où part l'image.
            Text(
                encours.device.name,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFBBBBBB),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            PlayerControlBar(
                isPlaying = status.playing,
                positionMs = affichee,
                durationMs = status.durationMs,
                scrubbing = false,
                showEpisodeButtons = false,
                canGoPrevious = false,
                playFocus = remember { FocusRequester() },
                // Quitter l'écran **ne coupe pas** la diffusion : elle continue
                // sur la télé, et l'arrêt a son propre bouton juste dessous.
                onBack = onBack,
                onTogglePause = { scope.launch { session.playPause() } },
                onSeekBack = { scope.launch { session.seek((affichee - PAS_MS).coerceAtLeast(0)) } },
                onSeekForward = { scope.launch { session.seek(affichee + PAS_MS) } },
                onCommitScrub = {},
                onNudgeScrub = {},
                onPreviousEpisode = {},
                onNextEpisode = {},
                onActivity = {},
                onSeekToFraction = { part ->
                    if (status.durationMs > 0) {
                        val cible = (part * status.durationMs).toLong()
                        affichee = cible
                        scope.launch { session.seek(cible) }
                    }
                },
            )

            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                MoovieButton(
                    onClick = {
                        scope.launch {
                            // Rendre l'écran d'abord, couper le relais ensuite :
                            // l'ordre inverse ferait afficher une erreur de
                            // lecture au récepteur. Voir CastSession.stopPlayback.
                            session.stopPlayback()
                            CastNow.clear()
                            onBack()
                        }
                    },
                ) { Text(stringResource(Res.string.cast_stop)) }
            }
        }
    }
}

private const val TICK_MS = 1_000L

/** Même pas que le lecteur, pour que le geste soit le même partout. */
private const val PAS_MS = 15_000L
