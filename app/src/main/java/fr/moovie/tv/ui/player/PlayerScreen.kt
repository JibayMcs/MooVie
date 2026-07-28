package fr.moovie.tv.ui.player

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import fr.moovie.tv.data.watch.WatchProgressRepository
import kotlinx.coroutines.delay

/**
 * Lecteur natif Media3/ExoPlayer avec :
 * - reprise de lecture (position sauvegardée par contenu via [mediaKey]),
 * - sous-titres externes fournis par le provider ([subtitles] : langue → URL),
 * - contrôles Media3 pilotables à la télécommande (D-pad).
 *
 * Pilotage D-pad : une couche Compose focusable capte la 1re touche pour réveiller
 * les contrôles puis passe le focus à la PlayerView, dont la navigation native
 * (seek, pause, sélection de pistes, CC) prend alors le relais. Quand les contrôles
 * se masquent, le focus revient à la couche Compose pour la prochaine touche.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    headers: Map<String, String> = emptyMap(),
    mediaKey: String = "",
    subtitles: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val progress = remember { WatchProgressRepository(context) }

    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setSeekBackIncrementMs(5_000)
            .setSeekForwardIncrementMs(15_000)
            .build()
    }

    // Sans MediaSession, Android route les touches média (play/pause/seek de la
    // télécommande) vers la dernière session système au lieu de l'app.
    val mediaSession = remember { MediaSession.Builder(context, player).build() }

    // Prépare le média (avec sous-titres externes) et reprend à la position sauvée.
    LaunchedEffect(streamUrl) {
        // Filet anti-flux fantôme : repart d'un lecteur vide même s'il était réutilisé.
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

    val dpadFocus = remember { FocusRequester() }

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
            // Quand les contrôles se masquent, on rend le focus à la couche
            // Compose : la prochaine touche les réveillera à nouveau.
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    if (visibility != View.VISIBLE) {
                        runCatching { dpadFocus.requestFocus() }
                    }
                },
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerView.player = null
            mediaSession.release()
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(dpadFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // La couche Compose n'a le focus que contrôles masqués : on les
                // réveille et on passe la main à la PlayerView (navigation native).
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                    Key.MediaPlayPause, Key.Spacebar,
                    Key.DirectionUp, Key.DirectionDown,
                    Key.DirectionLeft, Key.DirectionRight -> {
                        // OK/Play-Pause : bascule immédiate en plus de réveiller.
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

    LaunchedEffect(Unit) { runCatching { dpadFocus.requestFocus() } }
}
