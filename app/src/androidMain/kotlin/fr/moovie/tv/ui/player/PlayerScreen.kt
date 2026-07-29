package fr.moovie.tv.ui.player

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import fr.moovie.tv.data.intro.IntroDbRepository
import fr.moovie.tv.data.intro.IntroMedia
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.common_cancel
import fr.moovie.tv.resources.player_next_in
import fr.moovie.tv.resources.player_audio
import fr.moovie.tv.resources.player_next_episode
import fr.moovie.tv.resources.player_pause
import fr.moovie.tv.resources.player_play
import fr.moovie.tv.resources.player_prev_episode
import fr.moovie.tv.resources.player_progress
import fr.moovie.tv.resources.player_scrub_hint
import fr.moovie.tv.resources.player_seek_back
import fr.moovie.tv.resources.player_seek_forward
import fr.moovie.tv.resources.player_settings
import fr.moovie.tv.resources.player_skip_intro
import fr.moovie.tv.resources.player_skip_outro
import fr.moovie.tv.resources.player_speed
import fr.moovie.tv.resources.player_subtitles
import fr.moovie.tv.resources.player_subtitles_off
import fr.moovie.tv.resources.player_update_chip
import fr.moovie.tv.ui.components.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

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

/** Pas de saut des boutons ±, et du mode réglage de la barre de progression. */
private const val SEEK_STEP_MS = 15_000L
private const val SCRUB_STEP_MS = 10_000L

/** Vitesses proposées dans le menu Paramètres. */
private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** Durée du décompte avant l'enchaînement de l'épisode suivant. */
private const val AUTO_NEXT_SECONDS = 10

/** Durée d'affichage spontané de la pastille de mise à jour. */
private const val UPDATE_CHIP_MS = 10_000L

private enum class PlayerDialog { SUBTITLES, SETTINGS }

/**
 * Lecteur natif Media3/ExoPlayer.
 *
 * Les contrôles sont un **overlay Compose** et non le contrôleur intégré de
 * [PlayerView] : sur Android TV, ce dernier vit dans la hiérarchie de vues
 * Android et le D-pad n'arrivait à focaliser qu'un seul de ses boutons. Ici
 * chaque commande est un focusable Compose ordinaire, donc la navigation à la
 * télécommande fonctionne nativement — y compris le bouton « Passer l'intro /
 * le générique », qui appartient au même arbre de focus.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    headers: Map<String, String> = emptyMap(),
    mediaKey: String = "",
    subtitles: Map<String, String> = emptyMap(),
    title: String = "",
    subtitle: String = "",
    nextSeason: Int = 0,
    nextEpisode: Int = 0,
    /** Version disponible, ou null : affiche une pastille discrète en lecture. */
    updateVersion: String? = null,
    onUpdateSelected: () -> Unit = {},
    onBack: () -> Unit,
    onNextEpisode: (tmdbId: Int, season: Int, episode: Int) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val progress = remember { WatchProgressRepository() }
    val introRepo = remember { IntroDbRepository() }
    val settings = remember { SettingsRepository() }
    val skipEnabled by settings.skipIntroOutro.collectAsStateWithLifecycle(initialValue = true)
    val autoPlayNext by settings.autoPlayNext.collectAsStateWithLifecycle(initialValue = true)

    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            // Garde CPU + Wi-Fi éveillés pendant la lecture (évite les coupures
            // de flux quand le réseau se met en veille).
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    // Sans MediaSession, Android route les touches média (play/pause/seek de la
    // télécommande) vers la dernière session système au lieu de l'app.
    val mediaSession = remember { MediaSession.Builder(context, player).build() }

    // Surface vidéo seule : le contrôleur intégré est remplacé par l'overlay
    // Compose, et la vue ne doit pas capter le focus du D-pad.
    val playerView = remember {
        PlayerView(context).apply {
            this.player = player
            useController = false
            isFocusable = false
            isFocusableInTouchMode = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    // ── État observé par l'UI ────────────────────────────────────────────────
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var speed by remember { mutableStateOf(1f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var activityTick by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    // Position en cours de réglage à la télécommande (null = pas en mode réglage).
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    // Fin de lecture atteinte : déclenche l'enchaînement (remis à false = annulé).
    var ended by remember(streamUrl) { mutableStateOf(false) }
    // Secondes restantes du décompte (null = pas de décompte en cours).
    var autoNextSeconds by remember(streamUrl) { mutableStateOf<Int?>(null) }
    // Fenêtre d'apparition initiale de la pastille de mise à jour.
    var updateChipFresh by remember(updateVersion) { mutableStateOf(updateVersion != null) }

    fun wake() {
        controlsVisible = true
        activityTick++
    }

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

    // Suivi de la position pour la barre de progression (~2 rafraîchissements/s).
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.let { if (it == C.TIME_UNSET) 0L else it }
            delay(500)
        }
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

    // Garde l'écran allumé tant que ça joue (anti-veille de l'Android TV) et
    // tient l'UI au courant de l'état de lecture / des pistes disponibles.
    DisposableEffect(player, playerView) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                playerView.keepScreenOn = playing
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) ended = true
            }
        }
        isPlaying = player.isPlaying
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

    // Auto-masquage : la barre reste affichée en pause et tant qu'un menu est
    // ouvert, sinon elle se replie après 4 s sans appui.
    LaunchedEffect(activityTick, isPlaying, dialog) {
        if (!isPlaying || dialog != null) {
            controlsVisible = true
            return@LaunchedEffect
        }
        delay(4000)
        controlsVisible = false
    }

    val wakeFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val autoNextFocus = remember { FocusRequester() }

    // Pastille de mise à jour : visible quelques secondes à la détection, puis
    // seulement avec la barre de contrôles — on prévient sans imposer un
    // élément fixe à l'écran pendant tout le film.
    LaunchedEffect(updateVersion) {
        if (updateVersion == null) return@LaunchedEffect
        updateChipFresh = true
        delay(UPDATE_CHIP_MS)
        updateChipFresh = false
    }
    val showUpdateChip = updateVersion != null && (updateChipFresh || controlsVisible)

    /** Interrompt l'enchaînement : `ended` à false annule la coroutine en cours. */
    fun cancelAutoNext() {
        autoNextSeconds = null
        ended = false
    }

    // Fin d'un média : on le marque terminé (il sort de « Reprendre » et passe
    // en vu), puis on enchaîne l'épisode suivant après un décompte annulable.
    // Sans suite (film, fin de série) ou auto-play coupé : simple retour.
    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        val duration = player.duration.let { if (it == C.TIME_UNSET) 0L else it }
        if (mediaKey.isNotBlank() && duration > 0) progress.save(mediaKey, duration, duration)

        val hasNext = pid != null && pid.isTv && nextSeason > 0 && nextEpisode > 0
        if (!hasNext || !autoPlayNext) {
            onBack()
            return@LaunchedEffect
        }
        controlsVisible = true
        var remaining = AUTO_NEXT_SECONDS
        while (remaining > 0) {
            autoNextSeconds = remaining
            delay(1000)
            remaining--
        }
        autoNextSeconds = null
        onNextEpisode(pid.tmdbId, nextSeason, nextEpisode)
    }

    // Le focus suit l'état : barre visible → bouton Lecture ; barre masquée avec
    // un bouton « Passer » → ce bouton ; sinon la couche de réveil plein écran.
    LaunchedEffect(controlsVisible, activeSkip != null, autoNextSeconds != null) {
        delay(60)
        runCatching {
            when {
                autoNextSeconds != null -> autoNextFocus.requestFocus()
                controlsVisible -> playFocus.requestFocus()
                activeSkip != null -> skipFocus.requestFocus()
                else -> wakeFocus.requestFocus()
            }
        }
    }

    fun togglePause() {
        player.playWhenReady = !player.playWhenReady
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Décompte en cours : la 1re touche l'interrompt, quelle qu'elle
                // soit — on ne subit pas l'enchaînement en réveillant les contrôles.
                if (autoNextSeconds != null) {
                    cancelAutoNext()
                    return@onPreviewKeyEvent true
                }
                if (!controlsVisible) {
                    // Barre masquée : la 1re touche ne fait que réveiller les
                    // contrôles (play/pause agit quand même), elle n'est pas
                    // transmise aux boutons — sinon on déclencherait une action
                    // invisible pour l'utilisateur.
                    if (event.key == Key.MediaPlayPause || event.key == Key.MediaPlay ||
                        event.key == Key.MediaPause || event.key == Key.Spacebar
                    ) {
                        togglePause()
                    }
                    wake()
                    return@onPreviewKeyEvent true
                }
                // Barre visible : toute touche relance le minuteur, la navigation
                // reste gérée par les boutons focalisés.
                activityTick++
                false
            },
    ) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { playerView })

        // Couche de réveil : cible de focus quand la barre est masquée. Elle
        // cesse d'être focalisable dès que la barre s'affiche, sinon la
        // recherche de focus du D-pad retomberait dessus au lieu des boutons.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(wakeFocus)
                .focusable(enabled = !controlsVisible),
        )

        // Titre du média, visible avec les contrôles (souris ou télécommande).
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
                    .padding(horizontal = 48.dp, vertical = 32.dp),
            ) {
                if (title.isNotBlank()) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFBBBBBB),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Bouton « Passer l'intro / le générique », au-dessus de la barre pour
        // rester atteignable au D-pad quand les contrôles sont affichés.
        val skip = activeSkip
        if (skip != null) {
            MoovieButton(
                onClick = { doSkip() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = if (controlsVisible) 128.dp else 48.dp)
                    .focusRequester(skipFocus),
            ) {
                Text(
                    if (skip == SkipKind.INTRO) {
                        stringResource(Res.string.player_skip_intro)
                    } else {
                        stringResource(Res.string.player_skip_outro)
                    },
                )
            }
        }

        // Pastille de mise à jour, en haut à droite (le titre occupe la gauche).
        // Cliquer met en pause et laisse la bannière habituelle demander
        // confirmation : rien ne s'installe sur une simple erreur de visée.
        if (showUpdateChip && updateVersion != null) {
            MoovieButton(
                onClick = {
                    player.playWhenReady = false
                    onUpdateSelected()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 48.dp, top = 32.dp),
            ) {
                Icon(
                    Icons.Default.SystemUpdateAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.player_update_chip, updateVersion))
            }
        }

        // Décompte d'enchaînement, au-dessus de la barre de contrôles.
        autoNextSeconds?.let { seconds ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 128.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF21E1E1E))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(Res.string.player_next_in, seconds),
                    style = MaterialTheme.typography.titleMedium,
                )
                MoovieButton(
                    onClick = { cancelAutoNext() },
                    modifier = Modifier.focusRequester(autoNextFocus),
                ) {
                    Text(stringResource(Res.string.common_cancel))
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlBar(
                isPlaying = isPlaying,
                positionMs = scrubTarget ?: positionMs,
                durationMs = durationMs,
                scrubbing = scrubTarget != null,
                isTv = pid?.isTv == true,
                canGoPrevious = pid != null && pid.isTv && pid.episode > 1,
                playFocus = playFocus,
                onBack = onBack,
                onTogglePause = { togglePause() },
                onSeekBack = { player.seekBack() },
                onSeekForward = { player.seekForward() },
                onToggleScrub = {
                    val target = scrubTarget
                    if (target != null) {
                        player.seekTo(target)
                        scrubTarget = null
                    } else {
                        scrubTarget = positionMs
                    }
                },
                onNudgeScrub = { delta ->
                    val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
                    scrubTarget = ((scrubTarget ?: positionMs) + delta).coerceIn(0L, max)
                },
                onCancelScrub = { scrubTarget = null },
                onPreviousEpisode = { pid?.let { onNextEpisode(it.tmdbId, it.season, it.episode - 1) } },
                onNextEpisode = { pid?.let { onNextEpisode(it.tmdbId, it.season, it.episode + 1) } },
                onOpenSubtitles = { dialog = PlayerDialog.SUBTITLES },
                onOpenSettings = { dialog = PlayerDialog.SETTINGS },
                onActivity = { activityTick++ },
            )
        }

        when (dialog) {
            PlayerDialog.SUBTITLES -> OptionsDialog(
                sections = listOf(subtitleSection(tracks) { group, index ->
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, group == null)
                        .apply {
                            if (group != null) {
                                setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                            }
                        }
                        .build()
                    dialog = null
                }),
                onDismiss = { dialog = null },
            )
            PlayerDialog.SETTINGS -> OptionsDialog(
                sections = listOf(
                    speedSection(speed) {
                        speed = it
                        player.setPlaybackSpeed(it)
                        dialog = null
                    },
                    audioSection(tracks) { group, index ->
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                            .build()
                        dialog = null
                    },
                ).filter { it.options.isNotEmpty() },
                onDismiss = { dialog = null },
            )
            null -> Unit
        }
    }
}

@Composable
private fun ControlBar(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    scrubbing: Boolean,
    isTv: Boolean,
    canGoPrevious: Boolean,
    playFocus: FocusRequester,
    onBack: () -> Unit,
    onTogglePause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleScrub: () -> Unit,
    onNudgeScrub: (Long) -> Unit,
    onCancelScrub: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenSettings: () -> Unit,
    onActivity: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MoovieIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
            )
            MoovieIconButton(
                onClick = onTogglePause,
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) {
                    stringResource(Res.string.player_pause)
                } else {
                    stringResource(Res.string.player_play)
                },
                modifier = Modifier.focusRequester(playFocus),
            )
            MoovieIconButton(
                onClick = onSeekBack,
                icon = Icons.Default.FastRewind,
                contentDescription = stringResource(Res.string.player_seek_back),
            )
            MoovieIconButton(
                onClick = onSeekForward,
                icon = Icons.Default.FastForward,
                contentDescription = stringResource(Res.string.player_seek_forward),
            )
            if (isTv) {
                MoovieIconButton(
                    onClick = onPreviousEpisode,
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(Res.string.player_prev_episode),
                    enabled = canGoPrevious,
                )
                MoovieIconButton(
                    onClick = onNextEpisode,
                    icon = Icons.Default.SkipNext,
                    contentDescription = stringResource(Res.string.player_next_episode),
                )
            }
            MoovieIconButton(
                onClick = onOpenSubtitles,
                icon = Icons.Default.ClosedCaption,
                contentDescription = stringResource(Res.string.player_subtitles),
            )
            MoovieIconButton(
                onClick = onOpenSettings,
                icon = Icons.Default.Settings,
                contentDescription = stringResource(Res.string.player_settings),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                formatTime(positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = if (scrubbing) MOOVIE_ACCENT else Color(0xFFCCCCCC),
            )
            SeekBar(
                fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                scrubbing = scrubbing,
                onToggleScrub = onToggleScrub,
                onNudgeScrub = onNudgeScrub,
                onCancelScrub = onCancelScrub,
                onActivity = onActivity,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatTime(durationMs),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFCCCCCC),
            )
        }
    }
}

/**
 * Barre de progression pilotable au D-pad. OK entre/sort du « mode réglage » :
 * hors de ce mode, ←/→ ne sont pas consommées et servent à passer d'un bouton à
 * l'autre — sinon le focus resterait piégé sur la barre, sans issue possible à
 * la télécommande.
 */
@Composable
private fun SeekBar(
    fraction: Float,
    scrubbing: Boolean,
    onToggleScrub: () -> Unit,
    onNudgeScrub: (Long) -> Unit,
    onCancelScrub: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    // Quitter la barre annule un réglage non validé.
    LaunchedEffect(focused) { if (!focused) onCancelScrub() }

    val barHeight = if (focused || scrubbing) 10.dp else 6.dp
    Box(
        modifier = modifier
            .height(32.dp)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                onActivity()
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onToggleScrub()
                        true
                    }
                    Key.DirectionLeft -> if (scrubbing) {
                        onNudgeScrub(-SCRUB_STEP_MS)
                        true
                    } else {
                        false
                    }
                    Key.DirectionRight -> if (scrubbing) {
                        onNudgeScrub(SCRUB_STEP_MS)
                        true
                    } else {
                        false
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(if (focused) Color(0x66FFFFFF) else Color(0x40FFFFFF)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(barHeight)
                .clip(CircleShape)
                .background(if (scrubbing) Color.White else MOOVIE_ACCENT),
        )
        if (scrubbing) {
            Text(
                stringResource(Res.string.player_scrub_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFBBBBBB),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (focused) {
            Text(
                stringResource(Res.string.player_progress),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x00000000),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private data class PlayerOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

private data class OptionSection(val title: String, val options: List<PlayerOption>)

@Composable
private fun subtitleSection(tracks: Tracks, onSelect: (Tracks.Group?, Int) -> Unit): OptionSection {
    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val anySelected = groups.any { g -> (0 until g.length).any { g.isTrackSelected(it) } }
    val options = buildList {
        add(PlayerOption(stringResource(Res.string.player_subtitles_off), !anySelected) { onSelect(null, 0) })
        groups.forEach { group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                add(
                    PlayerOption(trackLabel(group, i), group.isTrackSelected(i)) { onSelect(group, i) },
                )
            }
        }
    }
    return OptionSection(stringResource(Res.string.player_subtitles), options)
}

@Composable
private fun audioSection(tracks: Tracks, onSelect: (Tracks.Group, Int) -> Unit): OptionSection {
    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val options = buildList {
        groups.forEach { group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                add(PlayerOption(trackLabel(group, i), group.isTrackSelected(i)) { onSelect(group, i) })
            }
        }
    }
    // Une seule piste = rien à choisir, la section est masquée par l'appelant.
    return OptionSection(stringResource(Res.string.player_audio), if (options.size > 1) options else emptyList())
}

@Composable
private fun speedSection(current: Float, onSelect: (Float) -> Unit): OptionSection =
    OptionSection(
        stringResource(Res.string.player_speed),
        SPEEDS.map { value ->
            PlayerOption("×%.2f".format(value).trimEnd('0').trimEnd('.', ','), value == current) { onSelect(value) }
        },
    )

private fun trackLabel(group: Tracks.Group, index: Int): String {
    val format = group.getTrackFormat(index)
    return format.label
        ?: format.language?.takeIf { it.isNotBlank() && it != "und" }
        ?: "#${index + 1}"
}

/** Une ligne du menu : intitulé de section ou choix sélectionnable. */
private sealed interface DialogRow {
    data class Header(val title: String) : DialogRow
    data class Item(val option: PlayerOption) : DialogRow
}

/** Menu du lecteur (sous-titres, vitesse, piste audio) pilotable au D-pad. */
@Composable
private fun OptionsDialog(sections: List<OptionSection>, onDismiss: () -> Unit) {
    val firstOption = remember { FocusRequester() }
    val rows = buildList {
        sections.forEach { section ->
            add(DialogRow.Header(section.title))
            section.options.forEach { add(DialogRow.Item(it)) }
        }
    }
    val firstItemIndex = rows.indexOfFirst { it is DialogRow.Item }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF5161616))
                .padding(24.dp),
        ) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(rows) { index, row ->
                    when (row) {
                        is DialogRow.Header -> Text(
                            row.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MOOVIE_ACCENT,
                        )
                        is DialogRow.Item -> MoovieButton(
                            onClick = row.option.onSelect,
                            selected = row.option.selected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (index == firstItemIndex) {
                                        Modifier.focusRequester(firstOption)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Text(row.option.label)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstOption.requestFocus() } }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
