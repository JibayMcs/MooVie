package fr.moovie.tv.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import fr.moovie.tv.data.download.DownloadRepository
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.data.settings.tmdbCountry
import fr.moovie.tv.data.remote.PlayRequest
import fr.moovie.tv.ui.remote.rememberTvSender
import fr.moovie.tv.ui.navigation.Screen

/**
 * Wrapper Android : branche le [DetailsViewModel] (TMDB, sources, suivi de
 * lecture) sur la fiche partagée [DetailsScreenContent] de jvmCommon. Garde ici
 * ce qui est Android-only : BackHandler, auto-lecture (reprise depuis
 * l'accueil) et départ vers le lecteur quand un flux est résolu.
 */
@Composable
fun DetailsScreen(
    tmdbId: Int,
    isTv: Boolean,
    onPlay: (Screen.Player) -> Unit,
    onBack: () -> Unit,
    onOpenPerson: (personId: Int, name: String) -> Unit = { _, _ -> },
    autoSources: Boolean = false,
    resumeSeason: Int = 0,
    resumeEpisode: Int = 0,
    /**
     * Bascule vers la télécommande, après un titre envoyé au téléviseur. Le
     * geste continue sur l'écran qui montre ce que la TV fait, plutôt que de
     * laisser le téléphone sur une fiche devenue sans objet.
     */
    onOpenRemote: () -> Unit = {},
    viewModel: DetailsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val resolved by viewModel.resolved.collectAsStateWithLifecycle()
    val inWatchlist by viewModel.inWatchlist.collectAsStateWithLifecycle()
    val resolveError by viewModel.resolveError.collectAsStateWithLifecycle()
    val resolvingUrl by viewModel.resolving.collectAsStateWithLifecycle()
    val sourceQualities by viewModel.qualities.collectAsStateWithLifecycle()
    val sourceHeights by viewModel.heights.collectAsStateWithLifecycle()
    val sourceStatuses by viewModel.linkStatus.collectAsStateWithLifecycle()
    val streamLang by viewModel.streamLanguage.collectAsStateWithLifecycle()
    // Indexés par le lien d'embed dont ils sont partis : c'est la seule façon
    // de savoir **quelle ligne** allumer dans le panneau des sources.
    val seasonDownload by viewModel.seasonDownload.collectAsStateWithLifecycle(initialValue = null)
    val downloadsBySource by remember { DownloadRepository().downloads }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val quickPlay by viewModel.quickPlay.collectAsStateWithLifecycle()
    val trailer by viewModel.trailer.collectAsStateWithLifecycle()
    val trailerExpanded by viewModel.trailerExpanded.collectAsStateWithLifecycle()
    val downloadSearching by viewModel.downloadSearching.collectAsStateWithLifecycle()
    val trailerAutoplay by viewModel.trailerAutoplay.collectAsStateWithLifecycle()
    val trailerSound by viewModel.trailerSound.collectAsStateWithLifecycle()
    val panelVisible by viewModel.panelVisible.collectAsStateWithLifecycle()
    val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
    // Reprise depuis l'accueil : lance la lecture directe une seule fois, dès que la fiche est chargée.
    val autoConsumed = remember { mutableStateOf(false) }

    // Envoi vers le téléviseur du salon. Le composant porte lui-même sa modale
    // de confirmation ; il n'expose ici qu'un « peut-on envoyer ».
    val tvSender = rememberTvSender(onSent = onOpenRemote)

    LaunchedEffect(tmdbId, isTv) { viewModel.start(tmdbId, isTv, resumeSeason, resumeEpisode) }
    LaunchedEffect(state) {
        if (autoSources && !autoConsumed.value) {
            when (state) {
                is DetailsState.Movie -> {
                    autoConsumed.value = true
                    viewModel.quickPlayMovie()
                }
                is DetailsState.Tv -> {
                    autoConsumed.value = true
                    viewModel.selectSeason(resumeSeason)
                    viewModel.quickPlayEpisode(resumeSeason, resumeEpisode)
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(resolved) {
        resolved?.let { s ->
            if (s.url.isNotBlank()) {
                // Ferme le panneau avant de partir : au retour (ou sur une autre
                // fiche, le ViewModel étant partagé), il ne doit pas rester ouvert.
                viewModel.closePanel()
                onPlay(
                    Screen.Player(
                        streamUrl = s.url,
                        headers = s.headers,
                        mediaKey = viewModel.playbackKey,
                        subtitles = s.subtitleUrls,
                        title = viewModel.playbackTitle,
                        subtitle = viewModel.playbackSubtitle,
                        nextSeason = viewModel.playbackNext?.first ?: 0,
                        nextEpisode = viewModel.playbackNext?.second ?: 0,
                        posterUrl = viewModel.playbackPoster,
                        expectedMinutes = viewModel.playbackMinutes ?: 0,
                        sourceUrl = viewModel.playingLink?.url.orEmpty(),
                        hoster = viewModel.playingLink?.hoster.orEmpty(),
                        language = viewModel.playingLink?.language.orEmpty(),
                        alternatives = viewModel.playbackAlternatives(),
                    ),
                )
            }
            viewModel.consumeResolved()
        }
    }


    // Retour : ferme d'abord le panneau des sources, puis la fiche d'épisode,
    // et seulement ensuite remonte à l'accueil (BackHandler de MainActivity).
    // La bande-annonce en premier : elle recouvre le reste, c'est elle que
    // Retour doit refermer avant le panneau ou la fiche d'épisode.
    BackHandler(enabled = trailerExpanded || panelVisible || selectedEpisode != null) {
        when {
            trailerExpanded -> viewModel.closeTrailer()
            panelVisible -> viewModel.closePanel()
            else -> viewModel.closeEpisode()
        }
    }

    DetailsScreenContent(
        state = state,
        sources = sources,
        resolveError = resolveError,
        resolvingUrl = resolvingUrl,
        onOpenPerson = { onOpenPerson(it.id, it.name) },
        streamLang = streamLang,
        watched = watched,
        resume = resume,
        quickPlay = quickPlay,
        panelVisible = panelVisible,
        selectedEpisode = selectedEpisode,
        movieKey = viewModel.movieKey(),
        episodeKey = viewModel::episodeKey,
        onQuickPlayMovie = viewModel::quickPlayMovie,
        onQuickPlayEpisode = viewModel::quickPlayEpisode,
        onSelectSeason = viewModel::selectSeason,
        onOpenEpisode = viewModel::openEpisode,
        onOpenEpisodePanel = viewModel::openEpisodePanel,
        onToggleWatched = viewModel::toggleWatched,
        onToggleSeasonWatched = viewModel::toggleSeasonWatched,
        inWatchlist = inWatchlist,
        onToggleWatchlist = viewModel::toggleWatchlist,
        onOpenPanel = viewModel::openPanel,
        onClosePanel = viewModel::closePanel,
        onPickSource = viewModel::play,
        onDownloadSource = viewModel::download,
        sourceStatuses = sourceStatuses,
        onDownloadSeason = { season -> viewModel.downloadSeason(season) },
        seasonDownload = seasonDownload,
        downloads = downloadsBySource.associateBy { it.sourceUrl },
        downloadList = downloadsBySource,
        sourceQualities = sourceQualities,
        sourceHeights = sourceHeights,
        // Null quand aucun téléviseur ne répond : pas de bouton plutôt qu'un
        // bouton inerte. Voir TvSender.
        onSendToTv = if (!tvSender.available) null else {
            {
                val episode = selectedEpisode
                val season = episode?.season ?: resumeSeason
                val number = episode?.episode?.episodeNumber ?: resumeEpisode
                // La reprise **de ce téléphone**, pour que la TV continue là où
                // on s'est arrêté plutôt qu'au début. La clé se recalcule au lieu
                // d'être devinée : c'est la même que celle du magasin.
                val key = if (isTv) "tv:$tmdbId:s${season}e$number" else "movie:$tmdbId"
                val here = resume[key]
                tvSender.ask(
                    PlayRequest(
                        tmdbId = tmdbId,
                        isTv = isTv,
                        season = season,
                        episode = number,
                        title = viewModel.playbackTitle,
                        subtitle = viewModel.playbackSubtitle,
                        artwork = viewModel.playbackPoster.orEmpty(),
                        positionMs = here?.positionMs ?: 0,
                        durationMs = here?.durationMs ?: 0,
                    ),
                )
            }
        },
        onRequestQuality = viewModel::requestQuality,
        trailer = trailer,
        onPlayTrailer = viewModel::openTrailer,
        trailerExpanded = trailerExpanded,
        onCloseTrailer = viewModel::closeTrailer,
        onDownloadBest = viewModel::downloadBest,
        downloadSearching = downloadSearching,
        trailerPreview = { stream, vol, onCtl, mod -> TrailerPreview(stream, vol, onCtl, mod) },
        trailerAutoplay = trailerAutoplay,
        trailerSound = trailerSound,
        country = tmdbCountry(),
        onDismissQuickPlay = viewModel::dismissQuickPlay,
        onBack = onBack,
    )
}
