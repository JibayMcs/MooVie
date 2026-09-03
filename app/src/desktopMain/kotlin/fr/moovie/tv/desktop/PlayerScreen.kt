package fr.moovie.tv.desktop

import fr.moovie.tv.data.net.LocalStreamProxy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import fr.moovie.tv.ui.theme.MoovieShape
import fr.moovie.tv.ui.theme.moovieSurface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.intro.IntroDbRepository
import fr.moovie.tv.data.intro.IntroMedia
import fr.moovie.tv.data.remote.RemoteKey
import fr.moovie.tv.data.remote.remoteVolume
import fr.moovie.tv.data.settings.ScreensaverDelay
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.ui.format.formatNowDateTime
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.watch.nextUpEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.common_cancel
import fr.moovie.tv.resources.player_buffering
import fr.moovie.tv.resources.player_chaining_next
import fr.moovie.tv.resources.player_mpv_help
import fr.moovie.tv.resources.player_mpv_missing
import fr.moovie.tv.resources.player_error
import fr.moovie.tv.resources.player_fullscreen
import fr.moovie.tv.resources.player_fullscreen_exit
import fr.moovie.tv.resources.player_mute
import fr.moovie.tv.resources.player_volume
import fr.moovie.tv.resources.player_volume_value
import fr.moovie.tv.resources.player_next_in
import fr.moovie.tv.resources.player_unmute
import fr.moovie.tv.resources.player_update_chip
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.download.DownloadQueue
import fr.moovie.tv.ui.player.ApplySubtitleStyle
import fr.moovie.tv.ui.player.PLAYER_AUTO_NEXT_SECONDS
import fr.moovie.tv.ui.player.PLAYER_SEEK_STEP_MS
import fr.moovie.tv.ui.player.PLAYER_UPDATE_CHIP_MS
import fr.moovie.tv.ui.player.PlayerAutoNextCountdown
import fr.moovie.tv.ui.player.PlayerDurationGuard
import fr.moovie.tv.ui.player.PlayerControlBar
import fr.moovie.tv.ui.player.PlayerDialogKind
import fr.moovie.tv.ui.subtitles.PlayerSubtitlesViewModel
import fr.moovie.tv.ui.subtitles.onlineSubtitleSection
import fr.moovie.tv.ui.subtitles.subtitleFpsSection
import fr.moovie.tv.ui.subtitles.subtitleSyncSection
import fr.moovie.tv.ui.player.PlayerOptionsDialog
import fr.moovie.tv.ui.player.PlayerSkipButton
import fr.moovie.tv.ui.player.SkipKind
import fr.moovie.tv.ui.intro.ReportMarkingBanner
import fr.moovie.tv.ui.intro.ReportSegmentViewModel
import fr.moovie.tv.ui.intro.ReportStep
import fr.moovie.tv.ui.intro.reportSegmentSection
import fr.moovie.tv.ui.player.PlayerTitleOverlay
import fr.moovie.tv.ui.player.PlayerTracks
import fr.moovie.tv.ui.player.PlayerUpdateChip
import fr.moovie.tv.core.player.matchAudioTrack
import fr.moovie.tv.ui.player.PlayerEpisodesPanel
import fr.moovie.tv.ui.player.parseMediaKey
import fr.moovie.tv.ui.player.toPlayerSegments
import fr.moovie.tv.data.sources.streamHeights
import fr.moovie.tv.ui.navigation.AltSource
import fr.moovie.tv.ui.player.QualityChoice
import fr.moovie.tv.ui.player.qualityOptions
import fr.moovie.tv.ui.player.qualitySection
import fr.moovie.tv.ui.player.bestDownloadStream
import fr.moovie.tv.ui.player.resolveAlternative
import fr.moovie.tv.ui.player.audioSection
import fr.moovie.tv.ui.player.speedSection
import fr.moovie.tv.ui.player.subtitleSection
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieScreensaver
import fr.moovie.tv.desktop.mpv.Libmpv
import fr.moovie.tv.desktop.mpv.MpvEngine
import fr.moovie.tv.desktop.mpv.MpvPlayerController
import fr.moovie.tv.desktop.mpv.MpvVideoSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Fraction de l'épisode au-delà de laquelle on précharge les sources du
 * suivant. Même seuil que sur Android : les deux lecteurs doivent se
 * comporter pareil.
 */
private const val PREFETCH_AT = 0.80


/**
 * Touches qui, en sortant de l'écran de veille, relancent aussi la lecture.
 * Les flèches ne font que réveiller : on regarde où on en est avant de reprendre.
 */
private val RESUME_KEYS = setOf(Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

/**
 * Curseur invisible, posé sur le lecteur quand les contrôles se replient.
 *
 * AWT n'expose aucun « masquer le curseur » : la seule voie portable est de lui
 * donner une image vide. Un pixel entièrement transparent suffit, et le système
 * s'en accommode aussi bien en fenêtré qu'en plein écran.
 *
 * Construit une seule fois : `createCustomCursor` passe par le serveur
 * graphique, et le refaire à chaque repli des contrôles serait payé à chaque
 * lecture.
 */
private val BLANK_CURSOR: PointerIcon by lazy {
    PointerIcon(
        Toolkit.getDefaultToolkit().createCustomCursor(
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            Point(0, 0),
            "moovie-blank",
        ),
    )
}

/**
 * Lecteur desktop via le moteur mpv embarqué.
 * Trames rendues dans Compose (rendu logiciel libmpv) → overlay de contrôles
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
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    /**
     * Vrai pendant que la source de l'épisode suivant se résout.
     *
     * L'enchaînement ne quitte plus le lecteur : il reste à l'écran, sur la
     * dernière image du générique, le temps que les extracteurs répondent et que
     * la sonde de lisibilité tranche. Sans l'annoncer, ces quelques secondes se
     * lisent comme une image figée — c'est-à-dire comme une panne, alors que
     * c'est justement le moment où tout se passe bien.
     */
    chainingNext: Boolean = false,
    onNextEpisode: (season: Int, episode: Int) -> Unit,
    /** Appelé une fois quand l'épisode approche de sa fin (voir PREFETCH_AT). */
    onPrefetchNext: () -> Unit = {},
    /** Le flux a cassé en lecture : rend la main à la cascade de sources. */
    onPlaybackFailed: () -> Unit = onBack,
    /**
     * Déclare ce qu'Échap doit refermer **avant** de quitter le lecteur, ou
     * null quand il n'y a rien à refermer. Même mécanisme que la fiche : la
     * fenêtre est seule à recevoir Échap, et sans cette déclaration le panneau
     * des épisodes restait ouvert pendant qu'on sortait du lecteur.
     */
    onRegisterBack: ((() -> Unit)?) -> Unit = {},
) {
    val progress = remember { WatchProgressRepository() }
    val settings = remember { SettingsRepository() }
    val autoPlayNext by settings.autoPlayNext.collectAsState(initial = true)
    val skipEnabled by settings.skipIntroOutro.collectAsState(initial = true)
    val clockEnabled by settings.playerClock.collectAsState(initial = true)
    val introRepo = remember { IntroDbRepository() }
    val screensaverDelay by settings.screensaverDelay.collectAsState(initial = ScreensaverDelay.M15)
    val saveScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val surface = remember { MpvVideoSurface() }

    // libmpv absent → écran d'erreur plutôt qu'un crash. Cas d'atelier : les
    // paquets de l'application embarquent la bibliothèque.
    if (Libmpv.instance == null) {
        MissingMpv(onBack)
        return
    }

    var finished by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf(false) }

    val moteur = remember {
        MpvEngine(
            surImage = surface::publie,
            surFin = { finished = true },
            surErreur = {
                println("[lecteur] $it")
                playError = true
            },
        )
    }

    /** Vue du lecteur exposée à la chrome partagée. */
    val controller = remember { MpvPlayerController(moteur) }

    ApplySubtitleStyle(controller)

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

    // Tant que ça joue, la machine ne s'endort pas. Regarder une vidéo n'est pas
    // une activité utilisateur pour le système : sans clavier ni souris, le
    // compteur d'inactivité court et Windows finissait par mettre l'ordinateur
    // en veille en pleine lecture. Lié à la lecture et non à l'écran du lecteur,
    // comme le `keepScreenOn` d'Android : en pause, on rend la main.
    KeepAwakeWhile(isPlaying)
    var timeMs by remember { mutableStateOf(0L) }
    var lengthMs by remember { mutableStateOf(0L) }
    // Remplissage du cache pendant une mise en mémoire tampon (0-100). mpv le
    // dit explicitement (`paused-for-cache`), là où libVLC obligeait à le
    // déduire d'une horloge qui avance.
    var bufferingPercent by remember(streamUrl) { mutableStateOf(100f) }
    // Affiché seulement si le remplissage dure : un tampon d'une demi-seconde
    // après un seek n'a pas à faire clignoter un « 0 % » à l'écran.
    var bufferingVisible by remember(streamUrl) { mutableStateOf(false) }
    // Ce flux a-t-il jamais produit une image ? Voir l'effet de fin : c'est la
    // seule façon de distinguer un épisode terminé d'un flux mort-né.
    var everPlayed by remember(streamUrl) { mutableStateOf(false) }
    // Relais des en-têtes, vivant le temps du flux. Il tient un socket et un
    // vivier de fils : le laisser derrière soi en fuirait un par lecture.
    //
    // Retenu pour toute la vie de l'écran et **non par flux** : c'est lui qu'il
    // faut arrêter en changeant d'épisode, et un état recréé par flux serait
    // reparti de `null` en perdant la référence au relais à arrêter. L'arrêt se
    // fait donc à la main, à l'ouverture du flux suivant.
    var proxy by remember { mutableStateOf<LocalStreamProxy?>(null) }
    // Clé de média du flux en cours, pour savoir sous quel épisode ranger
    // l'avancement au moment d'en ouvrir un autre — voir l'effet d'ouverture.
    val mediaKeySortant = remember { mutableStateOf(mediaKey) }
    // Position en cours de drag sur la barre (null = pas de drag).
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    // 0 à 200 : libVLC amplifie au-delà de 100 %, ce qui évite d'aller toucher
    // le mélangeur du système pour un film dont la piste est trop basse.
    var volume by remember { mutableStateOf(100) }
    var muted by remember { mutableStateOf(false) }
    // Auto-masquage : contrôles visibles à l'activité (souris/clavier), repliés
    // après 3 s d'inactivité pendant la lecture, toujours visibles en pause.
    var controlsVisible by remember { mutableStateOf(true) }
    var activityTick by remember { mutableStateOf(0) }

    // Horloge du bandeau, comme sur Android. Le réglage existait des deux côtés
    // et n'était lu que par un seul : la case se cochait sur desktop sans que
    // rien n'apparaisse jamais, ce qui se lit comme un réglage cassé plutôt que
    // comme un oubli de portage.
    //
    // Rafraîchie à la minute, et seulement contrôles visibles : un film dure
    // deux heures, réveiller la composition pour une horloge cachée ne sert à
    // rien. Le calage sur le changement de minute évite que l'heure affichée
    // traîne jusqu'à soixante secondes.
    var clock by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(clockEnabled, controlsVisible) {
        if (!clockEnabled || !controlsVisible) {
            clock = null
            return@LaunchedEffect
        }
        while (true) {
            val now = System.currentTimeMillis()
            clock = formatNowDateTime(now)
            delay(60_000 - now % 60_000)
        }
    }

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
        controller.togglePause()
    }

    // Passe par le contrôleur, jamais par libVLC en direct : lui seul compense
    // le retard des seeks HLS, et lui seul parle à libVLC hors du fil d'UI.
    fun seekBy(deltaMs: Long) {
        controller.seekBy(deltaMs)
    }

    fun setVolume(value: Int) {
        volume = value.coerceIn(0, MAX_VOLUME)
        // Un volume qu'on règle est un volume qu'on veut entendre : le remonter
        // depuis zéro doit suffire, sans avoir à décoiffer le muet à côté.
        muted = false
        moteur.coupeSon(false)
        moteur.regleVolumePourcent(volume)
    }

    fun toggleMute() {
        muted = !muted
        moteur.coupeSon(muted)
    }

    // Le volume, offert à la télécommande virtuelle du téléphone. Le même pas
    // que les flèches du clavier : c'est le même réglage, et deux cadences pour
    // un seul curseur se remarqueraient dès qu'on passe de l'un à l'autre.
    //
    // La lambda est posée une fois et lit l'état à chaque appel — `volume` est
    // un délégué sur un `mutableStateOf` retenu, donc elle voit la valeur
    // courante et non celle de la première composition.
    DisposableEffect(Unit) {
        remoteVolume = { key ->
            when (key) {
                RemoteKey.VOLUME_UP -> setVolume(volume + VOLUME_STEP)
                RemoteKey.VOLUME_DOWN -> setVolume(volume - VOLUME_STEP)
                else -> toggleMute()
            }
        }
        onDispose { remoteVolume = null }
    }

    // Démarrage : headers (UA/Referer, ce que les extracteurs exigent), reprise
    // à la position sauvée — l'option `start` du moteur, atomique, là où libVLC
    // imposait un `setTime` sur l'événement « prêt » — puis sous-titres
    // externes proposés sans en activer aucun.
    LaunchedEffect(streamUrl) {
        // ── Ce que le changement de flux doit remettre à zéro ────────────────
        //
        // L'enchaînement d'épisodes remplace les paramètres du lecteur **sans le
        // démonter** (voir `Main.kt`), et tout ce qui suit relève de ce seul
        // fait : sans ce préambule, l'état de l'épisode précédent traversait le
        // suivant. `finished` resté vrai ne rebasculait plus jamais, et
        // l'épisode suivant n'enchaînait donc jamais le sien ; `playError` resté
        // vrai renvoyait à la cascade un flux qui venait de s'ouvrir.
        //
        // Remis à zéro **ici** et non par `remember(streamUrl)`, et c'est le
        // point à ne pas inverser : le moteur est retenu pour toute la vie de
        // l'écran, et sa lambda `surFin` écrit dans *cet* état-ci. Un état
        // recréé par flux l'aurait laissée écrire dans celui de l'épisode
        // précédent — la fin de lecture n'arrivant alors plus jamais.
        finished = false
        playError = false

        // Le relais du flux précédent tient un socket et un vivier de fils.
        // Tant que quitter le lecteur était la seule façon de changer d'épisode,
        // `onDispose` s'en chargeait ; il en fuirait un par épisode maintenant
        // que l'écran survit au changement.
        proxy?.shutdown()
        proxy = null

        // Avancement de l'épisode qu'on quitte, pour la même raison : `onDispose`
        // n'arrive plus entre deux épisodes. Sans ça, un épisode quitté par le
        // panneau des épisodes perdait le sien — la fin de lecture, elle, passe
        // déjà par `markFinished`, et repasser ici n'écrit que la même chose.
        //
        // Lu **ici**, en tête de l'effet : le moteur joue encore l'ancien
        // fichier à cet instant, la position relevée est donc bien la sienne. La
        // clé, en revanche, a déjà changé — d'où celle qu'on retient à part.
        val sortant = mediaKeySortant.value
        if (sortant.isNotBlank() && sortant != mediaKey) {
            val t = runCatching { controller.positionMs() }.getOrDefault(0L)
            val d = runCatching { controller.durationMs() }.getOrDefault(0L)
            if (t > 0) saveScope.launch { progress.save(sortant, t, d) }
        }
        mediaKeySortant.value = mediaKey

        val resumeAt = if (mediaKey.isNotBlank()) progress.position(mediaKey) else 0L

        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        // mpv transmet ses en-têtes jusqu'aux segments (MpvHeadersTest le
        // verrouille) : le relais n'est plus là pour eux. Il reste pour le
        // **DNS** — sa résolution passe par le client de l'application, donc
        // par DoH, quand les fournisseurs d'accès bloquent les domaines des
        // hébergeurs. Même règle éprouvée qu'avant : un hôte assez pointilleux
        // pour exiger un Referer est aussi le genre d'hôte qu'on bloque.
        val played = if (header("Referer") != null && streamUrl.startsWith("http")) {
            val relay = LocalStreamProxy(headers)
            proxy = relay
            relay.localUrl(streamUrl)
        } else {
            streamUrl
        }
        // L'ouverture attend le réseau : hors du fil d'interface.
        withContext(Dispatchers.IO) {
            val ouvert = moteur.ouvre(played, headers, departMs = resumeAt)
            if (ouvert) {
                moteur.coupeSon(false)
                moteur.regleVolumePourcent(100)
                moteur.ajouteSousTitres(subtitles.values)
            }
        }
    }

    /** Clé de titre (`tv:<id>`) : la préférence audio vaut pour toute la série. */
    val titleKey = remember(mediaKey) {
        parseMediaKey(mediaKey)?.let { if (it.isTv) "tv:${it.tmdbId}" else null }
    }

    // Réapplique la piste audio retenue sur cette série — par libellé, jamais
    // par identifiant : libVLC numérote ses pistes par flux (voir matchAudioTrack).
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

    LaunchedEffect(bufferingPercent < 100f) {
        if (bufferingPercent >= 100f) {
            bufferingVisible = false
            return@LaunchedEffect
        }
        delay(700)
        bufferingVisible = true
    }

    // Suivi de l'état du lecteur + sauvegarde périodique de la position (~5 s).
    LaunchedEffect(mediaKey) {
        var ticks = 0
        var prefetchAsked = false
        while (true) {
            delay(500)
            isPlaying = controller.isPlaying
            // Du contrôleur : pendant un seek il rend la cible demandée, sinon
            // la barre repart en arrière le temps que le flux se replace, ce
            // qui se lit comme un seek qui a échoué.
            val time = controller.positionMs()
            // Un signal, pas une heuristique : mpv dit quand la lecture attend
            // le réseau, et où en est le remplissage.
            bufferingPercent = moteur.remplissageCache()
            if (time > 1_000) everPlayed = true
            timeMs = time
            lengthMs = controller.durationMs()
            ticks++
            if (ticks % 10 == 0 && mediaKey.isNotBlank() && isPlaying) {
                progress.save(mediaKey, timeMs, lengthMs)

                // Même déclencheur que sur Android : les catalogues répondent en
                // plusieurs secondes, autant les payer pendant que l'épisode
                // joue encore plutôt qu'après le générique.
                if (!prefetchAsked && lengthMs > 0 && timeMs >= lengthMs * PREFETCH_AT) {
                    prefetchAsked = true
                    onPrefetchNext()
                }
            }
        }
    }

    // ── Intro / générique (TheIntroDB) ───────────────────────────────────────
    // Mêmes segments que sur Android TV, pour les faire apparaître sur la barre
    // de progression. Le desktop n'a pas (encore) les boutons « Passer » : ici
    // l'information est purement visuelle.
    val pid = remember(mediaKey) { parseMediaKey(mediaKey) }
    var media by remember(streamUrl) { mutableStateOf<IntroMedia?>(null) }

    // Signalement d'un segment manquant à TheIntroDB.
    val reportViewModel = remember { ReportSegmentViewModel() }
    val reportStep by reportViewModel.step.collectAsState()
    val canReport by reportViewModel.canReport.collectAsState()
    LaunchedEffect(Unit) { reportViewModel.refreshAvailability() }
    // Ce que la base ne connaît pas pour ce média : c'est ça, et rien d'autre,
    // qui décide d'afficher l'icône.
    val introMissing = media?.intro?.isEmpty() ?: true
    val creditsMissing = media?.credits?.isEmpty() ?: true
    LaunchedEffect(pid, media) { reportViewModel.bind(pid, controller.durationMs()) }

    // La durée n'est connue qu'une fois le flux ouvert, et elle sert à l'API à
    // choisir la bonne version du titre : on attend qu'elle arrive.
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
    /**
     * Marque le média terminé : sortie de « Reprendre la lecture » et bascule
     * en « vu ». Un seul point de passage pour les deux façons de finir un
     * média — fin atteinte et générique passé — sinon la seconde oublie ce que
     * la première fait, ce qui laissait l'épisode dans la reprise alors qu'on
     * venait d'en sauter le générique. `saveScope` survit à la fermeture du
     * lecteur, le temps que l'écriture aboutisse.
     */
    fun markFinished() {
        if (mediaKey.isNotBlank() && lengthMs > 0) {
            saveScope.launch { progress.save(mediaKey, lengthMs, lengthMs) }
        }
        // La série doit rester dans « Reprendre la lecture » : l'entrée qu'on
        // vient de terminer en sort, et celle de l'épisode suivant n'existe pas
        // encore. Sans ce repère, finir un épisode faisait disparaître la série
        // du seul rail où l'on va voir où l'on en est.
        if (nextSeason > 0 && nextEpisode > 0) {
            nextUpEntry(mediaKey, title, posterUrl.takeIf { it.isNotBlank() }, nextSeason, nextEpisode)
                ?.let { next -> saveScope.launch { progress.queueNext(next) } }
        }
    }

    fun doSkip() {
        when (activeSkip) {
            SkipKind.INTRO -> media?.intro?.firstOrNull()?.endMs?.let { controller.seekTo(it) }
            SkipKind.CREDITS -> {
                // Passer le générique, c'est avoir fini le média.
                markFinished()
                if (nextSeason > 0 && nextEpisode > 0) {
                    onNextEpisode(nextSeason, nextEpisode)
                } else {
                    onBack()
                }
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
        // libVLC annonce « fin » aussi quand rien n'a jamais été lu. Le cas
        // observé : un CDN qui sert la playlist mais refuse les segments (403
        // faute de Referer, que libVLC ne transmet pas à ses sous-requêtes).
        // Le média s'ouvre, aucune piste n'arrive, la fin tombe aussitôt — et
        // l'épisode était marqué vu puis enchaîné sur le suivant, alors qu'on
        // n'en avait pas vu une image. C'est un échec de source : la fiche
        // reprend la cascade sur l'hébergeur suivant, comme pour une coupure.
        if (!everPlayed) {
            onPlaybackFailed()
            return@LaunchedEffect
        }
        markFinished()
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
            val t = runCatching { controller.positionMs() }.getOrDefault(0L)
            val d = runCatching { controller.durationMs() }.getOrDefault(0L)
            if (mediaKey.isNotBlank() && t > 0) {
                saveScope.launch { progress.save(mediaKey, t, d) }
            }
            // Sur un **fil dédié**, pas une coroutine : la fermeture est la
            // seule chose qui sépare « quitter le lecteur » de « la lecture
            // continue en arrière-plan », et elle ne doit dépendre de rien —
            // ni d'un scope, ni d'un dispatcher, ni de l'ordonnancement
            // coroutines du bureau. Mesuré : à la fermeture de la fenêtre, la
            // coroutine planifiée ici n'a jamais couru. Hors du fil
            // d'interface tout de même, `ferme()` attendant la fin des fils
            // du moteur.
            val relais = proxy
            Thread({
                runCatching { moteur.ferme() }
                    .onFailure { println("[lecteur] fermeture en échec : $it") }
                    .onSuccess { println("[lecteur] moteur fermé") }
                // Après le moteur : le relais sert encore des segments tant que
                // le lecteur n'est pas arrêté.
                runCatching { relais?.shutdown() }
            }, "moovie-lecteur-fermeture").start()
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
            // Le curseur suit les contrôles : replier la barre sans lui laissait
            // une flèche posée en plein milieu de l'image, et il fallait sortir
            // la souris de la fenêtre pour s'en débarrasser.
            //
            // `overrideDescendants` seulement quand il est masqué : contrôles
            // affichés, les boutons doivent garder la main qu'ils demandent.
            .pointerHoverIcon(
                if (controlsVisible) PointerIcon.Default else BLANK_CURSOR,
                overrideDescendants = !controlsVisible,
            )
            // Tout mouvement de souris réaffiche les contrôles — et rend donc
            // aussi le curseur.
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
                // **Le panneau des épisodes a la main.** Les autres menus sont
                // des `Dialog`, qui prennent le focus dans leur propre fenêtre ;
                // celui-ci glisse dans la page, donc les touches arrivent encore
                // ici. Sans ce retrait, Haut et Bas réglaient le volume au lieu
                // de parcourir la liste, et Espace mettait en pause : le panneau
                // s'affichait sans qu'on puisse s'en servir au clavier.
                if (dialog == PlayerDialogKind.EPISODES) return@onPreviewKeyEvent false
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
                    Key.DirectionUp -> { setVolume(volume + VOLUME_STEP); true }
                    Key.DirectionDown -> { setVolume(volume - VOLUME_STEP); true }
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
            PlayerTitleOverlay(title = title, subtitle = subtitle, clock = clock)
            // Au pointeur, marquer se fait au clic : pas de touche à deviner.
            ReportMarkingBanner(
                step = reportStep,
                positionMs = timeMs,
                onMark = {
                    reportViewModel.mark(controller.positionMs())
                    // Voir PlayerScreen (Android) : une intro se relève en deux
                    // bornes, et la modale n'a rien à montrer avant la seconde.
                    if (reportViewModel.step.value !is ReportStep.Marking) {
                        dialog = PlayerDialogKind.REPORT
                    }
                },
            )
        }

        // Résolution de l'épisode suivant : même place et même habillage que la
        // mise en mémoire tampon, parce que c'est la même chose pour qui
        // regarde — le lecteur attend le réseau. Prioritaire sur le tampon : les
        // deux messages sont centrés, et le flux qui s'achève n'a plus rien à
        // dire de son remplissage.
        if (chainingNext) {
            Text(
                stringResource(Res.string.player_chaining_next),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFDDDDDD),
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MoovieShape)
                    .background(Color(0xB3000000))
                    .moovieSurface(active = false, selected = true)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }

        // Mise en mémoire tampon : l'indicateur textuel hérité de l'époque
        // libVLC. mpv expose désormais une vraie plage tamponnée
        // (controller.bufferedMs) — la dessiner sur la barre comme Android TV
        // est un raffinement possible, l'indicateur reste juste en attendant.
        if (bufferingVisible && !playError && !chainingNext) {
            Text(
                stringResource(Res.string.player_buffering, bufferingPercent.toInt()),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFDDDDDD),
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MoovieShape)
                    .background(Color(0xB3000000))
                    // Même liseré que le reste de l'app : ce message est
                    // propre au desktop, il n'a pas à parler une autre langue.
                    .moovieSurface(active = false, selected = true)
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
                // L'horloge occupe ce même coin : la pastille se pose alors
                // *sous* elle, sinon les deux se superposent et deviennent
                // illisibles. Même règle que sur Android.
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(end = 48.dp, top = if (clock != null) 76.dp else 32.dp),
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

        // ── Qualité ──────────────────────────────────────────────────────
        //
        // Sur un master HLS, mpv expose chaque variante comme une piste vidéo
        // sélectionnable **à chaud** : plafonner la qualité ne coupe plus la
        // lecture. Seul le changement de *source* rouvre — c'est une autre URL,
        // et le moteur reprend à la position via son option de départ.
        var quality by remember(streamUrl) { mutableStateOf<QualityChoice>(QualityChoice.Auto) }
        var currentHeights by remember(streamUrl) { mutableStateOf(emptyList<Int>()) }
        var playedUrl by remember(streamUrl) { mutableStateOf(streamUrl) }
        var playedHeaders by remember(streamUrl) { mutableStateOf(headers) }
        val qualityScope = rememberCoroutineScope()

        // Les variantes se lisent dans la master playlist, comme avant : les
        // hauteurs y sont nommées, là où les pistes du démultiplexeur peuvent
        // arriver après l'ouverture du menu.
        //
        // **Le format est déduit de l'URL, jamais affirmé.** Ce `PlayableStream`
        // était construit avec `StreamFormat.HLS` en dur, y compris sur les
        // sources en `.mp4` — la garde « vide hors HLS » de `streamHeights`
        // était donc contournée par son propre appelant, et la fonction
        // chargeait le film entier en mémoire. Elle est désormais bornée de son
        // côté ; ici on cesse simplement de mentir.
        LaunchedEffect(playedUrl, playedHeaders) {
            val format = if (playedUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                StreamFormat.HLS
            } else {
                StreamFormat.UNKNOWN
            }
            currentHeights = runCatching {
                streamHeights(PlayableStream(playedUrl, format, playedHeaders))
            }.getOrDefault(emptyList())
        }

        fun reopen(url: String, hdrs: Map<String, String>) {
            val at = controller.positionMs()
            qualityScope.launch {
                withContext(Dispatchers.IO) {
                    // Le relais suit la même règle qu'à l'ouverture : il porte
                    // la résolution DoH des hôtes à Referer.
                    val referer = hdrs.entries.firstOrNull { it.key.equals("Referer", true) }?.value
                    val cible = if (referer != null && url.startsWith("http")) {
                        proxy?.shutdown()
                        val relay = LocalStreamProxy(hdrs)
                        proxy = relay
                        relay.localUrl(url)
                    } else {
                        url
                    }
                    runCatching { moteur.ouvre(cible, hdrs, departMs = at.coerceAtLeast(0)) }
                }
            }
        }

        fun applyQuality(choice: QualityChoice) {
            when (choice) {
                is QualityChoice.Auto -> {
                    quality = choice
                    moteur.selectionneVideoParHauteur(null)
                }
                is QualityChoice.Height -> {
                    quality = choice
                    moteur.selectionneVideoParHauteur(choice.height)
                }
                is QualityChoice.Source -> {
                    val alt = alternatives.firstOrNull { it.url == choice.url } ?: return
                    qualityScope.launch {
                        val stream = resolveAlternative(alt.url, alt.hoster, language)
                        // Échec : on garde le flux en cours plutôt que de casser
                        // une lecture qui marche pour une qualité non obtenue.
                        if (stream == null || stream.url.isBlank()) return@launch
                        playedUrl = stream.url
                        playedHeaders = stream.headers
                        quality = choice
                        reopen(stream.url, stream.headers)
                    }
                }
            }
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
                onCommitScrub = {},
                onNudgeScrub = {},
                onPreviousEpisode = {
                    if (nextEpisode > 1) onNextEpisode(nextSeason, nextEpisode - 2)
                },
                onNextEpisode = {
                    if (nextSeason > 0 && nextEpisode > 0) {
                        // Voir PlayerScreen (Android) : « Suivant » vaut fin de
                        // l'épisode courant, sinon les titres sans données
                        // TheIntroDB n'ont aucun chemin vers « vu ».
                        markFinished()
                        onNextEpisode(nextSeason, nextEpisode)
                    }
                },
                // Seulement sur une série : la clé de média porte l'identifiant
                // TMDB, et sans lui le panneau n'a rien à lister.
                onOpenEpisodes = pid?.takeIf { it.isTv }?.let { { dialog = PlayerDialogKind.EPISODES } },
                onOpenSubtitles = {
                    tracks = controller.tracks()
                    dialog = PlayerDialogKind.SUBTITLES
                },
                onOpenSettings = {
                    tracks = controller.tracks()
                    dialog = PlayerDialogKind.SETTINGS
                },
                onDownload = if (mediaKey.isNotBlank() && sourceUrl.isNotBlank()) {
                    {
                        // On cherche mieux avant de mettre en file : le fichier
                        // se garde, contrairement à la lecture qu'on a lancée
                        // vite. Voir bestDownloadStream — le flux en cours sert
                        // de repli si rien de meilleur ne répond.
                        qualityScope.launch {
                        val (dlUrl, dlHoster, dlStream) = bestDownloadStream(
                            playingUrl = sourceUrl,
                            playingHoster = hoster,
                            playingStream = PlayableStream(
                                url = streamUrl,
                                format = StreamFormat.UNKNOWN,
                                headers = headers,
                            ),
                            playingHeight = currentHeights.firstOrNull() ?: 0,
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
                VolumeSlider(
                    volume = volume,
                    muted = muted,
                    onVolume = { setVolume(it) },
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

        val subsViewModel = remember { PlayerSubtitlesViewModel() }
        val subsState by subsViewModel.state.collectAsState()
        val subsFile by subsViewModel.file.collectAsState()
        // Le fichier arrive déjà recalé : le lecteur n'a rien à calculer.
        LaunchedEffect(subsFile) { controller.loadExternalSubtitle(subsFile) }
        DisposableEffect(Unit) { onDispose { subsViewModel.onLeave() } }

        // Changement d'épisode sans quitter le lecteur : ce ViewModel reste
        // monté avec lui, et il porte l'état d'un média précis. Sans cette
        // remise à zéro, le menu de l'épisode suivant listait les candidats du
        // précédent — `load` ne cherche que sur une liste vide, il ne cherchait
        // donc plus — et son fichier recalé restait la piste externe demandée.
        //
        // Comparé à la clé retenue plutôt que déclenché sur `mediaKey` : un
        // `LaunchedEffect(mediaKey)` part aussi à la première composition, où
        // il n'y a rien à remettre à zéro et où `onLeave()` balaierait les
        // dérivés d'une clé vide.
        val subsBoundTo = remember { mutableStateOf(mediaKey) }
        LaunchedEffect(mediaKey) {
            if (subsBoundTo.value == mediaKey) return@LaunchedEffect
            subsBoundTo.value = mediaKey
            subsViewModel.reset()
        }

        // Sous-titres en ligne : recherche à l'ouverture du menu seulement. Elle
        // est gratuite, mais inutile tant que personne ne la demande — et à ce
        // moment-là le flux est prêt, donc sa cadence est connue.
        LaunchedEffect(dialog) {
            if (dialog == PlayerDialogKind.SUBTITLES && subsState.candidates.isEmpty()) {
                subsViewModel.load(mediaKey, title, controller.videoFps())
            }
        }

        // Échap referme le panneau au lieu de quitter le lecteur. C'est la
        // fenêtre qui reçoit la touche — voir `Main.kt` — donc c'est à elle
        // qu'il faut le dire, exactement comme le fait la fiche de détails.
        val panneauEpisodes = dialog == PlayerDialogKind.EPISODES
        DisposableEffect(panneauEpisodes) {
            onRegisterBack(if (panneauEpisodes) ({ dialog = null }) else null)
            onDispose { onRegisterBack(null) }
        }

        // La liste des épisodes, en panneau glissant plutôt qu'en modale
        // centrée : on choisit un épisode en gardant l'image sous les yeux.
        pid?.takeIf { it.isTv }?.let { identite ->
            PlayerEpisodesPanel(
                visible = dialog == PlayerDialogKind.EPISODES,
                tmdbId = identite.tmdbId,
                saisonCourante = identite.season,
                episodeCourant = identite.episode,
                onJouer = { saison, numero ->
                    dialog = null
                    onNextEpisode(saison, numero)
                },
                onFermer = { dialog = null },
            )
        }

        // Le panneau des épisodes est rendu juste au-dessus, dans la page : il
        // n'a rien à faire parmi les modales centrées.
        @Suppress("KotlinConstantConditions")
        when (dialog.takeIf { it != PlayerDialogKind.EPISODES }) {
            PlayerDialogKind.EPISODES -> Unit
            PlayerDialogKind.SUBTITLES -> PlayerOptionsDialog(
                sections = listOf(
                    subtitleSection(tracks) { trackId ->
                        // Une piste intégrée et un sous-titre externe affichés
                        // ensemble se chevauchent : choisir l'une retire l'autre.
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
                            // On chronomètre sur l'image : la modale s'efface.
                            reportViewModel.startMarking(kind)
                            dialog = null
                        },
                        onSend = reportViewModel::send,
                        onRedo = { reportViewModel.open(introMissing, creditsMissing) },
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
                            currentHeights = currentHeights,
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
                                saveScope.launch { progress.setAudioTrack(key, label) }
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
private fun MissingMpv(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.player_mpv_missing),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(stringResource(Res.string.player_mpv_help), color = Color(0xFF9A9A9A))
        // La raison exacte, quand on la connaît. Le conseil générique
        // (« réinstaller ») est **faux** dans le cas mesuré : le fichier était
        // présent et c'est une de ses dépendances qui manquait. Sans cette
        // ligne, l'écran envoie réinstaller une application intacte.
        Libmpv.diagnostic?.let { raison ->
            Text(
                raison,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7A7A85),
                textAlign = TextAlign.Center,
            )
        }
        MoovieButton(onClick = onBack) { Text(stringResource(Res.string.common_back)) }
    }
}

/** Plafond du volume. mpv amplifie au-delà de 100 %, jusqu'à `volume-max`. */
private const val MAX_VOLUME = 200

/** Pas d'un cran de volume, au clavier comme à la télécommande du téléphone. */
private const val VOLUME_STEP = 5

/** Repère du 100 % sur la piste, exprimé en fraction de sa largeur. */
private const val UNITY_FRACTION = 100f / MAX_VOLUME

/**
 * Réglage du volume propre au lecteur, de 0 à 200 %.
 *
 * L'icône seule ne savait que couper le son : pour un film dont la piste est
 * trop basse il fallait sortir de l'application et remonter le mélangeur du
 * système — qu'on redescendait ensuite, ou pas. Comme le moteur est libVLC, la
 * plage va jusqu'à 200 %, et la partie au-delà de 100 % est **peinte
 * autrement** : l'amplification est une correction, pas la zone normale de
 * réglage, et elle peut saturer. Le repère à 100 % permet d'y revenir sans
 * viser au jugé.
 *
 * Desktop uniquement : à la télécommande il n'y a rien à pointer, et sur
 * téléphone le volume est celui du système.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VolumeSlider(
    volume: Int,
    muted: Boolean,
    onVolume: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = if (muted) 0 else volume
    val trackWidth = 96.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        val label = stringResource(Res.string.player_volume, shown)
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(24.dp)
                .semantics { contentDescription = label }
                // La zone sensible fait 24 dp de haut pour une piste de 4 : une
                // piste fine se pointe mal, et c'est le geste le plus fréquent.
                .pointerInput(Unit) {
                    fun apply(x: Float) {
                        onVolume(((x / size.width) * MAX_VOLUME).toInt().coerceIn(0, MAX_VOLUME))
                    }
                    detectTapGestures { apply(it.x) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        onVolume(
                            ((change.position.x / size.width) * MAX_VOLUME)
                                .toInt().coerceIn(0, MAX_VOLUME),
                        )
                    }
                }
                // La molette est le geste attendu sur un curseur de volume.
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) onVolume(volume + if (dy < 0) 5 else -5)
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                val h = size.height
                val unity = size.width * UNITY_FRACTION
                val filled = size.width * (shown.toFloat() / MAX_VOLUME)
                // Fond : la zone d'amplification est déjà distincte à vide,
                // sans quoi on ne sait pas qu'elle existe avant d'y entrer.
                drawRect(Color(0x33FFFFFF), size = Size(unity, h))
                drawRect(
                    Color(0x22E0B057),
                    topLeft = Offset(unity, 0f),
                    size = Size(size.width - unity, h),
                )
                drawRect(
                    MOOVIE_ACCENT,
                    size = Size(minOf(filled, unity), h),
                )
                if (filled > unity) {
                    drawRect(
                        Color(0xFFE0B057),
                        topLeft = Offset(unity, 0f),
                        size = Size(filled - unity, h),
                    )
                }
                // Repère du 100 % : le seul point de la piste qu'on cherche.
                drawLine(
                    Color.White,
                    start = Offset(unity, -2f),
                    end = Offset(unity, h + 2f),
                    strokeWidth = 2f,
                )
            }
        }
        Text(
            stringResource(Res.string.player_volume_value, shown),
            style = MaterialTheme.typography.labelMedium,
            color = if (shown > 100) Color(0xFFE0B057) else Color(0xFFCCCCCC),
            // Largeur réservée : sans elle, passer de 99 à 100 décale toute la
            // barre de contrôles d'un caractère.
            modifier = Modifier.width(44.dp),
        )
    }
}


/**
 * Délai avant de se recaler après une réouverture.
 *
 * libVLC refuse un `setTime` tant que le média n'est pas ouvert : demander la
 * position tout de suite ne fait rien, et la lecture repart du début.
 */
