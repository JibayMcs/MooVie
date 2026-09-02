package fr.moovie.tv.ui.player

import fr.moovie.tv.shared.formaterDecimal
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fr.moovie.tv.ui.theme.MoovieShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.moovie.tv.data.intro.IntroMedia
import fr.moovie.tv.data.intro.Segment
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.common_cancel
import fr.moovie.tv.resources.player_audio
import fr.moovie.tv.resources.player_next_episode
import fr.moovie.tv.resources.player_next_in
import fr.moovie.tv.resources.player_cast
import fr.moovie.tv.resources.details_tab_episodes
import fr.moovie.tv.resources.player_pause
import fr.moovie.tv.resources.player_play
import fr.moovie.tv.resources.player_prev_episode
import fr.moovie.tv.resources.player_progress
import fr.moovie.tv.resources.player_seek_back
import fr.moovie.tv.resources.player_seek_forward
import fr.moovie.tv.resources.player_settings
import fr.moovie.tv.resources.report_segment
import fr.moovie.tv.resources.player_skip_intro
import fr.moovie.tv.resources.player_skip_outro
import fr.moovie.tv.resources.player_quality
import fr.moovie.tv.resources.player_quality_auto
import fr.moovie.tv.resources.player_speed
import fr.moovie.tv.resources.player_subtitles
import fr.moovie.tv.resources.player_subtitles_off
import fr.moovie.tv.resources.player_update_chip
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_MUTED
import fr.moovie.tv.ui.theme.margePage
import fr.moovie.tv.ui.theme.ESPACE_LARGE

// Chrome du lecteur partagée entre Android TV et desktop : barre de contrôles,
// barre de progression, menus, overlay de titre, boutons « Passer », pastille de
// mise à jour et décompte d'enchaînement.
//
// Tout ici ne manipule que des primitives et des lambdas : aucune dépendance à
// Media3 ni à VLCJ. Ce qui reste propre à chaque plateforme (surface vidéo,
// commandes natives, focus D-pad, keepScreenOn, MediaSession) vit dans l'écran
// appelant, qui fournit un [MooviePlayerController].

// ── Barre de contrôles ──────────────────────────────────────────────────────

/**
 * Barre du bas : boutons puis position / progression / durée.
 *
 * [showEpisodeButtons] n'affiche les flèches épisode précédent/suivant que pour
 * une série ; [canGoPrevious] les grise en début de saison.
 */
@Composable
fun PlayerControlBar(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    scrubbing: Boolean,
    /**
     * Fin du tampon, dessinée en piste secondaire. 0 = inconnue (desktop) : la
     * piste n'apparaît pas.
     */
    bufferedMs: Long = 0L,
    showEpisodeButtons: Boolean,
    canGoPrevious: Boolean,
    playFocus: FocusRequester,
    onBack: () -> Unit,
    onTogglePause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onCommitScrub: () -> Unit,
    onNudgeScrub: (Long) -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    /**
     * Menus du lecteur. **Nuls quand il n'y a pas de lecteur à régler** — la
     * télécommande pilote une box à distance et n'a accès ni à ses pistes ni à
     * ses réglages. Même règle que [onReportSegment] juste en dessous : l'icône
     * disparaît alors, une cible inerte étant une cible de trop.
     */
    /**
     * Ouvre la liste des épisodes.
     *
     * **Quand elle existe, les deux flèches disparaissent.** Elles ne savaient
     * dire que « d'un cran », sans jamais montrer où l'on en était ni ce qui
     * suivait ; le panneau répond aux deux, et garder les trois aurait fait deux
     * chemins vers le même geste — dont l'un occupe deux cibles de la rangée du
     * transport, là où l'on cherche pause et recul.
     *
     * Nulle sur un film, et sur les plateformes qui n'ont pas encore posé le
     * panneau : les flèches y restent, plutôt que de laisser une série sans
     * aucun moyen de changer d'épisode.
     */
    onOpenEpisodes: (() -> Unit)? = null,
    onOpenSubtitles: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    /**
     * Signalement d'un segment manquant à TheIntroDB. Null quand il n'y a rien
     * à signaler — segments déjà connus, ou pas de clé — auquel cas l'icône
     * n'apparaît pas du tout : une cible inerte est une cible de trop au D-pad.
     */
    onReportSegment: (() -> Unit)? = null,
    /**
     * Met le titre en cours en file de téléchargement.
     *
     * Null quand il n'y a rien à faire : lecture déjà locale, ou source
     * inconnue. Même règle que [onReportSegment] — l'icône disparaît alors, une
     * cible inerte étant une cible de trop au D-pad.
     */
    onDownload: (() -> Unit)? = null,
    /**
     * Reprend la lecture en cours sur un récepteur Cast.
     *
     * Null quand aucun récepteur ne répond — même règle que les précédents. Ce
     * bouton **manquait**, et son absence a été rapportée comme un défaut : on
     * lance un film, on veut le passer sur la télé, et l'icône n'existe que sur
     * la fiche qu'on vient de quitter. Décider de diffuser au moment où l'on
     * regarde est le cas normal, pas l'exception.
     */
    onCast: (() -> Unit)? = null,
    /** Clé média, pour lire l'avancement du téléchargement sur le bouton. */
    mediaKey: String = "",
    onActivity: () -> Unit,
    /**
     * Rendu non nul uniquement là où la barre est pilotable au pointeur : active
     * le clic et le glisser sur la progression. Laissé null sur TV, où le
     * réglage se fait au D-pad via le mode scrub.
     */
    onSeekToFraction: ((Float) -> Unit)? = null,
    /** Intro / générique repérés sur la barre. Vide = titre inconnu de l'API. */
    segments: List<PlayerSegment> = emptyList(),
    /** Commandes propres à la plateforme, ajoutées à droite (volume, plein écran). */
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            // La marge de page vaut aussi ici. Un téléviseur rogne ses bords au
            // sur-balayage, et une barre de lecture calée à trente-deux points
            // du bord y passait partiellement sous le cadre du poste — c'est
            // précisément le cas que la marge proportionnelle couvre.
            .padding(horizontal = margePage(), vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Le retour n'est pas une commande de lecture : il quitte le
            // lecteur. Un écart le détache du transport, sinon on le vise en
            // croyant reculer de quinze secondes — les deux icônes sont des
            // flèches, et elles étaient collées.
            MoovieIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
            )
            Spacer(modifier = Modifier.width(ESPACE_LARGE))
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
            // Flèches **circulaires**, pas les doubles triangles de `FastRewind` /
            // `FastForward` : ceux-ci ne se distinguaient plus des triangles-barre
            // de l'épisode précédent/suivant, posés juste à côté. Deux gestes très
            // différents — reculer de 15 s, changer d'épisode — se ressemblaient
            // au point qu'on se trompait de bouton.
            //
            // Sans chiffre : Material ne propose que 5, 10 et 30 s, et le pas est
            // de 15 (PLAYER_SEEK_STEP_MS). Une icône « 10 » sur un saut de 15
            // aurait été pire que pas de chiffre du tout.
            MoovieIconButton(
                onClick = onSeekBack,
                icon = Icons.Default.Replay,
                contentDescription = stringResource(Res.string.player_seek_back),
            )
            MoovieIconButton(
                onClick = onSeekForward,
                icon = Icons.Default.Replay,
                contentDescription = stringResource(Res.string.player_seek_forward),
                mirrored = true,
            )
            if (showEpisodeButtons && onOpenEpisodes == null) {
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
            // **Deux groupes, et l'écran entre eux.**
            //
            // Les dix commandes étaient alignées à gauche, dans le même souffle :
            // retour, lecture, saut arrière, saut avant, épisode précédent,
            // suivant, sous-titres, signalement, diffusion, réglages. Un mur.
            // Rien n'y distinguait ce qui pilote **ce qui joue** de ce qui règle
            // **comment ça joue**, et l'on cherchait « sous-titres » parmi des
            // triangles quand on venait de viser « pause ».
            //
            // À gauche le transport, à droite les options : c'est la
            // disposition de tous les lecteurs, et elle n'est pas arbitraire —
            // le transport se manipule sans regarder, les options se
            // choisissent en regardant. Les mettre à deux endroits, c'est dire
            // lequel est lequel sans un mot.
            Spacer(modifier = Modifier.weight(1f))
            // En tête des options, et non dans le transport : changer d'épisode
            // n'est pas piloter celui qui joue. C'est le même partage que la
            // fiche, où la liste des épisodes est un onglet et non un bouton.
            onOpenEpisodes?.let { open ->
                // **Écrit, et non dessiné.** Les autres commandes de cette
                // rangée sont des icônes parce qu'elles ont chacune un dessin
                // que tout le monde connaît — sous-titres, diffusion, réglages.
                // « La liste des épisodes » n'en a pas : le pictogramme de liste
                // veut dire « liste » et pas « épisodes », et il se serait
                // confondu avec les quatre autres carrés de la rangée. Un mot
                // coûte quelques points de large et ne se devine pas.
                MoovieButton(onClick = open) {
                    Text(
                        stringResource(Res.string.details_tab_episodes),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            onOpenSubtitles?.let { open ->
                MoovieIconButton(
                    onClick = open,
                    icon = Icons.Default.ClosedCaption,
                    contentDescription = stringResource(Res.string.player_subtitles),
                )
            }
            onReportSegment?.let { report ->
                MoovieIconButton(
                    onClick = report,
                    icon = Icons.Default.MoreTime,
                    contentDescription = stringResource(Res.string.report_segment),
                )
            }
            onCast?.let { diffuse ->
                MoovieIconButton(
                    onClick = diffuse,
                    icon = Icons.Default.Cast,
                    contentDescription = stringResource(Res.string.player_cast),
                )
            }
            onDownload?.let { enqueue ->
                PlayerDownloadButton(mediaKey = mediaKey, onEnqueue = enqueue)
            }
            onOpenSettings?.let { open ->
                MoovieIconButton(
                    onClick = open,
                    icon = Icons.Default.Settings,
                    contentDescription = stringResource(Res.string.player_settings),
                )
            }
            trailing()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                formatPlayerTime(positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = if (scrubbing) MOOVIE_ACCENT else MOOVIE_TEXT_MUTED,
            )
            PlayerSeekBar(
                fraction = if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                },
                bufferedFraction = if (durationMs > 0) {
                    (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                },
                durationMs = durationMs,
                segments = segments,
                scrubbing = scrubbing,
                onCommitScrub = onCommitScrub,
                onNudgeScrub = onNudgeScrub,
                onActivity = onActivity,
                onSeekToFraction = onSeekToFraction,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatPlayerTime(durationMs),
                style = MaterialTheme.typography.labelLarge,
                color = MOOVIE_TEXT_MUTED,
            )
        }
    }
}

/**
 * Segment repéré sur la barre de progression (intro ou générique TheIntroDB).
 * [endMs] nul = borne inconnue : le segment court jusqu'à la fin du média.
 */
data class PlayerSegment(val startMs: Long, val endMs: Long?, val kind: SkipKind)

/**
 * Segments d'un média TheIntroDB, prêts à être dessinés. Un segment sans début
 * connu commence à zéro ; sans aucune borne, il est ignoré — on ne colorie pas
 * la barre entière sur une donnée vide.
 */
fun IntroMedia.toPlayerSegments(): List<PlayerSegment> =
    intro.mapNotNull { it.toPlayerSegment(SkipKind.INTRO) } +
        credits.mapNotNull { it.toPlayerSegment(SkipKind.CREDITS) }

private fun Segment.toPlayerSegment(kind: SkipKind): PlayerSegment? {
    if (startMs == null && endMs == null) return null
    return PlayerSegment(startMs = startMs ?: 0L, endMs = endMs, kind = kind)
}

/** Hauteur des bandes de segments, sous la piste de progression. */
private val SEGMENT_BAND_HEIGHT = 5.dp

/** Élévation de l'infobulle de temps au-dessus de la barre de progression. */
private val SEEK_TOOLTIP_GAP = 26.dp

/**
 * Teinte d'un segment. Franchement différentes de l'accent rouge de la portion
 * déjà lue : une bande ne doit jamais se lire comme de la progression.
 */
private fun SkipKind.bandColor(): Color = when (this) {
    SkipKind.INTRO -> Color(0xFF3DA9FC)
    SkipKind.CREDITS -> Color(0xFFF5A623)
}

/**
 * Barre de progression pilotable au D-pad.
 *
 * **←/→ règlent dès que la barre a le focus.** Il a fallu, un temps, appuyer sur
 * OK pour « entrer en mode réglage » avant que les flèches ne servent à quelque
 * chose : rien ne l'indiquait à l'écran, et la barre paraissait simplement
 * inerte. Le saut part tout seul peu après le dernier appui — viser dix crans
 * ne fait donc qu'un seul saut, ce qui compte sur un flux HLS où chacun coûte un
 * rechargement.
 *
 * La contrepartie est que ←/→ sont désormais **consommées** : on quitte la barre
 * par ↑, vers les boutons. C'était l'unique raison d'être de l'ancien passage
 * par OK — laisser les flèches traverser pour sortir — et une seule issue suffit
 * dès lors que la barre est la dernière rangée de la chrome.
 */
@Composable
private fun PlayerSeekBar(
    fraction: Float,
    bufferedFraction: Float,
    durationMs: Long,
    segments: List<PlayerSegment>,
    scrubbing: Boolean,
    onCommitScrub: () -> Unit,
    onNudgeScrub: (Long) -> Unit,
    onActivity: () -> Unit,
    onSeekToFraction: ((Float) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    // Quitter la barre **valide** la visée, elle ne l'annule plus. Tant qu'il
    // fallait appuyer sur OK pour régler, l'abandon était le repli naturel ;
    // maintenant qu'une flèche suffit, avoir déplacé le curseur *est* la
    // demande, et repartir sans rien faire l'aurait silencieusement jetée.
    LaunchedEffect(focused) { if (!focused) onCommitScrub() }

    val barHeight = if (focused || scrubbing) 10.dp else 6.dp
    var widthPx by remember { mutableStateOf(1) }
    // Position survolée à la souris, en fraction de la barre (null = pas de
    // survol). Sert uniquement à l'infobulle : le survol ne déplace rien.
    var hoverFraction by remember { mutableStateOf<Float?>(null) }
    Box(
        modifier = modifier
            .height(32.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            // Pointeur : uniquement là où l'appelant le demande (desktop). Le
            // clic comme le glisser repositionnent directement la lecture, sans
            // passer par le mode scrub qui n'existe que pour le D-pad.
            .then(
                if (onSeekToFraction == null) {
                    Modifier
                } else {
                    // Survol suivi en passe Initial : les mouvements sont
                    // consommés par le détecteur de glisser ci-dessous, et
                    // l'infobulle doit continuer à suivre le curseur pendant le
                    // glisser, pas seulement avant.
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                when (event.type) {
                                    PointerEventType.Exit -> hoverFraction = null
                                    PointerEventType.Enter,
                                    PointerEventType.Move,
                                    PointerEventType.Press,
                                    -> {
                                        val x = event.changes.first().position.x
                                        hoverFraction = (x / widthPx).coerceIn(0f, 1f)
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { onActivity() },
                        ) { change, _ ->
                            onActivity()
                            onSeekToFraction((change.position.x / widthPx).coerceIn(0f, 1f))
                        }
                    }.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            onActivity()
                            onSeekToFraction((offset.x / widthPx).coerceIn(0f, 1f))
                        }
                    }
                },
            )
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                onActivity()
                when (event.key) {
                    // OK n'ouvre plus rien : il applique tout de suite ce
                    // qu'on vise, pour qui ne veut pas attendre le délai.
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onCommitScrub()
                        true
                    }
                    // Réglage direct : la barre focalisée, ←/→ déplacent la
                    // visée sans qu'on ait eu à valider quoi que ce soit avant.
                    // C'est le geste qu'on fait sans y penser sur n'importe
                    // quelle box, et l'ancien passage obligé par OK le rendait
                    // introuvable — rien à l'écran ne disait qu'il fallait
                    // « entrer » quelque part pour que les flèches servent.
                    Key.DirectionLeft -> {
                        onNudgeScrub(-PLAYER_SCRUB_STEP_MS)
                        true
                    }
                    Key.DirectionRight -> {
                        onNudgeScrub(PLAYER_SCRUB_STEP_MS)
                        true
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
        // Piste de chargement : jusqu'où le flux est en mémoire tampon. Un saut
        // au-delà de cette limite oblige à retélécharger, et cale sur un hôte
        // qui ignore les requêtes `Range` — d'où l'intérêt de la voir.
        if (bufferedFraction > fraction) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(Color(0x8AFFFFFF)),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(barHeight)
                .clip(CircleShape)
                .background(if (scrubbing) Color.White else MOOVIE_ACCENT),
        )
        // Segments TheIntroDB, sous la piste. La durée n'est connue qu'une fois
        // le flux ouvert : avant, rien à placer. Et quand l'API ne connaît pas
        // le titre, aucune bande n'apparaît — l'absence est l'information.
        if (segments.isNotEmpty() && durationMs > 0) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SEGMENT_BAND_HEIGHT)
                    .align(Alignment.Center)
                    .offset(y = barHeight / 2 + 4.dp),
            ) {
                segments.forEach { segment ->
                    val start = (segment.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    // Générique sans borne de fin : il file jusqu'au bout de la
                    // barre plutôt que de disparaître.
                    val end = segment.endMs
                        ?.let { (it.toFloat() / durationMs).coerceIn(0f, 1f) }
                        ?: 1f
                    if (end <= start) return@forEach
                    drawRoundRect(
                        color = segment.kind.bandColor(),
                        topLeft = Offset(size.width * start, 0f),
                        // Un segment très court reste visible : au minimum un rond.
                        size = Size(
                            width = (size.width * (end - start)).coerceAtLeast(size.height),
                            height = size.height,
                        ),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
            }
        }
        // Infobulle de temps, calée sur l'abscisse visée : le curseur au
        // pointeur, la tête de lecture en mode réglage à la télécommande. Même
        // service dans les deux cas — savoir où l'on tombe avant de valider.
        val tipFraction = hoverFraction ?: fraction.takeIf { scrubbing }
        if (tipFraction != null && durationMs > 0) {
            var tipWidth by remember { mutableStateOf(0) }
            Text(
                formatPlayerTime((tipFraction * durationMs).toLong()),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // Centrée sur le point visé, mais jamais débordante : aux
                    // deux extrémités elle se cale contre le bord de la barre.
                    .offset {
                        IntOffset(
                            x = (tipFraction * widthPx - tipWidth / 2f)
                                .roundToInt()
                                .coerceIn(0, (widthPx - tipWidth).coerceAtLeast(0)),
                            y = -SEEK_TOOLTIP_GAP.roundToPx(),
                        )
                    }
                    .onSizeChanged { tipWidth = it.width }
                    .clip(MoovieShape)
                    .background(Color(0xF21E1E1E))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (focused || scrubbing) {
            // Libellé d'accessibilité, gardé transparent : il décrit la barre
            // sans ajouter de texte visible sous la progression.
            //
            // Il n'y a plus de « OK pour valider » à côté : la consigne n'avait
            // de sens que tant qu'OK ouvrait un mode, et l'afficher maintenant
            // apprendrait un geste qui n'est plus nécessaire.
            Text(
                stringResource(Res.string.player_progress),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x00000000),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// ── Overlays ────────────────────────────────────────────────────────────────

/**
 * Titre et sous-titre du média, en haut à gauche, avec le dégradé de lisibilité.
 *
 * [clock] occupe le coin opposé : le bandeau existe déjà et porte son propre
 * dégradé, y poser l'heure évite d'empiler une seconde surface sur l'image.
 */
@Composable
fun PlayerTitleOverlay(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    clock: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            .padding(horizontal = margePage(), vertical = 32.dp),
    ) {
    Column {
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
                color = MOOVIE_TEXT_MUTED,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
        clock?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MOOVIE_TEXT_MUTED,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/**
 * Bouton « Passer l'intro / le générique ».
 *
 * **Fond opaque, contrairement au reste de l'app.** Un [MoovieButton] n'est que
 * son libellé au repos et ne s'habille qu'au focus : posé sur une image claire —
 * un générique sur fond de tapisserie, une plage — le texte blanc disparaissait
 * purement et simplement. Et il n'y a pas de focus au doigt, donc rien ne venait
 * jamais le rattraper.
 *
 * Ce n'est pas une exception gratuite : c'est le seul bouton **posé sur la
 * vidéo**, dont on ne maîtrise pas le fond. Les autres vivent sur les panneaux
 * sombres de l'app. Même parti que Netflix ou Prime Video, pour la même raison.
 *
 * Le liseré clair fait le reste du travail sur les fonds sombres, où un
 * rectangle noir se fondrait dans l'image.
 */
@Composable
fun PlayerSkipButton(kind: SkipKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MoovieButton(
        onClick = onClick,
        // Découpé **avant** le fond : sans ça le rectangle déborde des coins
        // arrondis que MoovieButton applique ensuite.
        modifier = modifier
            .clip(MoovieShape)
            .background(Color(0xD90E0E0E))
            .border(1.dp, Color(0x4DFFFFFF), MoovieShape),
    ) {
        Text(
            if (kind == SkipKind.INTRO) {
                stringResource(Res.string.player_skip_intro)
            } else {
                stringResource(Res.string.player_skip_outro)
            },
        )
    }
}

/**
 * Pastille de mise à jour. Le clic met en pause et laisse la bannière habituelle
 * demander confirmation : rien ne s'installe sur une simple erreur de visée.
 */
@Composable
fun PlayerUpdateChip(version: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MoovieButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Default.SystemUpdateAlt,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(stringResource(Res.string.player_update_chip, version))
    }
}

/** Décompte avant l'épisode suivant, avec son bouton d'annulation. */
@Composable
fun PlayerAutoNextCountdown(
    seconds: Int,
    cancelFocus: FocusRequester,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MoovieShape)
            .background(Color(0xF21E1E1E))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.player_next_in, seconds),
            style = MaterialTheme.typography.titleMedium,
        )
        MoovieButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) {
            Text(stringResource(Res.string.common_cancel))
        }
    }
}

// ── Menus ───────────────────────────────────────────────────────────────────

data class PlayerOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

data class PlayerOptionSection(val title: String, val options: List<PlayerOption>)

/** Section « Sous-titres », avec l'entrée « Désactivés » en tête. */
@Composable
fun subtitleSection(tracks: PlayerTracks, onSelect: (String?) -> Unit): PlayerOptionSection {
    val options = buildList {
        add(
            PlayerOption(
                stringResource(Res.string.player_subtitles_off),
                tracks.subtitlesOff,
            ) { onSelect(null) },
        )
        tracks.subtitles.forEach { track ->
            add(PlayerOption(track.label, track.selected) { onSelect(track.id) })
        }
    }
    return PlayerOptionSection(stringResource(Res.string.player_subtitles), options)
}

/** Section « Piste audio ». Une seule piste = rien à choisir, section vide. */
@Composable
fun audioSection(tracks: PlayerTracks, onSelect: (String) -> Unit): PlayerOptionSection {
    val options = tracks.audio.map { track ->
        PlayerOption(track.label, track.selected) { onSelect(track.id) }
    }
    return PlayerOptionSection(
        stringResource(Res.string.player_audio),
        if (options.size > 1) options else emptyList(),
    )
}

/**
 * Section « Qualité ».
 *
 * Prend une liste toute faite plutôt que d'interroger le lecteur, parce que les
 * deux moteurs ne savent pas la même chose : ExoPlayer expose les variantes du
 * flux et sait en changer à chaud ; libVLC 3 n'expose que la piste courante, et
 * la liste doit être lue dans la master playlist puis appliquée en rouvrant le
 * flux. Une abstraction commune aurait dû mentir à l'un des deux.
 *
 * Les entrées venues d'**autres sources** y figurent au même titre : ce qui
 * intéresse ici est la définition, pas l'hébergeur qui la sert. Le nom de la
 * source n'apparaît que sur celles-là, pour expliquer pourquoi les choisir
 * interrompt brièvement la lecture.
 *
 * Moins de deux entrées : section vide, donc absente du menu. Proposer un choix
 * unique donne l'illusion d'une possibilité qui n'existe pas.
 */
@Composable
fun qualitySection(options: List<PlayerTrack>, onSelect: (String) -> Unit): PlayerOptionSection =
    PlayerOptionSection(
        stringResource(Res.string.player_quality),
        if (options.size > 1) {
            val auto = stringResource(Res.string.player_quality_auto)
            options.map { track ->
                // Le fabricant d'options est pur et ne connaît pas les
                // ressources : il pose un marqueur, la traduction se fait ici.
                val label = if (track.label == AUTO_LABEL) auto else track.label
                PlayerOption(label, track.selected) { onSelect(track.id) }
            }
        } else {
            emptyList()
        },
    )

/** Section « Vitesse de lecture ». */
@Composable
fun speedSection(current: Float, onSelect: (Float) -> Unit): PlayerOptionSection =
    PlayerOptionSection(
        stringResource(Res.string.player_speed),
        PLAYER_SPEEDS.map { value ->
            val label = "×" + formaterDecimal(value.toDouble(), 2).trimEnd('0').trimEnd('.', ',')
            PlayerOption(label, value == current) { onSelect(value) }
        },
    )

/** Une ligne du menu : intitulé de section ou choix sélectionnable. */
private sealed interface DialogRow {
    data class Header(val title: String) : DialogRow
    data class Item(val option: PlayerOption) : DialogRow
}

/** Menu du lecteur (sous-titres, vitesse, piste audio) pilotable au D-pad. */
@Composable
fun PlayerOptionsDialog(sections: List<PlayerOptionSection>, onDismiss: () -> Unit) {
    val firstOption = remember { FocusRequester() }
    val rows = buildList {
        sections.forEach { section ->
            add(DialogRow.Header(section.title))
            section.options.forEach { add(DialogRow.Item(it)) }
        }
    }
    // Le focus va sur l'option **active** (la vitesse en cours, la piste
    // choisie), pas sur la première de la liste : c'est de là qu'on veut
    // repartir. À défaut d'option active, la première fait l'affaire.
    val focusItemIndex = rows
        .indexOfFirst { it is DialogRow.Item && it.option.selected }
        .takeIf { it >= 0 }
        ?: rows.indexOfFirst { it is DialogRow.Item }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                // Voir le panneau des sources : un plafond, pour ne pas déborder
                // d'un écran plus étroit que celui sur lequel on a mesuré.
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(MoovieShape)
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
                                    if (index == focusItemIndex) {
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
