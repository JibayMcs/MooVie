package fr.moovie.tv.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import fr.moovie.tv.data.cast.CastNow
import fr.moovie.tv.data.cast.CastVolume
import fr.moovie.tv.data.remote.RemoteKey
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.cast_mute
import fr.moovie.tv.resources.cast_stop
import fr.moovie.tv.resources.cast_unmute
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieProgressBar
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
 * qu'on puisse faire est de la transporter.
 *
 * ## Quitter coupe la diffusion
 *
 * C'était l'inverse au départ, et l'inverse était un piège. La diffusion vers un
 * Chromecast n'existe **que** tant que ce téléphone est là : c'est lui qui sert
 * les octets, par un relais dont la durée de vie est celle du processus. Laisser
 * l'écran se refermer sur une diffusion qui continue promettait donc quelque
 * chose que rien ne tient — un film qui s'arrête tout seul un peu plus tard,
 * sans que rien ne relie la panne au geste qui l'a causée.
 *
 * Retour et « Arrêter la diffusion » font donc la même chose. Le bouton reste,
 * parce qu'il **nomme** l'effet : un geste de retour sur un téléphone ne dit pas
 * de lui-même qu'il va éteindre la télé.
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
    val volume by session.volume.collectAsState()
    val scope = rememberCoroutineScope()

    // Couper d'abord, quitter ensuite — mais sans attendre : `stopAndClear` rend
    // la main tout de suite et poursuit le STOP sur son propre scope, celui de
    // la composition étant annulé à la ligne suivante.
    val quitte = {
        CastNow.stopAndClear()
        onBack()
    }

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

    // Les touches physiques du téléphone règlent le son de la télé, comme elles
    // le font déjà pour une box Moo-vie. Inutile de les détourner quand le
    // récepteur ne se laisse pas régler : ce serait confisquer le volume du
    // téléphone pour ne rien produire. Voir CastVolume.reglable.
    if (volume.reglable) {
        CaptureVolumeKeys { key ->
            val actuel = session.volume.value
            val pas = actuel.step.coerceAtLeast(PAS_MINIMUM)
            scope.launch {
                when (key) {
                    RemoteKey.VOLUME_UP -> session.setVolume(actuel.level + pas)
                    RemoteKey.VOLUME_DOWN -> session.setVolume(actuel.level - pas)
                    RemoteKey.MUTE -> session.setMuted(!actuel.muted)
                    else -> Unit
                }
            }
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

            CastVolumeRow(
                volume = volume,
                onLevel = { scope.launch { session.setVolume(it) } },
                onToggleMute = { scope.launch { session.setMuted(!volume.muted) } },
            )

            PlayerControlBar(
                isPlaying = status.playing,
                positionMs = affichee,
                durationMs = status.durationMs,
                scrubbing = false,
                showEpisodeButtons = false,
                canGoPrevious = false,
                playFocus = remember { FocusRequester() },
                onBack = quitte,
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
                MoovieButton(onClick = quitte) { Text(stringResource(Res.string.cast_stop)) }
            }
        }
    }
}

/**
 * Le son du récepteur : une bascule et une barre.
 *
 * ## Elle n'apparaît pas toujours
 *
 * Un récepteur qui déclare `controlType: "fixed"` — une sortie HDMI dont le
 * téléviseur garde la main sur le volume — accepte l'ordre et n'en fait rien.
 * Une barre qui ne bouge pas se lit comme une panne de l'application, alors que
 * c'est l'appareil qui a raison. Mieux vaut ne rien montrer.
 *
 * ## Elle suit ce que fait la vraie télécommande
 *
 * Le récepteur émet un `RECEIVER_STATUS` **spontané** dès que quelqu'un touche
 * au son par un autre chemin. La barre n'a donc pas besoin d'être la seule à
 * commander pour rester juste.
 *
 * Pas de `Slider` Material : la maison dessine ses barres avec
 * [MoovieProgressBar] et une zone de saisie plus haute que le trait, exactement
 * comme la barre de progression de la bande-annonce. Viser quatre pixels au
 * doigt est une épreuve.
 */
@Composable
private fun CastVolumeRow(
    volume: CastVolume,
    onLevel: (Double) -> Unit,
    onToggleMute: () -> Unit,
) {
    if (!volume.reglable) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MoovieIconButton(
            onClick = onToggleMute,
            icon = if (volume.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = stringResource(
                if (volume.muted) Res.string.cast_unmute else Res.string.cast_mute,
            ),
            selected = !volume.muted,
        )

        var largeurPx by remember { mutableIntStateOf(0) }
        // Coupé, la barre tombe à zéro : c'est ce qu'on entend. Le niveau, lui,
        // est intact et revient tel quel au rétablissement — voir setMuted.
        val part = if (volume.muted) 0f else volume.level.toFloat().coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                // `weight`, pas `fillMaxWidth` : dans une rangée, celui-ci
                // prendrait toute la largeur du parent et pousserait le
                // pourcentage hors de l'écran.
                .weight(1f)
                .height(28.dp)
                .onSizeChanged { largeurPx = it.width }
                .pointerInput(largeurPx) {
                    detectTapGestures { position ->
                        if (largeurPx > 0) onLevel((position.x / largeurPx).toDouble())
                    }
                }
                .pointerInput(largeurPx) {
                    // Le glissement autant que le clic : régler un volume est un
                    // geste continu, et n'accepter que des appuis obligerait à
                    // viser la valeur du premier coup.
                    detectHorizontalDragGestures { changement, _ ->
                        if (largeurPx > 0) {
                            onLevel((changement.position.x / largeurPx).toDouble().coerceIn(0.0, 1.0))
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                Box(modifier = Modifier.matchParentSize().background(Color(0x33FFFFFF)))
                MoovieProgressBar(
                    progress = part,
                    trackColor = Color.Transparent,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        Text(
            "${(part * 100).toInt()} %",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFBBBBBB),
        )
    }
}

private const val TICK_MS = 1_000L

/** Même pas que le lecteur, pour que le geste soit le même partout. */
private const val PAS_MS = 15_000L

/**
 * Plancher du pas de volume, quand l'appareil en déclare un très fin.
 *
 * Certains récepteurs annoncent un `stepInterval` de 0,02 : cinquante appuis
 * pour traverser l'échelle, là où la bascule d'un téléphone en demande une
 * quinzaine. Le geste paraîtrait cassé bien avant d'être arrivé.
 */
private const val PAS_MINIMUM = 0.05
