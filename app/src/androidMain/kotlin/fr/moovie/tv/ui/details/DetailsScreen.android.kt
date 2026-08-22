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
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.cast_failed
import fr.moovie.tv.resources.cast_searching
import fr.moovie.tv.resources.common_cancel
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.data.cast.CastDevice
import fr.moovie.tv.data.cast.CastNow
import fr.moovie.tv.data.cast.CastPlayback
import fr.moovie.tv.data.cast.CastPresence
import fr.moovie.tv.data.cast.CastSession
import fr.moovie.tv.ui.remote.CastFailureDialog
import fr.moovie.tv.ui.remote.CastTarget
import fr.moovie.tv.ui.remote.castTargetsFor
import fr.moovie.tv.ui.remote.CastTargetDialog
import fr.moovie.tv.ui.remote.rememberCastFollow
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

    // ── Chromecast ───────────────────────────────────────────────────────────
    //
    // La liste est tenue par [CastPresence], dont la veille tourne **au niveau
    // de l'application** (MainActivity) et non ici : le bouton doit exister
    // avant qu'on le touche, et un balayage mDNS prend quelques secondes.
    // Chercher depuis cet écran seul faisait démarrer la recherche au moment où
    // la fiche s'affiche, donc après le premier regard porté sur la barre — et
    // ne cherchait rien du tout sur l'accueil ou dans le lecteur.
    val chromecasts by CastPresence.devices.collectAsStateWithLifecycle()
    val hotesMoovie by CastPresence.moovieHosts.collectAsStateWithLifecycle()
    /** Récepteur choisi, en attente de la résolution du flux. */
    var castTo by remember { mutableStateOf<CastDevice?>(null) }
    var castEnCours by remember { mutableStateOf(false) }
    var castEchoue by remember { mutableStateOf(false) }
    var castChoix by remember { mutableStateOf<List<CastTarget>>(emptyList()) }
    val castFollow = rememberCastFollow()

    /**
     * Envoie vers la destination choisie.
     *
     * Les deux chemins n'ont **rien en commun** au-delà du geste : une Moo-vie
     * reçoit une intention et résout elle-même ; un Chromecast attend une URL,
     * donc c'est ce téléphone qui doit d'abord résoudre. D'où deux branches
     * franches plutôt qu'une abstraction qui ferait croire à une symétrie.
     */
    fun envoieVers(cible: CastTarget) {
        val episode = selectedEpisode
        val season = episode?.season ?: resumeSeason
        val number = episode?.episode?.episodeNumber ?: resumeEpisode
        when (cible) {
            is CastTarget.Moovie -> {
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
            // On note la cible, puis on déclenche la résolution : c'est
            // `LaunchedEffect(resolved)` qui prendra le relais, sur la branche
            // gardée par `castTo`.
            is CastTarget.Chromecast -> {
                castTo = cible.device
                castEnCours = true
                if (isTv) viewModel.quickPlayEpisode(season, number) else viewModel.quickPlayMovie()
            }
        }
    }

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
            // ── Diffusion vers un Chromecast ─────────────────────────────────
            //
            // **Une branche à côté, jamais à la place.** Le chemin de lecture
            // locale ci-dessous marche ; le modifier pour y glisser un cas
            // supplémentaire, c'est risquer une régression là où personne ne
            // l'attend. `castTo` est nul dans l'immense majorité des cas, et
            // tout continue exactement comme avant.
            val chromecast = castTo
            if (chromecast != null && s.url.isNotBlank()) {
                castTo = null
                viewModel.closePanel()
                castEnCours = true
                val session = CastSession(chromecast)
                val parti = runCatching {
                    session.start(
                        stream = s,
                        title = viewModel.playbackTitle,
                        subtitle = viewModel.playbackSubtitle,
                        artwork = viewModel.playbackPoster.orEmpty(),
                        positionMs = resume[viewModel.playbackKey]?.positionMs ?: 0,
                    )
                }.getOrDefault(false)
                castEnCours = false
                if (parti) {
                    CastNow.start(
                        session,
                        CastPlayback(
                            device = chromecast,
                            title = viewModel.playbackTitle,
                            subtitle = viewModel.playbackSubtitle,
                            artwork = viewModel.playbackPoster.orEmpty(),
                            mediaKey = viewModel.playbackKey,
                        ),
                    )
                    castFollow()
                    onOpenRemote()
                } else {
                    session.stop()
                    castEchoue = true
                }
                viewModel.consumeResolved()
                return@LaunchedEffect
            }

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


    // Le choix de la destination, quand il y en a plusieurs. Le composant se
    // tait tout seul en dessous de deux — voir CastTargetDialog.
    CastTargetDialog(
        targets = castChoix,
        onPick = { cible ->
            castChoix = emptyList()
            envoieVers(cible)
        },
        onDismiss = { castChoix = emptyList() },
    )

    // La résolution se fait **ici**, pas sur le téléviseur : jusqu'à trente
    // secondes à froid. Sans ce voile, on appuie et il ne se passe rien pendant
    // une demi-minute, ce qui se lit comme un bouton mort.
    if (castEnCours) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.cast_searching),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    // La même modale que dans le lecteur : les deux échouent pour les mêmes
    // raisons, et deux libellés qui divergent seraient deux libellés à tenir.
    if (castEchoue) {
        CastFailureDialog(onDismiss = { castEchoue = false })
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
        // Le bouton existe dès qu'**une** destination répond : un Chromecast
        // suffit, et c'est précisément le cas de quelqu'un qui n'a pas
        // d'Android TV. Le lier au seul appairage Moo-vie le rendait invisible
        // pour lui.
        onSendToTv = if (castTargetsFor(tvSender.target, chromecasts, hotesMoovie).isEmpty()) null else {
            {
                // Un appareil qui fait tourner Moo-vie n'est pas un Chromecast,
                // même s'il répond au protocole. Voir castTargetsFor.
                val cibles = castTargetsFor(tvSender.target, chromecasts, hotesMoovie)
                // Une seule destination ne se choisit pas : demander
                // confirmation ferait un écran de plus pour rien.
                when (cibles.size) {
                    0 -> Unit
                    1 -> envoieVers(cibles.first())
                    else -> castChoix = cibles
                }
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

