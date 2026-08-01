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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import fr.moovie.tv.data.intro.IntroDbRepository
import fr.moovie.tv.data.intro.IntroMedia
import fr.moovie.tv.data.settings.ScreensaverDelay
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_cancel
import fr.moovie.tv.resources.player_buffering
import fr.moovie.tv.resources.player_error
import fr.moovie.tv.resources.player_fullscreen
import fr.moovie.tv.resources.player_fullscreen_exit
import fr.moovie.tv.resources.player_mute
import fr.moovie.tv.resources.player_next_in
import fr.moovie.tv.resources.player_unmute
import fr.moovie.tv.resources.player_update_chip
import fr.moovie.tv.ui.player.PLAYER_AUTO_NEXT_SECONDS
import fr.moovie.tv.ui.player.PLAYER_SEEK_STEP_MS
import fr.moovie.tv.ui.player.PLAYER_UPDATE_CHIP_MS
import fr.moovie.tv.ui.player.PlayerAutoNextCountdown
import fr.moovie.tv.ui.player.PlayerDurationGuard
import fr.moovie.tv.ui.player.PlayerControlBar
import fr.moovie.tv.ui.player.PlayerDialogKind
import fr.moovie.tv.ui.player.PlayerOptionsDialog
import fr.moovie.tv.ui.player.PlayerSkipButton
import fr.moovie.tv.ui.player.SkipKind
import fr.moovie.tv.ui.player.PlayerTitleOverlay
import fr.moovie.tv.ui.player.PlayerTracks
import fr.moovie.tv.ui.player.PlayerUpdateChip
import fr.moovie.tv.ui.player.parseMediaKey
import fr.moovie.tv.ui.player.toPlayerSegments
import fr.moovie.tv.ui.player.audioSection
import fr.moovie.tv.ui.player.speedSection
import fr.moovie.tv.ui.player.subtitleSection
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
import com.sun.jna.NativeLibrary
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
import java.io.File
import java.nio.ByteBuffer

/**
 * Réception des frames libVLC (RV32/BGRA) exposées à Compose : la vidéo est
 * dessinée comme n'importe quelle image → les contrôles sont de vrais overlays
 * (pas d'interop Swing, pas de clignotement au masquage) et le plein écran est
 * natif Compose.
 *
 * **Deux threads se croisent ici** : libVLC produit les frames depuis son thread
 * vidéo pendant que Compose dessine depuis le thread AWT. Un bitmap unique
 * réutilisé entre les deux se faisait réécrire — ou carrément réallouer par un
 * changement de résolution en cours de stream adaptatif (HLS/DASH) — pendant que
 * Skia le copiait pour le dessiner : lecture hors du tampon, SIGSEGV en plein
 * visionnage. D'où les deux invariants ci-dessous, à ne pas casser :
 *
 * 1. le format et son tampon sont remplacés **d'un seul bloc** ([Frame]), jamais
 *    champ par champ : plus de `rowBytes` désaccordé avec l'`ImageInfo` ;
 * 2. chaque frame publiée a **son propre bitmap, figé en immuable** avant d'être
 *    exposée. Skia partage alors ses pixels au lieu de les recopier au dessin, et
 *    surtout plus personne ne les réécrit dans le dos du thread AWT.
 */
private class ComposeVideoSurface {

    /**
     * Format négocié avec libVLC + tampon de réception réutilisé d'une frame à
     * l'autre. Immuable : un changement de résolution crée une nouvelle instance
     * plutôt que de modifier celle que le thread vidéo est en train de lire.
     */
    private class Frame(width: Int, height: Int) {
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        val rowBytes = width * 4
        val pixels = ByteArray(width * height * 4)
    }

    /** Publié par `getBufferFormat`, lu par `renderCallback` — threads libVLC distincts. */
    @Volatile
    private var frame: Frame? = null

    /** Frame courante, immuable : Compose la dessine pendant que libVLC prépare la suivante. */
    var image: ImageBitmap? by mutableStateOf(null)
        private set

    /** Compteur de frames : lu par la composition pour forcer le redessin. */
    var frameTick: Int by mutableStateOf(0)
        private set

    val bufferFormatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            frame = Frame(sourceWidth, sourceHeight)
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
    }

    val renderCallback = RenderCallback { _, nativeBuffers, bufferFormat ->
        val current = frame ?: return@RenderCallback
        // Le format a changé entre la négociation et cette frame : on la jette
        // plutôt que de la lire avec les mauvaises dimensions. La suivante
        // arrivera avec le tampon accordé.
        if (bufferFormat.width != current.info.width ||
            bufferFormat.height != current.info.height
        ) {
            return@RenderCallback
        }

        val src = nativeBuffers[0]
        src.rewind()
        if (src.remaining() < current.pixels.size) return@RenderCallback
        src.get(current.pixels, 0, current.pixels.size)

        // Un bitmap par frame, figé avant publication (voir le contrat plus haut).
        val bmp = Bitmap()
        if (!bmp.installPixels(current.info, current.pixels, current.rowBytes)) {
            return@RenderCallback
        }
        bmp.setImmutable()

        image = bmp.asComposeImageBitmap()
        frameTick++
    }
}

/**
 * Touches qui, en sortant de l'écran de veille, relancent aussi la lecture.
 * Les flèches ne font que réveiller : on regarde où on en est avant de reprendre.
 */
private val RESUME_KEYS = setOf(Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

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
    /** Durée annoncée par TMDB, en minutes (0 = inconnue) — voir [PlayerDurationGuard]. */
    expectedMinutes: Int = 0,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onNextEpisode: (season: Int, episode: Int) -> Unit,
    /** Le flux a cassé en lecture : rend la main à la cascade de sources. */
    onPlaybackFailed: () -> Unit = onBack,
) {
    val progress = remember { WatchProgressRepository() }
    val settings = remember { SettingsRepository() }
    val autoPlayNext by settings.autoPlayNext.collectAsState(initial = true)
    val skipEnabled by settings.skipIntroOutro.collectAsState(initial = true)
    val introRepo = remember { IntroDbRepository() }
    val screensaverDelay by settings.screensaverDelay.collectAsState(initial = ScreensaverDelay.M15)
    val saveScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val surface = remember { ComposeVideoSurface() }

    // libVLC absent → écran d'erreur plutôt qu'un crash.
    val factory = remember {
        runCatching {
            // Depuis l'AppImage, AppRun pose MOOVIE_VLC_HOME sur la libvlc
            // embarquée, et on saute alors la découverte de vlcj : celle-ci
            // trouve le VLC du système et repositionne jna.library.path
            // par-dessus le nôtre. On chargeait ainsi la libvlc de l'hôte avec
            // notre libvlccore et nos plugins — exactement le mélange de
            // versions que l'embarquement doit supprimer.
            val home = System.getenv("MOOVIE_VLC_HOME")?.takeIf { File(it, "libvlc.so").exists() }
            if (home != null) {
                System.setProperty("jna.library.path", home)
                NativeLibrary.addSearchPath("vlc", home)
                NativeLibrary.addSearchPath("vlccore", home)
            } else {
                NativeDiscovery().discover()
            }
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

    /** Vue du lecteur exposée à la chrome partagée. */
    val controller = remember { VlcjPlayerController(player) }

    // Même garde-fou que sur Android TV, et volontairement le même code : un
    // flux nettement plus court que le média annoncé emprunte le chemin d'échec
    // habituel, et la cascade passe à la source suivante.
    PlayerDurationGuard(
        controller = controller,
        mediaId = streamUrl,
        expectedMinutes = expectedMinutes,
        onTooShort = onPlaybackFailed,
    )

    var tracks by remember { mutableStateOf(PlayerTracks()) }
    var speed by remember { mutableStateOf(1f) }
    var dialog by remember { mutableStateOf<PlayerDialogKind?>(null) }
    val playFocus = remember { FocusRequester() }
    val autoNextFocus = remember { FocusRequester() }

    var isPlaying by remember { mutableStateOf(true) }
    var timeMs by remember { mutableStateOf(0L) }
    var lengthMs by remember { mutableStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf(false) }
    // Remplissage du cache réseau (0-100). libVLC 3 n'expose pas de plage
    // tamponnée exploitable sur la barre : on affiche donc l'état de chargement
    // en clair, là où Android TV peut dessiner une vraie piste de tampon.
    var bufferingPercent by remember(streamUrl) { mutableStateOf(100f) }
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
    // Appui en cours qui a servi à sortir de la veille : sa fin doit être avalée.
    var swallowUntilRelease by remember { mutableStateOf(false) }

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
                // Ne JAMAIS rappeler libVLC depuis son propre thread
                // d'événements : il détient un verrou natif pendant le callback,
                // et toute commande émise ici l'interbloque. Symptôme observé :
                // écran noir, aucun son, horloge figée sur la position de
                // reprise, puis gel complet de la fenêtre au premier seek (le
                // thread UI venant s'échouer sur le même verrou). Seuls les
                // titres repris en cours étaient touchés, `setTime` n'étant
                // appelé que si resumeAt > 0.
                saveScope.launch {
                    runCatching {
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
                }
            }

            // Simple écriture d'état Compose : aucun appel natif, donc rien à
            // renvoyer sur le thread de commandes depuis ce callback.
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                bufferingPercent = newCache
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
            val time = player.status().time().coerceAtLeast(0)
            // Une horloge qui avance prouve que le flux coule : libVLC n'émet
            // pas toujours son « 100 % » final, et l'indicateur resterait
            // affiché en pleine lecture.
            if (time > timeMs) bufferingPercent = 100f
            timeMs = time
            lengthMs = player.status().length().coerceAtLeast(0)
            ticks++
            if (ticks % 10 == 0 && mediaKey.isNotBlank() && isPlaying) {
                progress.save(mediaKey, timeMs, lengthMs)
            }
        }
    }

    // ── Intro / générique (TheIntroDB) ───────────────────────────────────────
    // Mêmes segments que sur Android TV, pour les faire apparaître sur la barre
    // de progression. Le desktop n'a pas (encore) les boutons « Passer » : ici
    // l'information est purement visuelle.
    val pid = remember(mediaKey) { parseMediaKey(mediaKey) }
    var media by remember(streamUrl) { mutableStateOf<IntroMedia?>(null) }

    // La durée n'est connue qu'une fois le flux ouvert, et elle sert à l'API à
    // choisir la bonne version du titre : on attend qu'elle arrive.
    LaunchedEffect(streamUrl, skipEnabled, pid) {
        if (!skipEnabled || pid == null) return@LaunchedEffect
        repeat(20) {
            if (player.status().length() > 0) return@repeat
            delay(500)
        }
        media = introRepo.fetch(
            pid.tmdbId,
            pid.isTv,
            pid.season,
            pid.episode,
            player.status().length().coerceAtLeast(0),
        )
    }

    // Segment actif sous la tête de lecture. Dérivé de `timeMs`, déjà rafraîchi
    // toutes les 500 ms par la boucle d'état : inutile d'en ajouter une seconde.
    val activeSkip = remember(media, timeMs) {
        val intro = media?.intro?.firstOrNull()
        val credits = media?.credits?.firstOrNull()
        when {
            intro?.endMs != null && timeMs >= (intro.startMs ?: 0L) && timeMs <= intro.endMs ->
                SkipKind.INTRO
            // Générique sans borne de fin : actif jusqu'au bout du média.
            credits?.startMs != null && timeMs >= credits.startMs &&
                (credits.endMs == null || timeMs <= credits.endMs) -> SkipKind.CREDITS
            else -> null
        }
    }

    /**
     * Saute le segment actif. Passer le générique enchaîne l'épisode suivant
     * quand il y en a un — sinon (film, fin de série) on rend la main à la
     * fiche, exactement comme sur Android TV.
     */
    fun doSkip() {
        when (activeSkip) {
            SkipKind.INTRO -> media?.intro?.firstOrNull()?.endMs?.let { controller.seekTo(it) }
            SkipKind.CREDITS -> if (nextSeason > 0 && nextEpisode > 0) {
                onNextEpisode(nextSeason, nextEpisode)
            } else {
                onBack()
            }
            null -> Unit
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
        var remaining = PLAYER_AUTO_NEXT_SECONDS
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
            // Libération hors du thread UI, et stop() AVANT release() : libVLC
            // appelle notre callback de rendu depuis son thread vidéo, et le
            // relâcher pendant qu'un appel est en vol fige la fenêtre ou tue le
            // process. stop() coupe d'abord ces callbacks. Sur un flux bloqué
            // (écran noir, « buffer deadlock prevented » côté VLC) c'était
            // systématique au clic sur Retour.
            saveScope.launch {
                runCatching { player.controls().stop() }
                runCatching { player.release() }
                runCatching { factory.release() }
                controller.shutdown()
            }
        }
    }

    LaunchedEffect(updateVersion) {
        if (updateVersion == null) return@LaunchedEffect
        updateChipFresh = true
        delay(PLAYER_UPDATE_CHIP_MS)
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
                // Écran de veille : la touche en sort, et Espace / Entrée
                // relancent la lecture — même contrat que sur Android TV. Le
                // relâchement est avalé pour qu'il n'atteigne pas la barre de
                // contrôles restée ouverte sous la veille.
                if (screensaverOn || swallowUntilRelease) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (screensaverOn) {
                                screensaverOn = false
                                activityTick++
                                if (event.key in RESUME_KEYS && !isPlaying) togglePause()
                            }
                            swallowUntilRelease = true
                        }
                        else -> swallowUntilRelease = false
                    }
                    return@onPreviewKeyEvent true
                }
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

        // Titre du média : même overlay que sur Android TV.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerTitleOverlay(title = title, subtitle = subtitle)
        }

        // Mise en mémoire tampon : seul repère de chargement disponible ici,
        // libVLC 3 ne donnant aucune plage tamponnée (voir
        // VlcjPlayerController.bufferedMs). Sur Android TV, c'est une vraie
        // piste dessinée sur la barre de progression.
        if (bufferingPercent < 100f && !playError) {
            Text(
                stringResource(Res.string.player_buffering, bufferingPercent.toInt()),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFDDDDDD),
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }

        if (showUpdateChip && updateVersion != null) {
            PlayerUpdateChip(
                version = updateVersion,
                onClick = {
                    controller.pause()
                    onUpdateSelected()
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 32.dp),
            )
        }

        // Bouton « Passer l'intro / le générique ». Masqué pendant le décompte
        // d'enchaînement, qui occupe le même coin — et à ce moment-là le
        // générique est justement le segment actif.
        val skip = activeSkip
        if (skip != null && autoNextSeconds == null) {
            PlayerSkipButton(
                kind = skip,
                onClick = {
                    showControls()
                    doSkip()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = if (controlsVisible) 128.dp else 48.dp),
            )
        }

        autoNextSeconds?.let { seconds ->
            PlayerAutoNextCountdown(
                seconds = seconds,
                cancelFocus = autoNextFocus,
                onCancel = { cancelAutoNext() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 128.dp),
            )
        }

        // Barre de contrôles : la même que sur TV, avec en plus le volume et le
        // plein écran, qui n'ont pas d'équivalent à la télécommande.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControlBar(
                isPlaying = isPlaying,
                positionMs = timeMs,
                durationMs = lengthMs,
                // Pas de mode scrub au pointeur : le clic repositionne directement.
                scrubbing = false,
                // Intro / générique repérés sur la barre, comme sur Android TV.
                segments = remember(media) { media?.toPlayerSegments().orEmpty() },
                showEpisodeButtons = nextSeason > 0 || nextEpisode > 0,
                canGoPrevious = nextEpisode > 1,
                playFocus = playFocus,
                onBack = onBack,
                onTogglePause = { controller.togglePause() },
                onSeekBack = { controller.seekBy(-PLAYER_SEEK_STEP_MS) },
                onSeekForward = { controller.seekBy(PLAYER_SEEK_STEP_MS) },
                onToggleScrub = {},
                onNudgeScrub = {},
                onCancelScrub = {},
                onPreviousEpisode = {
                    if (nextEpisode > 1) onNextEpisode(nextSeason, nextEpisode - 2)
                },
                onNextEpisode = {
                    if (nextSeason > 0 && nextEpisode > 0) onNextEpisode(nextSeason, nextEpisode)
                },
                onOpenSubtitles = {
                    tracks = controller.tracks()
                    dialog = PlayerDialogKind.SUBTITLES
                },
                onOpenSettings = {
                    tracks = controller.tracks()
                    dialog = PlayerDialogKind.SETTINGS
                },
                onActivity = { showControls() },
                onSeekToFraction = { fraction ->
                    if (lengthMs > 0) controller.seekTo((fraction * lengthMs).toLong())
                },
            ) {
                MoovieIconButton(
                    onClick = { toggleMute() },
                    icon = if (muted || volume == 0) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = if (muted) {
                        stringResource(Res.string.player_unmute)
                    } else {
                        stringResource(Res.string.player_mute)
                    },
                )
                MoovieIconButton(
                    onClick = onToggleFullscreen,
                    icon = if (isFullscreen) {
                        Icons.Default.FullscreenExit
                    } else {
                        Icons.Default.Fullscreen
                    },
                    contentDescription = if (isFullscreen) {
                        stringResource(Res.string.player_fullscreen_exit)
                    } else {
                        stringResource(Res.string.player_fullscreen)
                    },
                )
            }
        }

        // Rendu à la cascade depuis un effet Compose et non depuis error() :
        // ce callback vient du thread d'événements de libVLC, d'où l'on ne
        // touche à rien (deadlock natif déjà rencontré).
        LaunchedEffect(playError) {
            if (playError) onPlaybackFailed()
        }

        if (playError) {
            Text(
                stringResource(Res.string.player_error),
                color = Color(0xFFE0A0A0),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp, start = 32.dp, end = 32.dp),
            )
        }

        when (dialog) {
            PlayerDialogKind.SUBTITLES -> PlayerOptionsDialog(
                sections = listOf(
                    subtitleSection(tracks) { trackId ->
                        controller.selectSubtitle(trackId)
                        tracks = controller.tracks()
                        dialog = null
                    },
                ),
                onDismiss = { dialog = null },
            )
            PlayerDialogKind.SETTINGS -> PlayerOptionsDialog(
                sections = listOf(
                    speedSection(speed) {
                        speed = it
                        controller.setSpeed(it)
                        dialog = null
                    },
                    audioSection(tracks) { trackId ->
                        controller.selectAudio(trackId)
                        tracks = controller.tracks()
                        dialog = null
                    },
                ).filter { it.options.isNotEmpty() },
                onDismiss = { dialog = null },
            )
            null -> Unit
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
