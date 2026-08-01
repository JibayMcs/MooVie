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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import fr.moovie.tv.ui.format.formatNowDateTime
import fr.moovie.tv.data.intro.IntroDbRepository
import fr.moovie.tv.data.intro.IntroMedia
import fr.moovie.tv.data.settings.ScreensaverDelay
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.ui.components.MoovieScreensaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Touches qui, en sortant de l'écran de veille, relancent aussi la lecture.
 * Les flèches ne font que réveiller : on regarde où on en est avant de reprendre.
 */
private val RESUME_KEYS = setOf(
    Key.DirectionCenter,
    Key.Enter,
    Key.NumPadEnter,
    Key.MediaPlayPause,
    Key.MediaPlay,
    Key.Spacebar,
)

/** Touches de validation : OK de la télécommande, Entrée d'un clavier. */
private val CONFIRM_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Lecteur natif Media3/ExoPlayer.
 *
 * Les contrôles sont un **overlay Compose** et non le contrôleur intégré de
 * [PlayerView] : sur Android TV, ce dernier vit dans la hiérarchie de vues
 * Android et le D-pad n'arrivait à focaliser qu'un seul de ses boutons. Ici
 * chaque commande est un focusable Compose ordinaire, donc la navigation à la
 * télécommande fonctionne nativement — y compris le bouton « Passer l'intro /
 * le générique », qui appartient au même arbre de focus.
 *
 * Toute la chrome (barre, menus, titre, pastille, décompte) vient de
 * [fr.moovie.tv.ui.player] partagé : ce fichier ne garde que la surface vidéo,
 * les commandes Media3 via [ExoPlayerController], le focus D-pad et les
 * minuteurs.
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
    /** Affiche du titre, utilisée par l'écran de veille. */
    posterUrl: String = "",
    /** Durée annoncée par TMDB, en minutes (0 = inconnue) — voir [PlayerDurationGuard]. */
    expectedMinutes: Int = 0,
    onBack: () -> Unit,
    onNextEpisode: (tmdbId: Int, season: Int, episode: Int) -> Unit = { _, _, _ -> },
    /** Le flux a cassé en lecture : rend la main à la cascade de sources. */
    onPlaybackFailed: () -> Unit = onBack,
) {
    val context = LocalContext.current
    val progress = remember { WatchProgressRepository() }
    val introRepo = remember { IntroDbRepository() }
    val settings = remember { SettingsRepository() }
    val skipEnabled by settings.skipIntroOutro.collectAsStateWithLifecycle(initialValue = true)
    val clockEnabled by settings.playerClock.collectAsStateWithLifecycle(initialValue = true)

    val autoPlayNext by settings.autoPlayNext.collectAsStateWithLifecycle(initialValue = true)
    val screensaverDelay by settings.screensaverDelay.collectAsStateWithLifecycle(
        initialValue = ScreensaverDelay.M15,
    )

    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setSeekBackIncrementMs(PLAYER_SEEK_STEP_MS)
            .setSeekForwardIncrementMs(PLAYER_SEEK_STEP_MS)
            // Garde CPU + Wi-Fi éveillés pendant la lecture (évite les coupures
            // de flux quand le réseau se met en veille).
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    /** Vue du lecteur exposée à la chrome partagée. */
    val controller = remember { ExoPlayerController(player) }

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
    // Fin du tampon Media3, pour la piste de chargement de la barre.
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var tracks by remember { mutableStateOf(PlayerTracks()) }
    var speed by remember { mutableStateOf(1f) }
    var controlsVisible by remember { mutableStateOf(true) }
    // Horloge du bandeau. Rafraîchie à la minute, et seulement quand les
    // contrôles sont visibles : un film dure deux heures, réveiller la
    // composition pour une horloge cachée ne servirait qu'à chauffer la box.
    var clock by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(clockEnabled, controlsVisible) {
        if (!clockEnabled || !controlsVisible) {
            clock = null
            return@LaunchedEffect
        }
        while (true) {
            val now = System.currentTimeMillis()
            clock = formatNowDateTime(now)
            // Se cale sur le changement de minute plutôt que d'attendre 60 s
            // en aveugle, sinon l'heure affichée traîne jusqu'à une minute.
            delay(60_000 - now % 60_000)
        }
    }
    var activityTick by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<PlayerDialogKind?>(null) }
    // Position en cours de réglage à la télécommande (null = pas en mode réglage).
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    // Fin de lecture atteinte : déclenche l'enchaînement (remis à false = annulé).
    var ended by remember(streamUrl) { mutableStateOf(false) }
    // Secondes restantes du décompte (null = pas de décompte en cours).
    var autoNextSeconds by remember(streamUrl) { mutableStateOf<Int?>(null) }
    // Fenêtre d'apparition initiale de la pastille de mise à jour.
    var updateChipFresh by remember(updateVersion) { mutableStateOf(updateVersion != null) }
    // Écran de veille affiché (lecture en pause depuis le délai choisi).
    var screensaverOn by remember { mutableStateOf(false) }
    // Appui en cours qui a servi à sortir de la veille : sa fin doit être avalée.
    var swallowUntilRelease by remember { mutableStateOf(false) }

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
                .setMimeType(
                    if (url.endsWith(".srt", true)) {
                        MimeTypes.APPLICATION_SUBRIP
                    } else {
                        MimeTypes.TEXT_VTT
                    },
                )
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
            positionMs = controller.positionMs()
            durationMs = controller.durationMs()
            bufferedMs = controller.bufferedMs()
            delay(500)
        }
    }

    // Scope volontairement détaché de la composition : la comptabilité de fin
    // part au moment précis où l'on quitte le lecteur, et un
    // `rememberCoroutineScope` serait annulé avant que DataStore ait écrit.
    val exitScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    /**
     * Marque le média terminé : sortie de « Reprendre la lecture » et bascule
     * en « vu ». Un seul point de passage pour les deux façons de finir un
     * média — fin atteinte et générique passé — sinon la seconde oublie ce que
     * la première fait, ce qui laissait l'épisode dans la reprise alors qu'on
     * venait d'en sauter le générique.
     */
    fun markFinished() {
        val duration = controller.durationMs()
        if (mediaKey.isNotBlank() && duration > 0) {
            exitScope.launch { progress.save(mediaKey, duration, duration) }
        }
    }

    // Sauvegarde périodique de la position (résolution ~5 s pour la reprise).
    LaunchedEffect(mediaKey) {
        if (mediaKey.isBlank()) return@LaunchedEffect
        while (true) {
            delay(5000)
            if (controller.isPlaying) {
                progress.save(mediaKey, controller.positionMs(), controller.durationMs())
            }
        }
    }

    // Flux nettement plus court que le média annoncé (logo, bande-annonce) :
    // on emprunte le chemin d'échec habituel, la cascade passe à la suivante.
    PlayerDurationGuard(
        controller = controller,
        mediaId = streamUrl,
        expectedMinutes = expectedMinutes,
        onTooShort = onPlaybackFailed,
    )

    // ── Intro / générique (TheIntroDB) ───────────────────────────────────────
    val pid = remember(mediaKey) { parseMediaKey(mediaKey) }
    var media by remember(streamUrl) { mutableStateOf<IntroMedia?>(null) }
    var activeSkip by remember { mutableStateOf<SkipKind?>(null) }

    // Récupère les segments une fois la durée connue (meilleur choix de version).
    LaunchedEffect(streamUrl, skipEnabled, pid) {
        if (!skipEnabled || pid == null) return@LaunchedEffect
        repeat(20) {
            if (controller.durationMs() > 0) return@repeat
            delay(500)
        }
        media = introRepo.fetch(
            pid.tmdbId,
            pid.isTv,
            pid.season,
            pid.episode,
            controller.durationMs(),
        )
    }

    // Détermine le segment actif selon la position courante.
    LaunchedEffect(media) {
        val m = media
        if (m == null) {
            activeSkip = null
            return@LaunchedEffect
        }
        val intro = m.intro.firstOrNull()
        val credits = m.credits.firstOrNull()
        while (true) {
            val pos = controller.positionMs()
            activeSkip = when {
                intro?.endMs != null && pos >= (intro.startMs ?: 0L) && pos <= intro.endMs ->
                    SkipKind.INTRO
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
                tracks = controller.tracks()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) ended = true
            }

            // Le flux a cassé une fois ouvert (manifeste vide, segments en 403,
            // codec refusé) : la sonde ne valide qu'un accès au premier octet.
            // On rend la main à la cascade plutôt que de laisser un écran noir.
            override fun onPlayerError(error: PlaybackException) {
                onPlaybackFailed()
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
        delay(PLAYER_UPDATE_CHIP_MS)
        updateChipFresh = false
    }
    val showUpdateChip = updateVersion != null && (updateChipFresh || controlsVisible)

    // Veille : uniquement sur une lecture en pause, et repoussée à chaque appui
    // (activityTick est incrémenté par le gestionnaire de touches racine).
    LaunchedEffect(isPlaying, screensaverDelay, activityTick) {
        screensaverOn = false
        if (isPlaying || screensaverDelay == ScreensaverDelay.NEVER) return@LaunchedEffect
        delay(screensaverDelay.minutes * 60_000L)
        screensaverOn = true
    }

    // Garde l'écran allumé pendant la veille, sinon la TV s'éteindrait avant
    // même qu'elle apparaisse. Borné : une pause oubliée ne doit pas laisser la
    // dalle allumée toute la nuit.
    LaunchedEffect(screensaverOn) {
        if (!screensaverOn) {
            // Sortie de veille : la couche disparaît et le focus retombe sur le
            // premier bouton de la barre, c'est-à-dire Retour. On le ramène sur
            // Lecture, sinon l'appui suivant quitte le film.
            if (controlsVisible) {
                delay(60)
                runCatching { playFocus.requestFocus() }
            }
            return@LaunchedEffect
        }
        playerView.keepScreenOn = true
        delay(PLAYER_SCREENSAVER_AWAKE_MS)
        playerView.keepScreenOn = false
    }

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
        markFinished()

        val hasNext = pid != null && pid.isTv && nextSeason > 0 && nextEpisode > 0
        if (!hasNext || !autoPlayNext) {
            onBack()
            return@LaunchedEffect
        }
        controlsVisible = true
        var remaining = PLAYER_AUTO_NEXT_SECONDS
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

    fun doSkip() {
        val m = media ?: return
        when (activeSkip) {
            SkipKind.INTRO -> m.intro.firstOrNull()?.endMs?.let { controller.seekTo(it) }
            SkipKind.CREDITS -> {
                // Passer le générique, c'est avoir fini le média.
                markFinished()
                if (pid != null && pid.isTv) {
                    onNextEpisode(pid.tmdbId, pid.season, pid.episode + 1)
                } else {
                    onBack()
                }
            }
            null -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                // Écran de veille : la touche ne sert qu'à en sortir. OK et les
                // touches de lecture relancent en plus le film — revenir devant
                // sa TV et appuyer sur OK veut dire « reprends », pas « quitte ».
                //
                // Le KeyUp est avalé lui aussi (swallowUntilRelease) : la barre
                // de contrôles est déjà affichée sous la veille, et la fin de
                // l'appui atteignait son bouton focalisé — d'où le retour à la
                // fiche au lieu de la reprise.
                if (screensaverOn || swallowUntilRelease) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (screensaverOn) {
                                screensaverOn = false
                                activityTick++
                                if (event.key in RESUME_KEYS && !controller.isPlaying) {
                                    controller.togglePause()
                                }
                            }
                            swallowUntilRelease = true
                        }
                        else -> swallowUntilRelease = false
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Décompte en cours : la 1re touche l'interrompt, quelle qu'elle
                // soit — on ne subit pas l'enchaînement en réveillant les contrôles.
                if (autoNextSeconds != null) {
                    cancelAutoNext()
                    return@onPreviewKeyEvent true
                }
                if (!controlsVisible) {
                    // Exception au réveil : le bouton « Passer » est fait pour
                    // être utilisé barre masquée, et le focus lui est donné
                    // explicitement dans ce cas. Valider dessus doit donc
                    // l'actionner.
                    //
                    // Sans ce cas, l'appui était consommé pour réveiller la barre,
                    // le focus filait au bouton Lecture — et le KeyUp, lui, n'est
                    // pas consommé : il atterrissait sur ce bouton. Appuyer sur
                    // « Passer l'intro » mettait donc le film en pause sans rien
                    // passer, alors que le focus visuel était bien sur le bouton.
                    // D'où le swallowUntilRelease : la fin de l'appui ne doit
                    // atteindre personne.
                    if (activeSkip != null && event.key in CONFIRM_KEYS) {
                        doSkip()
                        swallowUntilRelease = true
                        return@onPreviewKeyEvent true
                    }
                    // Barre masquée : la 1re touche ne fait que réveiller les
                    // contrôles (play/pause agit quand même), elle n'est pas
                    // transmise aux boutons — sinon on déclencherait une action
                    // invisible pour l'utilisateur.
                    if (event.key == Key.MediaPlayPause || event.key == Key.MediaPlay ||
                        event.key == Key.MediaPause || event.key == Key.Spacebar
                    ) {
                        controller.togglePause()
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
            PlayerTitleOverlay(title = title, subtitle = subtitle, clock = clock)
        }

        // Bouton « Passer l'intro / le générique », au-dessus de la barre pour
        // rester atteignable au D-pad quand les contrôles sont affichés.
        val skip = activeSkip
        if (skip != null) {
            PlayerSkipButton(
                kind = skip,
                onClick = { doSkip() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = if (controlsVisible) 128.dp else 48.dp)
                    .focusRequester(skipFocus),
            )
        }

        // Pastille de mise à jour, en haut à droite (le titre occupe la gauche).
        if (showUpdateChip && updateVersion != null) {
            PlayerUpdateChip(
                version = updateVersion,
                onClick = {
                    controller.pause()
                    onUpdateSelected()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 48.dp, top = 32.dp),
            )
        }

        // Décompte d'enchaînement, au-dessus de la barre de contrôles.
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

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControlBar(
                isPlaying = isPlaying,
                positionMs = scrubTarget ?: positionMs,
                durationMs = durationMs,
                scrubbing = scrubTarget != null,
                bufferedMs = bufferedMs,
                // Intro / générique repérés sur la barre, à la SponsorBlock.
                // Les mêmes bornes que les boutons « Passer » : rien de plus à
                // aller chercher, on cesse juste de les réserver aux boutons.
                segments = remember(media) { media?.toPlayerSegments().orEmpty() },
                showEpisodeButtons = pid?.isTv == true,
                canGoPrevious = pid != null && pid.isTv && pid.episode > 1,
                playFocus = playFocus,
                onBack = onBack,
                onTogglePause = { controller.togglePause() },
                onSeekBack = { controller.seekBy(-PLAYER_SEEK_STEP_MS) },
                onSeekForward = { controller.seekBy(PLAYER_SEEK_STEP_MS) },
                onToggleScrub = {
                    val target = scrubTarget
                    if (target != null) {
                        controller.seekTo(target)
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
                onPreviousEpisode = {
                    pid?.let { onNextEpisode(it.tmdbId, it.season, it.episode - 1) }
                },
                onNextEpisode = {
                    pid?.let { onNextEpisode(it.tmdbId, it.season, it.episode + 1) }
                },
                onOpenSubtitles = { dialog = PlayerDialogKind.SUBTITLES },
                onOpenSettings = { dialog = PlayerDialogKind.SETTINGS },
                onActivity = { activityTick++ },
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
        // reste ouverte en pause. Toute touche le referme et rend la main au
        // lecteur, toujours en pause.
        if (screensaverOn) {
            MoovieScreensaver(
                posterUrl = posterUrl.takeIf { it.isNotBlank() },
                onDismiss = {
                    screensaverOn = false
                    activityTick++
                },
            )
        }
    }
}
