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
import androidx.compose.material.icons.filled.BookmarkBorder
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.core.sources.model.EmbedLink
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
import fr.moovie.tv.resources.details_sources
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
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieMarqueeText
import fr.moovie.tv.ui.components.MoovieProgressBar
import fr.moovie.tv.ui.components.MoovieRail
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
 * Fiche film/série partagée TV + desktop : état hoisté (le ViewModel reste côté
 * plateforme — chargement TMDB, résolution de sources, suivi de lecture).
 */
@Composable
fun DetailsScreenContent(
    state: DetailsState,
    sources: SourcesState,
    resolveError: String?,
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
    /** Qualité vidéo mesurée par URL d'embed (voir DetailsViewModel.qualities). */
    sourceQualities: Map<String, String> = emptyMap(),
    onRequestQuality: (EmbedLink) -> Unit = {},
    onDismissQuickPlay: () -> Unit,
    onBack: () -> Unit,
    // Desktop uniquement : bouton retour à l'écran (sur TV, la télécommande a
    // sa propre touche Retour, pas besoin d'un bouton).
    showBackButton: Boolean = false,
) {
    val primaryFocus = remember { FocusRequester() }
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
            runCatching { primaryFocus.requestFocus() }
        }
    }

    val backdrop = (state as? DetailsState.Movie)?.details?.backdropUrl()
        ?: (state as? DetailsState.Tv)?.details?.backdropUrl()

    Box(modifier = Modifier.fillMaxSize()) {
        backdrop?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xAA0A0A0A), Color(0xE00A0A0A))),
            ),
        )

        // Scroll pleine largeur + marges portées par les enfants : les éléments
        // agrandis au focus débordent dans la marge au lieu d'être rognés.
        val hPad = Modifier.padding(horizontal = 48.dp)
        // Marge haute agrandie sur desktop pour que le titre passe sous le
        // bouton retour en overlay (sinon ils se chevauchent).
        val topPad = if (showBackButton) 96.dp else 48.dp
        // État nommé : la rangée des saisons doit pouvoir ramener la page en
        // haut, faute de quoi l'en-tête reste hors cadre (voir plus bas).
        val pageScroll = rememberScrollState()
        val pageScope = rememberCoroutineScope()
        // Sur la liste des épisodes d'une série, la page ne défile pas en bloc :
        // l'en-tête et les saisons restent posés, seuls les épisodes défilent
        // (voir plus bas). Partout ailleurs — film, fiche d'épisode — le
        // défilement global reste le bon comportement.
        val seriesList = state is DetailsState.Tv && selectedEpisode == null
        Column(
            modifier = Modifier.fillMaxSize()
                .then(if (seriesList) Modifier else Modifier.verticalScroll(pageScroll))
                .padding(top = topPad, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                DetailsState.Loading -> Text(stringResource(Res.string.common_loading), modifier = hPad)
                is DetailsState.Error -> {
                    Text(s.message, modifier = hPad)
                    MoovieButton(onClick = onBack, modifier = hPad) { Text(stringResource(Res.string.common_back)) }
                }
                is DetailsState.Movie -> {
                    val movieWatched = movieKey in watched
                    // Même mise en page que la fiche d'épisode — visuel à gauche,
                    // métadonnées et synopsis à droite — pour que les deux fiches
                    // du catalogue se ressemblent au lieu de diverger.
                    MovieHeader(details = s.details, isWatched = movieWatched)

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
                            modifier = Modifier.focusRequester(primaryFocus),
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
                    // Casting sous les boutons, comme sur la fiche d'épisode : la
                    // descente au D-pad atteint d'abord Lire, pas une vignette
                    // d'acteur.
                    CastRow(s.details.credits?.cast.orEmpty())
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
                            showName = s.details.name,
                            season = selected.season,
                            ep = ep,
                            isWatched = key in watched,
                            hasResume = resume.containsKey(key),
                            searching = quickPlay is QuickPlayState.Searching,
                            primaryFocus = primaryFocus,
                            onPlay = { onQuickPlayEpisode(selected.season, ep.episodeNumber) },
                            onOpenSources = { onOpenEpisodePanel(selected.season, ep.episodeNumber) },
                            onToggleWatched = { onToggleWatched(key) },
                        )
                    } else {
                        // Deux volets plutôt qu'un empilement : l'écran fait
                        // 960 × 540 dp, et un synopsis qui occupe toute la
                        // largeur pour trois lignes prend à la liste la hauteur
                        // de trois épisodes. Côte à côte, la description garde
                        // sa place et la liste récupère toute la colonne.
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                        Column(
                            modifier = Modifier.width(SERIES_PANE_WIDTH).padding(start = 48.dp),
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
                        (s.seasonYear ?: s.details.year)?.let { Text(it) }
                        ScrollingSynopsis(
                            text = s.seasonOverview.ifBlank { s.details.overview },
                            // Colonne étroite : le texte y tient sur plus de
                            // lignes, et il reste de la place sous les saisons.
                            lines = 8,
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
                        Text(stringResource(Res.string.details_seasons), style = MaterialTheme.typography.titleMedium)
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
                                        modifier = if (isCurrent) {
                                            Modifier.focusRequester(primaryFocus)
                                        } else {
                                            Modifier
                                        },
                                    ) {
                                        Text("S${season.seasonNumber}")
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
                        // Volet droit : la liste occupe toute la hauteur.
                        //
                        // LazyColumn et non Column défilante : c'est ce qui donne
                        // `animateScrollToItem`, seul moyen de caler l'épisode
                        // focalisé en haut — sans quoi il se colle en bas du
                        // cadre et le suivant reste invisible, exactement le
                        // défaut corrigé sur les rangées de l'accueil.
                        LazyColumn(
                            state = episodesState,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            // Marges dans le contentPadding : l'agrandissement au
                            // focus déborde dedans au lieu d'être rogné.
                            contentPadding = PaddingValues(end = 48.dp, bottom = 24.dp),
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
                                    // OK = fiche de l'épisode (comme un film) ;
                                    // OK long = bascule vu / non vu.
                                    onOpen = { onOpenEpisode(s.season, ep) },
                                    onToggleWatched = { onToggleWatched(key) },
                                )
                            }
                        }
                        }
                        }
                    }
                }
            }
        }

        // Bouton retour desktop, en overlay haut-gauche (masqué quand le panneau
        // des sources est ouvert : Échap/clic-extérieur le ferme d'abord).
        if (showBackButton && !panelVisible) {
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
                    onPick = onPickSource,
                    qualities = sourceQualities,
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
    onPick: (EmbedLink) -> Unit,
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
            .width(380.dp)
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
                            onClick = { onPick(link) },
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoovieButton(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildString {
                    append(link.hoster.replaceFirstChar { it.uppercase() })
                    if (rank != null) append(" · $rank")
                },
                style = MaterialTheme.typography.titleSmall,
            )
            // La qualité prime dès qu'elle est connue : c'est le critère de choix.
            // En attendant, le catalogue plutôt qu'une ligne vide — mieux vaut
            // apprendre d'où vient la source que de regarder un trou.
            val secondary = quality ?: link.provider?.let {
                stringResource(Res.string.details_source_via, it)
            }
            secondary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = if (quality != null) 0.9f else 0.55f),
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
@Composable
private fun MovieHeader(details: MovieDetails, isWatched: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Box(
            modifier = Modifier
                // Calé sur la *hauteur* de la vignette d'épisode (420 × 16:9 ≈
                // 236 dp), pas sur sa largeur : une affiche 2:3 de 240 dp de large
                // en ferait 360 de haut et repousserait titre, genres et note hors
                // de l'écran dès que le focus descend sur « Lecture ».
                .width(160.dp)
                .aspectRatio(2f / 3f)
                .clip(MoovieShape)
                .background(Color(0xFF222222)),
        ) {
            AsyncImage(
                model = details.posterUrl() ?: details.backdropUrl(),
                contentDescription = details.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                details.year?.let {
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
            if (details.overview.isNotBlank()) {
                Text(
                    details.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFDDDDDD),
                )
            }
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
    showName: String,
    season: Int,
    ep: Episode,
    isWatched: Boolean,
    hasResume: Boolean,
    searching: Boolean,
    primaryFocus: FocusRequester,
    onPlay: () -> Unit,
    onOpenSources: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    val hPad = Modifier.padding(horizontal = 48.dp)
    Row(
        modifier = hPad.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .aspectRatio(16f / 9f)
                .clip(MoovieShape)
                .background(Color(0xFF222222)),
        ) {
            AsyncImage(
                model = ep.stillUrlLarge(),
                contentDescription = ep.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (ep.overview.isNotBlank()) {
                Text(ep.overview, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDDDDDD))
            }
        }
    }
    Row(
        modifier = hPad,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoovieButton(onClick = onPlay, modifier = Modifier.focusRequester(primaryFocus)) {
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
        MoovieIconButton(
            onClick = onToggleWatched,
            icon = if (isWatched) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (isWatched) stringResource(Res.string.mark_unwatched) else stringResource(Res.string.mark_watched),
            selected = isWatched,
        )
    }
}

@Composable
private fun EpisodeRow(
    ep: Episode,
    isWatched: Boolean,
    progress: Float?,
    onOpen: () -> Unit,
    onToggleWatched: () -> Unit,
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
                AsyncImage(
                    model = ep.stillUrl(),
                    contentDescription = ep.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isWatched) {
                    WatchedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
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
                if (ep.overview.isNotBlank()) {
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

@Composable
private fun CastRow(cast: List<CastMember>) {
    val members = cast.take(15)
    if (members.isEmpty()) return
    Column {
        Text(stringResource(Res.string.details_cast), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(Modifier.height(8.dp))
        val castState = rememberLazyListState()
        MoovieRail(castState) {
        LazyRow(
            state = castState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            items(members) { member ->
                Column(
                    modifier = Modifier.width(96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(MoovieShape)
                            .background(Color(0xFF222222)),
                    ) {
                        AsyncImage(
                            model = member.profileUrl(),
                            contentDescription = member.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        member.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (member.character.isNotBlank()) {
                        Text(
                            member.character,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color(0xFF9A9A9A),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        }
    }
}
