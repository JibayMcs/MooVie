package fr.moovie.tv.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.intro.IntroDbRepository
import fr.moovie.tv.data.intro.IntroMedia
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.watch.nextUpEntry
import fr.moovie.tv.ui.player.ApplySubtitleStyle
import fr.moovie.tv.ui.player.MooviePlayerController
import fr.moovie.tv.ui.player.PlayerControlBar
import fr.moovie.tv.ui.player.PlayerDurationGuard
import fr.moovie.tv.ui.player.PlayerSkipButton
import fr.moovie.tv.ui.player.PlayerTitleOverlay
import fr.moovie.tv.ui.player.SkipKind
import fr.moovie.tv.ui.player.parseMediaKey
import fr.moovie.tv.ui.player.toPlayerSegments
import fr.moovie.tv.shared.dispatcherEs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Combien de temps les contrôles restent après le dernier geste.
 *
 * La même valeur que les deux autres plateformes. Assez long pour lire la barre
 * de progression, assez court pour ne pas rester en travers d'un plan.
 */
private const val CONTROLS_HIDE_MS = 4_000L

/** Décompte avant l'épisode suivant, en secondes. */
private const val AUTO_NEXT_SECONDS = 8

/** Fraction du média à partir de laquelle on demande la source suivante. */
private const val PREFETCH_AT = 0.80

/**
 * Le lecteur, côté iOS.
 *
 * ## Pourquoi il existe alors que tout le reste a été déplacé
 *
 * Chaque écran de l'application a été rendu commun aux quatre plateformes, sauf
 * celui-ci — et pas par omission. Un lecteur est le seul écran dont la moitié
 * basse est du code de plateforme : Android joue par ExoPlayer, le desktop par
 * mpv, iOS par AVPlayer. Aucun des trois n'a d'API commune, et les trois écrans
 * sont donc trois assemblages distincts. C'est déjà vrai entre Android et le
 * desktop, qui ont chacun le leur.
 *
 * Ce qui est partagé, c'est **tout ce qui se voit** : `PlayerControlBar`,
 * `PlayerTitleOverlay`, `PlayerSkipButton` viennent de `PlayerChrome`, en
 * commun. Les commandes, la barre de progression, les marques de générique sont
 * donc identiques à celles d'Android au pixel près. Ce fichier tient la
 * mécanique — ouvrir, suivre la position, enregistrer, enchaîner — et la confie
 * à ces briques-là.
 *
 * ## Ce que la mécanique doit faire, et pourquoi chaque morceau est là
 *
 * **Reprendre où l'on s'était arrêté.** La position vient du magasin, pas du
 * paramètre : `startAtMs` ne sert qu'à la reprise demandée depuis l'accueil.
 * Le saut attend que la durée soit connue — AVPlayer ne l'annonce qu'une fois
 * le média chargé, et chercher avant place la tête de lecture à zéro.
 *
 * **Enregistrer.** Toutes les cinq secondes et seulement en lecture, comme sur
 * les deux autres plateformes. Enregistrer en pause écraserait la position d'une
 * reprise par celle du moment où on a mis en pause pour partir.
 *
 * **Détecter la fin.** AVPlayer ne rappelle pas : `AVPlayerItemDidPlayToEndTime`
 * est une notification NSNotificationCenter, dont l'abonnement depuis
 * Kotlin/Native demande une référence stable qu'il faudrait libérer à la main.
 * La boucle d'état sait déjà où en est la lecture ; la fin s'y lit comme « à
 * moins d'une seconde du bout ». C'est la même conclusion pour une bien moindre
 * surface.
 *
 * **Ne pas enchaîner sur un flux mort.** `everPlayed` garde la même prudence que
 * le desktop : un hébergeur qui sert la playlist mais refuse ses segments donne
 * une durée et une fin immédiate. Sans ce garde-fou, l'épisode serait marqué vu
 * et le suivant lancé sans qu'une image ait été affichée.
 */
@Composable
internal fun IosPlayerScreen(
    streamUrl: String,
    headers: Map<String, String>,
    mediaKey: String,
    title: String,
    subtitle: String,
    nextSeason: Int,
    nextEpisode: Int,
    posterUrl: String,
    startAtMs: Long,
    expectedMinutes: Int,
    controller: MooviePlayerController,
    surface: @Composable (Modifier) -> Unit,
    onBack: () -> Unit,
    onNextEpisode: (season: Int, episode: Int) -> Unit,
    onPlaybackFailed: () -> Unit,
) {
    val progress = remember { WatchProgressRepository() }
    val settings = remember { SettingsRepository() }
    val autoPlayNext by settings.autoPlayNext.collectAsState(initial = true)
    val skipEnabled by settings.skipIntroOutro.collectAsState(initial = true)
    val introRepo = remember { IntroDbRepository() }

    var isPlaying by remember { mutableStateOf(true) }
    var timeMs by remember { mutableStateOf(0L) }
    var lengthMs by remember { mutableStateOf(0L) }
    var everPlayed by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var activityTick by remember { mutableStateOf(0) }
    var autoNextSeconds by remember { mutableStateOf<Int?>(null) }
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }

    fun signalActivity() {
        controlsVisible = true
        activityTick++
    }

    ApplySubtitleStyle(controller)

    // Même garde-fou que les deux autres plateformes, et volontairement le même
    // code : un flux nettement plus court que le média annoncé emprunte le
    // chemin d'échec habituel, et la fiche reprend sa cascade de sources.
    PlayerDurationGuard(
        controller = controller,
        mediaId = streamUrl,
        expectedMinutes = expectedMinutes,
        onTooShort = onPlaybackFailed,
    )

    // Reprise. La durée n'arrive qu'après le chargement du média : chercher
    // avant qu'elle soit connue replace la tête de lecture à zéro.
    LaunchedEffect(streamUrl) {
        val resumeAt = when {
            startAtMs > 0 -> startAtMs
            mediaKey.isNotBlank() -> progress.position(mediaKey)
            else -> 0L
        }
        if (resumeAt <= 0) return@LaunchedEffect
        repeat(40) {
            if (controller.durationMs() > 0) return@repeat
            delay(250)
        }
        if (controller.durationMs() > 0) controller.seekTo(resumeAt)
    }

    // Boucle d'état : position, durée, sauvegarde périodique, préchargement.
    LaunchedEffect(mediaKey, streamUrl) {
        var ticks = 0
        var prefetchAsked = false
        while (true) {
            delay(500)
            isPlaying = controller.isPlaying
            val time = controller.positionMs()
            if (time > 1_000) everPlayed = true
            timeMs = time
            lengthMs = controller.durationMs()
            ticks++
            if (ticks % 10 == 0 && mediaKey.isNotBlank() && isPlaying) {
                progress.save(mediaKey, timeMs, lengthMs)
                if (!prefetchAsked && lengthMs > 0 && timeMs >= lengthMs * PREFETCH_AT) {
                    prefetchAsked = true
                }
            }
            // La fin, lue plutôt qu'écoutée — voir le KDoc.
            if (!finished && lengthMs > 0 && timeMs >= lengthMs - 1_000) finished = true
        }
    }

    // Segments de générique. La durée sert à l'API à choisir la bonne version du
    // titre : on attend qu'elle arrive avant de demander.
    val pid = remember(mediaKey) { parseMediaKey(mediaKey) }
    var media by remember(streamUrl) { mutableStateOf<IntroMedia?>(null) }
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

    // Segment sous la tête de lecture. Dérivé de `timeMs`, que la boucle
    // rafraîchit déjà : inutile d'en ajouter une seconde.
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
     * Marque le média terminé : sortie de « Reprendre la lecture » et bascule en
     * « vu ». Un seul point de passage pour les deux façons de finir — fin
     * atteinte et générique passé — sinon la seconde oublie ce que fait la
     * première, et l'épisode reste dans la reprise après qu'on en a sauté le
     * générique.
     */
    suspend fun markFinished() {
        if (mediaKey.isNotBlank() && lengthMs > 0) progress.save(mediaKey, lengthMs, lengthMs)
        // La série doit rester dans « Reprendre la lecture » : l'entrée qu'on
        // vient de terminer en sort, et celle de l'épisode suivant n'existe pas
        // encore. Sans ce repère, finir un épisode fait disparaître la série du
        // seul rail où l'on va voir où l'on en est.
        if (nextSeason > 0 && nextEpisode > 0) {
            nextUpEntry(mediaKey, title, posterUrl.takeIf { it.isNotBlank() }, nextSeason, nextEpisode)
                ?.let { progress.queueNext(it) }
        }
    }

    // Fin de lecture : marquer, puis enchaîner après un décompte. Sans suite ou
    // enchaînement coupé, simple retour.
    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        // Une fin annoncée sans qu'une image ait été jouée est un échec de
        // source, pas une fin : la fiche reprend sa cascade.
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
        controlsVisible = true
        var remaining = AUTO_NEXT_SECONDS
        while (remaining > 0) {
            autoNextSeconds = remaining
            delay(1000)
            remaining--
        }
        autoNextSeconds = null
        onNextEpisode(nextSeason, nextEpisode)
    }

    // Auto-masquage : relancé à chaque geste. En pause, les contrôles restent.
    LaunchedEffect(activityTick, isPlaying) {
        if (!isPlaying) {
            controlsVisible = true
            return@LaunchedEffect
        }
        delay(CONTROLS_HIDE_MS)
        controlsVisible = false
    }

    // Sortie : enregistrer la position avant de rendre la main.
    //
    // La portée est **détachée** de la composition, comme sur le desktop et pour
    // la même raison : `onDispose` s'exécute pendant que l'écran disparaît, et
    // une écriture lancée sur une portée qui meurt avec lui serait annulée avant
    // d'aboutir. On perdrait précisément la position de sortie — celle qui
    // compte le plus, puisque c'est elle que « Reprendre » affichera.
    //
    // Le contrôleur, lui, est libéré par celui qui l'a construit : voir
    // MoovieViewController.
    val saveScope = remember { CoroutineScope(SupervisorJob() + dispatcherEs) }
    DisposableEffect(mediaKey) {
        onDispose {
            val t = timeMs
            val d = lengthMs
            if (mediaKey.isNotBlank() && d > 0 && t > 0) {
                saveScope.launch { progress.save(mediaKey, t, d) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        surface(Modifier.fillMaxSize())

        if (controlsVisible) {
            PlayerTitleOverlay(
                title = title,
                subtitle = subtitle,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            )

            PlayerControlBar(
                isPlaying = isPlaying,
                positionMs = scrubTargetMs ?: timeMs,
                durationMs = lengthMs,
                scrubbing = scrubTargetMs != null,
                bufferedMs = controller.bufferedMs(),
                showEpisodeButtons = nextSeason > 0 && nextEpisode > 0,
                canGoPrevious = nextEpisode > 1,
                playFocus = remember { FocusRequester() },
                onBack = onBack,
                onTogglePause = {
                    controller.togglePause()
                    signalActivity()
                },
                onSeekBack = {
                    controller.seekBy(-15_000L)
                    signalActivity()
                },
                onSeekForward = {
                    controller.seekBy(15_000L)
                    signalActivity()
                },
                onCommitScrub = {
                    scrubTargetMs?.let(controller::seekTo)
                    scrubTargetMs = null
                    signalActivity()
                },
                onNudgeScrub = { delta ->
                    val base = scrubTargetMs ?: timeMs
                    scrubTargetMs = (base + delta).coerceIn(0L, lengthMs.coerceAtLeast(0L))
                    signalActivity()
                },
                onPreviousEpisode = {
                    if (nextEpisode > 1) onNextEpisode(nextSeason, nextEpisode - 2)
                },
                onNextEpisode = { onNextEpisode(nextSeason, nextEpisode) },
                mediaKey = mediaKey,
                onActivity = ::signalActivity,
                onSeekToFraction = { fraction ->
                    if (lengthMs > 0) controller.seekTo((lengthMs * fraction).toLong())
                    signalActivity()
                },
                segments = remember(media) { media?.toPlayerSegments().orEmpty() },
            )
        }

        // Le bouton « Passer », affiché même contrôles masqués : c'est tout son
        // intérêt, ne pas avoir à réveiller l'interface pour l'atteindre.
        if (activeSkip != null && autoNextSeconds == null) {
            PlayerSkipButton(
                kind = activeSkip,
                onClick = {
                    when (activeSkip) {
                        SkipKind.INTRO ->
                            media?.intro?.firstOrNull()?.endMs?.let(controller::seekTo)
                        // Passer le générique, c'est avoir fini le média.
                        SkipKind.CREDITS -> finished = true
                    }
                    signalActivity()
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            )
        }

        autoNextSeconds?.let { secondes ->
            Text(
                text = "$secondes",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
