package fr.moovie.tv.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import fr.moovie.tv.data.remote.TypingField
import fr.moovie.tv.resources.remote_ok
import fr.moovie.tv.resources.common_back
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.theme.MoovieShape
import fr.moovie.tv.data.remote.NowPlaying
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.ui.player.PlayerControlBar
import fr.moovie.tv.ui.player.PlayerTitleOverlay
import fr.moovie.tv.ui.player.parseMediaKey
import fr.moovie.tv.data.remote.RemoteClient
import fr.moovie.tv.data.remote.RemoteKey
import fr.moovie.tv.data.remote.RemotePresence
import fr.moovie.tv.data.remote.RemoteStatus
import fr.moovie.tv.data.remote.RemoteTarget
import fr.moovie.tv.data.remote.RemoteTargetRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.remote_forget_target
import fr.moovie.tv.resources.remote_keyboard_hint
import fr.moovie.tv.resources.remote_offline
import fr.moovie.tv.resources.remote_offline_help
import fr.moovie.tv.ui.adaptive.isPointerUi
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.theme.MOOVIE_MAGENTA
import fr.moovie.tv.ui.theme.MOOVIE_ORANGE
import fr.moovie.tv.ui.theme.MOOVIE_VIOLET
import fr.moovie.tv.ui.theme.MoovieGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Télécommande virtuelle, sur le téléphone, pour piloter le téléviseur.
 *
 * ### Un joystick, pas quatre boutons
 *
 * Le geste est suivi sur le disque entier : on pose le pouce, on le garde, on
 * tourne, et la direction suit sans relâcher. Trois règles font la sensation, et
 * chacune corrige un défaut ressenti avant elle :
 *
 * - une **zone morte** au centre, du rayon du bouton OK ;
 * - une **hystérésis** de 8° sur les diagonales, sans quoi la direction
 *   oscillerait entre deux flèches au moindre tremblement ;
 * - **OK ne part que si le geste a commencé sur lui** — glisser dessus depuis
 *   une flèche ne valide jamais, ce qui serait le pire des accidents à la
 *   navigation.
 *
 * ### Partagée, mais pas identique — le pointeur change tout
 *
 * Elle a longtemps été Android uniquement, au motif que « la télécommande n'a de
 * sens que sur l'appareil qu'on tient ». C'était vrai de l'appareil **piloté**,
 * pas de celui qui pilote : on regarde une fiche sur son ordinateur comme sur
 * son téléphone, et l'envoyer au salon appelle les mêmes commandes ensuite.
 *
 * Ce qui se partage vraiment est la **moitié invisible** : le relevé d'état, la
 * recopie de progression, la présence, la saisie de texte. La chrome, elle,
 * diverge — et le joystick est la raison. Le porter tel quel donnait un geste au
 * pouce à faire à la souris, ce que personne ne fait. Au pointeur, l'écran
 * remplace donc le disque par des boutons ordinaires, **écoute le clavier**
 * ([remoteKeyFor]) et se donne une sortie visible : il n'y a pas de touche
 * Retour sur un clavier de bureau, et la flèche ronde du bas n'en est pas une —
 * elle envoie `BACK` au téléviseur, exactement le contraire.
 *
 * Le reste des spécificités tient dans trois coutures : le vibreur
 * ([RemoteHaptics]), les touches physiques de volume ([CaptureVolumeKeys]) et
 * l'horloge monotone ([monotonicMs]).
 */
@Composable
fun RemoteScreen(
    target: RemoteTarget,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /**
     * Au pointeur, la télécommande change de nature.
     *
     * Lu depuis [LocalUiFlavor] et non reçu en paramètre : c'est la règle du
     * projet, la distinction est d'**exécution**. Un drapeau passé à la main
     * aurait fini par mentir — il suffit d'un appelant qui l'oublie.
     */
    val pointer = isPointerUi
    val clavier = remember { FocusRequester() }
    /** Dernière répétition clavier laissée passer. Voir [acceptKeyRepeat]. */
    var derniereTouche by remember { mutableLongStateOf(0L) }
    val client = remember(target) { RemoteClient(target) }
    // Le magasin **du téléphone**. C'est lui qui fait foi : dès que les deux
    // appareils ne partagent pas le même compte de synchronisation, chacun a le
    // sien et rien ne les réconcilie jamais.
    val progress = remember { WatchProgressRepository() }
    val scope = rememberCoroutineScope()

    /** Direction maintenue, ou null. Elle pilote aussi la répétition. */
    var held by remember { mutableStateOf<RemoteKey?>(null) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    /**
     * Le téléviseur répond-il ? `null` tant qu'on cherche.
     *
     * Nourri par deux sources : la sonde à l'ouverture, et l'échec d'une touche.
     * La seconde compte autant que la première — c'est elle qu'on remarque, et
     * une touche perdue est silencieuse par construction ([RemoteClient]).
     */
    var online by remember { mutableStateOf<Boolean?>(null) }

    // Découverte à l'ouverture. L'adresse et le port du téléviseur périment
    // — bail DHCP pour l'une, redémarrage de l'application pour l'autre — et
    // c'est le seul moment où les rafraîchir a un coût acceptable : on vient
    // d'ouvrir l'écran, on ne s'en sert pas encore. Le dépôt mis à jour
    // recompose l'écran avec la bonne cible, et `remember(target)` reconstruit
    // le client dessus.
    LaunchedEffect(Unit) { online = RemotePresence.refresh() }

    /** Ce que le téléviseur lit, ou null : rien en cours, ou pas de réponse. */
    var now by remember(target) { mutableStateOf<NowPlaying?>(null) }

    /** Le champ qui attend une saisie sur le téléviseur, ou null. */
    var typing by remember(target) { mutableStateOf<TypingField?>(null) }

    /** Ce qu'on tape ici. Distinct de la valeur relevée, qui a une seconde de retard. */
    var draft by remember(target) { mutableStateOf("") }

    /** Position affichée. Avancée entre deux relevés pour que la barre glisse. */
    var shownMs by remember(target) { mutableLongStateOf(0L) }

    /** Position visée pendant qu'on fait glisser : la barre suit le doigt. */
    var scrubMs by remember(target) { mutableStateOf<Long?>(null) }

    /**
     * Instant après lequel on refait confiance au téléviseur.
     *
     * Un saut met un aller-retour à se voir : le relevé qui suit immédiatement
     * un `seek` rend encore l'ancienne position, et la barre revenait en arrière
     * sous le doigt avant de repartir. On ignore donc les relevés le temps que
     * l'ordre soit pris en compte.
     */
    var trustAfter by remember(target) { mutableLongStateOf(0L) }

    fun send(key: RemoteKey) {
        scope.launch {
            val ok = client.key(key)
            online = ok
            if (!ok) RemotePresence.lost()
        }
    }

    fun seekTo(positionMs: Long) {
        shownMs = positionMs
        scrubMs = null
        trustAfter = monotonicMs() + SEEK_SETTLE_MS
        scope.launch {
            val ok = client.seek(positionMs)
            online = ok
            if (!ok) RemotePresence.lost()
        }
    }

    // Relevé de l'état du téléviseur. Une seconde en lecture, deux à l'arrêt :
    // c'est une requête d'environ 200 octets sur le réseau local, mais elle
    // réveille la radio du téléphone à chaque fois — la ralentir quand rien ne
    // bouge ne coûte rien à l'affichage.
    LaunchedEffect(target) {
        // Relevés muets consécutifs. Un silence isolé ne prouve rien — un
        // paquet perdu suffit — et effacer le mini-lecteur dessus le faisait
        // clignoter. Il faut une petite série pour conclure à une absence.
        var silences = 0
        // Dernière position recopiée, pour n'écrire qu'à intervalle : le relevé
        // arrive chaque seconde, et DataStore réécrit tout son fichier à chaque
        // fois. La reprise n'a pas besoin d'être à la seconde près.
        var lastMirrorMs = 0L
        while (true) {
            when (val status = client.status()) {
                is RemoteStatus.Known -> {
                    silences = 0
                    online = true
                    now = status.state.now
                    typing = status.state.typing
                    val playing = status.state.now
                    if (playing != null && scrubMs == null &&
                        monotonicMs() >= trustAfter
                    ) {
                        shownMs = playing.positionMs
                    }
                    // Ce que la TV joue est enregistré **ici**, dans le magasin
                    // du téléphone. Voir mirrorProgress : sans ça la progression
                    // ne circule que dans un sens.
                    // En lecture, on suit ; à l'arrêt, on rattrape ce que la
                    // box a joué en dernier — c'est ce qui couvre le téléphone
                    // qui était fermé pendant qu'elle continuait.
                    (playing ?: status.state.lastPlayed)
                        ?.let { mirrorProgress(progress, it, lastMirrorMs) }
                        ?.let { lastMirrorMs = it }
                }
                // Pas de réponse : on **garde** ce qu'on affichait. Ce n'est
                // qu'après quelques silences d'affilée qu'on admet l'absence.
                RemoteStatus.Unreachable -> {
                    silences++
                    if (silences >= SILENCES_BEFORE_LOST) {
                        online = false
                        now = null
                        typing = null
                        RemotePresence.lost()
                    }
                }
            }
            delay(if (now?.playing == true) STATE_POLL_MS else STATE_IDLE_POLL_MS)
        }
    }

    // Entre deux relevés, la barre avance toute seule. Sans cela elle sauterait
    // d'une seconde entière à chaque réponse, ce qui se voit immédiatement comme
    // un à-coup — alors que la position, elle, est parfaitement connue.
    // Nouveau champ : on repart de ce qu'il contient déjà. Sur son seul label —
    // se recaler sur chaque relevé écraserait ce qu'on est en train de taper,
    // puisque la valeur relevée a toujours une seconde de retard sur le doigt.
    LaunchedEffect(typing?.label) { draft = typing?.value.orEmpty() }

    // Envoi au fil de la frappe, amorti. Sans l'amortissement, chaque lettre
    // serait une requête ; avec un délai trop long, la TV traîne visiblement
    // derrière le clavier.
    LaunchedEffect(draft, typing?.label) {
        val field = typing ?: return@LaunchedEffect
        if (draft == field.value) return@LaunchedEffect
        delay(TYPE_DEBOUNCE_MS)
        if (!client.text(draft)) RemotePresence.lost()
    }

    LaunchedEffect(now?.playing, scrubMs != null) {
        if (now?.playing != true || scrubMs != null) return@LaunchedEffect
        while (true) {
            delay(TICK_MS)
            shownMs += TICK_MS
        }
    }

    fun fire(key: RemoteKey, tick: HapticTick) {
        RemoteHaptics.tick(tick)
        send(key)
    }

    // Les touches de volume du téléphone règlent le son du téléviseur, tant que
    // cet écran est affiché. Il n'y a volontairement pas de bouton à l'écran en
    // face : le geste qu'on cherche ici est celui qu'on fait sans regarder, et
    // un doublon tactile n'ajouterait qu'une chose à lire. Voir
    // [CaptureVolumeKeys] pour ce que coûte le détournement, et sa borne.
    CaptureVolumeKeys { key ->
        fire(key, if (key == RemoteKey.MUTE) HapticTick.PRESS else HapticTick.STEP)
    }

    // Répétition au maintien : garder une flèche enfoncée fait défiler, comme
    // sur une vraie télécommande. Le premier envoi est fait par le geste ; ici
    // on ne s'occupe que de la suite. Changer de direction relance l'effet, donc
    // le compte repart — c'est ce qu'on attend en tournant le pouce.
    LaunchedEffect(held) {
        val key = held ?: return@LaunchedEffect
        delay(FIRST_REPEAT_MS)
        while (true) {
            RemoteHaptics.tick(HapticTick.STEP)
            send(key)
            delay(REPEAT_MS)
        }
    }

    // Le clavier **est** la télécommande sur un poste de travail. Hissé en
    // valeur parce que les deux mises en page — lecteur et navigation — doivent
    // l'écouter également. Voir [remoteKeyFor] : Échap n'en fait pas partie,
    // c'est la sortie de l'écran, et l'envoyer à la télé enfermerait.
    val ecouteClavier =
                if (!pointer) Modifier else Modifier
                    .focusRequester(clavier)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        // **Les deux moitiés de l'appui sont consommées**, la
                        // seconde sans rien envoyer. C'est la leçon déjà écrite
                        // dans `RemoteVolumeKeys.handle` côté Android, et le
                        // chemin clavier du desktop l'a rejouée : Compose
                        // active un bouton focalisé sur le **relâchement** de
                        // Espace ou Entrée. En ne filtrant que l'appui, une
                        // pression après un clic partait deux fois — une par
                        // ici, une par le bouton d'en dessous. Mesuré : 2.
                        if (event.key == Key.Escape) {
                            if (event.type == KeyEventType.KeyDown) onBack()
                            return@onPreviewKeyEvent true
                        }
                        val touche = remoteKeyFor(event.key) ?: return@onPreviewKeyEvent false
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        // La répétition du système est gardée — c'est elle qui
                        // fait défiler — mais amortie : maintenue, une flèche
                        // produit 25 requêtes par seconde sur le réseau local.
                        // On consomme quand même l'événement : le laisser
                        // repartir déplacerait le focus de la fenêtre.
                        val maintenant = monotonicMs()
                        if (acceptKeyRepeat(maintenant, derniereTouche)) {
                            derniereTouche = maintenant
                            fire(touche, HapticTick.STEP)
                        }
                        true
                    }

    // Le focus au montage : sans lui, le clavier ne pilote rien tant qu'on n'a
    // pas cliqué — exactement le geste qu'on cherche à éviter.
    LaunchedEffect(pointer, now != null) {
        if (pointer) runCatching { clavier.requestFocus() }
    }

    /**
     * **L'écran suit ce que fait la box.**
     *
     * Elle joue quelque chose : c'est un lecteur qu'il faut, avec la jaquette à
     * la place du flux et une barre qu'on clique. Une télécommande est un objet
     * qu'on tient sans regarder ; devant un écran d'ordinateur, l'objet naturel
     * est le lecteur, et le pavé directionnel n'y a rien à faire tant que rien
     * n'est à naviguer.
     *
     * Elle est dans ses menus : les flèches reviennent, seules. Même règle que
     * le mini-lecteur plus bas — un bloc absent ne pose aucune question, un
     * contrôle sans objet en pose une.
     */
    val enLecture = now
    if (pointer && enLecture != null) {
        RemotePlayerLayout(
            now = enLecture,
            positionMs = scrubMs ?: shownMs,
            listenKeyboard = ecouteClavier,
            onBack = onBack,
            onTogglePause = {
                // L'icône bascule avant confirmation : attendre le relevé ferait
                // un bouton qui met une seconde à réagir, donc un bouton sur
                // lequel on appuie deux fois.
                now = now?.let { it.copy(playing = !it.playing) }
                fire(RemoteKey.PLAY_PAUSE, HapticTick.PRESS)
            },
            onSeekBack = { fire(RemoteKey.REWIND, HapticTick.STEP) },
            onSeekForward = { fire(RemoteKey.FORWARD, HapticTick.STEP) },
            onSeekToFraction = { part ->
                if (enLecture.durationMs > 0) seekTo((part * enLecture.durationMs).toLong())
            },
            modifier = modifier,
        )
        return
    }

    Column(
        // Défilement vertical : le bandeau hors ligne ajoute trois lignes et un
        // bouton, et le pavé fait déjà 288 dp. Sur un écran court, la touche
        // Retour finissait sous le bord.
        modifier = modifier
            .fillMaxSize()
            .then(ecouteClavier)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        // La sortie d'abord, en haut à gauche : c'est là qu'on la cherche, et
        // elle doit rester atteignable avant même de comprendre le reste.
        if (pointer) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MoovieIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.common_back),
                )
                Spacer(Modifier.weight(1f))
            }
        }
        Text(
            target.name,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF9A9A9A),
        )
        if (online == false) {
            Spacer(Modifier.height(16.dp))
            OfflineNotice(
                onForget = {
                    // On dépile d'abord : sans cible, cet écran n'a plus rien à
                    // afficher, et la composition le viderait sous les doigts.
                    onBack()
                    scope.launch {
                        RemoteTargetRepository().forget()
                        RemotePresence.lost()
                    }
                },
            )
        }
        // Saisie : le téléviseur dit qu'un champ attend quelque chose, le
        // téléphone ouvre son clavier. C'est ce qui manquait — la saisie
        // existait, mais il fallait deviner qu'elle était possible et ouvrir le
        // clavier soi-même.
        typing?.let { field ->
            Spacer(Modifier.height(20.dp))
            TypingPanel(
                field = field,
                draft = draft,
                onDraftChange = { draft = it },
                onSubmit = { send(RemoteKey.OK) },
            )
        }

        // Mini-lecteur : présent seulement quand quelque chose joue. Rien à
        // replier, rien à griser — un bloc absent ne pose aucune question.
        now?.let { playing ->
            Spacer(Modifier.height(20.dp))
            NowPlayingPanel(
                now = playing,
                positionMs = scrubMs ?: shownMs,
                scrubbing = scrubMs != null,
                onScrub = { scrubMs = it },
                onScrubEnd = { scrubMs?.let(::seekTo) },
                onScrubCancel = { scrubMs = null },
                onSeekTo = ::seekTo,
            )
        }

        Spacer(Modifier.height(28.dp))

        // Le disque est un geste **au pouce** : on pose, on maintient, on
        // tourne. Rien de tout ça ne se fait à la souris — d'où un pavé de
        // boutons ordinaires au pointeur, et le clavier pour l'essentiel.
        if (!pointer) {
                Box(
                    modifier = Modifier
                        .size(288.dp)
                        .onSizeChanged { padSize = it }
                    .pointerInput(target) {
                        awaitEachGesture {
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            val dead = size.width * DEAD_ZONE
                            val down = awaitFirstDown(requireUnconsumed = false)

                            // Le geste qui **commence** au centre est un appui sur
                            // OK, et rien d'autre : il ne se transforme pas en
                            // direction si le doigt glisse ensuite.
                            if ((down.position - centre).getDistance() < dead) {
                                fire(RemoteKey.OK, HapticTick.PRESS)
                                do {
                                    val e = awaitPointerEvent()
                                } while (e.changes.any { it.pressed })
                                return@awaitEachGesture
                            }

                            var current: RemoteKey? = null
                            fun apply(position: Offset) {
                                val next = directionAt(position, centre, dead, current)
                                if (next == current) return
                                current = next
                                held = next
                                next?.let { fire(it, HapticTick.STEP) }
                            }
                            apply(down.position)

                            do {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull { it.id == down.id }?.let { apply(it.position) }
                            } while (event.changes.any { it.pressed })

                            held = null
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                DirectionDisc(held)

                // Les flèches sont posées **au-dessus** du disque et ne reçoivent
                // aucun geste : c'est le disque qui suit le doigt. Elles ne sont là
                // que pour dire où viser.
                Icon(Icons.Default.KeyboardArrowUp, null, tint = arrowTint(held, RemoteKey.UP), modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(38.dp))
                Icon(Icons.Default.KeyboardArrowDown, null, tint = arrowTint(held, RemoteKey.DOWN), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).size(38.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = arrowTint(held, RemoteKey.LEFT), modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(38.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = arrowTint(held, RemoteKey.RIGHT), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(38.dp))

                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF1D1D24), Color(0xFF141419))))
                        .border(2.dp, MoovieGradient, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // Une pastille, parce qu'un anneau vide se lit comme un trou
                    // dans le disque et non comme une touche.
                    Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF5A5A66)))
                }
            }
        } else {
            PointerPad(onKey = { fire(it, HapticTick.STEP) })
            Spacer(Modifier.height(14.dp))
            // Les boutons restent, mais l'aide dit où est le vrai geste. Sans
            // elle, personne ne devine qu'il n'a pas à viser à la souris.
            Text(
                stringResource(Res.string.remote_keyboard_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7A7A7A),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RoundKey(Icons.Default.FastRewind, 60.dp) { fire(RemoteKey.REWIND, HapticTick.STEP) }
            // L'icône dit l'état réel du téléviseur, et bascule **avant** d'en
            // avoir la confirmation : attendre le relevé suivant ferait un
            // bouton qui met une seconde à réagir, donc un bouton sur lequel on
            // appuie deux fois. Le relevé corrigera si l'ordre s'est perdu.
            RoundKey(
                icon = if (now?.playing == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                size = 72.dp,
            ) {
                now = now?.let { it.copy(playing = !it.playing) }
                fire(RemoteKey.PLAY_PAUSE, HapticTick.PRESS)
            }
            RoundKey(Icons.Default.FastForward, 60.dp) { fire(RemoteKey.FORWARD, HapticTick.STEP) }
        }
        Spacer(Modifier.height(20.dp))
        RoundKey(Icons.AutoMirrored.Filled.ArrowBack, 60.dp) {
            fire(RemoteKey.BACK, HapticTick.BACK)
        }

        // Pas de flèche pour quitter l'écran.
        //
        // Il y en a eu une, et elle ne pouvait que nuire : posée juste sous la
        // touche Retour ronde, elle faisait voisiner deux gestes **opposés** —
        // celle du haut envoie Retour au téléviseur, celle du bas quittait la
        // télécommande. Rien ne les distinguait à l'œil, et le seul retour
        // qu'on attend en tenant une télécommande est celui qui agit sur la
        // télé. Le geste système et la barre du bas suffisent pour sortir.
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Le clavier du téléphone, au service d'un champ du téléviseur.
 *
 * ### Il apparaît tout seul
 *
 * C'est ce qui change tout : la saisie à distance existait déjà, mais il fallait
 * savoir qu'elle existait, ouvrir un clavier à la main, et taper sans voir ce
 * que le champ contenait. Le téléviseur annonce désormais ses champs, et le
 * panneau se montre — puis disparaît quand le champ perd le focus.
 *
 * [TypingField.label] est repris tel quel : « Rechercher un film… » dit ce qu'on
 * remplit sans avoir à lever les yeux vers l'écran, ce qui est précisément le
 * geste qu'on cherche à éviter.
 *
 * Le focus est demandé à l'apparition pour que le clavier système s'ouvre sans
 * un appui de plus. Un champ qui surgit et qu'il faut ensuite toucher aurait
 * gardé la moitié du défaut qu'on corrige.
 */
@Composable
private fun TypingPanel(
    field: TypingField,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(field.label) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            field.label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF9A9A9A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.5.dp, Color(0xFF555555), MoovieShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                    cursorBrush = SolidColor(MOOVIE_MAGENTA),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            MoovieButton(onClick = onSubmit) { Text(stringResource(Res.string.remote_ok)) }
        }
    }
}

/**
 * Le mini-lecteur : ce qui passe sur le téléviseur, et où on en est.
 *
 * ### Ce qu'il change
 *
 * La télécommande envoyait des touches à l'aveugle. Avec l'état, le téléphone
 * devient un second écran : on voit ce qu'on pilote. C'est aussi ce qui rend le
 * bouton lecture honnête — il montrait une flèche de lecture y compris pendant
 * la lecture, faute de savoir.
 *
 * ### La barre se prend au doigt
 *
 * Une barre qui avance sous les yeux, on essaie de la toucher : la laisser
 * inerte se lit comme une panne. Elle accepte donc le glissement **et** l'appui
 * simple, qui sont deux gestes distincts pour la même intention.
 *
 * Pendant le glissement, [positionMs] vient du doigt et non du téléviseur — la
 * barre ne doit pas se battre avec les relevés, sinon elle tremble. C'est
 * seulement en relâchant que l'ordre part.
 *
 * La zone tactile fait 32 dp de haut pour un trait de 4 dp : viser une ligne de
 * quatre pixels avec un pouce est impossible, et l'épaissir visuellement en
 * ferait une barre de lecteur de salon, hors de propos ici.
 */
@Composable
private fun NowPlayingPanel(
    now: NowPlaying,
    positionMs: Long,
    scrubbing: Boolean,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onScrubCancel: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val duration = now.durationMs
    val fraction = if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (now.artwork.isNotBlank()) {
            MoovieAsyncImage(
                model = now.artwork,
                contentDescription = null,
                modifier = Modifier.size(width = 60.dp, height = 90.dp).clip(MoovieShape),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                now.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (now.subtitle.isNotBlank()) {
                Text(
                    now.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9A9A9A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            // Deux détecteurs, deux gestes. Le glissement ne démarre qu'après le
            // seuil de déplacement, si bien qu'un appui simple ne le déclenche
            // jamais et que les deux peuvent cohabiter sur la même zone.
            .pointerInput(duration) {
                if (duration <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { onScrub(msAt(it.x, size.width, duration)) },
                    // Sans argument : la dernière position visée est déjà celle
                    // que le parent tient, puisque c'est lui qui la reçoit à
                    // chaque déplacement. `onDragEnd`, lui, ne la connaît pas —
                    // relâcher aurait envoyé le téléviseur là où le doigt s'est
                    // posé, pas là où il a été relevé.
                    onDragEnd = onScrubEnd,
                    onDragCancel = onScrubCancel,
                    onHorizontalDrag = { change, _ ->
                        onScrub(msAt(change.position.x, size.width, duration))
                    },
                )
            }
            .pointerInput(duration) {
                if (duration <= 0) return@pointerInput
                detectTapGestures { onSeekTo(msAt(it.x, size.width, duration)) }
            },
    ) {
        val y = size.height / 2f
        val track = 4.dp.toPx()
        drawRect(
            color = Color(0xFF33333B),
            topLeft = Offset(0f, y - track / 2f),
            size = Size(size.width, track),
        )
        drawRect(
            brush = MoovieGradient,
            topLeft = Offset(0f, y - track / 2f),
            size = Size(size.width * fraction, track),
        )
        // La pastille n'apparaît qu'au doigt : au repos, la barre est un
        // indicateur et rien d'autre. Pendant le geste, il faut voir *où* on
        // vise, alors que le pouce couvre justement cet endroit.
        if (scrubbing) {
            drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(size.width * fraction, y))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(formatClock(positionMs), style = MaterialTheme.typography.labelSmall, color = Color(0xFF9A9A9A))
        if (duration > 0) {
            // Le temps **restant**, pas la durée : c'est la question qu'on se
            // pose devant un film (« il en reste combien ? »), et la durée est
            // déjà déductible de la barre.
            Text(
                "-" + formatClock(duration - positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9A9A9A),
            )
        }
    }
}

/** Position visée par un doigt posé à [x] sur une barre de [width] pixels. */
internal fun msAt(x: Float, width: Int, durationMs: Long): Long =
    ((x / width).coerceIn(0f, 1f) * durationMs).toLong()

/** `h:mm:ss` au-delà d'une heure, `m:ss` en deçà — comme le lecteur. */
internal fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

/**
 * « Le téléviseur ne répond pas », et la seule sortie qui existe.
 *
 * Un bandeau plutôt qu'un écran d'erreur : le pavé reste utilisable dessous,
 * parce qu'un téléviseur qui vient de rallumer répondra à l'appui suivant sans
 * qu'on ait rien à faire — et le bandeau disparaîtra de lui-même.
 *
 * Le bouton d'oubli est ce qui rend la situation réparable **depuis le
 * téléphone**. Sans lui, une cible fausse ou périmée — un jeton révoqué depuis
 * les réglages du téléviseur, un appairage sur un réseau qu'on a quitté — ne
 * pouvait se corriger qu'en désinstallant l'application.
 */
@Composable
private fun OfflineNotice(onForget: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.remote_offline),
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFFE0A0A0),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.remote_offline_help),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9A9A9A),
            textAlign = TextAlign.Center,
        )
        MoovieButton(onClick = onForget) {
            Text(stringResource(Res.string.remote_forget_target))
        }
    }
}

/** Le disque, et la lueur du secteur tenu. */
@Composable
private fun DirectionDisc(held: RemoteKey?) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF23232B), Color(0xFF141419)),
                center = Offset(centre.x, centre.y - radius * 0.35f),
                radius = radius * 1.6f,
            ),
            radius = radius,
            center = centre,
        )
        val key = held ?: return@Canvas
        val glow = when (key) {
            RemoteKey.UP -> MOOVIE_ORANGE
            RemoteKey.DOWN -> MOOVIE_VIOLET
            else -> MOOVIE_MAGENTA
        }
        // La lueur est bornée au secteur **et** au disque : sans le second
        // rognage elle déborderait en carré, le triangle allant jusqu'aux coins.
        val sector = Path().apply {
            moveTo(centre.x, centre.y)
            when (key) {
                RemoteKey.UP -> { lineTo(0f, 0f); lineTo(size.width, 0f) }
                RemoteKey.DOWN -> { lineTo(size.width, size.height); lineTo(0f, size.height) }
                RemoteKey.LEFT -> { lineTo(0f, size.height); lineTo(0f, 0f) }
                else -> { lineTo(size.width, 0f); lineTo(size.width, size.height) }
            }
            close()
        }
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(centre - Offset(radius, radius), centre + Offset(radius, radius))) }
        val clipped = Path().apply { op(sector, disc, androidx.compose.ui.graphics.PathOperation.Intersect) }
        // Le foyer se pose sous la flèche, pas au centre : centré, il éclairait
        // le point de fuite des quatre secteurs et le découpage se voyait.
        val focus = when (key) {
            RemoteKey.UP -> Offset(centre.x, centre.y - radius * 0.66f)
            RemoteKey.DOWN -> Offset(centre.x, centre.y + radius * 0.66f)
            RemoteKey.LEFT -> Offset(centre.x - radius * 0.66f, centre.y)
            else -> Offset(centre.x + radius * 0.66f, centre.y)
        }
        clipPath(clipped) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.42f), Color.Transparent),
                    center = focus,
                    radius = radius * 0.85f,
                ),
                radius = radius,
                center = centre,
            )
        }
    }
}

@Composable
private fun RoundKey(icon: ImageVector, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF22222A), Color(0xFF16161C))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color(0xFFCFCFD6), modifier = Modifier.size(size * 0.42f))
    }
}

private fun arrowTint(held: RemoteKey?, key: RemoteKey): Color =
    if (held == key) Color.White else Color(0xFFCFCFD6)

/**
 * Direction visée, ou null dans la zone morte.
 *
 * [current] sert l'hystérésis : on ne quitte une flèche qu'en s'en écartant
 * nettement, sinon la diagonale ferait osciller la direction entre deux valeurs
 * au moindre tremblement du pouce.
 */
internal fun directionAt(
    position: Offset,
    centre: Offset,
    dead: Float,
    current: RemoteKey?,
): RemoteKey? {
    val dx = position.x - centre.x
    val dy = position.y - centre.y
    if (hypot(dx, dy) < dead) return null
    val degrees = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
    current?.let { if (angleGap(degrees, axisOf(it)) < HYSTERESIS) return it }
    return AXES.minByOrNull { angleGap(degrees, axisOf(it)) }
}

private val AXES = listOf(RemoteKey.RIGHT, RemoteKey.DOWN, RemoteKey.LEFT, RemoteKey.UP)

private fun axisOf(key: RemoteKey): Float = when (key) {
    RemoteKey.RIGHT -> 0f
    RemoteKey.DOWN -> 90f
    RemoteKey.LEFT -> 180f
    else -> 270f
}

private fun angleGap(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}

/** 45° sépare deux flèches ; 8° de plus évitent l'oscillation sur la diagonale. */
private const val HYSTERESIS = 53f

/** Rayon de la zone morte, en fraction de la largeur du disque : celui du bouton OK. */
private const val DEAD_ZONE = 0.19f

private const val FIRST_REPEAT_MS = 380L
private const val REPEAT_MS = 120L

/** Cadence des relevés d'état pendant la lecture. */
private const val STATE_POLL_MS = 1_000L

/** À l'arrêt, rien ne bouge : inutile de réveiller la radio à la même cadence. */
private const val STATE_IDLE_POLL_MS = 2_000L

/** Pas d'avance locale de la barre entre deux relevés. */
private const val TICK_MS = 200L

/**
 * Délai pendant lequel les relevés sont ignorés après un saut.
 *
 * Un aller-retour plus le temps qu'ExoPlayer se cale : en deçà, la barre revient
 * à l'ancienne position juste après qu'on l'a lâchée, ce qui donne l'impression
 * que le saut a été refusé alors qu'il est en cours.
 */
private const val SEEK_SETTLE_MS = 1_500L

/**
 * Relevés muets d'affilée avant de conclure que le téléviseur n'est plus là.
 *
 * Trois, soit trois à six secondes de tolérance. En deçà, un paquet perdu sur le
 * Wi-Fi suffisait à faire disparaître le mini-lecteur ; bien au-delà, on
 * afficherait longtemps une lecture qui n'existe plus.
 */
private const val SILENCES_BEFORE_LOST = 3

/**
 * Amortissement de la frappe avant d'envoyer au téléviseur.
 *
 * Assez court pour que la TV suive le doigt, assez long pour qu'un mot ne coûte
 * pas une requête par lettre.
 */
private const val TYPE_DEBOUNCE_MS = 250L

/**
 * Recopie dans le magasin du téléphone ce que le téléviseur est en train de
 * lire, et rend la position écrite — ou null si rien ne l'a été.
 *
 * ## Pourquoi le téléphone doit tenir ce compte
 *
 * La progression ne circulait que dans un sens. On envoyait un épisode à 12:34,
 * on le finissait sur la box, et le téléphone en restait à 12:34 : son rail
 * « Reprendre » mentait. La synchro finit par réconcilier — **à condition que
 * les deux appareils partagent le même compte**. Ce n'est pas le cas général :
 * chacun peut avoir le sien, et alors rien ne les rapproche jamais.
 *
 * Or tout ce qu'il faut arrive déjà : le relevé d'état porte la position, la
 * durée, et maintenant la clé du média ([NowPlaying.mediaKey]). Il ne manquait
 * que de l'écrire.
 *
 * ## `register` avant `save`, encore
 *
 * Même règle que côté téléviseur, et pour la même raison : `save` ne met à jour
 * qu'une entrée existante et abandonne en silence sinon. Le téléphone peut très
 * bien n'avoir jamais ouvert ce titre — on regarde sur la TV ce qu'on a lancé
 * depuis l'accueil.
 *
 * ## Ce qui n'est pas couvert
 *
 * L'écran de télécommande doit être ouvert. Fermer l'application pendant que la
 * box continue laisse le téléphone en arrière ; seule la synchro rattrape ce
 * cas, quand elle est configurée des deux côtés.
 */
internal suspend fun mirrorProgress(
    progress: WatchProgressRepository,
    now: NowPlaying,
    lastWrittenMs: Long,
): Long? {
    val id = parseMediaKey(now.mediaKey) ?: return null
    if (now.positionMs <= 0 || now.durationMs <= 0) return null
    // Un écart, pas un intervalle de temps : c'est la position qui compte, et
    // un saut en arrière doit s'enregistrer aussi vite qu'une avancée.
    if (kotlin.math.abs(now.positionMs - lastWrittenMs) < MIRROR_STEP_MS) return null

    progress.register(
        ResumeEntry(
            key = now.mediaKey,
            tmdbId = id.tmdbId,
            isTv = id.isTv,
            season = id.season,
            episode = id.episode,
            title = now.title,
            imageUrl = now.artwork.ifBlank { null },
        ),
    )
    progress.save(now.mediaKey, now.positionMs, now.durationMs)
    return now.positionMs
}

/**
 * Écart de position au-delà duquel on réécrit la reprise.
 *
 * DataStore réécrit tout son fichier à chaque édition, et le relevé arrive
 * chaque seconde : recopier à chaque fois userait le disque pour une précision
 * dont la reprise n'a aucun besoin. Dix secondes, c'est moins que ce qu'on
 * perdrait à rouvrir le titre.
 */
private const val MIRROR_STEP_MS = 10_000L

/**
 * Le pavé directionnel au pointeur : des boutons, pas un geste.
 *
 * Trois rangées en croix plutôt qu'un disque. Le disque suit le pouce — on pose,
 * on maintient, on tourne — et rien de cela ne se transpose à une souris : il
 * faudrait cliquer-glisser en arc de cercle pour ce que quatre boutons font en
 * un clic.
 *
 * Volontairement secondaire à l'écran : c'est le clavier qui pilote
 * ([remoteKeyFor]), et ces boutons ne sont là que pour la souris qui traîne et
 * pour montrer ce que l'écran sait faire.
 */
@Composable
private fun PointerPad(onKey: (RemoteKey) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundKey(Icons.Default.KeyboardArrowUp, 56.dp) { onKey(RemoteKey.UP) }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundKey(Icons.AutoMirrored.Filled.KeyboardArrowLeft, 56.dp) { onKey(RemoteKey.LEFT) }
            // OK garde l'anneau dégradé du disque : c'est la seule touche qu'on
            // cherche des yeux, et l'identité visuelle de la télécommande.
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1D1D24), Color(0xFF141419))))
                    .border(2.dp, MoovieGradient, CircleShape)
                    .clickable { onKey(RemoteKey.OK) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF5A5A66)))
            }
            RoundKey(Icons.AutoMirrored.Filled.KeyboardArrowRight, 56.dp) { onKey(RemoteKey.RIGHT) }
        }
        Spacer(Modifier.height(8.dp))
        RoundKey(Icons.Default.KeyboardArrowDown, 56.dp) { onKey(RemoteKey.DOWN) }
    }
}

/**
 * La télécommande sous forme de lecteur, quand la box joue quelque chose.
 *
 * ## Pourquoi un lecteur et pas une télécommande
 *
 * Un pavé directionnel est fait pour être tenu sans regarder — c'est un objet de
 * canapé. Devant un écran d'ordinateur, personne ne vise quatre flèches à la
 * souris pour mettre en pause : l'objet attendu est celui qu'on connaît déjà,
 * un lecteur, avec sa barre qu'on clique.
 *
 * ## Notre lecteur, pas un lecteur inventé
 *
 * La chrome vient de [PlayerControlBar] et [PlayerTitleOverlay], celles du vrai
 * lecteur : elles ne manipulent que des primitives et des lambdas, sans rien
 * savoir de Media3 ni de mpv. La télécommande hérite donc de la même barre, des
 * mêmes icônes et du même pas de 15 s — et de tout ce qui y sera corrigé
 * ensuite. `onSeekToFraction` y était déjà prévu « là où la barre est pilotable
 * au pointeur », ce qui est exactement ce cas.
 *
 * Trois choses n'ont pas de sens à distance et disparaissent d'elles-mêmes : le
 * tampon (`bufferedMs = 0`, on ne connaît pas celui de la box), les pistes et
 * les réglages du lecteur d'en face. Les rendre nuls efface les icônes plutôt
 * que de les griser — une cible inerte est une cible de trop.
 */
@Composable
private fun RemotePlayerLayout(
    now: NowPlaying,
    positionMs: Long,
    listenKeyboard: Modifier,
    onBack: () -> Unit,
    onTogglePause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black).then(listenKeyboard)) {
        // La jaquette **à la place du flux**. Recadrée plein cadre : c'est ce
        // que le lecteur ferait de la vidéo, et l'écran doit se lire comme lui.
        if (now.artwork.isNotBlank()) {
            MoovieAsyncImage(
                model = now.artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        PlayerTitleOverlay(
            title = now.title,
            subtitle = now.subtitle,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerControlBar(
                isPlaying = now.playing,
                positionMs = positionMs,
                durationMs = now.durationMs,
                // Jamais : le mode scrub est un geste au D-pad, et ici la barre
                // se clique directement.
                scrubbing = false,
                showEpisodeButtons = false,
                canGoPrevious = false,
                playFocus = remember { FocusRequester() },
                onBack = onBack,
                onTogglePause = onTogglePause,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onCommitScrub = {},
                onNudgeScrub = {},
                onPreviousEpisode = {},
                onNextEpisode = {},
                onActivity = {},
                onSeekToFraction = onSeekToFraction,
            )
        }
    }
}
