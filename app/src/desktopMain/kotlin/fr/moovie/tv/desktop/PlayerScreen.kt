package fr.moovie.tv.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.settings.ScreensaverDelay
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_cancel
import fr.moovie.tv.resources.player_next_in
import fr.moovie.tv.resources.player_update_chip
import fr.moovie.tv.ui.components.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieScreensaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

/**
 * Réception des frames libVLC (RV32/BGRA) dans un bitmap Skia réutilisé,
 * exposé à Compose : la vidéo est dessinée comme n'importe quelle image →
 * les contrôles sont de vrais overlays (pas d'interop Swing, pas de
 * clignotement au masquage) et le plein écran est natif Compose.
 */
private class ComposeVideoSurface {
    private var bitmap: Bitmap? = null
    private var pixels: ByteArray = ByteArray(0)
    private var info: ImageInfo? = null

    /** Wrapper Compose de la frame courante (recréé à chaque frame, peu coûteux). */
    var image: ImageBitmap? by mutableStateOf(null)
        private set

    /** Compteur de frames : lu par la composition pour forcer le redessin. */
    var frameTick: Int by mutableStateOf(0)
        private set

    val bufferFormatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            info = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            pixels = ByteArray(sourceWidth * sourceHeight * 4)
            bitmap = Bitmap()
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
    }

    val renderCallback = RenderCallback { _, nativeBuffers, bufferFormat ->
        val bmp = bitmap ?: return@RenderCallback
        val fmt = info ?: return@RenderCallback
        val src = nativeBuffers[0]
        src.rewind()
        src.get(pixels, 0, minOf(src.remaining(), pixels.size))
        bmp.installPixels(fmt, pixels, bufferFormat.width * 4)
        image = bmp.asComposeImageBitmap()
        frameTick++
    }
}

/**
 * Lecteur desktop via libVLC (VLC doit être installé sur la machine).
 * Frames rendues dans Compose (callbacks vlcj) → overlay de contrôles
 * auto-masqué sans clignotement. Clavier : Espace = pause, ←/→ = ±10 s,
 * ↑/↓ = volume, M = muet, F/F11 = plein écran, Échap = retour (géré fenêtre).
 * Clic = pause/lecture, double-clic = plein écran.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DesktopPlayerScreen(
    streamUrl: String,
    headers: Map<String, String>,
    mediaKey: String,
    subtitles: Map<String, String>,
    title: String,
    subtitle: String,
    nextSeason: Int,
    nextEpisode: Int,
    /** Version disponible, ou null : affiche une pastille discrète en lecture. */
    updateVersion: String? = null,
    onUpdateSelected: () -> Unit = {},
    /** Affiche du titre, utilisée par l'écran de veille. */
    posterUrl: String = "",
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onNextEpisode: (season: Int, episode: Int) -> Unit,
) {
    val progress = remember { WatchProgressRepository() }
    val settings = remember { SettingsRepository() }
    val autoPlayNext by settings.autoPlayNext.collectAsState(initial = true)
    val screensaverDelay by settings.screensaverDelay.collectAsState(initial = ScreensaverDelay.M15)
    val saveScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val surface = remember { ComposeVideoSurface() }

    // libVLC absent → écran d'erreur plutôt qu'un crash.
    val factory = remember {
        runCatching {
            NativeDiscovery().discover()
            MediaPlayerFactory()
        }.onFailure {
            // Trace en console : indispensable pour diagnostiquer une libVLC
            // absente/incompatible (snap, version, JNA…).
            it.printStackTrace()
        }.getOrNull()
    }
    if (factory == null) {
        MissingVlc(onBack)
        return
    }

    val player = remember {
        factory.mediaPlayers().newEmbeddedMediaPlayer().apply {
            videoSurface().set(
                CallbackVideoSurface(
                    surface.bufferFormatCallback,
                    surface.renderCallback,
                    true,
                    VideoSurfaceAdapters.getVideoSurfaceAdapter(),
                ),
            )
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var timeMs by remember { mutableStateOf(0L) }
    var lengthMs by remember { mutableStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf(false) }
    // Position en cours de drag sur la barre (null = pas de drag).
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var volume by remember { mutableStateOf(100) }
    var muted by remember { mutableStateOf(false) }
    // Auto-masquage : contrôles visibles à l'activité (souris/clavier), repliés
    // après 3 s d'inactivité pendant la lecture, toujours visibles en pause.
    var controlsVisible by remember { mutableStateOf(true) }
    var activityTick by remember { mutableStateOf(0) }
    // Secondes restantes du décompte d'enchaînement (null = pas de décompte).
    var autoNextSeconds by remember(streamUrl) { mutableStateOf<Int?>(null) }
    // Fenêtre d'apparition initiale de la pastille de mise à jour.
    var updateChipFresh by remember(updateVersion) { mutableStateOf(updateVersion != null) }
    // Écran de veille affiché (lecture en pause depuis le délai choisi).
    var screensaverOn by remember { mutableStateOf(false) }

    fun showControls() {
        controlsVisible = true
        activityTick++
    }

    /** Interrompt l'enchaînement : `finished` à false annule la coroutine. */
    fun cancelAutoNext() {
        autoNextSeconds = null
        finished = false
    }

    fun togglePause() {
        player.controls().setPause(player.status().isPlaying)
    }

    fun seekBy(deltaMs: Long) {
        val len = player.status().length()
        val target = (player.status().time() + deltaMs).coerceIn(0L, if (len > 0) len else Long.MAX_VALUE)
        player.controls().setTime(target)
    }

    fun setVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        muted = false
        player.audio().isMute = false
        player.audio().setVolume(volume)
    }

    fun toggleMute() {
        muted = !muted
        player.audio().isMute = muted
    }

    // Démarrage : headers (UA/Referer, ce que les extracteurs exigent), reprise
    // à la position sauvée, sous-titres externes ajoutés une fois le média prêt.
    LaunchedEffect(streamUrl) {
        val resumeAt = if (mediaKey.isNotBlank()) progress.position(mediaKey) else 0L
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
                if (resumeAt > 0) mediaPlayer.controls().setTime(resumeAt)
                // Aligne libVLC sur l'état du slider (volume plein, non muet).
                mediaPlayer.audio().isMute = false
                mediaPlayer.audio().setVolume(100)
                subtitles.forEach { (_, url) ->
                    if (url.isNotBlank()) {
                        mediaPlayer.media().addSlave(MediaSlaveType.SUBTITLE, url, false)
                    }
                }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                finished = true
            }

            override fun error(mediaPlayer: MediaPlayer) {
                playError = true
            }
        })

        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        val options = buildList {
            add(":network-caching=1500")
            header("User-Agent")?.let { add(":http-user-agent=$it") }
            header("Referer")?.let { add(":http-referrer=$it") }
        }
        player.media().play(streamUrl, *options.toTypedArray())
    }

    // Suivi de l'état du lecteur + sauvegarde périodique de la position (~5 s).
    LaunchedEffect(mediaKey) {
        var ticks = 0
        while (true) {
            delay(500)
            isPlaying = player.status().isPlaying
            timeMs = player.status().time().coerceAtLeast(0)
            lengthMs = player.status().length().coerceAtLeast(0)
            ticks++
            if (ticks % 10 == 0 && mediaKey.isNotBlank() && isPlaying) {
                progress.save(mediaKey, timeMs, lengthMs)
            }
        }
    }

    // Minuterie d'auto-masquage : relancée à chaque activité ; en pause les
    // contrôles restent affichés.
    LaunchedEffect(activityTick, isPlaying) {
        if (!isPlaying) {
            controlsVisible = true
            return@LaunchedEffect
        }
        delay(3000)
        controlsVisible = false
    }

    // Fin de lecture : marque terminé (sort de « Reprendre », bascule en « vu »)
    // puis enchaîne l'épisode suivant après un décompte annulable. Sans suite
    // (film, fin de série) ou auto-play coupé : simple retour.
    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        if (mediaKey.isNotBlank() && lengthMs > 0) progress.save(mediaKey, lengthMs, lengthMs)
        val hasNext = nextSeason > 0 && nextEpisode > 0
        if (!hasNext || !autoPlayNext) {
            onBack()
            return@LaunchedEffect
        }
        showControls()
        var remaining = AUTO_NEXT_SECONDS
        while (remaining > 0) {
            autoNextSeconds = remaining
            delay(1000)
            remaining--
        }
        autoNextSeconds = null
        onNextEpisode(nextSeason, nextEpisode)
    }

    // Sortie : sauvegarde la position puis libère le lecteur.
    DisposableEffect(Unit) {
        onDispose {
            val t = runCatching { player.status().time() }.getOrDefault(0L)
            val d = runCatching { player.status().length() }.getOrDefault(0L)
            if (mediaKey.isNotBlank() && t > 0) {
                saveScope.launch { progress.save(mediaKey, t, d) }
            }
            player.release()
            factory.release()
        }
    }

    LaunchedEffect(updateVersion) {
        if (updateVersion == null) return@LaunchedEffect
        updateChipFresh = true
        delay(UPDATE_CHIP_MS)
        updateChipFresh = false
    }
    val showUpdateChip = updateVersion != null && (updateChipFresh || controlsVisible)

    // Veille : uniquement sur une lecture en pause, repoussée à chaque activité.
    LaunchedEffect(isPlaying, screensaverDelay, activityTick) {
        screensaverOn = false
        if (isPlaying || screensaverDelay == ScreensaverDelay.NEVER) return@LaunchedEffect
        delay(screensaverDelay.minutes * 60_000L)
        screensaverOn = true
    }

    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Tout mouvement de souris réaffiche les contrôles.
            .onPointerEvent(PointerEventType.Move) { showControls() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Rend le focus clavier au lecteur (un clic ailleurs a pu
                        // le perdre) pour que Espace/F/flèches restent fiables.
                        runCatching { keyFocus.requestFocus() }
                        showControls()
                        togglePause()
                    },
                    onDoubleTap = { onToggleFullscreen() },
                )
            }
            .focusRequester(keyFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Décompte en cours : la 1re touche l'interrompt, quelle qu'elle soit.
                if (autoNextSeconds != null) {
                    cancelAutoNext()
                    return@onPreviewKeyEvent true
                }
                showControls()
                when (event.key) {
                    Key.Spacebar -> { togglePause(); true }
                    Key.DirectionLeft -> { seekBy(-10_000); true }
                    Key.DirectionRight -> { seekBy(+10_000); true }
                    Key.DirectionUp -> { setVolume(volume + 5); true }
                    Key.DirectionDown -> { setVolume(volume - 5); true }
                    Key.M -> { toggleMute(); true }
                    Key.F, Key.F11 -> { onToggleFullscreen(); true }
                    else -> false
                }
            },
    ) {
        // Frame vidéo courante, letterboxée. key(frameTick) force le redessin
        // à chaque frame reçue de libVLC.
        key(surface.frameTick) {
            surface.image?.let { img ->
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Titre du média, affiché avec les contrôles (miroir du lecteur TV).
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                if (title.isNotBlank()) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFBBBBBB),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Pastille de mise à jour : quelques secondes à la détection, puis avec
        // les contrôles. Cliquer met en pause et laisse la bannière habituelle
        // demander confirmation.
        if (showUpdateChip && updateVersion != null) {
            MoovieButton(
                onClick = {
                    player.controls().setPause(true)
                    onUpdateSelected()
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
            ) {
                Text(stringResource(Res.string.player_update_chip, updateVersion))
            }
        }

        // Décompte d'enchaînement, au-dessus de la barre de contrôles.
        autoNextSeconds?.let { seconds ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 96.dp)
                    .background(Color(0xF21E1E1E))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(Res.string.player_next_in, seconds),
                    style = MaterialTheme.typography.titleMedium,
                )
                MoovieButton(onClick = { cancelAutoNext() }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            }
        }

        // Overlay de contrôles en bas, auto-masqué — la vidéo ne bouge pas.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xCC101010))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MoovieIconButton(
                        onClick = onBack,
                        icon = Icons.Default.ArrowBack,
                        contentDescription = "Retour",
                    )
                    MoovieIconButton(
                        onClick = { togglePause() },
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Lecture",
                    )
                    Text(
                        formatTime(timeMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFCCCCCC),
                    )
                    Slider(
                        value = scrubbing ?: if (lengthMs > 0) timeMs.toFloat() / lengthMs else 0f,
                        onValueChange = { scrubbing = it },
                        onValueChangeFinished = {
                            scrubbing?.let { player.controls().setTime((it * lengthMs).toLong()) }
                            scrubbing = null
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MOOVIE_ACCENT,
                            activeTrackColor = MOOVIE_ACCENT,
                            inactiveTrackColor = Color(0xFF333333),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatTime(lengthMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFCCCCCC),
                    )
                    // Volume : muet + glissière (↑/↓ = ±5, M = muet au clavier).
                    MoovieIconButton(
                        onClick = { toggleMute() },
                        icon = if (muted || volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (muted) "Rétablir le son" else "Couper le son",
                    )
                    Slider(
                        value = if (muted) 0f else volume / 100f,
                        onValueChange = { setVolume((it * 100).toInt()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFCCCCCC),
                            activeTrackColor = Color(0xFFCCCCCC),
                            inactiveTrackColor = Color(0xFF333333),
                        ),
                        modifier = Modifier.width(120.dp),
                    )
                    MoovieIconButton(
                        onClick = onToggleFullscreen,
                        icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Quitter le plein écran" else "Plein écran",
                    )
                }
                if (playError) {
                    Text(
                        "Lecture impossible — essaie un autre lecteur depuis le panneau Sources.",
                        color = Color(0xFFE0A0A0),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Écran de veille : recouvre tout, y compris la barre de contrôles qui
        // reste ouverte en pause. Toute touche ou mouvement de souris le referme
        // et rend la main au lecteur, toujours en pause.
        if (screensaverOn) {
            MoovieScreensaver(
                posterUrl = posterUrl.takeIf { it.isNotBlank() },
                onDismiss = {
                    screensaverOn = false
                    showControls()
                },
            )
        }
    }
}

@Composable
private fun MissingVlc(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("VLC introuvable", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Le lecteur desktop s'appuie sur libVLC. Installe VLC puis relance :\n" +
                "Linux : sudo apt install vlc — Windows/macOS : videolan.org",
            color = Color(0xFF9A9A9A),
        )
        MoovieButton(onClick = onBack) { Text("Retour") }
    }
}

/** Durée du décompte avant l'enchaînement de l'épisode suivant. */
private const val AUTO_NEXT_SECONDS = 10

/** Durée d'affichage spontané de la pastille de mise à jour. */
private const val UPDATE_CHIP_MS = 10_000L

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
