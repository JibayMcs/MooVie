package fr.moovie.tv.ios

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.intro.IntroDbRepository
import fr.moovie.tv.data.intro.IntroMedia
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.watch.nextUpEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.player_seek_back
import fr.moovie.tv.resources.player_seek_forward
import fr.moovie.tv.ui.player.ApplySubtitleStyle
import fr.moovie.tv.ui.player.MooviePlayerController
import fr.moovie.tv.ui.player.PLAYER_SEEK_STEP_MS
import fr.moovie.tv.ui.player.PlayerEpisodesPanel
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
import org.jetbrains.compose.resources.stringResource

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
 * Combien de temps le retour visuel du double appui reste affiché.
 *
 * Assez pour être lu, assez peu pour ne pas traîner sur l'image — et surtout
 * plus long que l'intervalle entre deux double-appuis d'une même série, sans
 * quoi le cumul repartirait de zéro entre deux gestes qui s'enchaînent.
 */
private const val SEEK_FEEDBACK_MS = 800L

/**
 * Le saut que le double appui vient de faire, tel qu'on l'annonce.
 *
 * Le cumul plutôt que le pas : trois appuis du même côté avancent de 45 s, et
 * afficher « 15 s » trois fois de suite laisserait croire que seul le dernier a
 * compté. Le côté est retenu parce qu'il décide de quel bord l'indicateur
 * s'affiche — et parce que changer de côté doit remettre le compte à zéro.
 */
private data class RetourSaut(val versArriere: Boolean, val cumulMs: Long)

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
    // L'identité du média : série, saison, épisode. Lue une fois, elle sert au
    // panneau des épisodes — qui n'existe que si l'on sait quelle série joue.
    val identiteMedia = remember(mediaKey) { parseMediaKey(mediaKey) }
    var episodesOuverts by remember(mediaKey) { mutableStateOf(false) }

    // Vrai d'emblée : le lecteur ouvre son flux, et la boucle d'état ne dira le
    // contraire qu'un demi-tour plus tard. Partir de « ça joue » ferait
    // clignoter l'écran noir sans indicateur, précisément à l'instant où
    // l'attente est la plus longue.
    var chargement by remember { mutableStateOf(true) }
    var retourSaut by remember { mutableStateOf<RetourSaut?>(null) }

    fun signalActivity() {
        controlsVisible = true
        activityTick++
    }

    /**
     * Interrompt le décompte d'enchaînement.
     *
     * `finished` à false suffit : c'est la clé du `LaunchedEffect` qui porte le
     * décompte, le remettre l'annule. Même mécanique que sur le desktop.
     */
    fun annulerEnchainement() {
        finished = false
        autoNextSeconds = null
        signalActivity()
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
            // Relevé dans la boucle qui existe déjà plutôt que dans une seconde,
            // au prix d'un demi-tour de retard sur l'apparition du rond. Le
            // premier chargement, celui qui se remarque, est couvert par la
            // valeur initiale.
            chargement = controller.isBuffering
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

    // Effacement du retour de saut, relancé à chaque nouvel appui : la clé de
    // l'effet est la valeur elle-même, si bien qu'un second double appui repousse
    // la disparition au lieu de la laisser tomber au terme du premier.
    LaunchedEffect(retourSaut) {
        if (retourSaut == null) return@LaunchedEffect
        delay(SEEK_FEEDBACK_MS)
        retourSaut = null
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

    // Pas de fond peint ici : `UIKitView` découpe un trou dans la composition
    // pour y laisser voir la vue native, et chaque aplat opaque posé par-dessus
    // est une occasion de le recouvrir. La vue native peint son propre noir —
    // y compris les bandes que laisse le respect des proportions.
    Box(
        modifier = Modifier
            .fillMaxSize()
            // **Sans ces gestes, le lecteur était un cul-de-sac.** Les commandes
            // se rangent seules après quelques secondes, et rien ne les
            // rappelait : ni retour, ni pause, ni barre de progression — on
            // restait devant l'image jusqu'à tuer l'application. C'est le
            // premier essai sur iPhone qui l'a montré.
            //
            // Les deux gestes sont ceux d'Android, au comportement près :
            // l'appui simple bascule les commandes, le double appui recule ou
            // avance de quinze secondes selon la moitié touchée. Le lecteur du
            // téléviseur n'en a pas besoin — une télécommande n'émet pas
            // d'événement de pointeur — mais un téléphone n'a que ça.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        when {
                            // Un décompte en cours se lit comme « je ne veux pas
                            // de la suite » quand on touche l'écran : c'est la
                            // seule interprétation utile du geste à ce
                            // moment-là.
                            autoNextSeconds != null -> annulerEnchainement()
                            controlsVisible -> controlsVisible = false
                            else -> signalActivity()
                        }
                    },
                    onDoubleTap = { position ->
                        val versArriere = position.x < size.width / 2f
                        controller.seekBy(
                            if (versArriere) -PLAYER_SEEK_STEP_MS else PLAYER_SEEK_STEP_MS,
                        )
                        // Le geste marchait déjà, mais rien ne le disait : la
                        // vidéo sautait sans qu'on sache si c'était la source qui
                        // avait toussé ou l'appui qui avait porté. On cumule tant
                        // que les appuis restent du même côté ; changer de bord
                        // annonce une nouvelle série.
                        val precedent = retourSaut
                        retourSaut = RetourSaut(
                            versArriere = versArriere,
                            cumulMs = if (precedent?.versArriere == versArriere) {
                                precedent.cumulMs + PLAYER_SEEK_STEP_MS
                            } else {
                                PLAYER_SEEK_STEP_MS
                            },
                        )
                        signalActivity()
                    },
                )
            },
    ) {
        surface(Modifier.fillMaxSize())

        // **L'image garde la dalle, les commandes se rangent dedans.**
        //
        // C'est le seul écran qui ne retire pas les encoches à sa racine, et
        // c'est voulu : rogner un film reviendrait à l'afficher en médaillon.
        // Mais la conséquence était que sa chrome ne les retirait pas non plus.
        // Un iPhone en paysage met la Dynamic Island sur un bord — une
        // cinquantaine de points — et l'indicateur d'accueil en bas : le retour
        // ou l'icône de fin de rangée y passaient dessous selon le sens de
        // rotation, et la barre de progression courait sous la poignée.
        //
        // Les seize points de `margePage()` ne pouvaient rien pour ça : ils sont
        // une marge de mise en page, pas une mesure de l'appareil. Chaque
        // couche de commandes prend donc la marge de sécurité, et elle seule.
        val marqueSecurite = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)

        // **Chaque couche porte son alignement, et la barre n'en a pas d'elle-même.**
        //
        // `PlayerControlBar` est un `Column(fillMaxWidth)` dont le dégradé va du
        // transparent au noir vers le bas : elle est faite pour être posée en
        // bas de l'écran, mais n'expose aucun `modifier` pour le dire. Sans
        // enveloppe alignée, un `Box` la place en haut à gauche — elle recouvrait
        // alors le titre et assombrissait le haut de l'image, ce qui donnait un
        // lecteur dont tout se tassait sous l'encoche.
        //
        // Android et le desktop l'enveloppent tous deux dans une
        // `AnimatedVisibility` alignée ; on fait pareil, ce qui rend au passage
        // le glissement d'apparition qu'ils ont et que ce lecteur n'avait pas.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).then(marqueSecurite),
        ) {
            PlayerTitleOverlay(title = title, subtitle = subtitle)
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).then(marqueSecurite),
        ) {
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
                    controller.seekBy(-PLAYER_SEEK_STEP_MS)
                    signalActivity()
                },
                onSeekForward = {
                    controller.seekBy(PLAYER_SEEK_STEP_MS)
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
                // Seulement sur une série : la clé de média porte l'identifiant
                // TMDB, et sans lui le panneau n'a rien à lister. Là où il est
                // branché, il remplace les deux flèches — voir `PlayerControlBar`.
                onOpenEpisodes = identiteMedia?.takeIf { it.isTv }?.let {
                    { episodesOuverts = true }
                },
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
                modifier = Modifier.align(Alignment.BottomEnd)
                    .then(marqueSecurite)
                    .padding(24.dp),
            )
        }

        autoNextSeconds?.let { secondes ->
            Text(
                text = "$secondes",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // **Le rond d'attente, que la vue native ne fournit pas.**
        //
        // Android rend sa vidéo dans une `PlayerView` et le desktop dans mpv :
        // tous deux affichent leur propre indicateur pendant la mise en mémoire
        // tampon. Un `AVPlayerLayer` ne montre rien — on ouvrait donc le lecteur
        // sur un écran noir, immobile et muet, sans moyen de distinguer un flux
        // qui charge d'un flux qui ne viendra jamais.
        //
        // Pas pendant le décompte d'enchaînement : le média est fini, ce qui
        // tourne alors n'est pas une attente de données.
        if (chargement && autoNextSeconds == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
            )
        }

        // Le retour visuel du double appui, du côté touché — voir [RetourSaut].
        retourSaut?.let { saut ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(
                        if (saut.versArriere) Alignment.CenterStart else Alignment.CenterEnd,
                    )
                    .padding(horizontal = 40.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                // La flèche circulaire de la barre de contrôles, miroitée pour
                // l'avance comme elle l'est là-bas : deux façons de faire le même
                // saut ne doivent pas porter deux dessins différents.
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = stringResource(
                        if (saut.versArriere) Res.string.player_seek_back
                        else Res.string.player_seek_forward,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp).then(
                        if (saut.versArriere) Modifier else Modifier.scale(scaleX = -1f, scaleY = 1f),
                    ),
                )
                Text(
                    text = "${saut.cumulMs / 1000} s",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // La liste des épisodes, en panneau glissant plutôt qu'en modale
        // centrée : on choisit un épisode en gardant l'image sous les yeux.
        //
        // **En dernier, comme sur Android et sur le bureau.** Il était composé
        // juste après la surface vidéo, donc *sous* tout le reste : la barre de
        // contrôles, qui occupe toute la largeur, se peignait par-dessus le
        // panneau et interceptait les appuis destinés à la liste. Dans un `Box`,
        // l'ordre de composition est l'ordre d'empilement — un panneau qui
        // recouvre l'écran doit être écrit après ce qu'il recouvre.
        identiteMedia?.takeIf { it.isTv }?.let { identite ->
            PlayerEpisodesPanel(
                visible = episodesOuverts,
                tmdbId = identite.tmdbId,
                saisonCourante = identite.season,
                episodeCourant = identite.episode,
                onJouer = { saison, numero ->
                    episodesOuverts = false
                    onNextEpisode(saison, numero)
                },
                onFermer = { episodesOuverts = false },
            )
        }
    }
}
