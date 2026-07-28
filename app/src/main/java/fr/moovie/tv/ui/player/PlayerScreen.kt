package fr.moovie.tv.ui.player

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import fr.moovie.tv.data.watch.WatchProgressRepository
import kotlinx.coroutines.delay

/**
 * Lecteur natif Media3/ExoPlayer avec :
 * - reprise de lecture (position sauvegardée par contenu via [mediaKey]),
 * - sous-titres externes fournis par le provider ([subtitles] : langue → URL),
 * - contrôles Media3 avec sélection de pistes (audio/sous-titres) et bouton CC.
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
            .build()
    }

    // Prépare le média (avec sous-titres externes) et reprend à la position sauvée.
    LaunchedEffect(streamUrl) {
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

    // PlayerView mémorisée pour pouvoir lui donner le focus D-pad (sinon les
    // touches télécommande n'atteignent pas les contrôles Media3).
    val playerView = remember {
        PlayerView(context).apply {
            this.player = player
            useController = true
            setShowSubtitleButton(true)
            controllerShowTimeoutMs = 3500
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerView.player = null
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { playerView },
        // update s'exécute après l'attachement → le focus D-pad va bien à la
        // PlayerView, condition pour que la télécommande pilote les contrôles.
        update = { it.requestFocus() },
    )
}
