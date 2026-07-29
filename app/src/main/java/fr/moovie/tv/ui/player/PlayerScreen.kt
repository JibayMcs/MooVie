package fr.moovie.tv.ui.player

import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.moovie.tv.R
import fr.moovie.tv.data.intro.IntroDbRepository
import fr.moovie.tv.data.intro.IntroMedia
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.ui.components.MoovieButton
import kotlinx.coroutines.delay

/** Contenu déduit de la mediaKey ("movie:<id>" ou "tv:<id>:s<S>e<E>"). */
private data class PlaybackId(val tmdbId: Int, val isTv: Boolean, val season: Int, val episode: Int)

private fun parseMediaKey(key: String): PlaybackId? {
    val parts = key.split(":")
    return when {
        parts.size >= 2 && parts[0] == "movie" ->
            parts[1].toIntOrNull()?.let { PlaybackId(it, false, 0, 0) }
        parts.size >= 3 && parts[0] == "tv" -> {
            val tmdb = parts[1].toIntOrNull() ?: return null
            val m = Regex("s(\\d+)e(\\d+)").find(parts[2]) ?: return null
            PlaybackId(tmdb, true, m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        else -> null
    }
}

private enum class SkipKind { INTRO, CREDITS }

/**
 * Lecteur natif Media3/ExoPlayer : reprise, sous-titres externes, contrôles
 * pilotables au D-pad, anti-veille, et boutons « Passer l'intro / le générique »
 * (TheIntroDB) — passer le générique enchaîne l'épisode suivant ou revient à
 * l'accueil pour un film.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    headers: Map<String, String> = emptyMap(),
    mediaKey: String = "",
    subtitles: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
    onNextEpisode: (tmdbId: Int, season: Int, episode: Int) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val progress = remember { WatchProgressRepository(context) }
    val introRepo = remember { IntroDbRepository() }
    val settings = remember { SettingsRepository(context) }
    val skipEnabled by settings.skipIntroOutro.collectAsStateWithLifecycle(initialValue = true)

    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setSeekBackIncrementMs(5_000)
            .setSeekForwardIncrementMs(15_000)
            // Garde CPU + Wi-Fi éveillés pendant la lecture (évite les coupures
            // de flux quand le réseau se met en veille).
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    // Sans MediaSession, Android route les touches média (play/pause/seek de la
    // télécommande) vers la dernière session système au lieu de l'app.
    val mediaSession = remember { MediaSession.Builder(context, player).build() }

    // Prépare le média (avec sous-titres externes) et reprend à la position sauvée.
    LaunchedEffect(streamUrl) {
        player.stop()
        player.clearMediaItems()

        val subConfigs = subtitles.mapNotNull { (lang, url) ->
            if (url.isBlank()) return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                .setMimeType(if (url.endsWith(".srt", true)) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                .setLanguage(lang)
                .build()
        }
        val item = MediaItem.Builder()
            .setUri(streamUrl)
            .setSubtitleConfigurations(subConfigs)
            .build()
        player.setMediaItem(item)

        val resumeAt = if (mediaKey.isNotBlank()) progress.position(mediaKey) else 0L
        if (resumeAt > 0) player.seekTo(resumeAt)
        player.prepare()
        player.playWhenReady = true
    }

    // Sauvegarde périodique de la position (résolution ~5 s pour la reprise).
    LaunchedEffect(mediaKey) {
        if (mediaKey.isBlank()) return@LaunchedEffect
        while (true) {
            delay(5000)
            val pos = player.currentPosition
            val dur = player.duration.let { if (it == C.TIME_UNSET) 0L else it }
            if (player.isPlaying) progress.save(mediaKey, pos, dur)
        }
    }

    // ── Intro / générique (TheIntroDB) ───────────────────────────────────────
    val pid = remember(mediaKey) { parseMediaKey(mediaKey) }
    var media by remember(streamUrl) { mutableStateOf<IntroMedia?>(null) }
    var activeSkip by remember { mutableStateOf<SkipKind?>(null) }

    // Récupère les segments une fois la durée connue (meilleur choix de version).
    LaunchedEffect(streamUrl, skipEnabled, pid) {
        if (!skipEnabled || pid == null) return@LaunchedEffect
        var dur = 0L
        repeat(20) {
            val d = player.duration
            if (d != C.TIME_UNSET && d > 0) return@repeat
            delay(500)
        }
        player.duration.let { if (it != C.TIME_UNSET && it > 0) dur = it }
        media = introRepo.fetch(pid.tmdbId, pid.isTv, pid.season, pid.episode, dur)
    }

    // Détermine le segment actif selon la position courante.
    LaunchedEffect(media) {
        val m = media
        if (m == null) { activeSkip = null; return@LaunchedEffect }
        val intro = m.intro.firstOrNull()
        val credits = m.credits.firstOrNull()
        while (true) {
            val pos = player.currentPosition
            activeSkip = when {
                intro?.endMs != null && pos >= (intro.startMs ?: 0L) && pos <= intro.endMs -> SkipKind.INTRO
                credits?.startMs != null && pos >= credits.startMs &&
                    (credits.endMs == null || pos <= credits.endMs) -> SkipKind.CREDITS
                else -> null
            }
            delay(500)
        }
    }

    val dpadFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }

    val playerView = remember {
        PlayerView(context).apply {
            this.player = player
            useController = true
            setShowSubtitleButton(true)
            controllerShowTimeoutMs = 3500
            isFocusable = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    if (visibility != View.VISIBLE) {
                        runCatching { dpadFocus.requestFocus() }
                    }
                },
            )
        }
    }

    // Garde l'écran allumé tant que ça joue (anti-veille de l'Android TV).
    DisposableEffect(player, playerView) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerView.keepScreenOn = isPlaying
            }
        }
        playerView.keepScreenOn = player.isPlaying
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            playerView.keepScreenOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerView.player = null
            mediaSession.release()
            player.release()
        }
    }

    fun doSkip() {
        val m = media ?: return
        when (activeSkip) {
            SkipKind.INTRO -> m.intro.firstOrNull()?.endMs?.let { player.seekTo(it) }
            SkipKind.CREDITS -> {
                if (pid != null && pid.isTv) onNextEpisode(pid.tmdbId, pid.season, pid.episode + 1)
                else onBack()
            }
            null -> Unit
        }
    }

    // Auto-focus du bouton de skip dès qu'un segment devient actif ; le focus
    // revient au lecteur quand le bouton disparaît.
    LaunchedEffect(activeSkip) {
        if (activeSkip != null) {
            delay(50)
            runCatching { skipFocus.requestFocus() }
        } else {
            runCatching { dpadFocus.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(dpadFocus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                        Key.MediaPlayPause, Key.Spacebar,
                        Key.DirectionUp, Key.DirectionDown,
                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (event.key == Key.DirectionCenter || event.key == Key.Enter ||
                                event.key == Key.NumPadEnter || event.key == Key.MediaPlayPause ||
                                event.key == Key.Spacebar
                            ) {
                                player.playWhenReady = !player.playWhenReady
                            }
                            playerView.showController()
                            playerView.requestFocus()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { playerView })
        }

        // Bouton « Passer » en overlay, visible que les contrôles soient affichés
        // ou non. Auto-focalisé ; une flèche rend la main au lecteur.
        val skip = activeSkip
        if (skip != null) {
            MoovieButton(
                onClick = { doSkip() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(48.dp)
                    .focusRequester(skipFocus)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key in DPAD_DIRECTIONS) {
                            runCatching { dpadFocus.requestFocus() }
                            true
                        } else {
                            false
                        }
                    },
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (skip == SkipKind.INTRO) stringResource(R.string.player_skip_intro) else stringResource(R.string.player_skip_outro))
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { dpadFocus.requestFocus() } }
}

private val DPAD_DIRECTIONS = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
)
