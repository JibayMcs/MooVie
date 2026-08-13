package fr.moovie.tv.ui.player

import fr.moovie.tv.data.download.DownloadQueue
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.model.PlayableStream
import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import fr.moovie.tv.data.remote.NowPlaying
import fr.moovie.tv.data.remote.RemoteNowPlaying
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
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import fr.moovie.tv.ui.adaptive.isTouchUi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.player_pause
import fr.moovie.tv.resources.player_notification_channel
import fr.moovie.tv.resources.player_pip
import fr.moovie.tv.resources.player_play
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.navigation.AltSource
import fr.moovie.tv.ui.player.QualityChoice
import fr.moovie.tv.ui.player.qualityOptions
import fr.moovie.tv.ui.player.qualitySection
import fr.moovie.tv.ui.player.bestDownloadStream
import fr.moovie.tv.ui.player.resolveAlternative
import fr.moovie.tv.ui.format.formatNowDateTime
import fr.moovie.tv.core.player.matchAudioTrack
import fr.moovie.tv.data.intro.IntroDbRepository
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.data.intro.IntroMedia
import fr.moovie.tv.data.settings.ScreensaverDelay
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.watch.nextUpEntry
import fr.moovie.tv.ui.components.MoovieScreensaver
import fr.moovie.tv.ui.intro.ReportMarkingBanner
import fr.moovie.tv.ui.intro.ReportSegmentViewModel
import fr.moovie.tv.ui.intro.ReportStep
import fr.moovie.tv.ui.intro.reportSegmentSection
import fr.moovie.tv.ui.subtitles.PlayerSubtitlesViewModel
import fr.moovie.tv.ui.subtitles.onlineSubtitleSection
import fr.moovie.tv.ui.subtitles.subtitleFpsSection
import fr.moovie.tv.ui.subtitles.subtitleSyncSection
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
 * Touches qui basculent lecture/pause, **quel que soit l'état de la barre**.
 *
 * Elles n'étaient traitées que barre masquée. Barre visible, l'événement
 * redescendait vers le bouton focalisé — qui n'écoute qu'Entrée, jamais
 * `MediaPlayPause`. Une touche média ne faisait donc rien, et comme la barre
 * reste affichée tant que la lecture est en pause, le premier appui mettait en
 * pause et **plus aucun ne relançait**. Le défaut existait pour une vraie
 * télécommande aussi ; il ne se voyait pas parce qu'on s'y sert d'OK.
 */
private val MEDIA_TOGGLE_KEYS = setOf(
    Key.MediaPlayPause,
    Key.MediaPlay,
    Key.MediaPause,
    Key.Spacebar,
)

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
/**
 * Fraction de l'épisode au-delà de laquelle on précharge les sources du
 * suivant. Assez tard pour que l'épisode soit probablement fini, assez tôt
 * pour que les catalogues aient le temps de répondre.
 */
private const val PREFETCH_AT = 0.80

/**
 * Fenêtre pendant laquelle une erreur de lecture est imputée au rechargement
 * d'un sous-titre plutôt qu'à la source. Assez large pour couvrir une
 * re-préparation réseau, assez courte pour ne pas masquer une vraie panne.
 */
private const val SUBTITLE_RELOAD_GRACE_MS = 8_000L

/**
 * Pas fin de déplacement pendant un signalement. À la seconde : viser la fin
 * d'une intro demande cette précision, et les 15 s des flèches horizontales ne
 * servent qu'à s'en approcher.
 */
private const val REPORT_FINE_STEP_MS = 1_000L

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
    /**
     * Le lien d'embed derrière ce flux, pour proposer le téléchargement depuis
     * le lecteur. Vide = lecture locale ou source inconnue, et le bouton
     * disparaît.
     */
    sourceUrl: String = "",
    hoster: String = "",
    language: String = "",
    /** Autres sources de la même langue, pour le menu « Qualité ». */
    alternatives: List<AltSource> = emptyList(),
    onBack: () -> Unit,
    onNextEpisode: (tmdbId: Int, season: Int, episode: Int) -> Unit = { _, _, _ -> },
    /** Appelé une fois quand l'épisode approche de sa fin (voir PREFETCH_AT). */
    onPrefetchNext: () -> Unit = {},
    /** Le flux a cassé en lecture : rend la main à la cascade de sources. */
    onPlaybackFailed: () -> Unit = onBack,
) {
    val context = LocalContext.current
    // Lu une fois : `isTouchUi` est un CompositionLocal, illisible depuis un
    // LaunchedEffect.
    val touchUi = isTouchUi
    val progress = remember { WatchProgressRepository() }
    val introRepo = remember { IntroDbRepository() }
    val settings = remember { SettingsRepository() }
    val skipEnabled by settings.skipIntroOutro.collectAsStateWithLifecycle(initialValue = true)
    val clockEnabled by settings.playerClock.collectAsStateWithLifecycle(initialValue = true)

    val autoPlayNext by settings.autoPlayNext.collectAsStateWithLifecycle(initialValue = true)
    val screensaverDelay by settings.screensaverDelay.collectAsStateWithLifecycle(
        initialValue = ScreensaverDelay.M15,
    )

    // Hissée hors du lecteur : changer de source, c'est changer d'hébergeur, donc
    // de `Referer`. La fabrique est mutable et ses propriétés s'appliquent aux
    // sources de données créées **ensuite** — les poser avant de monter le
    // nouveau média suffit, sans reconstruire le lecteur.
    val httpFactory = remember {
        DefaultHttpDataSource.Factory().apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
    }
    val player = remember {
        ExoPlayer.Builder(context)
            // `DefaultDataSource` aiguille selon le schéma de l'URI ; `httpFactory`
            // seule ne sait ouvrir que du http(s). Les sous-titres externes vivent
            // en `file://` dans le cache : servis par la fabrique HTTP, ils
            // cassaient sur un ClassCastException (FileURLConnection vers
            // HttpURLConnection), ce que le lecteur signalait comme un flux mort.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)),
            )
            .setSeekBackIncrementMs(PLAYER_SEEK_STEP_MS)
            .setSeekForwardIncrementMs(PLAYER_SEEK_STEP_MS)
            // Garde CPU + Wi-Fi éveillés pendant la lecture (évite les coupures
            // de flux quand le réseau se met en veille).
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                // Aucun sous-titre au démarrage : ExoPlayer active sinon de
                // lui-même une piste dont la langue lui paraît pertinente, et on
                // se retrouve avec des sous-titres sur tous les dialogues sans
                // les avoir demandés. Les afficher reste un choix explicite.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
    }

    /** Vue du lecteur exposée à la chrome partagée. */
    val controller = remember { ExoPlayerController(player) }

    // Instant du dernier chargement de sous-titre externe.
    //
    // Media3 n'accepte une piste externe qu'au montage du média : l'activer
    // impose de repréparer le flux. Si cette re-préparation échoue, l'erreur
    // ressemble à s'y méprendre à un flux mort — et la cascade renvoyait à la
    // fiche pour chercher une autre source, alors que la lecture allait bien une
    // seconde plus tôt. On distingue les deux par le temps écoulé.
    var subtitleReloadAt by remember { mutableStateOf(0L) }


    // Sans MediaSession, Android route les touches média (play/pause/seek de la
    // télécommande) vers la dernière session système au lieu de l'app.
    // Le relais qui ajoute « épisode précédent / suivant » aux commandes vues du
    // système : voir [EpisodePlayer]. C'est **lui** qu'on confie à la session,
    // pas l'ExoPlayer nu — sinon le volet n'offre qu'une flèche gauche.
    val sessionPlayer = remember(player) { EpisodePlayer(player) }
    val mediaSession = remember { MediaSession.Builder(context, sessionPlayer).build() }

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

    /**
     * Vidéo agrandie pour remplir l'écran, au prix d'un rognage.
     *
     * Le ratio de la source ne se négocie pas : un 16:9 sur un écran en 2,23:1
     * laisse forcément des bandes. Les faire disparaître, c'est déborder de la
     * hauteur et perdre un quart de l'image, haut et bas. Un choix, donc, pas un
     * défaut à corriger d'office — et il se fait au pincement, comme partout.
     */
    var zoomToFill by remember { mutableStateOf(false) }
    LaunchedEffect(zoomToFill) {
        playerView.resizeMode = if (zoomToFill) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
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

    // ── Vignette (picture-in-picture) ────────────────────────────────────────
    //
    // Sur téléphone seulement : voir [Pip]. La taille de l'image est lue à
    // l'appel plutôt que suivie dans un état — elle ne sert qu'à construire les
    // paramètres de la vignette, et le lecteur la connaît mieux que nous.
    val enPip by Pip.actif.collectAsStateWithLifecycle()
    // Le bouton de la barre n'a de sens que là où la vignette existe : sur un
    // téléviseur, et sur une version d'Android qui l'ignore, il ne ferait rien.
    val pipDisponible = remember(context, touchUi) { touchUi && Pip.disponible(context) }
    Pip.Register(
        actif = touchUi,
        taille = { player.videoSize },
        enLecture = { controller.isPlaying },
        bascule = { controller.togglePause() },
        libellePause = stringResource(Res.string.player_pause),
        libelleLecture = stringResource(Res.string.player_play),
    )
    // Réaccorde la vignette à chaque changement de lecture : c'est ce qui arme
    // la bascule automatique d'Android 12+ **et** retourne l'icône du bouton.
    LaunchedEffect(isPlaying) {
        (context as? Activity)?.let { Pip.rafraichit(it) }
    }
    // Une boîte de dialogue ou une barre de contrôles dessinées dans 200 dp de
    // large sont illisibles, et la vignette a ses propres commandes.
    LaunchedEffect(enPip) {
        if (enPip) {
            dialog = null
            controlsVisible = false
            screensaverOn = false
        }
    }

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

    // ── Qualité ──────────────────────────────────────────────────────────
    //
    // Deux mécaniques derrière un seul menu : plafonner le flux courant, ce
    // qu'ExoPlayer fait à chaud et sans coupure ; ou changer de source, ce qui
    // impose de remonter un média et de se recaler.
    var quality by remember(streamUrl) { mutableStateOf<QualityChoice>(QualityChoice.Auto) }
    val qualityScope = rememberCoroutineScope()

    fun applyQuality(choice: QualityChoice) {
        when (choice) {
            is QualityChoice.Auto -> {
                player.trackSelectionParameters =
                    player.trackSelectionParameters.buildUpon().clearVideoSizeConstraints().build()
                quality = choice
            }
            is QualityChoice.Height -> {
                // Un plafond, pas un verrou : sous une définition donnée le
                // flux reste adaptatif, et une connexion qui faiblit peut
                // toujours descendre. Verrouiller ferait caler la lecture.
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(Int.MAX_VALUE, choice.height)
                    .build()
                quality = choice
            }
            is QualityChoice.Source -> {
                val alt = alternatives.firstOrNull { it.url == choice.url } ?: return
                qualityScope.launch {
                    val stream = resolveAlternative(alt.url, alt.hoster, language)
                    // Échec : on garde le flux en cours. On ne casse pas une
                    // lecture qui marche pour une qualité qu'on n'a pas eue.
                    if (stream == null || stream.url.isBlank()) return@launch
                    val at = player.currentPosition
                    httpFactory.setDefaultRequestProperties(stream.headers)
                    player.setMediaItem(MediaItem.fromUri(stream.url))
                    player.prepare()
                    if (at > 0) player.seekTo(at)
                    player.playWhenReady = true
                    quality = choice
                }
            }
        }
    }

    // Suivi de la position pour la barre de progression (~2 rafraîchissements/s).
    //
    // La même boucle nourrit le mini-lecteur du téléphone : elle relève déjà
    // tout ce qu'il affiche, et deux minuteurs à cadences voisines auraient fini
    // par se désynchroniser — la barre d'ici et celle de là-bas ne montrant pas
    // la même seconde du même film.
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = controller.positionMs()
            durationMs = controller.durationMs()
            bufferedMs = controller.bufferedMs()
            RemoteNowPlaying.publish(
                NowPlaying(
                    title = title,
                    subtitle = subtitle,
                    artwork = posterUrl,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    playing = controller.isPlaying,
                ),
            )
            delay(500)
        }
    }

    // Le téléphone peut déplacer la lecture, et seulement tant que le lecteur
    // est à l'écran. Le saut est réenvoyé sur ce fil-ci : l'appel arrive d'une
    // socket, et le lecteur n'accepte d'ordres que du fil principal.
    val seekScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        RemoteNowPlaying.attachSeek { target ->
            seekScope.launch { controller.seekTo(target) }
        }
        // Sans cet effacement, le téléphone garderait à l'écran un mini-lecteur
        // figé sur l'épisode qu'on vient de quitter — pire que de n'afficher
        // rien, parce qu'il invite à appuyer sur des boutons qui ne font plus
        // rien.
        onDispose { RemoteNowPlaying.clear() }
    }

    /** Clé de titre (`tv:<id>`) : la préférence audio vaut pour toute la série. */
    val titleKey = remember(mediaKey) {
        parseMediaKey(mediaKey)?.let { if (it.isTv) "tv:${it.tmdbId}" else null }
    }

    // Réapplique la piste audio retenue sur cette série. Le libellé, jamais
    // l'identifiant : celui-ci est propre au flux (voir matchAudioTrack).
    var audioRestored by remember(mediaKey) { mutableStateOf(false) }
    LaunchedEffect(tracks.audio, titleKey) {
        val key = titleKey ?: return@LaunchedEffect
        if (audioRestored || tracks.audio.size < 2) return@LaunchedEffect
        val remembered = progress.audioTrack(key) ?: return@LaunchedEffect
        val target = matchAudioTrack(remembered, tracks.audio.map { it.label }) ?: return@LaunchedEffect
        val track = tracks.audio.firstOrNull { it.label == target } ?: return@LaunchedEffect
        audioRestored = true
        if (!track.selected) {
            controller.selectAudio(track.id)
            tracks = controller.tracks()
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
        // La série doit rester dans « Reprendre la lecture » : l'entrée qu'on
        // vient de terminer en sort, et celle de l'épisode suivant n'existe pas
        // encore. Sans ce repère, finir un épisode faisait disparaître la série
        // du seul rail où l'on va voir où l'on en est.
        if (nextSeason > 0 && nextEpisode > 0) {
            nextUpEntry(mediaKey, title, posterUrl.takeIf { it.isNotBlank() }, nextSeason, nextEpisode)
                ?.let { next -> exitScope.launch { progress.queueNext(next) } }
        }
    }

    // Sauvegarde périodique de la position (résolution ~5 s pour la reprise).
    // La même boucle sert à amorcer le préchargement de l'épisode suivant :
    // inutile d'ajouter une minuterie pour une question qu'on se pose déjà.
    LaunchedEffect(mediaKey) {
        if (mediaKey.isBlank()) return@LaunchedEffect
        var prefetchAsked = false
        while (true) {
            delay(5000)
            if (controller.isPlaying) {
                val position = controller.positionMs()
                val duration = controller.durationMs()
                progress.save(mediaKey, position, duration)

                // Assez tard pour que l'épisode soit probablement fini, assez
                // tôt pour que les catalogues aient le temps de répondre — ils
                // mettent plusieurs secondes, et c'est ce délai qu'on payait
                // jusqu'ici en écran noir après le générique.
                if (!prefetchAsked && duration > 0 && position >= duration * PREFETCH_AT) {
                    prefetchAsked = true
                    onPrefetchNext()
                }
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

    // Commandes dans le volet et sur l'écran verrouillé. Sur téléphone
    // seulement : Android TV n'a pas de volet de notifications, la notification
    // y serait invisible et son seul effet serait d'exister.
    // Les deux commandes d'épisode, posées sur le relais : elles alimentent à la
    // fois les flèches de la notification et celles que le système dessine sur
    // l'écran verrouillé, les secondes ne lisant que la session.
    val versEpisode: ((Int) -> () -> Unit) = { delta ->
        {
            pid?.let {
                // Même règle qu'au bouton de la barre : passer au suivant, c'est
                // en avoir fini avec celui-ci.
                if (delta > 0) markFinished()
                onNextEpisode(it.tmdbId, it.season, it.episode + delta)
            }
            Unit
        }
    }
    LaunchedEffect(sessionPlayer, pid) {
        sessionPlayer.onPrecedent =
            versEpisode(-1).takeIf { pid?.isTv == true && (pid?.episode ?: 0) > 1 }
        sessionPlayer.onSuivant = versEpisode(+1).takeIf { pid?.isTv == true }
    }

    PlayerMediaNotification(
        actif = touchUi,
        player = sessionPlayer,
        session = mediaSession,
        titre = title,
        soustitre = subtitle,
        posterUrl = posterUrl,
        nomCanal = stringResource(Res.string.player_notification_channel),
    )
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

    val reportViewModel: ReportSegmentViewModel = viewModel()
    val reportStep by reportViewModel.step.collectAsStateWithLifecycle()
    val canReport by reportViewModel.canReport.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { reportViewModel.refreshAvailability() }

    // Ce que TheIntroDB ne connaît pas pour cet épisode. `media` à null couvre
    // les deux cas qui se ressemblent de l'extérieur : titre absent de la base,
    // ou réseau muet.
    val introMissing = media?.intro?.isEmpty() ?: true
    val creditsMissing = media?.credits?.isEmpty() ?: true
    LaunchedEffect(pid, media) {
        reportViewModel.bind(pid, controller.durationMs())
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
                // Erreur juste après un chargement de sous-titre : c'est la
                // re-préparation qui a échoué, pas la source. On repart sans le
                // sous-titre plutôt que de sacrifier la lecture en cours. Le
                // compteur est remis à zéro : un second échec, lui, est bien un
                // problème de flux et repart vers la cascade.
                if (System.currentTimeMillis() - subtitleReloadAt < SUBTITLE_RELOAD_GRACE_MS) {
                    subtitleReloadAt = 0L
                    controller.loadExternalSubtitle(null)
                    return
                }
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
    // Version à afficher sur la pastille, ou null s'il n'y a rien à montrer :
    // porter la non-nullité dans la valeur évite de la revérifier plus bas.
    val subsViewModel: PlayerSubtitlesViewModel = viewModel()
    val subsState by subsViewModel.state.collectAsStateWithLifecycle()
    val subsFile by subsViewModel.file.collectAsStateWithLifecycle()
    // Le fichier arrive déjà recalé : le lecteur n'a qu'à le charger.
    //
    // **Jamais sur la valeur initiale.** Un LaunchedEffect se déclenche dès la
    // première composition, donc avec `null` : sans ce garde-fou, chaque
    // ouverture du lecteur repréparait le flux qui venait de démarrer — et un
    // lien d'hébergeur supporte mal d'être redemandé. L'échec remontait alors à
    // `onPlayerError`, qui rend la main à la cascade : retour à la fiche et
    // nouvelle résolution des sources, sans que rien n'ait été demandé.
    var subsApplied by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(subsFile) {
        if (subsFile == null && subsApplied == null) return@LaunchedEffect
        subsApplied = subsFile
        subtitleReloadAt = System.currentTimeMillis()
        controller.loadExternalSubtitle(subsFile)
    }
    DisposableEffect(Unit) { onDispose { subsViewModel.onLeave() } }

    val chipVersion = updateVersion?.takeIf { updateChipFresh || controlsVisible }

    // Veille : uniquement sur une lecture en pause, et repoussée à chaque appui
    // (activityTick est incrémenté par le gestionnaire de touches racine).
    LaunchedEffect(isPlaying, screensaverDelay, activityTick) {
        screensaverOn = false
        // Jamais sur téléphone : le système y éteint l'écran de lui-même, et
        // l'affiche rebondissante n'a de sens que devant une télé qu'on a
        // laissée allumée.
        if (touchUi) return@LaunchedEffect
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
            // ── Pilotage au doigt ────────────────────────────────────────────
            //
            // Toute la chrome de ce lecteur se réveille sur un appui *touche* :
            // sur un téléphone, où il n'y en a aucune, elle était donc
            // inatteignable — ni pause, ni recherche, ni retour. Un simple
            // écouteur tactile suffit à la rendre accessible, et il ne gêne pas
            // la télécommande, qui n'émet pas d'événement de pointeur.
            .then(
                if (!isTouchUi) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            // Appui simple : montre la chrome, ou la range si
                            // elle est déjà là. C'est le geste attendu partout.
                            onTap = {
                                if (screensaverOn) {
                                    screensaverOn = false
                                    activityTick++
                                } else if (autoNextSeconds != null) {
                                    cancelAutoNext()
                                } else if (controlsVisible) {
                                    controlsVisible = false
                                } else {
                                    wake()
                                }
                            },
                            // Double appui : recule ou avance de 15 s selon la
                            // moitié touchée, sans passer par la barre — c'est
                            // le geste que tout lecteur mobile propose.
                            onDoubleTap = { offset ->
                                val backwards = offset.x < size.width / 2f
                                controller.seekBy(
                                    if (backwards) -PLAYER_SEEK_STEP_MS else PLAYER_SEEK_STEP_MS,
                                )
                                wake()
                            },
                        )
                    }
                },
            )
            // Pincement : écarter remplit l'écran, resserrer rend l'image
            // entière. Bloc séparé du précédent — deux détecteurs de gestes ne
            // cohabitent pas dans un même `pointerInput`.
            .then(
                if (!isTouchUi) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            // Seuil large : sous 5 %, c'est le tremblement de
                            // deux doigts posés, pas une intention.
                            if (zoom > 1.05f) zoomToFill = true
                            if (zoom < 0.95f) zoomToFill = false
                        }
                    }
                },
            )
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
                // Capture en cours : OK relève la position, Retour abandonne.
                // Prioritaire sur tout le reste, y compris la mise en pause —
                // pendant qu'on chronomètre, OK ne peut vouloir dire qu'une
                // chose. Le KeyUp est avalé pour qu'il n'atteigne pas la barre
                // au moment où elle réapparaît.
                if (reportStep is ReportStep.Marking) {
                    when (event.key) {
                        in CONFIRM_KEYS -> {
                            reportViewModel.mark(controller.positionMs())
                            // Une intro se relève en deux bornes : tant que la
                            // seconde manque, ouvrir la modale la ferait
                            // apparaître vide par-dessus l'image qu'on est
                            // justement en train de chronométrer.
                            if (reportViewModel.step.value !is ReportStep.Marking) {
                                dialog = PlayerDialogKind.REPORT
                            }
                            swallowUntilRelease = true
                        }
                        Key.Back, Key.Escape -> reportViewModel.cancel()
                        // On doit pouvoir **se déplacer** pendant la capture :
                        // attendre qu'une intro qu'on connaît déjà se termine
                        // est absurde quand on peut aller directement au bon
                        // endroit. Deux granularités, mappées naturellement sur
                        // la croix : horizontal pour approcher, vertical pour
                        // ajuster à la seconde. Le timecode visé est affiché.
                        Key.DirectionLeft -> controller.seekBy(-PLAYER_SEEK_STEP_MS)
                        Key.DirectionRight -> controller.seekBy(PLAYER_SEEK_STEP_MS)
                        Key.DirectionDown -> controller.seekBy(-REPORT_FINE_STEP_MS)
                        Key.DirectionUp -> controller.seekBy(REPORT_FINE_STEP_MS)
                        else -> return@onPreviewKeyEvent true
                    }
                    positionMs = controller.positionMs()
                    return@onPreviewKeyEvent true
                }
                // Une touche média dit ce qu'elle veut, et le dit seule : elle
                // se traite donc **avant** la question de la barre. Elle réveille
                // les contrôles au passage, parce qu'on veut voir ce qu'on vient
                // de faire.
                if (event.key in MEDIA_TOGGLE_KEYS) {
                    controller.togglePause()
                    wake()
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
                    // contrôles, elle n'est pas transmise aux boutons — sinon on
                    // déclencherait une action invisible pour l'utilisateur.
                    //
                    // Une exception, où l'intention ne fait aucun doute : **OK**.
                    // Sur une télécommande de TV, OK devant une image sans
                    // contrôles veut dire « mets en pause » ; obliger à un
                    // premier appui pour révéler la barre puis à un second pour
                    // agir fait payer deux gestes ce que l'utilisateur demandait
                    // au premier. (Les touches média, elles, sont traitées plus
                    // haut — elles n'ont pas à dépendre de la barre.)
                    if (event.key in CONFIRM_KEYS) {
                        controller.togglePause()
                        // OK va donner le focus au bouton Lecture en réveillant
                        // la barre, et le KeyUp — qui n'est pas consommé —
                        // l'atteindrait : la pause serait aussitôt annulée. Même
                        // piège que pour « Passer l'intro » juste au-dessus.
                        swallowUntilRelease = true
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
            visible = controlsVisible && !enPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerTitleOverlay(title = title, subtitle = subtitle, clock = clock)
            ReportMarkingBanner(reportStep, positionMs)
        }

        // Bouton « Passer l'intro / le générique », au-dessus de la barre pour
        // rester atteignable au D-pad quand les contrôles sont affichés.
        val skip = activeSkip
        if (skip != null && !enPip) {
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
        // L'horloge occupe ce même coin : la pastille se pose alors *sous* elle,
        // sinon les deux se superposent et deviennent illisibles.
        if (chipVersion != null && !enPip) {
            PlayerUpdateChip(
                version = chipVersion,
                onClick = {
                    controller.pause()
                    onUpdateSelected()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 48.dp, top = if (clock != null) 76.dp else 32.dp),
            )
        }

        // Décompte d'enchaînement, au-dessus de la barre de contrôles.
        autoNextSeconds?.takeIf { !enPip }?.let { seconds ->
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
            visible = controlsVisible && !enPip,
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
                    pid?.let {
                        // Passer à l'épisode suivant, c'est en avoir fini avec
                        // celui-ci — au même titre que sauter le générique. Sans
                        // ça, les titres absents de TheIntroDB n'avaient aucun
                        // chemin vers « vu » : faute de segment détecté, le
                        // bouton « Passer le générique » n'apparaît jamais et
                        // seul « Suivant » restait, qui ne marquait rien.
                        markFinished()
                        onNextEpisode(it.tmdbId, it.season, it.episode + 1)
                    }
                },
                onOpenSubtitles = { dialog = PlayerDialogKind.SUBTITLES },
                onOpenSettings = { dialog = PlayerDialogKind.SETTINGS },
                onDownload = if (mediaKey.isNotBlank() && sourceUrl.isNotBlank()) {
                    {
                        // On cherche mieux avant de mettre en file : le fichier
                        // se garde, contrairement à la lecture qu'on a lancée
                        // vite. Voir bestDownloadStream — quelques secondes au
                        // plus, et le flux en cours sert de repli.
                        qualityScope.launch {
                        val (dlUrl, dlHoster, dlStream) = bestDownloadStream(
                            playingUrl = sourceUrl,
                            playingHoster = hoster,
                            playingStream = PlayableStream(
                                url = streamUrl,
                                format = StreamFormat.UNKNOWN,
                                headers = headers,
                            ),
                            playingHeight = player.videoHeights().firstOrNull() ?: 0,
                            alternatives = alternatives,
                            language = language,
                            expectedMinutes = expectedMinutes.takeIf { it > 0 },
                        )
                        DownloadQueue.enqueue(
                            Download(
                                key = mediaKey,
                                title = title,
                                subtitle = subtitle,
                                imageUrl = posterUrl.takeIf { it.isNotBlank() },
                                // Déduits de la clé plutôt que laissés vides :
                                // un téléchargement sans titre d'origine ne sait
                                // plus dire de quelle série il vient.
                                tmdbId = parseMediaKey(mediaKey)?.tmdbId ?: 0,
                                isTv = parseMediaKey(mediaKey)?.isTv ?: false,
                                createdAt = System.currentTimeMillis(),
                                sourceUrl = dlUrl,
                                hoster = dlHoster,
                                language = language,
                            ),
                            dlStream,
                        )
                        }
                    }
                } else {
                    null
                },
                mediaKey = mediaKey,
                onReportSegment = if (canReport && (introMissing || creditsMissing)) {
                    {
                        reportViewModel.bind(pid, controller.durationMs())
                        reportViewModel.open(introMissing, creditsMissing)
                        dialog = PlayerDialogKind.REPORT
                    }
                } else {
                    null
                },
                onActivity = { activityTick++ },
                // Au doigt, la barre se manipule directement : toucher place la
                // lecture, glisser la déplace. Le mode « scrub » de la
                // télécommande — OK pour entrer, flèches pour viser, OK pour
                // valider — n'a pas d'équivalent tactile et laissait la barre
                // inerte. Null hors tactile : sur TV il rendrait la barre
                // sensible à un pointeur qui n'existe pas.
                onSeekToFraction = if (!isTouchUi) {
                    null
                } else {
                    { fraction ->
                        if (durationMs > 0) controller.seekTo((fraction * durationMs).toLong())
                    }
                },
                // Passage en vignette au doigt. Le geste d'accueil y mène déjà,
                // mais il faut le savoir : un bouton est ce qui **apprend** que
                // la fonction existe. Il se pose dans le créneau réservé aux
                // commandes de plateforme, la vignette n'existant pas ailleurs.
                trailing = {
                    if (pipDisponible) {
                        MoovieIconButton(
                            onClick = { (context as? Activity)?.let { Pip.entre(it) } },
                            icon = Icons.Default.PictureInPictureAlt,
                            contentDescription = stringResource(Res.string.player_pip),
                        )
                    }
                },
            )
        }

        // Sous-titres en ligne : la recherche ne part qu'à l'ouverture du menu.
        // Elle est gratuite, mais inutile tant que personne ne la demande — et à
        // ce moment-là le flux est prêt, donc sa cadence est connue.
        LaunchedEffect(dialog) {
            if (dialog == PlayerDialogKind.SUBTITLES && subsState.candidates.isEmpty()) {
                subsViewModel.load(mediaKey, title, controller.videoFps())
            }
        }

        when (dialog.takeIf { !enPip }) {
            PlayerDialogKind.SUBTITLES -> PlayerOptionsDialog(
                sections = listOf(
                    subtitleSection(tracks) { trackId ->
                        // Choisir une piste intégrée retire le sous-titre externe :
                        // les deux affichés ensemble se chevauchent.
                        subsViewModel.clear()
                        controller.selectSubtitle(trackId)
                        tracks = controller.tracks()
                        dialog = null
                    },
                    onlineSubtitleSection(subsState) { candidate ->
                        subsViewModel.pick(candidate)
                        dialog = null
                    },
                    // Le réglage garde la modale ouverte : on ajuste par appuis
                    // successifs, la refermer à chaque pas serait intenable.
                    subtitleSyncSection(
                        state = subsState,
                        onNudge = subsViewModel::nudge,
                        onReset = subsViewModel::resetTiming,
                    ),
                    subtitleFpsSection(subsState) { subsViewModel.assumeSubtitleFps(it) },
                ),
                onDismiss = { dialog = null },
            )
            PlayerDialogKind.REPORT -> PlayerOptionsDialog(
                sections = listOf(
                    reportSegmentSection(
                        step = reportStep,
                        onMark = { kind ->
                            // La capture se fait sur l'image : la modale doit
                            // disparaître, sinon on chronomètre à l'aveugle.
                            reportViewModel.startMarking(kind)
                            dialog = null
                            controlsVisible = false
                        },
                        onSend = reportViewModel::send,
                        onRedo = {
                            reportViewModel.open(introMissing, creditsMissing)
                        },
                    ),
                ),
                onDismiss = {
                    dialog = null
                    reportViewModel.cancel()
                },
            )
            PlayerDialogKind.SETTINGS -> PlayerOptionsDialog(
                sections = listOf(
                    speedSection(speed) {
                        speed = it
                        controller.setSpeed(it)
                        dialog = null
                    },
                    qualitySection(
                        qualityOptions(
                            currentHeights = player.videoHeights(),
                            alternatives = alternatives,
                            selected = quality,
                        ),
                    ) { id ->
                        QualityChoice.parse(id)?.let { applyQuality(it) }
                        dialog = null
                    },
                    audioSection(tracks) { trackId ->
                        controller.selectAudio(trackId)
                        tracks = controller.tracks()
                        // Retenu au niveau du *titre* : on choisit une langue
                        // pour une série, pas pour un épisode.
                        titleKey?.let { key ->
                            tracks.audio.firstOrNull { it.id == trackId }?.label?.let { label ->
                                exitScope.launch { progress.setAudioTrack(key, label) }
                            }
                        }
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
        if (screensaverOn && !enPip) {
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

/**
 * Les définitions que le flux courant annonce, de la plus haute à la plus basse.
 *
 * Lues à l'ouverture du menu plutôt que suivies en continu : elles ne changent
 * qu'au montage d'un média, et un écouteur de plus sur le lecteur serait un
 * écouteur de plus à défaire.
 */
private fun ExoPlayer.videoHeights(): List<Int> = runCatching {
    currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).height } }
        .filter { it > 0 }
        .distinct()
        .sortedDescending()
}.getOrDefault(emptyList())
