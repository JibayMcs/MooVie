package fr.moovie.tv.ui.details

import fr.moovie.tv.shared.formaterDecimal
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.download.readyInSeason
import fr.moovie.tv.data.download.DownloadState
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.font.FontWeight
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
import fr.moovie.tv.data.tmdb.TmdbItem
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
import fr.moovie.tv.resources.details_sources
import fr.moovie.tv.resources.details_sources_absent
import fr.moovie.tv.resources.details_send_to_tv
import fr.moovie.tv.resources.details_sources_none_enabled
import fr.moovie.tv.resources.details_sources_partial_absent
import fr.moovie.tv.resources.details_sources_partial_failed
import fr.moovie.tv.resources.details_sources_unreachable
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
import fr.moovie.tv.resources.player_fullscreen
import fr.moovie.tv.resources.player_pause
import fr.moovie.tv.resources.player_play
import fr.moovie.tv.resources.trailer_mute
import fr.moovie.tv.resources.trailer_unmute
import fr.moovie.tv.resources.details_download_season_partial
import fr.moovie.tv.resources.details_download_season_queued
import fr.moovie.tv.resources.details_download_season
import fr.moovie.tv.resources.details_download_season_progress
import fr.moovie.tv.ui.format.upcomingDate
import fr.moovie.tv.resources.details_episode_upcoming
import fr.moovie.tv.resources.details_source_dead
import fr.moovie.tv.resources.details_source_via
import fr.moovie.tv.resources.details_source_measuring
import fr.moovie.tv.resources.details_source_quality_unknown
import fr.moovie.tv.data.sources.hosterLabel
import fr.moovie.tv.core.sources.usecase.orderedLinksFor
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
import fr.moovie.tv.ui.theme.MOOVIE_RATING
import fr.moovie.tv.ui.theme.MOOVIE_READY
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
import fr.moovie.tv.ui.player.MooviePlayerController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.theme.MOOVIE_ERROR
import fr.moovie.tv.ui.theme.MOOVIE_SCRIM
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE_HIGH
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_MUTED
import fr.moovie.tv.ui.theme.MOOVIE_WARN

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
/**
 * L'espacement entre les blocs de la page.
 *
 * Nommé plutôt que répété : le défilement vers un onglet s'en sert pour savoir
 * où la barre commence, et deux valeurs indépendantes finiraient par diverger —
 * la page se calerait alors seize points à côté, ce qui se voit.
 */
private val ESPACEMENT_PAGE = 16.dp

/**
 * De combien on grossit la bande-annonce pour sortir ses barres du cadre.
 *
 * 2,39 ÷ 2,07 : le rapport entre le format d'un film et celui du hero. En
 * dessous, les barres d'un 2,39:1 restent visibles ; au-dessus, on ampute pour
 * rien les bandes-annonces qui n'en ont pas.
 */
private const val ZOOM_APERCU = 1.16f

private const val HERO_PREVIEW_DELAY_MS = 3_000L

/**
 * Rythme auquel on demande au lecteur où il en est, pour savoir s'il a fini.
 *
 * Une demi-seconde : deux lectures par seconde suffisent à rendre l'image du
 * hero sans qu'on voie l'attente, et c'est déjà moins que le sondage des
 * contrôles du plein écran, qui affichent une position au dixième près.
 */
private const val FIN_APERCU_POLL_MS = 500L

/**
 * De combien on peut manquer la fin sans la manquer.
 *
 * Un lecteur ne s'arrête presque jamais pile sur la dernière milliseconde
 * annoncée : il reste un reliquat de quelques dixièmes, et exiger l'égalité
 * ferait attendre le butoir de la durée annoncée alors que la vidéo est
 * visiblement terminée.
 */
private const val FIN_APERCU_MARGE_MS = 800L

/** Fondu d'apparition de l'aperçu : il remplace une affiche, il ne surgit pas. */
private const val HERO_PREVIEW_FADE_MS = 800

/** L'interface s'efface doucement — c'est un fondu, pas une disparition. */
private const val UI_FADE_MS = 600

/**
 * Inactivité au bout de laquelle la chrome de la bande-annonce se replie.
 *
 * Quatre secondes, comme la barre du lecteur de films : c'est la même
 * situation — une vidéo qu'on regarde, des contrôles dont on n'a besoin qu'au
 * moment où l'on y pense — et deux durées différentes pour un même geste se
 * remarqueraient sans qu'on sache dire pourquoi.
 */
private const val TRAILER_CHROME_IDLE_MS = 4_000L

/**
 * Le son monte plus lentement que l'image ne s'efface, et redescend d'autant.
 * Un son qui apparaît d'un coup s'entend comme un défaut ; un fondu s'entend
 * comme une intention.
 */
private const val TRAILER_SOUND_FADE_MS = 1_200

/**
 * Temps laissé au bandeau « langue indisponible » avant de s'effacer.
 *
 * Deux mots à lire — « VF indisponible » —, sur un écran qu'on regarde de loin
 * et sans s'attendre à devoir lire. Quatre secondes couvrent le temps de
 * remarquer qu'il est apparu, plus celui de le lire.
 */
private const val QUICKPLAY_BANNER_MS = 4_000L

/**
 * La même chose lorsque le bandeau porte **aussi** le motif.
 *
 * « 6 catalogues n'ont pas ce titre, 2 sont injoignables » fait une dizaine de
 * mots, soit à peu près quatre secondes de plus au rythme d'un spectateur qui ne
 * lisait pas — d'où le doublement plutôt qu'un arrondi choisi au jugé. Le
 * bandeau ne bloque rien et se pose en bas de l'écran : le laisser trop
 * longtemps ne coûte qu'un peu d'encombrement, le retirer trop tôt coûte
 * l'information elle-même, qui est tout l'objet de cette ligne.
 */
private const val QUICKPLAY_BANNER_WITH_REASON_MS = 8_000L

/**
 * Le triangle de lecture, dessiné.
 *
 * Il était un caractère — « ▶ » collé devant le libellé, dans la ressource de
 * traduction. Ça paraît économique et ça ne l'est pas : le glyphe vient de la
 * police du système, il change de dessin et d'alignement d'un appareil à
 * l'autre, il ne s'aligne pas sur la ligne de base du texte qu'il précède, et
 * il traverse les trois fichiers de traduction où personne ne s'attend à
 * trouver un pictogramme. Une icône se dessine, se teinte et se mesure.
 */
@Composable
private fun RowScope.IconeLecture() {
    Icon(
        Icons.Default.PlayArrow,
        contentDescription = null,
        // Alignée sur la hauteur d'x du libellé plutôt que sur sa taille
        // nominale : à taille égale, un pictogramme plein pèse plus qu'une
        // lettre et paraîtrait deux fois trop gros.
        modifier = Modifier.size(20.dp),
    )
    Spacer(Modifier.width(8.dp))
}

/**
 * La note TMDB : une étoile et un nombre.
 *
 * L'étoile était elle aussi un caractère, et le même dessin variait d'un écran
 * à l'autre — pleine ici, creuse là, décalée sous la ligne ailleurs.
 */
@Composable
private fun Note(valeur: Double) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = MOOVIE_RATING,
            modifier = Modifier.size(15.dp),
        )
        Text(
            formaterDecimal(valeur, 1),
            style = MaterialTheme.typography.titleSmall,
            color = MOOVIE_RATING,
        )
    }
}

/**
 * Largeur d'une vignette du casting.
 *
 * Une seule constante pour la carte *et* le portrait qu'elle contient : c'est ce
 * qui garantit qu'aucun liseré de fond ne réapparaisse entre les deux. Deux
 * valeurs, et l'écart se rejoue au premier changement.
 */
private val CAST_CARD_WIDTH = 96.dp

/**
 * Dispose la fiche d'une série **au doigt** : en-tête et épisodes dans un seul
 * défilement.
 *
 * ## Pourquoi l'en-tête et les épisodes arrivent séparément
 *
 * La page ne défilait pas, seule la liste le faisait, si bien que le titre, le
 * résumé, la rangée des saisons et les actions occupaient à demeure la moitié
 * haute d'un écran de téléphone — il restait une fenêtre de deux épisodes pour
 * parcourir la saison, et un geste sur l'en-tête ne faisait rien du tout. Ce
 * n'est pas ainsi qu'une page se lit sur un téléphone : elle défile en entier.
 *
 * D'où ces deux paramètres plutôt qu'un `content` unique. Pour que l'en-tête
 * défile *avec* les épisodes, il doit être un élément de leur liste — et un
 * élément de liste paresseuse ne peut être posé que depuis un `LazyListScope`,
 * que le point d'appel n'a pas. C'est aussi ce qui garde la paresse : on aurait
 * pu rendre la page défilante et poser les épisodes dans une `Column`
 * ordinaire, au prix de composer les vingt-cinq d'un coup, images comprises.
 *
 * ## Ce qui a disparu
 *
 * Il y avait ici un second cas, deux volets côte à côte pour le grand écran,
 * avec un en-tête posé hors du défilement. La refonte du hero l'a remplacé : la
 * fiche série défile désormais en entier comme celle d'un film, et la liste
 * d'épisodes vit sous un onglet. Le paramètre `compact` avec lui — cette
 * fonction n'est plus appelée que pour le tactile, et un booléen dont une seule
 * valeur est atteignable ne décrit plus rien.
 */
@Composable
private fun SeriesPanes(
    episodesState: LazyListState,
    modifier: Modifier = Modifier,
    /** Titre, résumé de saison, rangée des saisons et actions. */
    header: @Composable (Modifier) -> Unit,
    /** « En savoir plus », qui prend la place des épisodes, ou null s'il est fermé. */
    infoPanel: (@Composable (Modifier) -> Unit)?,
    /** Les épisodes, posés en éléments de liste paresseuse. */
    episodes: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = episodesState,
        modifier = modifier,
        // Les marges de la page passent par le `contentPadding` de la liste,
        // qui les porte maintenant pour tout le monde — en-tête compris.
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { header(Modifier.fillMaxWidth()) }
        if (infoPanel != null) {
            item { infoPanel(Modifier.fillMaxWidth()) }
        } else {
            episodes()
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
    /**
     * Les titres proches, pour l'onglet « À voir aussi ». Vide = pas d'onglet :
     * voir [DetailsTabs].
     */
    recommendations: List<TmdbItem> = emptyList(),
    /** Ouvre une autre fiche depuis « À voir aussi ». */
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit = { _, _ -> },
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
    /** Hauteurs mesurées par URL : elles ordonnent le panneau des sources. */
    sourceHeights: Map<String, Int> = emptyMap(),
    /**
     * Envoyer ce titre au téléviseur appairé, ou null s'il n'y en a pas à
     * portée.
     *
     * Nullable plutôt qu'un booléen à côté : un bouton qui n'existe pas ne peut
     * pas être appuyé par erreur, et la condition « une TV répond **maintenant** »
     * ([fr.moovie.tv.data.remote.RemotePresence]) est déjà tranchée par
     * l'appelant. L'écran partagé n'a pas à connaître le réseau local.
     */
    onSendToTv: (() -> Unit)? = null,
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
    /** Réglage utilisateur : l'aperçu du hero démarre-t-il avec le son. */
    trailerSound: Boolean = false,
    /**
     * Pays de l'utilisateur (`FR`), pour choisir la bonne classification d'âge
     * dans le panneau « En savoir plus ».
     */
    country: String = "FR",
    onDismissQuickPlay: () -> Unit,
    onBack: () -> Unit,
    /**
     * Referme la fiche d'un épisode et revient à la liste de la série.
     *
     * Séparé de [onBack], qui quitte la fiche entière : ce sont deux retours
     * différents, et les confondre faisait sortir de la série pour aller à
     * l'accueil. Voir le bouton retour plus bas.
     */
    onCloseEpisode: () -> Unit = {},
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

    /**
     * Écart entre le rang d'un épisode dans la saison et son index dans la liste
     * paresseuse.
     *
     * Le titre « Épisodes (saison N) » occupe toujours le premier élément. Au
     * doigt, l'en-tête de la série en occupe un de plus : il défile avec les
     * épisodes au lieu de rester posé au-dessus — voir [SeriesPanes].
     */
    val episodeItemOffset = if (compact) 2 else 1

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
    //
    // **Sur le titre, pas sur l'état.** `state` change aussi quand on choisit
    // une autre saison — et reprendre le focus à ce moment-là ramenait la page
    // tout en haut (`primaryModifier` remonte à la prise de focus), c'est-à-dire
    // qu'on quittait la liste qu'on venait d'ouvrir. Le geste dit exactement le
    // contraire : je reste ici, je change de saison. La clé du titre, elle, ne
    // bouge pas d'une saison à l'autre.
    LaunchedEffect(state.titleKey(), selectedEpisode) {
        val tv = state as? DetailsState.Tv
        // Sur grand écran, le hero porte maintenant un bouton « Reprendre ·
        // S2E4 » : viser l'épisode dans la liste ferait passer le focus
        // par-dessus le hero à l'ouverture, pour arriver au même geste un cran
        // plus loin. Au doigt, où il n'y a pas de hero, la visée reste utile.
        val wantsEpisode = compact && tv != null && tv.resumeEpisode > 0 &&
            selectedEpisode == null && !autoFocusDone
        if (wantsEpisode) {
            // Le décalage compte les éléments posés avant les épisodes : voir
            // [episodeItemOffset].
            val index = tv.episodes.indexOfFirst { it.episodeNumber == tv.resumeEpisode }
            if (index >= 0) runCatching { episodesState.scrollToItem(index + episodeItemOffset) }
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

    // Le lecteur du fond, prêté par la plateforme. C'est *lui* que les contrôles
    // pilotent : il n'y a pas de second lecteur pour la bande-annonce.
    var trailerController by remember(ready?.video?.key) {
        mutableStateOf<MooviePlayerController?>(null)
    }
    // Le réglage donne l'état de départ, l'utilisateur garde la main ensuite.
    var trailerMuted by remember(trailerExpanded) { mutableStateOf(!trailerSound) }

    // Le son de l'aperçu **dans le hero**. Le réglage donne l'état de départ ;
    // le bouton du cadre garde la main ensuite.
    //
    // Muet par défaut, et c'est le bon défaut : la page vient de s'ouvrir,
    // personne n'a rien demandé, et une vidéo qui parle toute seule dans un
    // salon est une nuisance.
    var apercuMuet by remember(ready?.video?.key) { mutableStateOf(!trailerSound) }

    // L'activité du pointeur passe par un flux et **non par un état Compose** :
    // une souris en émet des dizaines par seconde, et un `mutableStateOf`
    // incrémenté à chacun recomposerait toute la fiche pendant qu'on la
    // traverse. Ici rien ne recompose tant que la chrome ne change pas.
    val pointerActivity = remember {
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    // Chrome de la bande-annonce : visible à l'ouverture, repliée sans activité,
    // rappelée par n'importe quel geste. Même contrat que le lecteur de films —
    // c'est la même situation, une vidéo qu'on regarde et des contrôles qui
    // n'ont aucune raison de rester en travers.
    var trailerChromeVisible by remember(trailerExpanded) { mutableStateOf(true) }

    // Compteur d'activité, et non un instant : le relancer relance l'effet, ce
    // qui redémarre le décompte sans horloge à comparer.
    var trailerWake by remember(trailerExpanded) { mutableStateOf(0) }

    LaunchedEffect(trailerExpanded, trailerWake) {
        if (!trailerExpanded) return@LaunchedEffect
        trailerChromeVisible = true
        // En pause, la chrome reste — comme au lecteur, où l'on veut voir où
        // l'on en est. L'état est relu à l'échéance plutôt que sondé en
        // continu : une lecture toutes les quatre secondes, contre dix par
        // seconde pour un sondage, et personne ne voit la différence.
        while (true) {
            delay(TRAILER_CHROME_IDLE_MS)
            if (trailerController?.isPlaying != false) {
                trailerChromeVisible = false
                return@LaunchedEffect
            }
        }
    }

    // La souris réveille la chrome : sur desktop c'est le seul geste disponible
    // sans cliquer, et cliquer pour faire réapparaître une barre qu'on veut
    // simplement consulter serait une pause non demandée.
    LaunchedEffect(trailerExpanded) {
        if (!trailerExpanded) return@LaunchedEffect
        while (true) {
            pointerActivity.first()
            trailerWake++
        }
    }

    // **Le mode cinéma n'existe plus.**
    //
    // La bande-annonce passait toute seule au premier plan après quelques
    // secondes sans souris : l'interface s'effaçait, le son montait, et la vidéo
    // prenait l'écran. C'était une réponse au fait qu'elle jouait *derrière* la
    // page, où on la voyait mal. Elle joue maintenant dans le cadre du hero,
    // avec ses commandes — dont « agrandir ». Il ne reste à un déclenchement
    // automatique que ses inconvénients : une page qui se dérobe pendant qu'on
    // la lit, et qu'il faut réveiller pour s'en servir.
    //
    // Ne reste donc que la demande explicite.
    val trailerInFront = trailerExpanded
    val soundWanted = when {
        trailerExpanded -> !trailerMuted
        // L'aperçu du hero. Hors du hero encadré — téléphone, fiche d'épisode —
        // aucun bouton ne touche à `apercuMuet`, et le réglage y décide seul.
        else -> !apercuMuet
    }

    val volume by animateFloatAsState(
        targetValue = if (soundWanted) 1f else 0f,
        animationSpec = tween(TRAILER_SOUND_FADE_MS),
        label = "trailerVolume",
    )
    val uiAlpha by animateFloatAsState(
        targetValue = if (trailerInFront) 0f else 1f,
        animationSpec = tween(UI_FADE_MS),
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
        // **Déjà en cours : on ne relance pas, mais on surveille quand même.**
        //
        // Cet effet se rejoue à chaque ouverture *et* fermeture des contrôles.
        // Remettre l'aperçu à zéro en sortant le faisait recharger le même
        // manifeste dans un second lecteur, ce que googlevideo sanctionne d'un
        // 403 — le défaut qui avait déjà valu un plantage. On saute donc le
        // démarrage, mais pas la veille de fin qui suit : revenir du plein écran
        // laissait sinon l'aperçu tourner jusqu'à la dernière trame, sans jamais
        // rendre son image au hero.
        if (!previewPlaying) {
            delay(HERO_PREVIEW_DELAY_MS)
            previewPlaying = true
        }

        // ── Ce qui se passe à la fin de la bande-annonce ─────────────────────
        //
        // **On rend le cadre à l'image**, plutôt que de laisser la dernière
        // trame figée — ou, sur mpv, un cadre vidé donc noir, ce qui est pire :
        // le hero paraît cassé alors qu'il ne fait qu'avoir fini.
        //
        // Et quand il n'y a pas d'image à rendre, on **reboucle** : un cadre
        // noir vaut moins que la même bande-annonce une seconde fois. Le
        // rembobinage passe par le lecteur déjà ouvert (`seekTo`) et non par un
        // second, qui rouvrirait le même manifeste googlevideo — 403 garanti,
        // c'est le défaut qui avait valu un plantage.
        //
        // La fin se **constate** au lieu de se calculer. `durationSeconds` vient
        // de YouTube et décrit la vidéo, pas ce que le flux nous laisse lire :
        // googlevideo bride certains manifestes, et la lecture s'arrête bien
        // avant l'échéance annoncée. On surveille donc la position du lecteur,
        // en gardant la durée annoncée comme butoir — sans elle, un flux bridé
        // ne finirait jamais.
        val butoirMs = ready.durationSeconds.takeIf { it > 0 }?.times(1000L)
        while (true) {
            var joueDepuisMs = 0L
            while (true) {
                delay(FIN_APERCU_POLL_MS)
                val lecteur = trailerController
                val position = lecteur?.positionMs() ?: 0L
                val duree = lecteur?.durationMs() ?: 0L
                if (duree > 0 && position >= duree - FIN_APERCU_MARGE_MS) break
                // **Le butoir ne compte que le temps joué.** En pause,
                // l'utilisateur a demandé que rien ne bouge — l'échéance
                // comprise. Sans cette garde, une bande-annonce mise en pause
                // au bouton du cadre rendait sa place à l'image toute seule,
                // au bout de sa durée annoncée.
                if (lecteur?.isPlaying != false) joueDepuisMs += FIN_APERCU_POLL_MS
                if (butoirMs != null && joueDepuisMs >= butoirMs) break
            }
            if (backdrop != null) {
                previewPlaying = false
                return@LaunchedEffect
            }
            trailerController?.seekTo(0)
        }
    }

    // `BoxWithConstraints` et non `Box` : le hero se dimensionne en fraction
    // de la hauteur visible, et cette hauteur n'existe plus une fois entré dans
    // le défilement vertical, où l'espace disponible est infini par nature.
    // Vrai quand la page s'ouvre sur une image à fond perdu : elle décide du
    // fond, de la marge haute et de la hauteur du hero.
    //
    // La fiche d'un **épisode** en est exclue : elle a son propre en-tête
    // (`EpisodeHero`), qui montre l'image de l'épisode et non celle de la
    // série, et qui n'a jamais été le sujet de cette refonte.
    val heroPleinCadre = !compact && when (state) {
        is DetailsState.Movie -> true
        is DetailsState.Tv -> selectedEpisode == null
        else -> false
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().then(
            // Observé seulement quand la bande-annonce est au premier plan :
            // c'est ce flux qui réveille sa chrome, y compris au doigt — un
            // écran tactile n'a pas de survol, mais toucher est bien une
            // activité.
            if (!trailerExpanded) {
                Modifier
            } else {
                // Passe **Initial** : on observe le pointeur sans lui prendre
                // ses événements. Un bouton survolé doit continuer de réagir
                // pendant que l'interface se rallume.
                Modifier.pointerInput(trailerExpanded) {
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
        // Le fond flouté habille la page **quand rien d'autre ne le fait**. Sous
        // un hero net qui porte déjà l'image en grand, il la répétait en flou
        // derrière le reste de la page : deux fois la même chose, dont une
        // illisible. La maquette s'arrête au noir sous le hero, et elle a raison.
        if (!heroPleinCadre) {
            backdrop?.let { url ->
                MoovieAsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(40.dp),
                )
            }
        }
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
        // **Le hero commence au pixel zéro, tout le reste non.**
        //
        // Une marge haute existe pour que le titre ne passe pas sous le bouton
        // retour en overlay. Le hero, lui, doit toucher le bord : c'est une
        // image à fond perdu, et la repousser de 96 dp laissait une bande noire
        // au-dessus qui la transformait en bannière posée dans la page. Le
        // bouton retour passe par-dessus, ce qu'il sait déjà faire, et le
        // dégradé du hero le porte sans qu'il devienne illisible.
        // **Toute la hauteur visible, moins la barre d'onglets.**
        //
        // C'est la mesure de la maquette, et elle vaut mieux qu'un pourcentage :
        // l'image va jusqu'en bas de l'écran, et la barre d'onglets se pose
        // dessous, juste assez visible pour dire que la page continue. Un
        // pourcentage laissait soit une bande morte sous le hero, soit rien du
        // tout selon la fenêtre ; ici le raccord est exact quelle que soit la
        // taille.
        //
        // Le plancher tient le cas d'une fenêtre écrasée en hauteur, où le titre
        // et les boutons ne tiendraient plus — on préfère alors déborder et
        // laisser défiler.
        val hauteurHero = (maxHeight - hauteurOnglets() - amorceSousOnglets())
            .coerceAtLeast(300.dp)
        // **La marge du hero n'est pas celle de la page.**
        //
        // 48 dp collaient le titre et le synopsis contre le bord gauche, tout
        // le contenu tassé sur le premier tiers d'une image qui en fait trois.
        // La maquette respire : elle rentre son bloc d'environ un dixième de la
        // largeur de chaque côté, ce qui fait de la place au texte au lieu de
        // l'empiler. Le plancher garde la marge de page sur une fenêtre
        // étroite, où un dixième ne serait plus rien.
        val margeHero = (maxWidth * 0.09f).coerceAtLeast(hPadDp)
        // Ce qui suit le hero s'aligne sur lui : la barre d'onglets, les
        // rangées, les panneaux. Un contenu rentré de 48 dp sous un titre rentré
        // de 170 en aurait fait deux pages posées l'une sur l'autre.
        val hPadHero = Modifier.padding(horizontal = margeHero)
        val topPad = when {
            heroPleinCadre -> 0.dp
            showBackButton -> 96.dp
            else -> 48.dp
        }
        val pageScope = rememberCoroutineScope()
        // **La page ne porte pas son propre défilement quand un enfant le
        // porte déjà.** C'est le cas de la fiche série au doigt, dont
        // `SeriesPanes` est une `LazyColumn`.
        //
        // Sur grand écran, ce n'était pas une question de conteneur mais de
        // parti pris : l'en-tête restait posé et seuls les épisodes défilaient,
        // pour ne pas perdre de vue ce qu'on parcourait. Le hero plein cadre le
        // rend intenable — il ne resterait rien à la liste — et la maquette
        // tranche dans l'autre sens : la page défile en entier, hero compris.
        val seriesList = compact && state is DetailsState.Tv && selectedEpisode == null

        // **Un seul lecteur, et il ne bouge pas de l'arbre.**
        //
        // La bande-annonce doit tenir dans le cadre du hero tant qu'on parcourt
        // la fiche, et prendre l'écran entier dès qu'elle passe devant. Ce sont
        // deux places à l'écran, mais surtout deux places dans l'arbre si l'on
        // écrit l'appel là où il paraît : la seconde ferait naître un second
        // lecteur, qui rouvrirait le même manifeste googlevideo — et
        // googlevideo répond 403 à la seconde demande. Un `movableContentOf`
        // aurait dû résoudre exactement cela ; essayé, il n'apparie pas les
        // deux sites (l'un est un paramètre différé de `DetailsHero`) et
        // Compose plante sur un nœud qui a déjà un parent.
        //
        // Le lecteur reste donc **ici**, seul, et c'est son cadre qu'on
        // déplace. Le hero occupe exactement le haut de la page, sur toute la
        // largeur : sa boîte est connue sans avoir à la mesurer.
        //
        // Vrai quand la bande-annonce joue dans le cadre du hero : elle y prend
        // la place de l'image, et c'est le hero qui porte ses commandes.
        val apercuDansHero = heroPleinCadre && !trailerInFront &&
            previewPlaying && ready != null && trailerPreview != null
        if (previewPlaying && ready != null && trailerPreview != null) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(HERO_PREVIEW_FADE_MS)),
                modifier = if (apercuDansHero) {
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(hauteurHero)
                        // La page défile, le hero monte, la vidéo le suit.
                        // Lue dans la couche graphique et non dans la mesure :
                        // le défilement ne redéclenche alors qu'un dessin.
                        .graphicsLayer { translationY = -pageScroll.value.toFloat() }
                } else {
                    Modifier.fillMaxSize()
                },
            ) {
                // **Un peu plus que remplir : mordre.**
                //
                // Les deux lecteurs recadrent déjà pour couvrir le cadre, et ça
                // ne suffit pas. Une bande-annonce de cinéma est un 2,39:1
                // **encodé dans un 16:9** : les barres noires sont dans les
                // images, pas autour de la vidéo, et aucun mode de
                // redimensionnement ne les distingue du film. Couvrir un cadre
                // de 2,07:1 avec ce 16:9 en retire la moitié ; le reste
                // s'affiche en haut et en bas du hero, en bandes franches — ce
                // qu'on nous a remonté d'un salon comme des « séparations ».
                //
                // Ce grossissement les fait sortir du cadre. Il coûte 15 % de
                // débord à une bande-annonce réellement en 16:9, ce qui est le
                // bon échange : c'est un décor, pas un plan qu'on cadre.
                Box(
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                ) {
                    trailerPreview(
                        ready.stream,
                        volume,
                        { trailerController = it },
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = ZOOM_APERCU
                            scaleY = ZOOM_APERCU
                        },
                    )
                }
            }
        }

        // Le voile s'efface avec l'interface qu'il sert à rendre lisible : le
        // garder derrière la bande-annonce agrandie l'assombrirait pour rien.
        //
        // Il ne sert plus rien du tout sous un hero plein cadre : il existait
        // pour tempérer le fond flouté, qui n'y est plus, et sa couleur est au
        // demeurant celle du fond du thème — il n'assombrirait que du noir.
        // Le garder aurait en revanche voilé la bande-annonce, qui se joue
        // désormais dessous.
        if (!heroPleinCadre) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { alpha = uiAlpha }
                    .background(
                        Brush.verticalGradient(listOf(Color(0xAA0A0A0A), Color(0xE00A0A0A))),
                    ),
            )
        }

        // ── Onglets ─────────────────────────────────────────────────────────
        //
        // La barre ne s'affiche que sous un hero plein cadre : c'est la même
        // refonte, et elle ne descend ni au doigt ni sur la fiche d'un épisode.
        //
        // Retenu par valeur et non par index, remis à zéro au changement de
        // titre : d'une fiche à l'autre la barre n'a pas les mêmes onglets, et
        // « le troisième » n'y désigne pas la même chose.
        var ongletChoisi by remember(state.titleKey()) { mutableStateOf<DetailsTab?>(null) }
        val onglets = buildList {
            if (state is DetailsState.Tv && selectedEpisode == null) add(DetailsTab.EPISODES)
            if (recommendations.isNotEmpty()) add(DetailsTab.SIMILAIRES)
            if (ready != null) add(DetailsTab.BANDES_ANNONCES)
            add(DetailsTab.INFOS)
        }
        // L'onglet retenu peut avoir disparu — « À voir aussi » n'arrive
        // qu'après sa requête, « Bandes-annonces » après la résolution du flux.
        // On retombe alors sur le premier, jamais sur du vide.
        val ongletActif = ongletChoisi?.takeIf { it in onglets } ?: onglets.first()

        // **Choisir un onglet amène à son contenu.**
        //
        // La barre est posée au bas de l'image : au repos, ce qu'elle ouvre est
        // hors de l'écran. Changer d'onglet ne montrait donc rien du tout — il
        // fallait défiler ensuite, en devinant qu'il y avait quelque chose à
        // voir. La page vient se caler juste sous la barre, qui reste visible en
        // haut : on garde le moyen de changer d'avis.
        //
        // La cible se calcule, elle ne se mesure pas : le hero est le premier
        // élément de la colonne et la barre lui est collée. Un
        // `onGloballyPositioned` aurait donné le même nombre au prix d'un état
        // de plus et d'une image de retard.
        val densite = LocalDensity.current
        val hautDesOnglets = with(densite) { hauteurHero.roundToPx() }
        // Défini une fois pour les deux fiches : c'est la même barre, au même
        // endroit, et la dupliquer aurait fini par la faire diverger.
        val barreOnglets: @Composable () -> Unit = {
            // Remontée de l'espacement de la page : la barre doit **toucher**
            // le bas de l'image, comme la maquette. Les seize points de noir
            // que la colonne glissait entre les deux ajoutaient une troisième
            // bande sombre à un bas d'écran qui en comptait déjà deux — et
            // c'est cet empilement qu'on lit comme des blocs séparés.
            BandeauOnglets(modifier = Modifier.offset(y = -ESPACEMENT_PAGE)) {
                DetailsTabs(
                    onglets = onglets,
                    actif = ongletActif,
                    onSelect = {
                        ongletChoisi = it
                        pageScope.launch { pageScroll.animateScrollTo(hautDesOnglets) }
                    },
                    modifier = hPadHero,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
                // Effacée, pas démontée : la page garde sa position de
                // défilement et son focus, et revient telle qu'on l'a laissée
                // au premier mouvement de souris.
                .graphicsLayer { alpha = uiAlpha }
                .then(if (seriesList) Modifier else Modifier.verticalScroll(pageScroll))
                .padding(top = topPad, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(ESPACEMENT_PAGE),
        ) {
            when (val s = state) {
                DetailsState.Loading -> SkeletonDetails(modifier = hPad)
                is DetailsState.Error -> {
                    Text(s.message, modifier = hPad)
                    MoovieButton(onClick = onBack, modifier = hPad) { Text(stringResource(Res.string.common_back)) }
                }
                is DetailsState.Movie -> {
                    val movieWatched = movieKey in watched
                    // Bouton Lire direct : loader pendant le chargement des sources,
                    // cliquable dès qu'un lien dans la langue préférée existe,
                    // « VF indisponible » sinon. Le panneau reste en choix manuel.
                    val active = sources as? SourcesState.Active
                    val prefReady = active?.links?.any { it.language == streamLang.name } == true
                    val loadingSources = active == null || active.anyLoading
                    val searching = quickPlay is QuickPlayState.Searching
                    // Les actions deviennent un **emplacement** : sur grand écran elles se
                    // posent dans le hero, sous le titre, et non plus dans le flux de la
                    // page. Les hisser ici plutôt que de les dupliquer garde une seule
                    // définition des boutons pour les deux mises en page — leur état de
                    // sources est déjà bien assez subtil pour n'exister qu'à un endroit.
                    val boutonLire: @Composable () -> Unit = {
                            MoovieButton(
                                // Jamais inerte, et c'est le point. Pendant le
                                // chargement, la lecture démarre dès qu'une source
                                // arrive. Et **quand il n'y en a aucune**, l'appui
                                // reste le seul moyen d'obtenir le motif : la lecture
                                // rapide conclut aussitôt et le publie dans son
                                // bandeau. La garde d'avant en faisait un cul-de-sac
                                // silencieux — on appuyait sur l'action principale de
                                // la fiche, focalisée à l'arrivée sur TV, et il ne se
                                // passait rien du tout.
                                //
                                // Le bouton de l'épisode (EpisodeHero) n'a jamais eu
                                // cette garde : c'est pour ça que le bandeau se voyait
                                // sur un épisode et jamais sur un film. Deux boutons
                                // pour le même geste, un seul savait échouer.
                                //
                                // Appuyer deux fois ne relance rien : `startQuickPlay`
                                // sort si son job tourne encore.
                                onClick = onQuickPlayMovie,
                                // Le fond plein vient du modificateur, posé
                                // avant que `MoovieButton` ne dessine sa propre
                                // surface : `selected` seul ne donne qu'un
                                // liseré, ce qui distingue mal l'action
                                // principale de ses voisines. C'est le rose de
                                // l'identité, pas une couleur inventée ici.
                                modifier = primaryModifier.then(
                                    if (compact) {
                                        Modifier
                                    } else {
                                        // Toute la colonne de gauche, comme le
                                        // « S'ABONNER » de la maquette : c'est
                                        // cette largeur — pas seulement sa
                                        // couleur — qui le désigne comme
                                        // l'action de la page. Un bouton qui se
                                        // contente d'entourer « Lire » reste un
                                        // bouton parmi d'autres.
                                        //
                                        // Borné quand même : sur une fenêtre
                                        // très large, la colonne dépasse le
                                        // demi-millier de points et un bouton
                                        // de cette longueur ne se lit plus
                                        // comme un bouton.
                                        Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = LARGEUR_MAX_BOUTON_PRINCIPAL)
                                            .clip(MoovieShape)
                                            .background(MOOVIE_ACCENT)
                                    },
                                ),
                                // **L'action principale doit se voir comme
                                // telle.** Au repos, un MoovieButton n'est que
                                // son libellé : posé au milieu de quatre autres,
                                // « Lire » ne se distinguait de « Sources » que
                                // par le mot. `selected` lui donne la surface
                                // pleine du thème, et la marge le fait peser —
                                // c'est le gros bouton de la maquette, obtenu
                                // avec le bouton du projet plutôt qu'avec un
                                // second modèle à entretenir.
                                selected = !compact,
                                contentPadding = if (compact) {
                                    PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                } else {
                                    PaddingValues(horizontal = 24.dp, vertical = 20.dp)
                                },
                            ) {
                                val libelle: @Composable RowScope.() -> Unit = {
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
                                        prefReady -> {
                                            IconeLecture()
                                            Text(
                                                (
                                                    if (resume.containsKey(movieKey)) {
                                                        stringResource(Res.string.details_resume)
                                                    } else {
                                                        stringResource(Res.string.details_play)
                                                    }
                                                    ).let { if (compact) it else it.uppercase() },
                                            )
                                        }
                                        loadingSources -> {
                                            CircularProgressIndicator(
                                                color = MOOVIE_ACCENT,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(Res.string.details_searching, streamLang.name))
                                        }
                                        else -> Text(stringResource(Res.string.details_lang_unavailable, streamLang.name), color = MOOVIE_TEXT_DIM)
                                    }
                                }
                                if (compact) libelle() else LibellePrincipal(libelle)
                            }
                    }

                    // Tout ce qui n'est pas « Lire » : les gestes qu'on fait sur
                    // un titre une fois qu'on a décidé de ne pas le lancer tout
                    // de suite.
                    val actionsSecondaires: @Composable () -> Unit = {
                            MoovieButton(onClick = onOpenPanel) { Text(stringResource(Res.string.details_sources)) }
                            // Icône seule : la rangée est déjà pleine, et un
                            // téléviseur se reconnaît sans légende. N'apparaît que
                            // si une TV a répondu — voir onSendToTv.
                            onSendToTv?.let { send ->
                                MoovieIconButton(
                                    onClick = send,
                                    icon = Icons.Default.Cast,
                                    contentDescription = stringResource(Res.string.details_send_to_tv),
                                )
                            }
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

                    // **Deux lignes sur grand écran, une seule au doigt.**
                    //
                    // Aligné avec ses voisines, « Lire » n'était que le premier
                    // bouton d'une rangée de six : la couleur le distinguait, la
                    // position non. Seul sur sa ligne, il devient ce qu'il est —
                    // l'action de la page — et les gestes secondaires forment un
                    // groupe en dessous, qu'on lit comme tel. C'est le « S'ABONNER »
                    // de la maquette, et c'est aussi une meilleure descente au
                    // D-pad : du bouton principal on tombe sur les autres, au lieu
                    // de les traverser latéralement pour sortir de la fiche.
                    //
                    // Au doigt la largeur ne le permet pas : deux lignes de boutons
                    // sur 448 dp repousseraient le synopsis hors de l'écran, et la
                    // rangée unique y tient déjà.
                    val actionsFilm: @Composable () -> Unit = {
                        if (compact) {
                            Row(
                                modifier = hPad,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                boutonLire()
                                actionsSecondaires()
                            }
                        } else {
                            // Pas de marge : le hero porte déjà celle de la page,
                            // la remettre ici indenterait les boutons deux fois.
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                boutonLire()
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    actionsSecondaires()
                                }
                            }
                        }
                    }

                    if (compact) {
                        // Même mise en page que la fiche d'épisode — visuel à gauche,
                        // métadonnées et synopsis à droite — pour que les deux fiches
                        // du catalogue se ressemblent au lieu de diverger.
                        MovieHeader(
                            details = s.details,
                            isWatched = movieWatched,
                            showOverview = false,
                        )
                        actionsFilm()
                    } else {
                        DetailsHero(
                            backdropUrl = s.details.backdropUrl(),
                            afficheUrl = s.details.posterUrl(),
                            titre = s.details.title,
                            meta = metaFilm(s.details),
                            synopsis = s.details.overview,
                            credits = creditsDe(s.details.credits, s.details.countries.map { it.name }),
                            hauteur = hauteurHero,
                            marge = margeHero,
                            actions = actionsFilm,
                            // La bande-annonce joue derrière, dans ce cadre
                            // exact : l'image la masquerait.
                            imageMasquee = apercuDansHero,
                            controles = if (apercuDansHero) {
                                {
                                    ApercuControles(
                                        controller = trailerController,
                                        muet = apercuMuet,
                                        onCoupeLeSon = { apercuMuet = !apercuMuet },
                                        onAgrandir = onPlayTrailer,
                                        onRedescend = {
                                            runCatching { primaryFocus.requestFocus() }.isSuccess
                                        },
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                    // Synopsis après les boutons sur téléphone : le glisser avant
                    // reléguait « Lire » sous dix-sept lignes de résumé, donc hors
                    // écran, pour un film qu'on venait pourtant de choisir.
                    if (compact && s.details.overview.isNotBlank()) {
                        Text(
                            s.details.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MOOVIE_TEXT_MUTED,
                            modifier = hPad,
                        )
                    }
                    if (compact) {
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
                    } else {
                        barreOnglets()
                        when (ongletActif) {
                            // Un film n'a pas d'épisodes : l'onglet n'est pas
                            // dans la barre, et la branche n'est là que pour
                            // que le `when` reste exhaustif.
                            DetailsTab.EPISODES -> Unit
                            DetailsTab.SIMILAIRES ->
                                SimilarRow(recommendations, margeHero, onOpenTitle)
                            DetailsTab.BANDES_ANNONCES ->
                                ready?.let { TrailerTab(it, onPlayTrailer, hPadHero) }
                            // Le panneau technique **et** le casting : « en
                            // savoir plus » sur un film, c'est les deux. Le
                            // casting n'a plus de place à demeure sous le hero,
                            // qui occupe l'écran entier ; il rejoint donc ce
                            // qu'il complète plutôt que de flotter seul.
                            DetailsTab.INFOS -> {
                                MovieInfoPanel(
                                    details = s.details,
                                    country = country,
                                    modifier = hPadHero.fillMaxWidth(),
                                    // La page du film défile déjà en bloc.
                                    scrollable = false,
                                )
                                CastRow(s.details.credits?.cast.orEmpty(), margeHero, onOpenPerson)
                            }
                        }
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
                            onSendToTv = onSendToTv,
                            primaryModifier = primaryModifier,
                            onPlay = { onQuickPlayEpisode(selected.season, ep.episodeNumber) },
                            onOpenSources = { onOpenEpisodePanel(selected.season, ep.episodeNumber) },
                            onToggleWatched = { onToggleWatched(key) },
                            cast = s.details.credits?.cast.orEmpty(),
                            onOpenPerson = onOpenPerson,
                            fallbackArt = s.details.backdropUrl() ?: s.details.posterUrl(),
                        )
                    } else {
                        val seasonAllWatched = s.episodes.isNotEmpty() &&
                            s.episodes.all { episodeKey(s.season, it.episodeNumber) in watched }
                        // Saison annoncée mais pas commencée : sa date de
                        // première diffusion vaut mieux que son année nue.
                        val seasonUpcoming = upcomingDate(s.seasonAirDate)

                        // L'épisode que le bouton principal du hero lancera :
                        // celui qu'on reprend, ou le premier de la saison. Il
                        // n'y en a jamais eu sur une fiche de série — il fallait
                        // descendre dans la liste — alors que c'est le geste le
                        // plus fréquent qu'on y fait.
                        val episodeAReprendre = s.episodes.firstOrNull {
                            it.episodeNumber == s.resumeEpisode
                        } ?: s.episodes.firstOrNull { it.episodeNumber > 0 }

                        // ── Blocs partagés par les deux mises en page ────────
                        //
                        // Écrits une fois et posés à deux endroits : au doigt
                        // dans l'en-tête de `SeriesPanes`, sur grand écran sous
                        // l'onglet « Épisodes ». Ce sont les mêmes commandes, et
                        // les dupliquer les aurait laissées diverger.
                        val selecteurSaisons: @Composable () -> Unit = {
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
                                        MOOVIE_WARN
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
                                        //
                                        // Sur grand écran, le point d'entrée est
                                        // désormais le bouton « Lire » du hero :
                                        // `primaryModifier` porte un
                                        // `FocusRequester` unique, qui ne peut pas
                                        // être posé sur deux nœuds à la fois.
                                        modifier = if (isCurrent && compact) {
                                            primaryModifier
                                        } else {
                                            Modifier
                                        },
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
                                                // ne compte plus, la coche suffit.
                                                if (ready > 0 && total > 0 && !complete) {
                                                    append("  $ready/$total")
                                                }
                                            },
                                            color = if (complete) MOOVIE_READY else Color.Unspecified,
                                        )
                                        if (complete) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MOOVIE_READY,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                        }

                        // Actions de titre sur leur propre ligne, et non en fin
                        // de rangée des saisons : sur une série de vingt-deux
                        // saisons elles se retrouvaient à vingt-deux boutons du
                        // bord, donc introuvables. Ici elles sont toujours au
                        // même endroit, à un appui vers le bas.
                        val actionsSerie: @Composable () -> Unit = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                            // Le téléviseur du salon, comme sur la fiche film —
                            // et **seulement sur grand écran** : la rangée du
                            // téléphone ne l'a jamais eu, et cette refonte ne
                            // descend pas au doigt.
                            if (!compact) onSendToTv?.let { send ->
                                MoovieIconButton(
                                    onClick = send,
                                    icon = Icons.Default.Cast,
                                    contentDescription = stringResource(Res.string.details_send_to_tv),
                                )
                            }
                        }
                        }

                        // Une ligne d'épisode, la même dans les deux mises en
                        // page. Seul le recentrage diffère : au doigt la liste
                        // est paresseuse et sait se caler par index, sur grand
                        // écran la page défile en bloc et `bringIntoView` s'en
                        // charge tout seul.
                        val ligneEpisode: @Composable (Int, Episode) -> Unit = { index, ep ->
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
                                    if (it.isFocused && compact) pageScope.launch {
                                        delay(80)
                                        episodesState.animateScrollToItem(index + episodeItemOffset)
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

                        if (compact) {
                        // Deux volets plutôt qu'un empilement : l'écran fait
                        // 960 × 540 dp, et un synopsis qui occupe toute la
                        // largeur pour trois lignes prend à la liste la hauteur
                        // de trois épisodes. Côte à côte, la description garde
                        // sa place et la liste récupère toute la colonne.
                        SeriesPanes(
                            episodesState = episodesState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            header = { headerModifier ->
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
                        if (seasonUpcoming != null) {
                            Text(
                                stringResource(Res.string.details_episode_upcoming, seasonUpcoming),
                                color = MOOVIE_ACCENT,
                            )
                        } else (s.seasonYear ?: s.details.year)?.let { Text(it) }
                        ScrollingSynopsis(
                            text = s.seasonOverview.ifBlank { s.details.overview },
                            // Empilé sur téléphone, il mange la liste
                            // d'épisodes — on le resserre.
                            lines = 3,
                            style = MaterialTheme.typography.bodyMedium,
                            // Déroulé en continu : dans l'en-tête il n'y a pas
                            // de carte à focaliser pour déclencher la lecture,
                            // et un résumé tronqué net serait inatteignable.
                            active = true,
                        )
                        // Bloc de commande resserré : titre, saisons et actions
                        // se suivent de près pour rendre à la liste la hauteur
                        // de deux épisodes. L'espacement de 16 dp de la colonne
                        // parente, appliqué entre chacun, la lui prenait.
                        selecteurSaisons()
                        actionsSerie()
                        }
                            },
                        // « En savoir plus » prend la place de la liste des
                        // épisodes. C'est le cas qui a dicté la conception — on
                        // consulte la date du prochain épisode, puis on veut ses
                        // épisodes, sans avoir à défiler.
                            infoPanel = if (infoVisible) {
                                { modifierPanneau ->
                                    TvInfoPanel(
                                        details = s.details,
                                        country = country,
                                        // Au doigt il est un élément d'une liste
                                        // qui porte déjà le défilement de la page :
                                        // lui en donner un second l'imbriquerait.
                                        scrollable = false,
                                        modifier = modifierPanneau,
                                    )
                                }
                            } else {
                                null
                            },
                            episodes = {
                        item {
                            Text(stringResource(Res.string.details_episodes_season, s.season), style = MaterialTheme.typography.titleMedium)
                        }
                        itemsIndexed(s.episodes) { index, ep -> ligneEpisode(index, ep) }
                        // Casting **dans** le défilement, en queue de liste.
                        // Posé sous les volets, c'était un bloc fixe d'environ
                        // 190 dp pris à une liste qui n'a déjà que ce qui reste
                        // sous l'en-tête : il restait une fenêtre d'un épisode
                        // et demi pour parcourir la saison.
                        //
                        // hPad nul : la marge de 16 dp vient déjà du
                        // contentPadding de la liste. La cumuler décalerait la
                        // rangée par rapport aux épisodes qu'elle suit.
                        item {
                            CastRow(s.details.credits?.cast.orEmpty(), 0.dp, onOpenPerson)
                        }
                            },
                        )
                        } else {
                            // ── La fiche série, comme la fiche film ─────────
                            //
                            // Même hero, même barre d'onglets, et la liste des
                            // épisodes sous le premier d'entre eux. Les deux
                            // fiches du catalogue se ressemblent enfin, ce que
                            // ni l'ancien en-tête ni les deux volets ne
                            // permettaient.
                            DetailsHero(
                                backdropUrl = s.details.backdropUrl(),
                                afficheUrl = s.details.posterUrl(),
                                titre = s.details.name,
                                meta = metaSerie(s.details),
                                // Le résumé **de la saison** quand TMDB le
                                // donne : c'est celle qu'on est venu voir.
                                synopsis = s.seasonOverview.ifBlank { s.details.overview },
                                credits = creditsSerie(s.details.credits, s.details.createdBy.map { it.name }),
                                hauteur = hauteurHero,
                                marge = margeHero,
                                actions = {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        // Une saison annoncée n'a rien à lire :
                                        // le bouton disparaît plutôt que de
                                        // rester inerte, comme celui du
                                        // téléchargement de saison juste à côté.
                                        if (episodeAReprendre != null && seasonUpcoming == null) {
                                            BoutonLireEpisode(
                                                season = s.season,
                                                episode = episodeAReprendre,
                                                searching = quickPlay is QuickPlayState.Searching,
                                                aReprendre = resume.containsKey(
                                                    episodeKey(s.season, episodeAReprendre.episodeNumber),
                                                ),
                                                modifier = primaryModifier,
                                                onClick = {
                                                    onQuickPlayEpisode(
                                                        s.season,
                                                        episodeAReprendre.episodeNumber,
                                                    )
                                                },
                                            )
                                        }
                                        actionsSerie()
                                    }
                                },
                                // La bande-annonce joue derrière, dans ce cadre
                                // exact : l'image la masquerait.
                                imageMasquee = apercuDansHero,
                                controles = if (apercuDansHero) {
                                    {
                                        ApercuControles(
                                            controller = trailerController,
                                            muet = apercuMuet,
                                            onCoupeLeSon = { apercuMuet = !apercuMuet },
                                            onAgrandir = onPlayTrailer,
                                            onRedescend = {
                                                runCatching { primaryFocus.requestFocus() }.isSuccess
                                            },
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                            barreOnglets()
                            when (ongletActif) {
                                DetailsTab.EPISODES -> Column(
                                    modifier = hPadHero.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    selecteurSaisons()
                                    if (seasonUpcoming != null) {
                                        Text(
                                            stringResource(
                                                Res.string.details_episode_upcoming,
                                                seasonUpcoming,
                                            ),
                                            color = MOOVIE_ACCENT,
                                        )
                                    }
                                    Text(
                                        stringResource(Res.string.details_episodes_season, s.season),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    // Composés tous à la fois, et non
                                    // paresseusement : la page entière défile
                                    // désormais, et une liste paresseuse à
                                    // hauteur infinie dans un défilement
                                    // vertical n'est pas mesurable. Une saison
                                    // en compte vingt-cinq au pire — c'est le
                                    // prix du défilement continu qu'exige la
                                    // maquette, et il se paie une fois.
                                    s.episodes.forEachIndexed { index, ep ->
                                        ligneEpisode(index, ep)
                                    }
                                }
                                DetailsTab.SIMILAIRES ->
                                    SimilarRow(recommendations, margeHero, onOpenTitle)
                                DetailsTab.BANDES_ANNONCES ->
                                    ready?.let { TrailerTab(it, onPlayTrailer, hPadHero) }
                                DetailsTab.INFOS -> {
                                    TvInfoPanel(
                                        details = s.details,
                                        country = country,
                                        // La page défile déjà en bloc.
                                        scrollable = false,
                                        modifier = hPadHero.fillMaxWidth(),
                                    )
                                    CastRow(s.details.credits?.cast.orEmpty(), margeHero, onOpenPerson)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Réveil à la télécommande, pendant que la chrome est repliée.
        //
        // Une couche focalisable qui avale la première touche : sans elle, le
        // D-pad continuerait d'atteindre les boutons de la chrome invisible —
        // on relancerait la lecture ou on fermerait la bande-annonce en
        // croyant simplement rallumer les contrôles. Même dispositif que le
        // lecteur, pour la même raison.
        val trailerWakeFocus = remember { FocusRequester() }
        if (trailerExpanded && previewPlaying && !trailerChromeVisible) {
            LaunchedEffect(Unit) { runCatching { trailerWakeFocus.requestFocus() } }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(trailerWakeFocus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        // Le retour n'est pas une activité : il ferme, et
                        // l'intercepter piégerait l'utilisateur devant une
                        // vidéo dont il ne saurait plus sortir.
                        if (event.key == Key.Back) return@onPreviewKeyEvent false
                        trailerWake++
                        true
                    },
            )
        }

        // Contrôles de la bande-annonce, tout en haut de la pile : ils se posent
        // sur l'aperçu **et** sur l'interface effacée, qui reste composée
        // dessous pour garder sa position de défilement et son focus.
        AnimatedVisibility(
            visible = trailerExpanded && previewPlaying && trailerChromeVisible,
            enter = fadeIn(animationSpec = tween(UI_FADE_MS)),
            exit = fadeOut(animationSpec = tween(UI_FADE_MS)),
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
        //
        // **Au doigt seulement, désormais.** Sur grand écran, ces deux-là sont
        // devenus des onglets — « Bandes-annonces » et « En savoir plus » — et
        // les garder ici en aurait fait deux chemins vers le même contenu, dont
        // celui-ci se serait de surcroît superposé aux commandes de la
        // bande-annonce, qui occupent le même coin du hero.
        if (compact && !trailerExpanded && !panelVisible && state !is DetailsState.Loading) {
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
        //
        // **Il recule d'un cran, il ne quitte pas.** Sur la fiche d'un épisode,
        // il appelait `onBack`, qui dépile la navigation : on sautait la fiche
        // de la série pour se retrouver à l'accueil. Et quand la fiche *est* la
        // racine de la pile — c'est le cas du crochet de développement — il n'y
        // avait rien à dépiler, donc il ne se passait rien du tout.
        //
        // Échap (desktop) et la touche Retour (Android) faisaient déjà cette
        // cascade chacun de leur côté ; le bouton, lui, ne l'avait jamais
        // apprise. Le panneau des sources et la bande-annonce n'y figurent pas
        // parce qu'ils masquent ce bouton — la condition juste au-dessus.
        if (showBackButton && !panelVisible && !trailerExpanded) {
            MoovieIconButton(
                onClick = { if (selectedEpisode != null) onCloseEpisode() else onBack() },
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
                    heights = sourceHeights,
                    statuses = sourceStatuses,
                    downloads = downloads,
                    onRequestQuality = onRequestQuality,
                )
            }
        }

        // Bannière de lecture rapide (recherche en cours / indisponible),
        // surtout utile pour les épisodes qui n'ont pas de bouton dédié.
        val q = quickPlay
        // Calculé une fois : le motif décide de ce qu'on affiche *et* du temps
        // qu'on laisse pour le lire. Deux calculs séparés finiraient par diverger,
        // et le bandeau se retirerait au milieu d'une phrase.
        val unavailableReason = (q as? QuickPlayState.Unavailable)
            ?.let { sources as? SourcesState.Active }
            ?.let { emptySourcesReason(it) }
        if (q is QuickPlayState.Unavailable) {
            // Le motif peut arriver après le bandeau — un catalogue encore en
            // vol au moment du verdict. La clé l'inclut donc, sinon la durée
            // resterait celle d'une seule ligne.
            LaunchedEffect(q, unavailableReason != null) {
                delay(
                    if (unavailableReason != null) {
                        QUICKPLAY_BANNER_WITH_REASON_MS
                    } else {
                        QUICKPLAY_BANNER_MS
                    },
                )
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
                    // Ce bandeau est souvent le *seul* écran que l'utilisateur
                    // voit : le panneau ne s'ouvre de lui-même que s'il y a des
                    // liens, et ici il n'y en a aucun. Le motif doit donc être
                    // ici, pas seulement dans le panneau.
                    is QuickPlayState.Unavailable -> Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            stringResource(Res.string.details_lang_unavailable, q.lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MOOVIE_ERROR,
                        )
                        unavailableReason?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
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
    /**
     * Hauteur mesurée par URL. C'est elle qui **ordonne** le panneau, pas le
     * libellé : « 1080p » et « 720p » ne se trient pas comme des chaînes.
     */
    heights: Map<String, Int>,
    /** Demande la mesure d'un lien ; sans effet si elle est déjà connue. */
    onRequestQuality: (EmbedLink) -> Unit,
) {
    val links = state.links
    // Le panneau montre **exactement** l'ordre que la cascade suivra : c'est la
    // même fonction qui décide des deux. Auparavant la liste gardait l'ordre des
    // catalogues pendant que la lecture rapide suivait les définitions, si bien
    // que ce qui se lançait n'était pas ce qui figurait en tête — le classement
    // paraissait arbitraire parce qu'il y en avait deux.
    val grouped = links.groupBy { it.language ?: "?" }
        .mapValues { (lang, _) -> orderedLinksFor(links, preferred = lang, heights = heights) }
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
                trackColor = MOOVIE_SURFACE_HIGH,
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
            Text(it, style = MaterialTheme.typography.labelMedium, color = MOOVIE_ERROR, modifier = pPad)
        }
        if (prefMissing) {
            Text(
                stringResource(Res.string.details_lang_missing, preferred.name),
                style = MaterialTheme.typography.labelMedium,
                color = MOOVIE_ERROR,
                modifier = pPad,
            )
        }
        Spacer(Modifier.height(4.dp))

        when {
            links.isEmpty() && state.anyLoading -> SkeletonRows(modifier = pPad)
            links.isEmpty() -> Column(modifier = pPad, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.details_no_sources),
                    color = MOOVIE_ERROR,
                )
                // Le « pourquoi », qui distingue un titre absent d'une panne
                // réseau : sans lui les deux se lisent comme une app cassée.
                emptySourcesReason(state)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
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
                        // `merge` est une méthode par défaut de Java 8, absente du
                        // commun. Deux lignes disent la même chose : le rang est le
                        // nombre de fois qu'on a vu ce couple, celle-ci comprise.
                        val rank = (seen[id] ?: 0) + 1
                        seen[id] = rank
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
                    append(hosterLabel(link))
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
            // La définition est **toujours** écrite, même quand on ne la connaît
            // pas encore. Une colonne où une ligne sur deux porte « 1080p » et
            // l'autre le nom d'un catalogue ne se compare pas : on lit deux
            // informations différentes à la même place, et le classement paraît
            // arbitraire alors qu'il ne l'est pas. Dire « mesure… » puis
            // « définition inconnue » coûte deux mots et rend la colonne lisible
            // de haut en bas.
            val qualityText = quality ?: when (status) {
                LinkStatus.UNKNOWN, LinkStatus.CHECKING ->
                    stringResource(Res.string.details_source_measuring)
                else -> stringResource(Res.string.details_source_quality_unknown)
            }
            val secondary = downloadLine ?: if (dead) {
                stringResource(Res.string.details_source_dead)
            } else {
                // Le catalogue reste, après la définition : il départage deux
                // lignes identiques, et c'est lui qu'on regarde quand une source
                // déçoit régulièrement.
                buildString {
                    append(qualityText)
                    link.provider?.let {
                        append(" · ")
                        append(stringResource(Res.string.details_source_via, it))
                    }
                }
            }
            secondary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        download?.state == DownloadState.DONE -> MOOVIE_READY
                        download?.state == DownloadState.FAILED -> MOOVIE_ERROR
                        downloadLine != null -> MOOVIE_ACCENT
                        dead -> MOOVIE_ERROR
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
                color = MOOVIE_TEXT_MUTED,
            )
        }
        if (sourceCount > 0) {
            Text(
                pluralStringResource(Res.plurals.details_source_count, sourceCount, sourceCount) +
                    " · " +
                    pluralStringResource(Res.plurals.details_catalogue_count, withResults, withResults),
                style = MaterialTheme.typography.labelMedium,
                color = MOOVIE_TEXT_MUTED,
            )
        }
        if (failed.isNotEmpty()) {
            Text(
                failed.joinToString(", ") { it.name },
                style = MaterialTheme.typography.labelMedium,
                color = MOOVIE_ERROR,
            )
        }
    }
}

/**
 * Met en mots [diagnoseEmptySources] — ou null tant qu'on cherche encore.
 *
 * Toute l'information vient des statuts déjà collectés : c'est une mise en forme,
 * pas une seconde enquête.
 */
@Composable
private fun emptySourcesReason(state: SourcesState.Active): String? {
    val diagnosis = diagnoseEmptySources(state) ?: return null
    val failed = state.providers.count { it.status == ProviderStatus.FAILED }
    val answered = state.providers.size - failed
    return when (diagnosis) {
        SourceDiagnosis.NONE_ENABLED ->
            stringResource(Res.string.details_sources_none_enabled)

        SourceDiagnosis.UNREACHABLE -> pluralStringResource(
            Res.plurals.details_sources_unreachable,
            state.providers.size,
            state.providers.size,
        )

        SourceDiagnosis.ABSENT -> pluralStringResource(
            Res.plurals.details_sources_absent,
            answered,
            answered,
        )

        // Les deux moitiés sont comptées séparément : « 1 catalogue n'a pas ce
        // titre, 2 sont injoignables » ne se plie pas à un seul pluriel.
        SourceDiagnosis.PARTIAL ->
            pluralStringResource(Res.plurals.details_sources_partial_absent, answered, answered) +
                ", " +
                pluralStringResource(Res.plurals.details_sources_partial_failed, failed, failed)
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
            .background(MOOVIE_SCRIM),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MOOVIE_READY,
            modifier = Modifier.size(14.dp),
        )
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

/**
 * Les trois commandes de la bande-annonce **du cadre du hero**.
 *
 * Agrandir, couper le son, mettre en pause : rien d'autre, et c'est délibéré.
 * L'aperçu n'est pas une séance — il illustre le titre. Une barre de position,
 * un titre de vidéo ou un bouton de fermeture appartiennent à la vue agrandie,
 * qu'[onAgrandir] ouvre d'un appui et où [TrailerControls] les donne déjà.
 *
 * Elles ne se replient pas. Celles du plein écran s'effacent parce qu'elles
 * masqueraient l'image pendant qu'on regarde ; ici elles occupent un coin d'une
 * page qu'on est en train de parcourir, et une commande qui apparaît et
 * disparaît au bord du regard est plus gênante qu'une commande visible.
 *
 * @param onRedescend rend le focus au corps de la fiche. Ces boutons sont seuls
 *   en haut à droite : sous eux, le faisceau vertical de la recherche de focus
 *   ne rencontre rien — le contenu est à gauche — et le D-pad y resterait
 *   coincé. Même câblage explicite que la rangée qu'ils remplacent.
 */
@Composable
private fun ApercuControles(
    controller: MooviePlayerController?,
    muet: Boolean,
    onCoupeLeSon: () -> Unit,
    onAgrandir: () -> Unit,
    onRedescend: () -> Boolean,
) {
    // Sondé, comme dans les contrôles du plein écran : aucun de nos moteurs ne
    // pousse son état de lecture. Trois fois par seconde suffit à ce qu'un
    // appui sur pause change l'icône sans qu'on le remarque.
    var joue by remember(controller) { mutableStateOf(true) }
    LaunchedEffect(controller) {
        while (true) {
            controller?.let { joue = it.isPlaying }
            delay(APERCU_POLL_MS)
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown &&
                event.key == Key.DirectionDown &&
                onRedescend()
        },
    ) {
        MoovieIconButton(
            onClick = onAgrandir,
            icon = Icons.Default.Fullscreen,
            contentDescription = stringResource(Res.string.player_fullscreen),
        )
        MoovieIconButton(
            onClick = onCoupeLeSon,
            icon = if (muet) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = stringResource(
                if (muet) Res.string.trailer_unmute else Res.string.trailer_mute,
            ),
            selected = !muet,
        )
        MoovieIconButton(
            onClick = { controller?.togglePause() },
            icon = if (joue) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(
                if (joue) Res.string.player_pause else Res.string.player_play,
            ),
        )
    }
}

private const val APERCU_POLL_MS = 300L

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
                .background(MOOVIE_SURFACE_HIGH),
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
                    Text(it, style = MaterialTheme.typography.titleSmall, color = MOOVIE_TEXT_MUTED)
                }
                formatDuration(details.runtime)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall,
                        color = MOOVIE_TEXT_MUTED,
                    )
                }
                if (details.voteAverage > 0) {
                    Note(details.voteAverage)
                }
            }
            if (showOverview && details.overview.isNotBlank()) {
                Text(
                    details.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MOOVIE_TEXT_MUTED,
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
    /** Envoyer *cet épisode* au téléviseur, ou null s'il n'y en a pas à portée. */
    onSendToTv: (() -> Unit)? = null,
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
                .background(MOOVIE_SURFACE_HIGH),
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
                    Text(it, style = MaterialTheme.typography.titleSmall, color = MOOVIE_TEXT_MUTED)
                }
                formatDuration(ep.runtime)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall,
                        color = MOOVIE_TEXT_MUTED,
                    )
                }
                if (ep.voteAverage > 0) {
                    Note(ep.voteAverage)
                }
            }
            // Sur téléphone le synopsis passe après les boutons, comme sur la
            // fiche d'un film : sinon « Lire » se retrouve sous le résumé.
            if (!compact && ep.overview.isNotBlank()) {
                Text(ep.overview, style = MaterialTheme.typography.bodyMedium, color = MOOVIE_TEXT_MUTED)
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
                IconeLecture()
                Text(if (hasResume) stringResource(Res.string.details_resume) else stringResource(Res.string.details_play))
            }
        }
        MoovieButton(onClick = onOpenSources) { Text(stringResource(Res.string.details_sources)) }
        // Même bouton que sur une fiche de film, et à la même place : c'est
        // depuis un épisode qu'on veut le plus souvent continuer sur la TV.
        onSendToTv?.let { send ->
            MoovieIconButton(
                onClick = send,
                icon = Icons.Default.Cast,
                contentDescription = stringResource(Res.string.details_send_to_tv),
            )
        }
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
            color = MOOVIE_TEXT_MUTED,
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
                    .background(MOOVIE_SURFACE_HIGH),
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
                            tint = MOOVIE_READY,
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
                    color = if (isWatched) MOOVIE_TEXT_DIM else Color.White,
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
                    .background(MOOVIE_SURFACE_HIGH),
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
                    color = MOOVIE_TEXT_DIM,
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
                .background(MOOVIE_SURFACE),
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
 * L'habillage du bouton principal, sur grand écran : centré, gras, en capitales.
 *
 * Un `MoovieButton` aligne son contenu à gauche, ce qui convient à un bouton
 * dont la largeur épouse le libellé. Le bouton principal, lui, a une largeur
 * **imposée** — c'est ce qui le désigne comme l'action de la page — et son
 * libellé s'y retrouvait collé au bord gauche, avec un grand vide à droite : on
 * lisait un bouton mal rempli plutôt qu'un bouton large. Le `weight` prend toute
 * la place disponible et recentre.
 *
 * Le gras et les capitales viennent de la maquette, où le bouton porte le seul
 * texte criard de la page. C'est cohérent : tout le reste du hero est du texte
 * qu'on lit, celui-ci est un texte sur lequel on appuie.
 *
 * Rien de tout cela au doigt, où le bouton est un bouton parmi d'autres dans
 * une rangée — voir l'appel, qui garde l'ancien contenu tel quel.
 */
@Composable
private fun RowScope.LibellePrincipal(contenu: @Composable RowScope.() -> Unit) {
    ProvideTextStyle(
        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = contenu,
        )
    }
}

/**
 * Le bouton principal de la fiche série : « Reprendre · S2E4 ».
 *
 * ## Pourquoi il n'existait pas
 *
 * La fiche série n'avait aucune action de lecture : on descendait dans la liste
 * jusqu'à l'épisode voulu, et c'est lui qu'on ouvrait. Cela se tenait quand
 * l'écran montrait la liste d'emblée. Sous un hero plein cadre, la liste est à
 * un défilement de là, et le geste le plus fréquent d'une fiche de série —
 * reprendre où l'on en était — n'avait plus de bouton.
 *
 * ## Le numéro dans le libellé
 *
 * « Reprendre » seul demanderait de faire confiance : reprendre *quoi* ?
 * L'épisode visé est déduit de l'historique, et une déduction qu'on ne montre
 * pas est une déduction qu'on ne peut pas corriger. Affiché, il se vérifie d'un
 * coup d'œil, et la liste reste là pour choisir autrement.
 *
 * `S2E4` n'est pas traduit : c'est une notation, la même dans toutes les langues
 * où le projet est distribué, et c'est aussi celle des clés de l'historique.
 */
@Composable
private fun BoutonLireEpisode(
    season: Int,
    episode: Episode,
    searching: Boolean,
    aReprendre: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    MoovieButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = LARGEUR_MAX_BOUTON_PRINCIPAL)
            .clip(MoovieShape)
            .background(MOOVIE_ACCENT),
        selected = true,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    ) {
        LibellePrincipal {
            if (searching) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.details_playing))
            } else {
                IconeLecture()
                Text(
                    buildString {
                        append(
                            if (aReprendre) {
                                stringResource(Res.string.details_resume)
                            } else {
                                stringResource(Res.string.details_play)
                            }.uppercase(),
                        )
                        append("  ·  S")
                        append(season)
                        append('E')
                        append(episode.episodeNumber)
                    },
                )
            }
        }
    }
}

/**
 * L'onglet « Bandes-annonces » : une vignette, celle qu'on sait jouable.
 *
 * ## Pourquoi une seule
 *
 * TMDB en déclare souvent une dizaine — teasers, featurettes, extraits — mais
 * *déclarer* n'est pas *jouer* : une clé YouTube retirée, restreinte par région
 * ou refusée par le client du jour ne se distingue d'une bonne qu'en essayant.
 * La fiche n'en résout qu'une, la meilleure, et c'est la seule dont on sache
 * qu'un appui la lancera (voir [TrailerState]). Un mur de vignettes dont une sur
 * trois s'excuse serait un plus mauvais onglet qu'une vignette qui tient parole.
 *
 * ## Pourquoi elle est grande
 *
 * Il n'y a rien à comparer ni à choisir : la vignette n'est pas un élément de
 * liste, c'est la porte de l'onglet. Aux dimensions d'une affiche de catalogue,
 * elle aurait l'air d'un premier élément dont les suivants manquent.
 */
@Composable
private fun TrailerTab(ready: TrailerState.Ready, onPlay: () -> Unit, hPad: Modifier) {
    Column(modifier = hPad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MoovieCard(onClick = onPlay, modifier = Modifier.width(TRAILER_CARD_WIDTH)) {
            Column {
                Box {
                    MoovieAsyncImage(
                        // La vignette de YouTube plutôt qu'une image de TMDB :
                        // c'est celle de la vidéo qu'on va lancer, pas une autre
                        // du même film. `hqdefault` existe pour toute vidéo, y
                        // compris celles qui n'ont pas de version haute
                        // définition — `maxresdefault` rend 404 sur celles-là.
                        model = "https://img.youtube.com/vi/${ready.video.key}/hqdefault.jpg",
                        contentDescription = ready.video.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(MOOVIE_SURFACE_HIGH),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MOOVIE_SCRIM),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Text(
                    ready.video.name.ifBlank { stringResource(Res.string.details_trailer) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

/**
 * Au-delà, le bouton principal cesse de se lire comme un bouton.
 *
 * Il occupe la colonne de gauche du hero, qui suit la largeur de la fenêtre :
 * sans borne, une fenêtre de 2 500 points lui en donnait plus de sept cents,
 * soit une barre horizontale portant trois mots au milieu.
 */
private val LARGEUR_MAX_BOUTON_PRINCIPAL = 460.dp

/** Largeur de la vignette de bande-annonce : un 16:9 qu'on voit de loin. */
private val TRAILER_CARD_WIDTH = 360.dp

/**
 * Rangée « À voir aussi » : les titres proches, en affiches.
 *
 * Construite comme la rangée du casting juste dessous, et pour les mêmes
 * raisons — marge dans le `contentPadding` pour que le focus déborde au lieu
 * d'être rogné, `MoovieRail` pour la barre de défilement du projet. Une
 * troisième mise en page de rangée n'aurait rien apporté qu'un écart de plus.
 *
 * Quinze titres au plus. TMDB en renvoie jusqu'à vingt, et au-delà d'une
 * quinzaine on ne parcourt plus une suggestion mais un catalogue — ce que
 * l'accueil fait déjà, mieux.
 */
@Composable
private fun SimilarRow(
    items: List<TmdbItem>,
    hPad: Dp,
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
) {
    val proches = items.take(15)
    if (proches.isEmpty()) return
    val etat = rememberLazyListState()
    MoovieRail(etat) {
        LazyRow(
            state = etat,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = hPad),
        ) {
            items(proches) { item ->
                // `mediaType` vient de TMDB et manque sur certaines entrées ;
                // on retombe alors sur le type de la fiche d'où l'on part, qui
                // est le bon dans l'immense majorité des cas — les
                // recommandations d'une série sont des séries.
                val estSerie = item.mediaType == "tv"
                MoovieCard(
                    onClick = { onOpenTitle(item.id, estSerie) },
                    modifier = Modifier.width(SIMILAR_CARD_WIDTH),
                ) {
                    Column {
                        MoovieAsyncImage(
                            model = item.posterUrl(),
                            contentDescription = item.displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .background(MOOVIE_SURFACE_HIGH),
                        )
                        Text(
                            item.displayTitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Largeur d'une affiche d'« À voir aussi » — celle du catalogue. */
private val SIMILAR_CARD_WIDTH = 140.dp

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
