package fr.moovie.tv.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.download.readyInSeason
import fr.moovie.tv.data.download.DownloadState
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import fr.moovie.tv.ui.theme.MoovieShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.data.tmdb.CastMember
import fr.moovie.tv.data.tmdb.Episode
import fr.moovie.tv.data.tmdb.MovieDetails
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.common_loading
import fr.moovie.tv.resources.details_cast
import fr.moovie.tv.resources.details_episode_header
import fr.moovie.tv.resources.details_episodes_season
import fr.moovie.tv.resources.details_lang_missing
import fr.moovie.tv.resources.details_lang_unavailable
import fr.moovie.tv.resources.details_no_sources
import fr.moovie.tv.resources.details_play
import fr.moovie.tv.resources.details_playing
import fr.moovie.tv.resources.details_resume
import fr.moovie.tv.resources.details_searching
import fr.moovie.tv.resources.details_searching_source
import fr.moovie.tv.resources.details_trying_source
import fr.moovie.tv.resources.details_seasons
import fr.moovie.tv.resources.details_download_best
import fr.moovie.tv.resources.details_sources
import fr.moovie.tv.resources.details_tab_info
import fr.moovie.tv.resources.details_tab_overview
import fr.moovie.tv.resources.details_trailer
import fr.moovie.tv.resources.details_source_download_hint
import fr.moovie.tv.resources.details_source_dl_queued
import fr.moovie.tv.resources.details_source_dl_running
import fr.moovie.tv.resources.details_source_dl_paused
import fr.moovie.tv.resources.details_source_dl_failed
import fr.moovie.tv.resources.player_download_done
import fr.moovie.tv.resources.details_download_season_partial
import fr.moovie.tv.resources.details_download_season_queued
import fr.moovie.tv.resources.details_download_season
import fr.moovie.tv.resources.details_download_season_progress
import fr.moovie.tv.ui.format.upcomingDate
import fr.moovie.tv.resources.details_episode_upcoming
import fr.moovie.tv.resources.details_source_dead
import fr.moovie.tv.resources.details_source_via
import fr.moovie.tv.resources.details_catalogue_count
import fr.moovie.tv.resources.details_source_count
import fr.moovie.tv.resources.details_sources_searching
import fr.moovie.tv.resources.mark_season_unwatched
import fr.moovie.tv.resources.mark_season_watched
import fr.moovie.tv.resources.mark_unwatched
import fr.moovie.tv.resources.mark_watched
import fr.moovie.tv.resources.watchlist_add
import fr.moovie.tv.resources.watchlist_remove
import fr.moovie.tv.core.format.formatDuration
import fr.moovie.tv.ui.format.formatMediaDate
import fr.moovie.tv.ui.components.LocalMoovieCardActive
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieMarqueeText
import fr.moovie.tv.ui.components.MoovieProgressBar
import fr.moovie.tv.ui.components.MoovieRail
import fr.moovie.tv.ui.components.SkeletonDetails
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import fr.moovie.tv.ui.adaptive.isPointerUi
import fr.moovie.tv.ui.player.MooviePlayerController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Largeur du volet gauche d'une fiche série (titre, résumé, saisons, actions).
 *
 * Contenue à dessein : en 1080p l'écran ne fait que 960 dp de large, et chaque
 * point pris ici est un point de moins pour la liste des épisodes — qui est ce
 * qu'on vient consulter.
 */
private val SERIES_PANE_WIDTH = 380.dp

/**
 * Délai laissé au `bringIntoView` déclenché par la prise de focus avant de
 * ramener la page en haut.
 *
 * Il s'exécute sur les images qui suivent la demande de focus, pas pendant :
 * remettre le défilement à zéro sans attendre se ferait écraser juste après.
 */
private const val SCROLL_SETTLE_MS = 120L

/**
 * Temps passé sur une fiche avant que sa bande-annonce ne démarre d'elle-même.
 *
 * Trois secondes : assez pour que traverser des fiches n'en lance aucune, assez
 * court pour que s'arrêter sur un titre donne l'impression que l'app répond.
 */
private const val HERO_PREVIEW_DELAY_MS = 3_000L

/** Fondu d'apparition de l'aperçu : il remplace une affiche, il ne surgit pas. */
private const val HERO_PREVIEW_FADE_MS = 800

/**
 * Temps sans mouvement de souris avant que l'interface ne s'efface.
 *
 * Trois secondes, comme le délai de l'aperçu lui-même : assez pour ne pas
 * clignoter quand on traverse la fenêtre, assez court pour que rester immobile
 * soit visiblement récompensé.
 */
private const val CINEMA_IDLE_MS = 3_000L

/** L'interface s'efface doucement — c'est un fondu, pas une disparition. */
private const val CINEMA_UI_FADE_MS = 600

/**
 * Le son monte plus lentement que l'image ne s'efface, et redescend d'autant.
 * Un son qui apparaît d'un coup s'entend comme un défaut ; un fondu s'entend
 * comme une intention.
 */
private const val CINEMA_SOUND_FADE_MS = 1_200

/**
 * Largeur d'une vignette du casting.
 *
 * Une seule constante pour la carte *et* le portrait qu'elle contient : c'est ce
 * qui garantit qu'aucun liseré de fond ne réapparaisse entre les deux. Deux
 * valeurs, et l'écart se rejoue au premier changement.
 */
private val CAST_CARD_WIDTH = 96.dp

/**
 * Dispose les deux volets d'une série : description à gauche, épisodes à droite
 * sur grand écran — l'un au-dessus de l'autre sur téléphone.
 *
 * Le conteneur fournit à l'appelant les modificateurs de chaque volet plutôt que
 * de les laisser au point d'appel : `weight` n'existe que dans le scope de la
 * `Row` ou de la `Column`, il doit donc être construit ici, une fois le sens de
 * l'empilement décidé.
 */
@Composable
private fun SeriesPanes(
    compact: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (headerModifier: Modifier, listModifier: Modifier) -> Unit,
) {
    if (compact) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                Modifier.weight(1f).fillMaxWidth(),
            )
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            content(
                Modifier.width(SERIES_PANE_WIDTH).padding(start = 48.dp),
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/**
 * Fiche film/série partagée TV + desktop : état hoisté (le ViewModel reste côté
 * plateforme — chargement TMDB, résolution de sources, suivi de lecture).
 */
@Composable
fun DetailsScreenContent(
    state: DetailsState,
    sources: SourcesState,
    resolveError: String?,
    /** URL de la source en cours de résolution (voir DetailsViewModel.resolving). */
    resolvingUrl: String?,
    /** Ouvre la filmographie d'une personne du casting. */
    onOpenPerson: (CastMember) -> Unit = {},
    streamLang: StreamLanguage,
    watched: Set<String>,
    resume: Map<String, ResumeEntry>,
    quickPlay: QuickPlayState,
    panelVisible: Boolean,
    selectedEpisode: EpisodeSelection?,
    movieKey: String,
    episodeKey: (season: Int, episode: Int) -> String,
    onQuickPlayMovie: () -> Unit,
    onQuickPlayEpisode: (season: Int, episode: Int) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onOpenEpisode: (season: Int, episode: Episode) -> Unit,
    onOpenEpisodePanel: (season: Int, episode: Int) -> Unit,
    onToggleWatched: (String) -> Unit,
    onToggleSeasonWatched: () -> Unit,
    /** Titre présent dans « À regarder plus tard ». */
    inWatchlist: Boolean = false,
    onToggleWatchlist: () -> Unit = {},
    onOpenPanel: () -> Unit,
    onClosePanel: () -> Unit,
    onPickSource: (EmbedLink) -> Unit,
    /** Appui long sur une source : la mettre en file de téléchargement. */
    onDownloadSource: (EmbedLink) -> Unit = {},
    /** Télécharge toute la saison affichée, source vérifiée par épisode. */
    onDownloadSeason: (Int) -> Unit = {},
    /** Avancement de la recherche de sources, null quand rien ne tourne. */
    seasonDownload: DetailsViewModel.SeasonDownload? = null,
    /** Qualité vidéo mesurée par URL d'embed (voir DetailsViewModel.qualities). */
    sourceQualities: Map<String, String> = emptyMap(),
    onRequestQuality: (EmbedLink) -> Unit = {},
    /** Verdict de la sonde par URL d'embed — voir [LinkStatus]. */
    sourceStatuses: Map<String, LinkStatus> = emptyMap(),
    /**
     * Téléchargements en cours ou terminés, indexés par le lien d'embed dont
     * ils sont partis. Voir [SourceRow] pour la raison de cette clé.
     */
    downloads: Map<String, Download> = emptyMap(),
    /**
     * Les téléchargements bruts, pour compter par saison. La carte ci-dessus est
     * indexée par lien d'embed, ce qui ne permet pas de dénombrer.
     */
    downloadList: List<Download> = emptyList(),
    /** Bande-annonce du titre. [TrailerState.None] = aucun bouton affiché. */
    trailer: TrailerState = TrailerState.None,
    onPlayTrailer: () -> Unit = {},
    /**
     * Aperçu muet de la bande-annonce dans le fond de la fiche, fourni par la
     * plateforme — ExoPlayer côté Android, libVLC côté desktop. Le lecteur ne
     * peut pas vivre dans `jvmCommon` : il n'y a pas de moteur vidéo commun aux
     * deux, et c'est exactement le genre de chose que ce projet garde dans
     * `androidMain` / `desktopMain`.
     *
     * Null = pas d'aperçu, la fiche garde son image de fond.
     */
    trailerPreview: (
        @Composable (
            stream: PlayableStream,
            volume: Float,
            onController: (MooviePlayerController?) -> Unit,
            modifier: Modifier,
        ) -> Unit
    )? = null,
    /**
     * La bande-annonce est-elle au premier plan, contrôles visibles. Elle ne
     * change pas d'écran : c'est l'aperçu du fond qui reçoit les contrôles.
     */
    trailerExpanded: Boolean = false,
    onCloseTrailer: () -> Unit = {},
    /** Télécharge le titre affiché dans la meilleure définition trouvable. */
    onDownloadBest: () -> Unit = {},
    /** Recherche de la meilleure source en cours : le bouton tourne. */
    downloadSearching: Boolean = false,
    /** Réglage utilisateur : l'aperçu se lance-t-il tout seul. */
    trailerAutoplay: Boolean = true,
    /** Réglage utilisateur : le son de l'aperçu monte-t-il en mode cinéma. */
    trailerSound: Boolean = false,
    /**
     * Pays de l'utilisateur (`FR`), pour choisir la bonne classification d'âge
     * dans le panneau « En savoir plus ».
     */
    country: String = "FR",
    onDismissQuickPlay: () -> Unit,
    onBack: () -> Unit,
    // Desktop uniquement : bouton retour à l'écran (sur TV, la télécommande a
    // sa propre touche Retour, pas besoin d'un bouton).
    showBackButton: Boolean = false,
) {
    val primaryFocus = remember { FocusRequester() }
    // Téléphone : les deux volets de la série s'empilent au lieu de se partager
    // la largeur. 380 dp de description sur 448 dp d'écran ne laissaient rien
    // aux épisodes.
    val compact = useBottomNav
    val resumeEpisodeFocus = remember { FocusRequester() }
    // Hissé jusqu'ici parce que le placement du focus de reprise en a besoin :
    // dans une LazyColumn, un épisode hors écran n'est pas composé du tout, donc
    // son FocusRequester n'existe pas. Il faut défiler jusqu'à lui d'abord.
    val episodesState = rememberLazyListState()

    // Série reprise en cours : le focus descend sur l'épisode à suivre plutôt
    // que de rester sur la rangée des saisons — sinon on arrive avec « S1 »
    // sélectionné alors que la liste affiche une tout autre saison, et il faut
    // redescendre à la main jusqu'à l'épisode qu'on venait voir.
    //
    // Une seule fois par fiche : changer de saison ensuite est un geste
    // délibéré, et lui reprendre le focus serait insupportable.
    val seriesId = (state as? DetailsState.Tv)?.details?.id
    var autoFocusDone by remember(seriesId) { mutableStateOf(false) }

    // État nommé : la rangée des saisons doit pouvoir ramener la page en haut,
    // et l'arrivée sur une fiche aussi — faute de quoi l'en-tête reste hors
    // cadre (voir juste en dessous, et plus bas).
    val pageScroll = rememberScrollState()
    val topScope = rememberCoroutineScope()

    /**
     * Le bouton principal, **et** la remontée de page qui va avec.
     *
     * Atteindre le premier élément d'une fiche, c'est en atteindre le haut : sur
     * une télécommande il n'y a pas de défilement libre, seulement du focus qui
     * se déplace. Or `bringIntoView` ne défile que le strict nécessaire, et le
     * bouton reste visible dans la page décalée — descendre au casting puis
     * remonter laissait donc l'en-tête hors cadre, définitivement.
     *
     * Lier la remontée au focus plutôt qu'à l'arrivée couvre les deux cas d'un
     * coup : la première prise de focus comme toutes les suivantes.
     *
     * L'attente laisse passer le `bringIntoView`, qui s'exécute sur les images
     * suivantes et écraserait un défilement posé trop tôt.
     */
    val primaryModifier = Modifier
        .focusRequester(primaryFocus)
        .onFocusChanged { focus ->
            if (!focus.isFocused) return@onFocusChanged
            topScope.launch {
                delay(SCROLL_SETTLE_MS)
                runCatching { pageScroll.animateScrollTo(0) }
            }
        }

    // Le focus est aussi replacé quand on entre/sort d'une fiche d'épisode :
    // le bouton porteur de `primaryFocus` change de nœud à ce moment-là.
    LaunchedEffect(state, selectedEpisode) {
        val tv = state as? DetailsState.Tv
        val wantsEpisode = tv != null && tv.resumeEpisode > 0 && selectedEpisode == null && !autoFocusDone
        if (wantsEpisode) {
            // +1 : le titre « Épisodes (saison N) » occupe le premier élément.
            val index = tv.episodes.indexOfFirst { it.episodeNumber == tv.resumeEpisode }
            if (index >= 0) runCatching { episodesState.scrollToItem(index + 1) }
            // La liste d'épisodes n'est pas encore posée au moment où l'état
            // change : on retente le temps qu'elle le soit.
            repeat(10) {
                if (runCatching { resumeEpisodeFocus.requestFocus() }.isSuccess) {
                    autoFocusDone = true
                    return@LaunchedEffect
                }
                delay(50)
            }
        }
        if (state is DetailsState.Movie || state is DetailsState.Tv) {
            // La remontée est portée par `primaryModifier`, qui réagit à la
            // prise de focus — donc ici comme à chaque retour sur le bouton.
            runCatching { primaryFocus.requestFocus() }

            // Repli pour le cas où le bouton n'existe pas encore : la demande de
            // focus échoue alors en silence, et personne ne remonterait la page.
            //
            // Donner le focus déclenche un `bringIntoView` : la page défile pour
            // amener l'élément visé dans le cadre, et mange la marge haute. On
            // arrivait sur une fiche dont la ligne de genres et l'affiche
            // étaient **coupées en deux**, sans aucun moyen de remonter — le
            // bouton est le premier élément focalisable, la flèche haut n'a
            // nulle part où aller, et redescendre puis remonter ne redéfile pas
            // puisque la cible est déjà visible. Le décalage était donc collant
            // pour toute la durée de la fiche.
            //
            delay(SCROLL_SETTLE_MS)
            runCatching { pageScroll.scrollTo(0) }
        }
    }

    val backdrop = (state as? DetailsState.Movie)?.details?.backdropUrl()
        ?: (state as? DetailsState.Tv)?.details?.backdropUrl()

    // Aperçu du hero : la bande-annonce prend la place de l'image de fond au
    // bout de quelques secondes, puis lui rend la place à la fin.
    //
    // Le délai n'est pas cosmétique : sans lui, traverser une rangée de la
    // recherche déclencherait une lecture par fiche effleurée. On attend le
    // temps qu'il faut pour dire qu'on s'est arrêté sur ce titre.
    // Panneau « En savoir plus ». Remis à plat au changement de titre : ouvrir
    // une fiche doit toujours montrer la fiche, pas la vue technique qu'on
    // consultait sur la précédente.
    var infoVisible by remember(state.titleKey()) { mutableStateOf(false) }

    val ready = trailer as? TrailerState.Ready
    var previewPlaying by remember(ready?.video?.key) { mutableStateOf(false) }

    // ── Mode cinéma ─────────────────────────────────────────────────────────
    //
    // L'interface s'efface, le son monte, et la bande-annonce a l'écran pour
    // elle. Le moindre mouvement de souris la rend : on est revenu, on veut
    // ses boutons.
    //
    // **Au pointeur seulement.** Le signal d'activité est le mouvement de
    // souris, qui n'existe ni sur un téléviseur ni sur un téléphone ; sans lui
    // l'interface disparaîtrait sans aucun moyen de la rappeler. L'aperçu y
    // reste donc muet, sous une interface visible, ce qu'il était déjà.
    val cinemaCapable = isPointerUi && trailerPreview != null
    var cinema by remember(ready?.video?.key) { mutableStateOf(false) }

    // Le lecteur du fond, prêté par la plateforme. C'est *lui* que les contrôles
    // pilotent : il n'y a pas de second lecteur pour la bande-annonce.
    var trailerController by remember(ready?.video?.key) {
        mutableStateOf<MooviePlayerController?>(null)
    }
    // Le réglage donne l'état de départ, l'utilisateur garde la main ensuite.
    var trailerMuted by remember(trailerExpanded) { mutableStateOf(!trailerSound) }

    // L'activité passe par un flux et **non par un état Compose** : une souris
    // émet des dizaines d'événements par seconde, et un `mutableStateOf`
    // incrémenté à chacun recomposerait toute la fiche pendant qu'on la
    // traverse. Ici rien ne recompose tant que `cinema` ne change pas.
    val pointerActivity = remember {
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    // Suspendue tant que la bande-annonce est au premier plan : là, un mouvement
    // de souris sert à viser un bouton, pas à réclamer l'interface de la fiche.
    LaunchedEffect(previewPlaying, cinemaCapable, trailerExpanded) {
        if (!previewPlaying || !cinemaCapable || trailerExpanded) {
            cinema = false
            return@LaunchedEffect
        }
        while (true) {
            // Le mode s'arme après un temps sans mouvement…
            while (withTimeoutOrNull(CINEMA_IDLE_MS) { pointerActivity.first() } != null) Unit
            cinema = true
            // …et se désarme au premier mouvement suivant, puis se réarme :
            // c'est la boucle qui fait le va-et-vient, sans horloge à lire.
            pointerActivity.first()
            cinema = false
        }
    }

    // Les deux états qui découvrent la bande-annonce, et la seule différence
    // entre eux est la présence des contrôles.
    val trailerInFront = trailerExpanded || cinema
    val soundWanted = when {
        trailerExpanded -> !trailerMuted
        // Le mode cinéma s'arme tout seul : il obéit au réglage, sans discuter.
        cinema -> trailerSound
        else -> false
    }

    val volume by animateFloatAsState(
        targetValue = if (soundWanted) 1f else 0f,
        animationSpec = tween(CINEMA_SOUND_FADE_MS),
        label = "trailerVolume",
    )
    val uiAlpha by animateFloatAsState(
        targetValue = if (trailerInFront) 0f else 1f,
        animationSpec = tween(CINEMA_UI_FADE_MS),
        label = "detailsUiAlpha",
    )

    LaunchedEffect(ready?.video?.key, trailerAutoplay, trailerPreview != null, trailerExpanded) {
        if (ready == null || trailerPreview == null) {
            previewPlaying = false
            return@LaunchedEffect
        }
        // Une demande explicite passe devant le délai **et** devant le réglage :
        // le bouton doit répondre tout de suite, y compris autoplay désactivé,
        // sans quoi il paraîtrait cassé chez qui l'a coupé.
        if (trailerExpanded) {
            previewPlaying = true
            return@LaunchedEffect
        }
        // Autoplay coupé : refermer les contrôles rend le réglage à sa place et
        // l'affiche au hero. Le bouton reste une exception ponctuelle, il ne
        // laisse pas une lecture derrière lui.
        if (!trailerAutoplay) {
            previewPlaying = false
            return@LaunchedEffect
        }
        // **Déjà en cours : on n'y touche pas.** Cet effet se relance à chaque
        // ouverture *et fermeture* des contrôles ; remettre l'aperçu à zéro en
        // sortant le faisait recharger le même manifeste dans un second
        // lecteur, ce que googlevideo sanctionne d'un 403 — le défaut qui avait
        // déjà valu un plantage. Fermer les contrôles doit rendre l'interface,
        // rien d'autre.
        if (previewPlaying) return@LaunchedEffect
        delay(HERO_PREVIEW_DELAY_MS)
        previewPlaying = true
        // On rend la main à l'affiche à la fin plutôt que de laisser une image
        // figée : `durationSeconds` vient de YouTube, et le lecteur d'aperçu n'a
        // pas de rappel de fin à nous donner. Zéro (durée inconnue) laisse
        // simplement l'aperçu courir.
        val duree = ready.durationSeconds
        if (duree > 0) {
            delay(duree * 1000L)
            previewPlaying = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().then(
            if (!cinemaCapable) {
                Modifier
            } else {
                // Passe **Initial** : on observe le pointeur sans lui prendre
                // ses événements. Un bouton survolé doit continuer de réagir
                // pendant que l'interface se rallume.
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            pointerActivity.tryEmit(Unit)
                        }
                    }
                }
            },
        ),
    ) {
        backdrop?.let { url ->
            MoovieAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp),
            )
        }
        // Au-dessus de l'affiche, et non à sa place : elle reste dessous pendant
        // le fondu, et pendant la seconde ou deux que le flux met à afficher sa
        // première image. Sans elle on verrait du noir.
        if (previewPlaying && ready != null && trailerPreview != null) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(HERO_PREVIEW_FADE_MS)),
                modifier = Modifier.fillMaxSize(),
            ) {
                trailerPreview(
                    ready.stream,
                    volume,
                    { trailerController = it },
                    Modifier.fillMaxSize(),
                )
            }
        }
        // Le voile s'efface avec l'interface qu'il sert à rendre lisible : le
        // garder en mode cinéma assombrirait la bande-annonce pour rien.
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { alpha = uiAlpha }
                .background(
                    Brush.verticalGradient(listOf(Color(0xAA0A0A0A), Color(0xE00A0A0A))),
                ),
        )

        // Scroll pleine largeur + marges portées par les enfants : les éléments
        // agrandis au focus débordent dans la marge au lieu d'être rognés.
        // 48 dp de marge, c'est le recul d'un salon. Sur 448 dp de large elles
        // mangent un cinquième de l'écran à elles seules.
        // Une seule source pour la marge de la page : le Modifier pour les
        // blocs ordinaires, la valeur pour ce qui a besoin d'un `contentPadding`
        // (les rangées défilantes). Deux constantes séparées finiraient par
        // diverger, et c'est exactement ce qui avait décalé le casting.
        val hPadDp = if (compact) 16.dp else 48.dp
        val hPad = Modifier.padding(horizontal = hPadDp)
        // Marge haute agrandie sur desktop pour que le titre passe sous le
        // bouton retour en overlay (sinon ils se chevauchent).
        val topPad = if (showBackButton) 96.dp else 48.dp
        val pageScope = rememberCoroutineScope()
        // Sur la liste des épisodes d'une série, la page ne défile pas en bloc :
        // l'en-tête et les saisons restent posés, seuls les épisodes défilent
        // (voir plus bas). Partout ailleurs — film, fiche d'épisode — le
        // défilement global reste le bon comportement.
        val seriesList = state is DetailsState.Tv && selectedEpisode == null
        Column(
            modifier = Modifier.fillMaxSize()
                // Effacée, pas démontée : la page garde sa position de
                // défilement et son focus, et revient telle qu'on l'a laissée
                // au premier mouvement de souris.
                .graphicsLayer { alpha = uiAlpha }
                .then(if (seriesList) Modifier else Modifier.verticalScroll(pageScroll))
                .padding(top = topPad, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                DetailsState.Loading -> SkeletonDetails(modifier = hPad)
                is DetailsState.Error -> {
                    Text(s.message, modifier = hPad)
                    MoovieButton(onClick = onBack, modifier = hPad) { Text(stringResource(Res.string.common_back)) }
                }
                is DetailsState.Movie -> {
                    val movieWatched = movieKey in watched
                    // Même mise en page que la fiche d'épisode — visuel à gauche,
                    // métadonnées et synopsis à droite — pour que les deux fiches
                    // du catalogue se ressemblent au lieu de diverger.
                    MovieHeader(
                        details = s.details,
                        isWatched = movieWatched,
                        showOverview = !compact,
                    )

                    // Bouton Lire direct : loader pendant le chargement des sources,
                    // cliquable dès qu'un lien dans la langue préférée existe,
                    // « VF indisponible » sinon. Le panneau reste en choix manuel.
                    val active = sources as? SourcesState.Active
                    val prefReady = active?.links?.any { it.language == streamLang.name } == true
                    val loadingSources = active == null || active.anyLoading
                    val searching = quickPlay is QuickPlayState.Searching
                    Row(
                        modifier = hPad,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MoovieButton(
                            onClick = {
                                // Cliquable aussi pendant le chargement : la lecture
                                // démarrera dès qu'une source arrive.
                                if (prefReady || loadingSources) onQuickPlayMovie()
                            },
                            modifier = primaryModifier,
                        ) {
                            when {
                                searching -> {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(Res.string.details_playing))
                                }
                                prefReady -> Text(
                                    if (resume.containsKey(movieKey)) stringResource(Res.string.details_resume) else stringResource(Res.string.details_play),
                                )
                                loadingSources -> {
                                    CircularProgressIndicator(
                                        color = MOOVIE_ACCENT,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(Res.string.details_searching, streamLang.name))
                                }
                                else -> Text(stringResource(Res.string.details_lang_unavailable, streamLang.name), color = Color(0xFF8A8A8A))
                            }
                        }
                        MoovieButton(onClick = onOpenPanel) { Text(stringResource(Res.string.details_sources)) }
                        // Bande-annonce et « En savoir plus » ne sont plus ici :
                        // ils vivent en haut à droite de la fiche. La rangée
                        // d'actions retrouve de l'air, et ces deux-là ne sont
                        // pas des actions sur le titre — l'un ouvre une vidéo,
                        // l'autre change de vue.
                        DownloadBestButton(downloadSearching, onDownloadBest)
                        // Œil = marquer vu / non vu (outline verte quand vu).
                        MoovieIconButton(
                            onClick = { onToggleWatched(movieKey) },
                            icon = if (movieWatched) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (movieWatched) stringResource(Res.string.mark_unwatched) else stringResource(Res.string.mark_watched),
                            selected = movieWatched,
                        )
                        // Signet = « À regarder plus tard ». Plein + outline verte
                        // quand le titre y est déjà, comme l'œil juste avant :
                        // l'état se lit sans avoir à ouvrir quoi que ce soit.
                        MoovieIconButton(
                            onClick = onToggleWatchlist,
                            icon = if (inWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(
                                if (inWatchlist) Res.string.watchlist_remove else Res.string.watchlist_add,
                            ),
                            selected = inWatchlist,
                        )
                    }
                    // Synopsis après les boutons sur téléphone : le glisser avant
                    // reléguait « Lire » sous dix-sept lignes de résumé, donc hors
                    // écran, pour un film qu'on venait pourtant de choisir.
                    if (compact && s.details.overview.isNotBlank()) {
                        Text(
                            s.details.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFDDDDDD),
                            modifier = hPad,
                        )
                    }
                    // « En savoir plus » prend la place du casting plutôt que de
                    // s'ajouter sous lui : c'est ce qui rend le retour immédiat.
                    if (infoVisible) {
                        MovieInfoPanel(
                            details = s.details,
                            country = country,
                            modifier = hPad.fillMaxWidth(),
                            // La page du film défile déjà en bloc.
                            scrollable = false,
                        )
                    } else {
                        // Casting sous les boutons, comme sur la fiche d'épisode :
                        // la descente au D-pad atteint d'abord Lire, pas une
                        // vignette d'acteur.
                        CastRow(s.details.credits?.cast.orEmpty(), hPadDp, onOpenPerson)
                    }
                }
                is DetailsState.Tv -> {
                    val selected = selectedEpisode
                    if (selected != null) {
                        // Fiche d'un épisode : même logique qu'un film (visuel,
                        // synopsis complet, Lire / Sources / Marquer vu).
                        // Retour / Échap revient à la liste des épisodes.
                        val ep = selected.episode
                        val key = episodeKey(selected.season, ep.episodeNumber)
                        EpisodeDetail(
                            infoVisible = infoVisible,
                            infoPanel = {
                                TvInfoPanel(
                                    details = s.details,
                                    country = country,
                                    modifier = hPad.fillMaxWidth(),
                                    // Fiche d'épisode : la page défile en bloc,
                                    // comme celle d'un film.
                                    scrollable = false,
                                )
                            },
                            onDownloadBest = onDownloadBest,
                            downloadSearching = downloadSearching,
                            showName = s.details.name,
                            season = selected.season,
                            ep = ep,
                            isWatched = key in watched,
                            hasResume = resume.containsKey(key),
                            searching = quickPlay is QuickPlayState.Searching,
                            primaryModifier = primaryModifier,
                            onPlay = { onQuickPlayEpisode(selected.season, ep.episodeNumber) },
                            onOpenSources = { onOpenEpisodePanel(selected.season, ep.episodeNumber) },
                            onToggleWatched = { onToggleWatched(key) },
                            cast = s.details.credits?.cast.orEmpty(),
                            onOpenPerson = onOpenPerson,
                            fallbackArt = s.details.backdropUrl() ?: s.details.posterUrl(),
                        )
                    } else {
                        // Deux volets plutôt qu'un empilement : l'écran fait
                        // 960 × 540 dp, et un synopsis qui occupe toute la
                        // largeur pour trois lignes prend à la liste la hauteur
                        // de trois épisodes. Côte à côte, la description garde
                        // sa place et la liste récupère toute la colonne.
                        SeriesPanes(
                            compact = compact,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) { headerModifier, listModifier ->
                        Column(
                            modifier = headerModifier,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                        // En-tête posé hors du défilement : il décrit ce qu'on
                        // est en train de parcourir, et le perdre au premier
                        // appui vers le bas revenait à naviguer à l'aveugle
                        // dans une liste de vingt épisodes.
                        Text(s.details.name, style = MaterialTheme.typography.headlineSmall)
                        // Année et résumé **de la saison** quand TMDB les donne.
                        // Ils ne l'étaient jamais : le parseur ignorait les deux
                        // champs, si bien que les vingt-deux saisons d'une série
                        // affichaient le même texte et la même année.
                        // Saison annoncée mais pas commencée : sa date de
                        // première diffusion vaut mieux que son année nue.
                        val seasonUpcoming = upcomingDate(s.seasonAirDate)
                        if (seasonUpcoming != null) {
                            Text(
                                stringResource(Res.string.details_episode_upcoming, seasonUpcoming),
                                color = MOOVIE_ACCENT,
                            )
                        } else (s.seasonYear ?: s.details.year)?.let { Text(it) }
                        ScrollingSynopsis(
                            text = s.seasonOverview.ifBlank { s.details.overview },
                            // Colonne étroite : le texte y tient sur plus de
                            // lignes, et il reste de la place sous les saisons.
                            // Empilé sur téléphone, il mange en revanche la
                            // liste d'épisodes — on le resserre.
                            lines = if (compact) 3 else 8,
                            style = MaterialTheme.typography.bodyMedium,
                            // Déroulé en continu : dans l'en-tête il n'y a pas
                            // de carte à focaliser pour déclencher la lecture,
                            // et un résumé tronqué net serait inatteignable.
                            active = true,
                        )
                        val seasonAllWatched = s.episodes.isNotEmpty() &&
                            s.episodes.all { episodeKey(s.season, it.episodeNumber) in watched }
                        // Bloc de commande resserré : titre, saisons et actions
                        // se suivent de près pour rendre à la liste la hauteur
                        // de deux épisodes. L'espacement de 16 dp de la colonne
                        // parente, appliqué entre chacun, la lui prenait.
                        //
                        // L'interception du Haut qui ramenait la page en haut a
                        // disparu avec elle : l'en-tête ne défile plus, il n'y a
                        // plus rien à découvrir au-dessus.
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(Res.string.details_seasons),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            // Télécharger la saison entière plutôt qu'ouvrir
                            // chaque épisode, son panneau de sources, et tomber
                            // une fois sur deux sur un hébergeur mort. La
                            // sélection réutilise le verdict du lecteur.
                            // Rien de diffusé, rien à télécharger : le bouton
                            // disparaît au lieu de rester inerte. C'est le cas
                            // d'une saison annoncée, où l'appui ne produisait
                            // aucun effet visible et ressemblait à une panne.
                            val airedCount = s.episodes.count {
                                it.episodeNumber > 0 && upcomingDate(it.airDate) == null
                            }
                            if (airedCount > 0) {
                            MoovieButton(onClick = { onDownloadSeason(s.season) }) {
                                Text(
                                    when {
                                        seasonDownload == null ->
                                            stringResource(Res.string.details_download_season)
                                        // Terminé : on dit ce qui a été fait,
                                        // **et ce qui a échoué**. « Recherche
                                        // 8/8… » puis un retour au libellé
                                        // d'origine laissait croire à un arrêt,
                                        // alors que deux épisodes n'avaient
                                        // simplement aucune source jouable — un
                                        // compte que l'on tenait déjà sans
                                        // jamais l'afficher.
                                        seasonDownload.done && seasonDownload.failed > 0 ->
                                            stringResource(
                                                Res.string.details_download_season_partial,
                                                seasonDownload.queued.toString(),
                                                seasonDownload.failed.toString(),
                                            )
                                        seasonDownload.done ->
                                            stringResource(
                                                Res.string.details_download_season_queued,
                                                seasonDownload.queued.toString(),
                                            )
                                        else -> stringResource(
                                            Res.string.details_download_season_progress,
                                            seasonDownload.checked.toString(),
                                            seasonDownload.total.toString(),
                                        )
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (seasonDownload?.done == true && seasonDownload.failed > 0) {
                                        Color(0xFFE0B057)
                                    } else {
                                        Color.Unspecified
                                    },
                                )
                            }
                            }
                        }
                        val seasonsState = rememberLazyListState()
                        MoovieRail(seasonsState) {
                            LazyRow(
                                state = seasonsState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                // Marge réduite : la colonne est déjà décalée,
                                // 48 dp de plus rognerait deux saisons.
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                itemsIndexed(s.details.seasons.filter { it.seasonNumber > 0 }) { index, season ->
                                    val isCurrent = season.seasonNumber == s.season
                                    MoovieButton(
                                        onClick = { onSelectSeason(season.seasonNumber) },
                                        // La saison affichée se lit au soulignement,
                                        // comme partout ailleurs. Le « ● » collé au
                                        // libellé était un marqueur d'avant le thème :
                                        // deux langages pour un même état.
                                        selected = isCurrent,
                                        // Et le focus arrive sur elle, pas sur S1 —
                                        // sinon on remonte des saisons pour se
                                        // retrouver au début d'une série qu'on suit.
                                        modifier = if (isCurrent) primaryModifier else Modifier,
                                    ) {
                                        // Compté une fois : le libellé et la couleur
                                        // répondent à la même question.
                                        val ready = downloadList.readyInSeason(
                                            s.details.id,
                                            season.seasonNumber,
                                        )
                                        // TMDB compte les épisodes annoncés, pas
                                        // les diffusés : sur une saison en cours
                                        // le dénominateur inclut ce qui n'existe
                                        // pas encore, et la pastille ne passait
                                        // jamais au vert même tout téléchargé.
                                        // La liste d'épisodes n'est chargée que
                                        // pour la saison affichée — ailleurs, le
                                        // compte annoncé reste la seule mesure.
                                        val total = if (season.seasonNumber == s.season) {
                                            s.episodes.count {
                                                it.episodeNumber > 0 && upcomingDate(it.airDate) == null
                                            }
                                        } else {
                                            season.episodeCount
                                        }
                                        val complete = ready > 0 && ready >= total
                                        Text(
                                            buildString {
                                                append("S${season.seasonNumber}")
                                                // Ce qui manque, pas ce qu'on a :
                                                // « 3/8 » se lit comme un reste à
                                                // faire, alors qu'une pastille
                                                // verte sur une saison incomplète
                                                // serait un mensonge. Complète, on
                                                // ne compte plus, le vert suffit.
                                                if (ready > 0 && total > 0) {
                                                    append(if (complete) "  ✓" else "  $ready/$total")
                                                }
                                            },
                                            color = if (complete) Color(0xFF7DDC7D) else Color.Unspecified,
                                        )
                                    }
                                }
                            }
                        }
                        // Actions de titre sur leur propre ligne, et non en fin
                        // de rangée des saisons : sur une série de vingt-deux
                        // saisons elles se retrouvaient à vingt-deux boutons du
                        // bord, donc introuvables. Ici elles sont toujours au
                        // même endroit, à un appui vers le bas.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MoovieIconButton(
                                onClick = onToggleSeasonWatched,
                                icon = if (seasonAllWatched) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (seasonAllWatched) stringResource(Res.string.mark_season_unwatched) else stringResource(Res.string.mark_season_watched),
                                selected = seasonAllWatched,
                            )
                            MoovieIconButton(
                                onClick = onToggleWatchlist,
                                icon = if (inWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = stringResource(
                                    if (inWatchlist) Res.string.watchlist_remove else Res.string.watchlist_add,
                                ),
                                selected = inWatchlist,
                            )
                        }
                        }
                        }
                        // Volet droit : « En savoir plus » y prend la place de la
                        // liste des épisodes. C'est le cas qui a dicté la
                        // conception — on consulte la date du prochain épisode,
                        // puis on veut ses épisodes, sans avoir à défiler.
                        if (infoVisible) {
                            TvInfoPanel(
                                details = s.details,
                                country = country,
                                // Il remplace la liste des épisodes, seul
                                // élément défilant de la fiche de série.
                                scrollable = true,
                                modifier = listModifier.padding(
                                    if (compact) {
                                        PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    } else {
                                        PaddingValues(end = 48.dp, bottom = 24.dp)
                                    },
                                ),
                            )
                        } else {
                        // Volet droit : la liste occupe toute la hauteur.
                        //
                        // LazyColumn et non Column défilante : c'est ce qui donne
                        // `animateScrollToItem`, seul moyen de caler l'épisode
                        // focalisé en haut — sans quoi il se colle en bas du
                        // cadre et le suivant reste invisible, exactement le
                        // défaut corrigé sur les rangées de l'accueil.
                        LazyColumn(
                            state = episodesState,
                            modifier = listModifier,
                            // Marges dans le contentPadding : l'agrandissement au
                            // focus déborde dedans au lieu d'être rogné.
                            contentPadding = if (compact) {
                                PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            } else {
                                PaddingValues(end = 48.dp, bottom = 24.dp)
                            },
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                        item {
                            Text(stringResource(Res.string.details_episodes_season, s.season), style = MaterialTheme.typography.titleMedium)
                        }
                        itemsIndexed(s.episodes) { index, ep ->
                            val key = episodeKey(s.season, ep.episodeNumber)
                            val isNext = ep.episodeNumber == s.resumeEpisode
                            Box(
                                modifier = Modifier.onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) {
                                        return@onPreviewKeyEvent false
                                    }
                                    when (event.key) {
                                        // Gauche = retour aux commandes de la
                                        // série. Rien n'est à gauche d'un
                                        // épisode, et remonter jusqu'aux saisons
                                        // épisode par épisode sur une saison de
                                        // vingt-cinq était le seul chemin.
                                        Key.DirectionLeft ->
                                            runCatching { primaryFocus.requestFocus() }.isSuccess
                                        // Remonter depuis le 1er épisode va sur
                                        // la saison *affichée*, pas sur S1 : la
                                        // recherche de focus native prend le
                                        // voisin le plus proche et renverrait au
                                        // début d'une série suivie depuis des
                                        // saisons.
                                        Key.DirectionUp -> index == 0 &&
                                            runCatching { primaryFocus.requestFocus() }.isSuccess
                                        else -> false
                                    }
                                }.onFocusChanged {
                                    // Voir RowSlot sur l'accueil : le délai laisse
                                    // passer le `bringIntoView` du système, qui
                                    // part sur la même prise de focus et
                                    // écraserait l'alignement.
                                    if (it.isFocused) pageScope.launch {
                                        delay(80)
                                        episodesState.animateScrollToItem(index + 1)
                                    }
                                },
                            ) {
                                EpisodeRow(
                                    ep = ep,
                                    isWatched = key in watched,
                                    isNext = isNext,
                                    modifier = if (isNext) {
                                        Modifier.focusRequester(resumeEpisodeFocus)
                                    } else {
                                        Modifier
                                    },
                                    progress = resume[key]?.progress,
                                    download = downloads.values.firstOrNull { it.key == key },
                                    // OK = fiche de l'épisode (comme un film) ;
                                    // OK long = bascule vu / non vu.
                                    onOpen = { onOpenEpisode(s.season, ep) },
                                    onToggleWatched = { onToggleWatched(key) },
                                    fallbackArt = s.details.backdropUrl() ?: s.details.posterUrl(),
                                )
                            }
                        }
                        // Casting **dans** le défilement, en queue de liste, et
                        // seulement sur téléphone. Posé sous les volets, c'était
                        // un bloc fixe d'environ 190 dp pris à une liste qui n'a
                        // déjà que ce qui reste sous l'en-tête : il restait une
                        // fenêtre d'un épisode et demi pour parcourir la saison.
                        //
                        // En dernier élément il ne coûte plus rien tant qu'on ne
                        // descend pas le chercher, et la liste récupère toute la
                        // hauteur.
                        //
                        // hPad nul : la marge de 16 dp vient déjà du
                        // contentPadding de la liste. La cumuler décalerait la
                        // rangée par rapport aux épisodes qu'elle suit.
                        if (compact) {
                            item {
                                CastRow(s.details.credits?.cast.orEmpty(), 0.dp, onOpenPerson)
                            }
                        }
                        }
                        } // fin du `else` : liste des épisodes ou « En savoir plus »
                        }
                        // Le casting d'une série est en queue de la liste des
                        // épisodes (voir plus haut), pas ici : sous les volets il
                        // aurait été un bloc fixe pris à la liste.
                        //
                        // Rien sur écran large non plus. Mesuré sur les 540 dp
                        // d'une TV : l'en-tête (titre, résumé, saisons, actions)
                        // en prend ~350, il en reste ~145 quand la rangée en
                        // demande ~190. Ajoutée quand même, elle **effaçait le
                        // sélecteur de saisons** hors de l'écran — une page qui
                        // perd sa navigation pour gagner une illustration est un
                        // mauvais échange. Le casting y reste à un appui : la
                        // fiche d'un épisode, elle, a la place.
                    }
                }
            }
        }

        // Contrôles de la bande-annonce, tout en haut de la pile : ils se posent
        // sur l'aperçu **et** sur l'interface effacée, qui reste composée
        // dessous pour garder sa position de défilement et son focus.
        AnimatedVisibility(
            visible = trailerExpanded && previewPlaying,
            enter = fadeIn(animationSpec = tween(CINEMA_UI_FADE_MS)),
            exit = fadeOut(animationSpec = tween(CINEMA_UI_FADE_MS)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TrailerControls(
                controller = trailerController,
                title = (trailer as? TrailerState.Ready)?.video?.name.orEmpty(),
                muted = trailerMuted,
                onToggleMute = { trailerMuted = !trailerMuted },
                onClose = onCloseTrailer,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Bande-annonce et « En savoir plus », en haut à droite.
        //
        // Sortis de la rangée d'actions, où ils s'entassaient avec Lire,
        // Sources, l'œil et le signet : sur un portrait de 448 dp la ligne
        // débordait, et c'est ce qui avait déjà fait disparaître le signet.
        // Ils n'y avaient de toute façon pas leur place — cette rangée agit sur
        // le titre (le lire, le marquer, le mettre de côté), là où ces deux-là
        // ouvrent une vidéo et changent de vue.
        //
        // Masqués quand la bande-annonce est au premier plan ou que le panneau
        // des sources est ouvert : ils recouvriraient l'un comme l'autre.
        if (!trailerExpanded && !panelVisible && state !is DetailsState.Loading) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = hPadDp, vertical = 24.dp)
                    .graphicsLayer { alpha = uiAlpha }
                    // Redescendre, explicitement.
                    //
                    // Ces boutons sont seuls en haut à droite : sous eux, le
                    // faisceau vertical de la recherche de focus ne rencontre
                    // rien — le contenu de la fiche est à gauche. Compose ne
                    // trouve donc aucune cible et le focus reste coincé là,
                    // sans aucun moyen de revenir à la télécommande.
                    //
                    // C'est le même piège que la descente en-tête → contenu
                    // déjà câblée sur les rangées : quand la géométrie ne
                    // porte pas le chemin, il faut l'écrire.
                    .onPreviewKeyEvent { event ->
                        event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionDown &&
                            runCatching { primaryFocus.requestFocus() }.isSuccess
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrailerButton(trailer, onPlayTrailer)
                InfoToggleButton(infoVisible) { infoVisible = !infoVisible }
            }
        }

        // Bouton retour desktop, en overlay haut-gauche (masqué quand le panneau
        // des sources est ouvert : Échap/clic-extérieur le ferme d'abord).
        if (showBackButton && !panelVisible && !trailerExpanded) {
            MoovieIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
            )
        }

        // Panneau des sources : s'ouvre dès le clic, se remplit en streaming.
        // On mémorise le dernier état actif pour garder le contenu pendant la
        // sortie animée (où `sources` repasse à Idle).
        val lastActive = remember { mutableStateOf<SourcesState.Active?>(null) }
        (sources as? SourcesState.Active)?.let { lastActive.value = it }
        // Scrim de fermeture : un clic/tap hors du panneau le ferme (souris sur
        // desktop, touch éventuel). Pointer uniquement — invisible au D-pad TV.
        if (panelVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onClosePanel() } },
            )
        }
        AnimatedVisibility(
            visible = panelVisible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            lastActive.value?.let { active ->
                SourcesSlideOver(
                    state = active,
                    preferred = streamLang,
                    resolveError = resolveError,
                    resolvingUrl = resolvingUrl,
                    onPick = onPickSource,
                    onDownload = onDownloadSource,
                    qualities = sourceQualities,
                    statuses = sourceStatuses,
                    downloads = downloads,
                    onRequestQuality = onRequestQuality,
                )
            }
        }

        // Bannière de lecture rapide (recherche en cours / indisponible),
        // surtout utile pour les épisodes qui n'ont pas de bouton dédié.
        val q = quickPlay
        if (q is QuickPlayState.Unavailable) {
            LaunchedEffect(q) {
                delay(4000)
                onDismissQuickPlay()
            }
        }
        AnimatedVisibility(
            visible = q !is QuickPlayState.Idle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(MoovieShape)
                    .background(Color(0xF21E1E1E))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (q) {
                    is QuickPlayState.Searching -> {
                        CircularProgressIndicator(
                            color = MOOVIE_ACCENT,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        val hoster = q.hoster
                        Text(
                            if (hoster != null) {
                                // Même capitalisation que dans le panneau des sources.
                                stringResource(
                                    Res.string.details_trying_source,
                                    hoster.replaceFirstChar { it.uppercase() },
                                )
                            } else {
                                stringResource(Res.string.details_searching_source, q.label)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is QuickPlayState.Unavailable -> Text(
                        stringResource(Res.string.details_lang_unavailable, q.lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE0A0A0),
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun SourcesSlideOver(
    state: SourcesState.Active,
    preferred: StreamLanguage,
    resolveError: String?,
    resolvingUrl: String?,
    onPick: (EmbedLink) -> Unit,
    onDownload: (EmbedLink) -> Unit,
    /** Verdict de la sonde par URL, rempli au fil de l'eau. */
    statuses: Map<String, LinkStatus>,
    downloads: Map<String, Download>,
    /** Qualité mesurée par URL d'embed, remplie au fil de l'eau. */
    qualities: Map<String, String>,
    /** Demande la mesure d'un lien ; sans effet si elle est déjà connue. */
    onRequestQuality: (EmbedLink) -> Unit,
) {
    val links = state.links
    val grouped = links.groupBy { it.language ?: "?" }
    // La langue préférée d'abord, puis celles du réglage dans leur ordre de
    // déclaration, puis tout ce que les catalogues auraient étiqueté autrement :
    // une langue inédite apparaît ainsi sans qu'on ait touché à ce code.
    val order = (listOf(preferred.name) + StreamLanguage.entries.map { it.name } + grouped.keys)
        .distinct()
    val sections = order.filter { grouped.containsKey(it) }.map { it to grouped.getValue(it) }
    val prefMissing = links.isNotEmpty() && !grouped.containsKey(preferred.name)
    val firstFocus = remember { FocusRequester() }

    // Focalise le 1er lecteur dès qu'une source arrive (le panneau s'ouvre vide).
    LaunchedEffect(links.isNotEmpty()) {
        if (links.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    // Marges horizontales portées par les enfants : la liste défilante va jusqu'aux
    // bords du panneau, les boutons agrandis au focus ne sont plus rognés.
    val pPad = Modifier.padding(horizontal = 24.dp)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            // Plafond, pas largeur fixe : 380 dp tiennent sur les 448 dp d'un
            // Pixel, mais un téléphone plus étroit — 360 dp de large, ce qui
            // court encore — verrait le panneau déborder de l'écran.
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .background(Color(0xF2121212))
            // Avale les clics : cliquer dans le panneau ne doit pas atteindre
            // le scrim de fermeture situé derrière.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.details_sources), style = MaterialTheme.typography.titleLarge, modifier = pPad)

        // Barre de progression tant qu'au moins un provider charge.
        if (state.anyLoading) {
            LinearProgressIndicator(
                color = MOOVIE_ACCENT,
                trackColor = Color(0xFF2A2A2A),
                modifier = Modifier.fillMaxWidth().then(pPad),
            )
        }
        SourcesSummary(state.providers, sourceCount = links.size, modifier = pPad)

        // L'appui long ne se devine pas. La mention ne s'affiche que lorsqu'il y
        // a quelque chose à télécharger : sur un panneau vide elle décrirait une
        // action impossible.
        if (links.isNotEmpty()) {
            Text(
                stringResource(Res.string.details_source_download_hint),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = pPad,
            )
        }

        resolveError?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFFE06A6A), modifier = pPad)
        }
        if (prefMissing) {
            Text(
                stringResource(Res.string.details_lang_missing, preferred.name),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE0A0A0),
                modifier = pPad,
            )
        }
        Spacer(Modifier.height(4.dp))

        when {
            links.isEmpty() && state.anyLoading -> SkeletonRows(modifier = pPad)
            links.isEmpty() -> Text(
                stringResource(Res.string.details_no_sources),
                color = Color(0xFFE0A0A0),
                modifier = pPad,
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                sections.forEachIndexed { sectionIndex, (lang, sourcesInLang) ->
                    item(key = "h_$lang") {
                        Text(
                            lang,
                            style = MaterialTheme.typography.titleMedium,
                            color = MOOVIE_ACCENT,
                            modifier = Modifier.padding(top = if (sectionIndex == 0) 0.dp else 8.dp),
                        )
                    }
                    // Numérote les liens que la ligne afficherait à l'identique.
                    // Le regroupement porte sur ce qui est **visible** — hébergeur
                    // et variante — et surtout pas sur le catalogue : celui-ci
                    // n'apparaît plus dès qu'une qualité est mesurée, et deux
                    // « Voe / 720p » de catalogues différents redevenaient alors
                    // impossibles à départager.
                    val ranks = sourcesInLang.groupingBy { it.hoster to it.variant }.eachCount()
                    val seen = mutableMapOf<Pair<String, String?>, Int>()

                    itemsIndexed(sourcesInLang, key = { _, l -> l.url }) { linkIndex, link ->
                        val id = link.hoster to link.variant
                        val rank = seen.merge(id, 1, Int::plus) ?: 1
                        // La mesure part quand la ligne entre à l'écran : dans une
                        // LazyColumn, seules les lignes visibles sont composées, donc
                        // on ne résout pas trente liens pour en montrer six.
                        LaunchedEffect(link.url) { onRequestQuality(link) }
                        SourceRow(
                            link = link,
                            rank = if ((ranks[id] ?: 1) > 1) rank else null,
                            quality = qualities[link.url],
                            status = statuses[link.url] ?: LinkStatus.UNKNOWN,
                            resolving = link.url == resolvingUrl,
                            download = downloads[link.url],
                            onClick = { onPick(link) },
                            onLongClick = { onDownload(link) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (sectionIndex == 0 && linkIndex == 0) {
                                        Modifier.focusRequester(firstFocus)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** Pastilles de progression par provider (chargement / trouvé / vide / échec). */
/**
 * Une source dans le panneau.
 *
 * Le nom d'hébergeur seul ne suffisait pas : trois liens « Vidzy » côte à côte
 * ne se distinguaient par rien, et le choix se faisait à l'aveugle. La ligne
 * porte donc ce que la source déclare vraiment — la **variante** (doublage,
 * palier de qualité) mise en évidence à droite, et le **catalogue** d'origine en
 * seconde ligne. À défaut de tout critère, un rang numérique.
 *
 * Les couleurs secondaires sont des blancs transparents plutôt qu'un gris fixe :
 * le fond du bouton passe au rouge d'accentuation quand il a le focus, et un
 * gris y deviendrait illisible.
 */
@Composable
private fun SourceRow(
    link: EmbedLink,
    rank: Int?,
    quality: String?,
    /**
     * Ce que la sonde en a conclu. Une source morte est grisée et le dit, mais
     * reste choisissable : la sonde a des faux négatifs, et interdire vaudrait
     * moins bien que prévenir.
     */
    status: LinkStatus,
    /** Cette source est celle qu'on est en train d'ouvrir. */
    resolving: Boolean,
    /**
     * Le téléchargement lancé **depuis cette source**, s'il existe.
     *
     * La jointure se fait sur `sourceUrl` et non sur la clé média : un
     * `Download` appartient au titre, mais il garde le lien d'embed dont il est
     * parti. C'est ce qui permet d'allumer la ligne qu'on a effectivement
     * choisie — et une seule, la file refusant un second téléchargement pour le
     * même titre.
     *
     * Sans lui, un appui long ne changeait **rien** à l'écran : l'action la plus
     * longue de l'application ne disait ni qu'elle avait commencé, ni où elle en
     * était.
     */
    download: Download?,
    onClick: () -> Unit,
    /**
     * Appui long = télécharger. Pas un second bouton : la ligne est déjà pleine,
     * et sur un portrait un élément de plus la ferait déborder — c'est déjà la
     * raison pour laquelle le témoin de résolution *remplace* la variante.
     * L'appui long est par ailleurs l'idiome que l'app enseigne partout
     * ailleurs : épingler un genre, marquer vu, renommer un profil.
     */
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dead = status == LinkStatus.DEAD
    // Colonne autour du bouton : la barre d'avancement se pose **sous** la
    // ligne, pleine largeur, plutôt que de disputer la place horizontale déjà
    // comptée — c'est la même raison qui fait que le témoin de résolution
    // remplace la variante au lieu de s'y ajouter.
    Column(modifier = Modifier.fillMaxWidth()) {
    MoovieButton(onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildString {
                    append(link.hoster.replaceFirstChar { it.uppercase() })
                    if (rank != null) append(" · $rank")
                },
                style = MaterialTheme.typography.titleSmall,
                // Le titre s'éteint avec la source : c'est ce qui se lit en
                // premier, donc ce qui doit porter le verdict.
                color = if (dead) Color.White.copy(alpha = 0.45f) else Color.Unspecified,
            )
            // La qualité prime dès qu'elle est connue : c'est le critère de choix.
            // En attendant, le catalogue plutôt qu'une ligne vide — mieux vaut
            // apprendre d'où vient la source que de regarder un trou.
            // Le verdict prime sur la qualité : savoir qu'une source ne
            // répond pas vaut mieux que de savoir en quelle définition elle ne
            // répond pas.
            //
            // Le téléchargement prime sur les deux : c'est la seule information
            // qui répond à une action qu'on vient de déclencher, alors que la
            // qualité et le verdict décrivent un état permanent.
            val downloadLine = when (download?.state) {
                DownloadState.QUEUED -> stringResource(Res.string.details_source_dl_queued)
                DownloadState.RUNNING -> stringResource(
                    Res.string.details_source_dl_running,
                    (download.progress * 100).toInt().toString(),
                )
                DownloadState.PAUSED -> stringResource(Res.string.details_source_dl_paused)
                DownloadState.DONE -> stringResource(Res.string.player_download_done)
                DownloadState.FAILED -> stringResource(Res.string.details_source_dl_failed)
                null -> null
            }
            val secondary = downloadLine ?: if (dead) {
                stringResource(Res.string.details_source_dead)
            } else {
                quality ?: link.provider?.let { stringResource(Res.string.details_source_via, it) }
            }
            secondary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        download?.state == DownloadState.DONE -> Color(0xFF7DDC7D)
                        download?.state == DownloadState.FAILED -> Color(0xFFE0A0A0)
                        downloadLine != null -> MOOVIE_ACCENT
                        dead -> Color(0xFFE0A0A0)
                        quality != null -> Color.White.copy(alpha = 0.9f)
                        else -> Color.White.copy(alpha = 0.55f)
                    },
                )
            }
        }
        link.variant?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        // Ouvrir une source prend une à trois secondes : on interroge
        // l'hébergeur, on désobfusque, puis on vérifie que le flux est bien
        // servi. Sans ce témoin, l'appui semblait n'avoir rien fait et le
        // lecteur s'ouvrait « tout seul » plus tard.
        //
        // Il remplace la variante plutôt que de s'ajouter à côté : la ligne est
        // déjà pleine, et sur un portrait un élément de plus la ferait déborder.
        if (resolving) {
            Spacer(Modifier.width(10.dp))
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    // Déterminée dès qu'on connaît le nombre de segments. Avant, la ligne de
    // texte dit déjà « en attente » : une barre figée à zéro se lirait comme un
    // blocage plutôt que comme un démarrage.
    if (download?.state == DownloadState.RUNNING && download.totalSegments > 0) {
        MoovieProgressBar(
            progress = download.progress,
            trackColor = Color(0x33FFFFFF),
            modifier = Modifier.fillMaxWidth().height(3.dp),
        )
    }
    }
}

/**
 * Synthèse compacte de la recherche de sources.
 *
 * Remplace une puce par catalogue : à cinq providers, la rangée débordait de
 * l'écran — la dernière puce sortait du cadre et le nom s'y cassait en colonne.
 * Chaque ligne de la liste indiquant désormais son catalogue d'origine, ces
 * puces faisaient doublon.
 *
 * Ne reste ici que ce que la liste ne dit pas : l'avancement, et les catalogues
 * en échec — un catalogue vide n'est pas une information utile, un catalogue
 * cassé en est une.
 */
@Composable
private fun SourcesSummary(
    providers: List<ProviderProgress>,
    sourceCount: Int,
    modifier: Modifier = Modifier,
) {
    val loading = providers.count { it.status == ProviderStatus.LOADING }
    val withResults = providers.count { it.status == ProviderStatus.DONE }
    val failed = providers.filter { it.status == ProviderStatus.FAILED }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading > 0) {
            CircularProgressIndicator(
                color = MOOVIE_ACCENT,
                strokeWidth = 2.dp,
                modifier = Modifier.size(12.dp),
            )
            Text(
                stringResource(Res.string.details_sources_searching),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFCCCCCC),
            )
        }
        if (sourceCount > 0) {
            Text(
                pluralStringResource(Res.plurals.details_source_count, sourceCount, sourceCount) +
                    " · " +
                    pluralStringResource(Res.plurals.details_catalogue_count, withResults, withResults),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFCCCCCC),
            )
        }
        if (failed.isNotEmpty()) {
            Text(
                failed.joinToString(", ") { "✕ ${it.name}" },
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE06A6A),
            )
        }
    }
}

/** Lignes fantômes (skeleton) animées tant qu'aucune source n'est encore arrivée. */
@Composable
private fun SkeletonRows(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(MoovieShape)
                    .background(Color.White.copy(alpha = alpha * 0.15f)),
            )
        }
    }
}

/** Pastille ✓ (contenu déjà vu). */
@Composable
private fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0xCC0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", color = Color(0xFF5FD98A), style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * En-tête d'une fiche film, calqué sur celui d'un épisode : visuel à gauche,
 * métadonnées et synopsis à droite.
 *
 * Les deux fiches du catalogue avaient divergé — l'épisode montrait son visuel,
 * sa date, sa durée et sa note ; le film se contentait d'un titre suivi d'une
 * ligne de texte. Même gabarit des deux côtés, à une différence près : une
 * affiche est au format 2:3 là où une vignette d'épisode est en 16:9.
 */
/**
 * Bouton « Bande-annonce ».
 *
 * N'apparaît que sur un flux **déjà résolu** — il n'a donc ni état de
 * chargement ni état d'échec : les deux se produisent avant qu'il n'existe. Sur
 * un titre sans bande-annonce jouable il ne rend rien du tout, plutôt qu'un
 * bouton grisé qui prendrait une place que la rangée n'a pas et ajouterait un
 * arrêt au D-pad ne menant nulle part.
 *
 * **Icône seule**, comme l'œil et le signet qui la suivent. En toutes lettres
 * elle mesurait cent quarante points de plus, et sur un portrait de 448 dp
 * c'était le signet « à regarder plus tard » qui sortait de la rangée — un
 * bouton existant rendu invisible par un bouton neuf. Le libellé n'est pas
 * perdu : [MoovieIconButton] en fait une infobulle au survol, donc au bureau,
 * et le `contentDescription` partout ailleurs.
 *
 * `Theaters` plutôt qu'un triangle de lecture : « Lire » en porte déjà un, et
 * deux triangles voisins dans la même rangée ne se distingueraient pas.
 */
/**
 * Bascule du panneau « En savoir plus ».
 *
 * Un seul bouton pour aller **et** revenir : c'est ce qui permet de consulter
 * une date de diffusion puis de retomber sur ses épisodes d'un appui. Deux
 * entrées séparées auraient demandé de chercher par où sortir.
 *
 * **L'état est porté par l'icône**, pleine quand le panneau est ouvert et
 * évidée sinon — comme le signet (`Bookmark` / `BookmarkBorder`) et l'œil juste
 * à côté. Le seul `selected` n'y suffisait pas : `moovieSurface` dessine le même
 * soulignement pour la sélection (alpha 0,65) et pour le focus ou le survol
 * (alpha 1,0), si bien qu'un bouton éteint mais survolé paraît **plus** allumé
 * qu'un bouton allumé. Tant que le pointeur restait dessus — c'est-à-dire juste
 * après l'avoir cliqué — les deux états étaient indiscernables.
 */
@Composable
private fun InfoToggleButton(visible: Boolean, onClick: () -> Unit) {
    MoovieIconButton(
        onClick = onClick,
        icon = if (visible) Icons.Filled.Info else Icons.Outlined.Info,
        contentDescription = stringResource(
            if (visible) Res.string.details_tab_overview else Res.string.details_tab_info,
        ),
        selected = visible,
    )
}

/** Clé d'identité du titre affiché, pour remettre les vues locales à plat. */
private fun DetailsState.titleKey(): String = when (this) {
    is DetailsState.Movie -> "movie:${details.id}"
    is DetailsState.Tv -> "tv:${details.id}"
    else -> ""
}

/**
 * Télécharge le titre affiché, dans la meilleure définition trouvable.
 *
 * Icône seule, comme ses voisins de rangée. Il tourne pendant la recherche :
 * celle-ci résout et sonde plusieurs sources, ce qui prend quelques secondes,
 * et sans rien à l'écran l'appui semblerait n'avoir rien fait.
 *
 * Distinct de l'appui long sur une source du panneau, qui reste le moyen de
 * choisir un hébergeur précis : ici on ne désigne rien, on demande le meilleur.
 */
@Composable
private fun DownloadBestButton(searching: Boolean, onClick: () -> Unit) {
    MoovieIconButton(
        onClick = { if (!searching) onClick() },
        icon = if (searching) Icons.Default.HourglassEmpty else Icons.Default.Download,
        contentDescription = stringResource(Res.string.details_download_best),
        selected = searching,
    )
}

@Composable
private fun TrailerButton(trailer: TrailerState, onClick: () -> Unit) {
    if (trailer !is TrailerState.Ready) return
    MoovieIconButton(
        onClick = onClick,
        icon = Icons.Default.Theaters,
        contentDescription = stringResource(Res.string.details_trailer),
    )
}

@Composable
private fun MovieHeader(
    details: MovieDetails,
    isWatched: Boolean,
    /**
     * Faux sur téléphone : le synopsis y est rendu **après** les boutons, pas
     * avant. Dix-sept lignes de résumé entre le titre et « Lire » obligeaient à
     * faire défiler la page pour lancer un film qu'on venait déjà de choisir.
     */
    showOverview: Boolean = true,
) {
    val compact = useBottomNav
    // Côte à côte, l'affiche laisse 230 dp au texte : le synopsis s'y replie sur
    // quatre mots par ligne et déroule dix-sept lignes, tandis que la moitié
    // gauche reste vide sous l'affiche. Empilé, chaque bloc a toute la largeur.
    val header = @Composable {
        Box(
            modifier = Modifier
                // Calé sur la *hauteur* de la vignette d'épisode (420 × 16:9 ≈
                // 236 dp), pas sur sa largeur : une affiche 2:3 de 240 dp de large
                // en ferait 360 de haut et repousserait titre, genres et note hors
                // de l'écran dès que le focus descend sur « Lecture ».
                .width(if (compact) 150.dp else 160.dp)
                .aspectRatio(2f / 3f)
                .clip(MoovieShape)
                .background(Color(0xFF222222)),
        ) {
            MoovieAsyncImage(
                model = details.posterUrl() ?: details.backdropUrl(),
                contentDescription = details.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (compact) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            header()
            MovieMeta(
                details = details,
                isWatched = isWatched,
                showOverview = showOverview,
                centered = true,
            )
        }
        return
    }

    Row(
        modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        header()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MovieMeta(details = details, isWatched = isWatched, showOverview = showOverview)
        }
    }
}

/** Genres, titre, année/durée/note, et le synopsis quand il a sa place ici. */
@Composable
private fun MovieMeta(
    details: MovieDetails,
    isWatched: Boolean,
    showOverview: Boolean,
    centered: Boolean = false,
) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            val genres = details.genres.mapNotNull { it.name.takeIf(String::isNotBlank) }
            if (genres.isNotEmpty()) {
                Text(
                    genres.take(3).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MOOVIE_ACCENT,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(details.title, style = MaterialTheme.typography.headlineSmall)
                if (isWatched) WatchedBadge()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Film pas encore sorti : la date complète remplace l'année,
                // qui à elle seule ne dit pas s'il est déjà disponible. Aucune
                // source n'existera avant, autant l'annoncer que laisser
                // chercher.
                val upcoming = upcomingDate(details.releaseDate)
                if (upcoming != null) {
                    Text(
                        stringResource(Res.string.details_episode_upcoming, upcoming),
                        style = MaterialTheme.typography.titleSmall,
                        color = MOOVIE_ACCENT,
                    )
                } else details.year?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = Color(0xFFCCCCCC))
                }
                formatDuration(details.runtime)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFCCCCCC),
                    )
                }
                if (details.voteAverage > 0) {
                    Text(
                        "★ %.1f".format(details.voteAverage),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFE6B800),
                    )
                }
            }
            if (showOverview && details.overview.isNotBlank()) {
                Text(
                    details.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFDDDDDD),
                )
            }
    }
}

/**
 * Fiche détaillée d'un épisode : le pendant de la fiche d'un film (visuel,
 * synopsis complet, Lire / Sources / Marquer vu) pour que le comportement du
 * bouton OK soit le même partout.
 */
@Composable
private fun EpisodeDetail(
    /**
     * « En savoir plus » sur une fiche d'épisode montre les métadonnées de la
     * **série** : c'est d'elle qu'on veut la chaîne, le statut ou la date du
     * prochain épisode. Sans ce paramètre le bouton basculait un état que
     * personne ne lisait, et paraissait mort.
     */
    infoVisible: Boolean = false,
    infoPanel: @Composable () -> Unit = {},
    onDownloadBest: () -> Unit = {},
    downloadSearching: Boolean = false,
    showName: String,
    season: Int,
    ep: Episode,
    isWatched: Boolean,
    hasResume: Boolean,
    searching: Boolean,
    /** Porte le focus d'entrée **et** ramène la page en haut. Voir son origine. */
    primaryModifier: Modifier,
    onPlay: () -> Unit,
    onOpenSources: () -> Unit,
    onToggleWatched: () -> Unit,
    /**
     * Casting **de la série**, pas de l'épisode : TMDB ne donne les invités
     * qu'épisode par épisode, au prix d'un appel de plus, alors que ce qu'on
     * cherche en reconnaissant un visage est le rôle principal.
     */
    cast: List<CastMember> = emptyList(),
    onOpenPerson: (CastMember) -> Unit = {},
    /** Voir [EpisodeRow]. Même repli, en grand. */
    fallbackArt: Any? = null,
) {
    val compact = useBottomNav
    val hPadDp = if (compact) 16.dp else 48.dp
    val hPad = Modifier.padding(horizontal = hPadDp)
    // La vignette fait 420 dp de large. Sur les 448 dp d'un téléphone en
    // portrait il ne restait que 28 dp à la colonne de texte : titre, synopsis
    // et boutons étaient bien composés, mais écrasés à néant — d'où une page qui
    // semblait ne contenir qu'une image.
    val still = @Composable { modifier: Modifier ->
        Box(
            modifier = modifier
                .aspectRatio(16f / 9f)
                .clip(MoovieShape)
                .background(Color(0xFF222222)),
        ) {
            MoovieAsyncImage(
                model = ep.stillUrlLarge(),
                contentDescription = ep.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallback = fallbackArt,
            )
        }
    }
    val meta = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.details_episode_header, showName, season),
                style = MaterialTheme.typography.labelLarge,
                color = MOOVIE_ACCENT,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${ep.episodeNumber}. ${ep.name}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (isWatched) WatchedBadge()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                formatMediaDate(ep.airDate)?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = Color(0xFFCCCCCC))
                }
                formatDuration(ep.runtime)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFCCCCCC),
                    )
                }
                if (ep.voteAverage > 0) {
                    Text(
                        "★ %.1f".format(ep.voteAverage),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFE6B800),
                    )
                }
            }
            // Sur téléphone le synopsis passe après les boutons, comme sur la
            // fiche d'un film : sinon « Lire » se retrouve sous le résumé.
            if (!compact && ep.overview.isNotBlank()) {
                Text(ep.overview, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDDDDDD))
            }
        }
    }

    if (compact) {
        Column(
            modifier = hPad.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            still(Modifier.fillMaxWidth())
            meta()
        }
    } else {
        Row(
            modifier = hPad.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            still(Modifier.width(420.dp))
            Box(modifier = Modifier.weight(1f)) { meta() }
        }
    }

    Row(
        modifier = hPad,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoovieButton(onClick = onPlay, modifier = primaryModifier) {
            if (searching) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.details_playing))
            } else {
                Text(if (hasResume) stringResource(Res.string.details_resume) else stringResource(Res.string.details_play))
            }
        }
        MoovieButton(onClick = onOpenSources) { Text(stringResource(Res.string.details_sources)) }
        DownloadBestButton(downloadSearching, onDownloadBest)
        MoovieIconButton(
            onClick = onToggleWatched,
            icon = if (isWatched) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (isWatched) stringResource(Res.string.mark_unwatched) else stringResource(Res.string.mark_watched),
            selected = isWatched,
        )
    }
    if (compact && ep.overview.isNotBlank()) {
        Text(
            ep.overview,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFDDDDDD),
            modifier = hPad,
        )
    }
    if (infoVisible) {
        infoPanel()
    } else {
        // Sous les boutons, comme sur un film : la descente au D-pad atteint
        // d'abord Lire, pas une vignette d'acteur.
        CastRow(cast, hPadDp, onOpenPerson)
    }
}

@Composable
private fun EpisodeRow(
    ep: Episode,
    isWatched: Boolean,
    progress: Float?,
    onOpen: () -> Unit,
    onToggleWatched: () -> Unit,
    /** Le téléchargement de cet épisode, s'il existe. */
    download: Download? = null,
    /**
     * Visuel du titre, affiché barré à la place d'une vignette absente. Le fond
     * de la série plutôt que son affiche : la vignette est en 16:9, où une
     * affiche 2:3 rognée ne montre qu'une bande du milieu.
     */
    fallbackArt: Any? = null,
    /** Épisode à reprendre ou à suivre : barre accent, et cible du focus d'arrivée. */
    isNext: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // OK → fiche de l'épisode ; OK long → bascule vu/non vu.
    MoovieCard(
        onClick = onOpen,
        onLongClick = onToggleWatched,
        focusedScale = 1.02f,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isNext) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(90.dp)
                        .clip(MoovieShape)
                        .background(MOOVIE_ACCENT),
                )
            }
            Box(
                modifier = Modifier
                    .size(160.dp, 90.dp)
                    .clip(MoovieShape)
                    .background(Color(0xFF222222)),
            ) {
                MoovieAsyncImage(
                    model = ep.stillUrl(),
                    contentDescription = ep.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    fallback = fallbackArt,
                )
                if (isWatched) {
                    WatchedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
                // Pastille « sur le disque » : elle dit ce qu'aucune autre
                // marque ne dit — que cet épisode se regardera sans réseau.
                if (download?.state == DownloadState.DONE) {
                    // Sur fond opaque : une icône claire posée à même la
                    // vignette disparaît dès que l'image est claire, ce qui est
                    // le cas d'une scène de jour sur deux. Le disque sombre la
                    // rend lisible quelle que soit l'image dessous.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(Color(0xCC000000), CircleShape)
                            .padding(4.dp),
                    ) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = stringResource(Res.string.player_download_done),
                            tint = Color(0xFF7DDC7D),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                // En cours : barre accent en bas de la vignette. Elle occupe la
                // même bande que la progression de lecture, qui n'a pas de sens
                // sur un épisode qu'on n'a pas encore.
                if (download?.state == DownloadState.RUNNING ||
                    download?.state == DownloadState.QUEUED
                ) {
                    MoovieProgressBar(
                        progress = download.progress,
                        trackColor = Color(0x66000000),
                        modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                    )
                }
                // Épisode commencé : mini-barre de progression sur la vignette.
                if (!isWatched && progress != null && progress > 0f) {
                    MoovieProgressBar(
                        progress = progress,
                        trackColor = Color(0x66000000),
                        modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                MoovieMarqueeText(
                    text = "${ep.episodeNumber}. ${ep.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isWatched) Color(0xFF9A9A9A) else Color.White,
                )
                // Épisode à venir : sa date remplace le synopsis, qui est de
                // toute façon vide à ce stade. C'est ce que le cadre gris ne
                // savait pas dire.
                val upcoming = upcomingDate(ep.airDate)
                if (upcoming != null) {
                    Text(
                        stringResource(Res.string.details_episode_upcoming, upcoming),
                        style = MaterialTheme.typography.bodySmall,
                        color = MOOVIE_ACCENT,
                    )
                }
                if (upcoming == null && ep.overview.isNotBlank()) {
                    ScrollingSynopsis(ep.overview)
                }
            }
        }
    }
}

/**
 * Synopsis d'une carte d'épisode : borné à [lines] lignes au repos, déroulé en
 * boucle tant que la carte est focalisée. Sans ça la fin du résumé est
 * inatteignable à la télécommande (texte simplement tronqué).
 */
@Composable
private fun ScrollingSynopsis(
    text: String,
    lines: Int = 2,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    /**
     * Null = suit le focus de la carte qui contient le texte, ce qui est le cas
     * d'usage d'origine. Forcé à true dans l'en-tête d'une série, où il n'y a
     * aucune carte à focaliser et où un résumé tronqué resterait inatteignable.
     */
    active: Boolean? = null,
) {
    val scrolling = active ?: LocalMoovieCardActive.current
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val height = remember(style, lines, density) {
        val lineSp = if (style.lineHeight.isSp) style.lineHeight.value else style.fontSize.value * 1.4f
        with(density) { (lineSp * lines).sp.toDp() }
    }

    LaunchedEffect(scrolling, text) {
        if (!scrolling) {
            scroll.scrollTo(0)
            return@LaunchedEffect
        }
        delay(900)
        while (true) {
            if (scroll.maxValue > 0) {
                // Vitesse constante : la durée suit la hauteur à parcourir.
                scroll.animateScrollTo(
                    scroll.maxValue,
                    tween(durationMillis = (scroll.maxValue * 14).coerceIn(1200, 9000), easing = LinearEasing),
                )
                delay(1500)
                scroll.animateScrollTo(0, tween(400))
                delay(1500)
            } else {
                delay(600)
            }
        }
    }

    Box(modifier = modifier.height(height)) {
        Text(text, style = style, modifier = Modifier.verticalScroll(scroll, enabled = false))
    }
}

/**
 * Une personne du casting : portrait, nom, rôle.
 *
 * Cliquable dès qu'elle a un identifiant TMDB — c'est ce qui rend la rangée
 * traversable au D-pad, là où elle n'était qu'un décor. Sans identifiant, la
 * carte garde **exactement les mêmes dimensions** mais ne réagit pas : une
 * rangée dont les vignettes changent de taille selon qu'on peut les ouvrir
 * serait plus déroutante que le manque lui-même.
 *
 * Le portrait occupe toute la largeur de la carte, et les textes ont la même
 * marge de tous les côtés. Un premier jet gardait un portrait de 80 dp au
 * milieu d'une carte de 96 : les 8 dp de fond de part et d'autre passaient
 * inaperçus tant qu'il n'y avait ni fond ni bordure, et sautaient aux yeux dès
 * que la carte en a eu — avec un texte qui, lui, touchait les bords.
 */
@Composable
private fun CastCard(member: CastMember, onClick: () -> Unit) {
    val body = @Composable {
        Column {
            MoovieAsyncImage(
                model = member.profileUrl(),
                contentDescription = member.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    // Carré : un portrait TMDB est en 2:3, le rogner au carré
                    // cadre sur le visage plutôt que sur le buste.
                    .aspectRatio(1f)
                    .background(Color(0xFF222222)),
            )
            // Hauteurs **réservées**, pas subies : un nom sur deux lignes
                // (« Tramell Tillman ») rendait sa carte plus haute que ses
                // voisines et décalait la ligne du rôle d'une vignette à
                // l'autre. Deux lignes pour le nom, une pour le rôle — même
                // vide — et la rangée s'aligne.
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    member.name,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    member.character,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF9A9A9A),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }

    // Sans identifiant, TMDB ne saura pas ouvrir sa filmographie : la carte
    // reste inerte plutôt que de prendre le focus pour ne rien faire.
    if (member.id <= 0) {
        Box(
            modifier = Modifier
                .width(CAST_CARD_WIDTH)
                .clip(MoovieShape)
                .background(Color(0xFF141414)),
        ) { body() }
        return
    }
    // Agrandissement discret : la rangée est faite de vignettes, le zoom des
    // affiches y serait disproportionné.
    MoovieCard(
        onClick = onClick,
        focusedScale = 1.06f,
        modifier = Modifier.width(CAST_CARD_WIDTH),
    ) { body() }
}

/**
 * Rangée du casting.
 *
 * @param hPad marge horizontale, **imposée par la page** et non figée ici. Elle
 *   valait 48 dp en dur : sur un téléphone, dont le reste du contenu est à
 *   16 dp, la rangée partait donc trente-deux points plus loin que tout ce qui
 *   la surplombe. La marge d'une rangée dépend de la page qui l'accueille, pas
 *   de la rangée.
 *
 *   Elle va dans le `contentPadding` de la LazyRow et non dans un `padding`
 *   externe : c'est ce qui laisse les cartes agrandies au focus déborder dans la
 *   marge au lieu d'être rognées par le conteneur.
 */
@Composable
private fun CastRow(cast: List<CastMember>, hPad: Dp, onOpenPerson: (CastMember) -> Unit) {
    val members = cast.take(15)
    if (members.isEmpty()) return
    Column {
        Text(stringResource(Res.string.details_cast), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = hPad))
        Spacer(Modifier.height(8.dp))
        val castState = rememberLazyListState()
        MoovieRail(castState) {
        LazyRow(
            state = castState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = hPad),
        ) {
            items(members) { member ->
                CastCard(member = member, onClick = { onOpenPerson(member) })
            }
        }
        }
    }
}
