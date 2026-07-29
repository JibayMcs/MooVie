package fr.moovie.tv.ui.details

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.data.sources.EmbedLink
import fr.moovie.tv.data.tmdb.CastMember
import fr.moovie.tv.data.tmdb.Episode
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.common_loading
import fr.moovie.tv.resources.details_cast
import fr.moovie.tv.resources.details_episodes_season
import fr.moovie.tv.resources.details_lang_missing
import fr.moovie.tv.resources.details_lang_unavailable
import fr.moovie.tv.resources.details_no_sources
import fr.moovie.tv.resources.details_play
import fr.moovie.tv.resources.details_playing
import fr.moovie.tv.resources.details_resume
import fr.moovie.tv.resources.details_runtime
import fr.moovie.tv.resources.details_searching
import fr.moovie.tv.resources.details_searching_source
import fr.moovie.tv.resources.details_seasons
import fr.moovie.tv.resources.details_sources
import fr.moovie.tv.resources.mark_season_unwatched
import fr.moovie.tv.resources.mark_season_watched
import fr.moovie.tv.resources.mark_unwatched
import fr.moovie.tv.resources.mark_watched
import fr.moovie.tv.ui.components.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.components.MoovieIconButton
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

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
    movieKey: String,
    episodeKey: (season: Int, episode: Int) -> String,
    onQuickPlayMovie: () -> Unit,
    onQuickPlayEpisode: (season: Int, episode: Int) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onToggleWatched: (String) -> Unit,
    onToggleSeasonWatched: () -> Unit,
    onOpenPanel: () -> Unit,
    onClosePanel: () -> Unit,
    onPickSource: (EmbedLink) -> Unit,
    onDismissQuickPlay: () -> Unit,
    onBack: () -> Unit,
    // Desktop uniquement : bouton retour à l'écran (sur TV, la télécommande a
    // sa propre touche Retour, pas besoin d'un bouton).
    showBackButton: Boolean = false,
) {
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(state) {
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
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
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
                    Row(
                        modifier = hPad,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(s.details.title, style = MaterialTheme.typography.headlineMedium)
                        if (movieWatched) WatchedBadge()
                    }
                    s.details.year?.let { Text(stringResource(Res.string.details_runtime, it, s.details.runtime?.toString() ?: "?"), modifier = hPad) }
                    Text(s.details.overview, style = MaterialTheme.typography.bodyMedium, modifier = hPad)
                    CastRow(s.details.credits?.cast.orEmpty())

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
                    }
                }
                is DetailsState.Tv -> {
                    Text(s.details.name, style = MaterialTheme.typography.headlineMedium, modifier = hPad)
                    s.details.year?.let { Text(it, modifier = hPad) }
                    Text(s.details.overview, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = hPad)
                    val seasonAllWatched = s.episodes.isNotEmpty() &&
                        s.episodes.all { episodeKey(s.season, it.episodeNumber) in watched }
                    Text(stringResource(Res.string.details_seasons), style = MaterialTheme.typography.titleMedium, modifier = hPad)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 48.dp),
                    ) {
                        itemsIndexed(s.details.seasons.filter { it.seasonNumber > 0 }) { index, season ->
                            MoovieButton(
                                onClick = { onSelectSeason(season.seasonNumber) },
                                modifier = if (index == 0) Modifier.focusRequester(primaryFocus) else Modifier,
                            ) {
                                Text(if (season.seasonNumber == s.season) "● S${season.seasonNumber}" else "S${season.seasonNumber}")
                            }
                        }
                        // En fin de rangée (atteignable au D-pad) : œil = marquer la
                        // saison vue / non vue (outline verte quand tout est vu).
                        item {
                            MoovieIconButton(
                                onClick = onToggleSeasonWatched,
                                icon = if (seasonAllWatched) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (seasonAllWatched) stringResource(Res.string.mark_season_unwatched) else stringResource(Res.string.mark_season_watched),
                                selected = seasonAllWatched,
                            )
                        }
                    }
                    Text(stringResource(Res.string.details_episodes_season, s.season), style = MaterialTheme.typography.titleMedium, modifier = hPad)
                    s.episodes.forEach { ep ->
                        val key = episodeKey(s.season, ep.episodeNumber)
                        Box(modifier = hPad) {
                            EpisodeRow(
                                ep = ep,
                                isWatched = key in watched,
                                progress = resume[key]?.progress,
                                // Clic = lecture directe (meilleure source dans la langue
                                // préférée) ; le panneau ne s'ouvre qu'en secours.
                                onSources = { onQuickPlayEpisode(s.season, ep.episodeNumber) },
                                onToggleWatched = { onToggleWatched(key) },
                            )
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
                SourcesSlideOver(state = active, preferred = streamLang, resolveError = resolveError, onPick = onPickSource)
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
                    .clip(RoundedCornerShape(24.dp))
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
                        Text(stringResource(Res.string.details_searching_source, q.label), style = MaterialTheme.typography.bodyMedium)
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
) {
    val links = state.links
    val grouped = links.groupBy { it.language ?: "?" }
    val order = (listOf(preferred.name, "VF", "VOSTFR", "VO") + grouped.keys).distinct()
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
        ProviderChips(state.providers, modifier = pPad)

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
                    itemsIndexed(sourcesInLang, key = { _, l -> l.url }) { linkIndex, link ->
                        val isFirst = sectionIndex == 0 && linkIndex == 0
                        MoovieButton(
                            onClick = { onPick(link) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isFirst) Modifier.focusRequester(firstFocus) else Modifier),
                        ) {
                            Text(link.hoster.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }
        }
    }
}

/** Pastilles de progression par provider (chargement / trouvé / vide / échec). */
@Composable
private fun ProviderChips(providers: List<ProviderProgress>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        providers.forEach { p ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (p.status) {
                    ProviderStatus.LOADING -> CircularProgressIndicator(
                        color = MOOVIE_ACCENT,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(12.dp),
                    )
                    ProviderStatus.DONE -> Text("✓", color = Color(0xFF5FD98A), style = MaterialTheme.typography.labelMedium)
                    ProviderStatus.EMPTY -> Text("—", color = Color(0xFF8A8A8A), style = MaterialTheme.typography.labelMedium)
                    ProviderStatus.FAILED -> Text("✕", color = Color(0xFFE06A6A), style = MaterialTheme.typography.labelMedium)
                }
                Text(p.name, style = MaterialTheme.typography.labelMedium, color = Color(0xFFCCCCCC))
            }
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
                    .clip(RoundedCornerShape(10.dp))
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

@Composable
private fun EpisodeRow(
    ep: Episode,
    isWatched: Boolean,
    progress: Float?,
    onSources: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    // Clic → sources de l'épisode ; OK long → bascule vu/non vu.
    MoovieCard(
        onClick = onSources,
        onLongClick = onToggleWatched,
        focusedScale = 1.02f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp, 90.dp)
                    .clip(RoundedCornerShape(6.dp))
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
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MOOVIE_ACCENT,
                        trackColor = Color(0x66000000),
                        modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${ep.episodeNumber}. ${ep.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isWatched) Color(0xFF9A9A9A) else Color.White,
                )
                if (ep.overview.isNotBlank()) {
                    Text(ep.overview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CastRow(cast: List<CastMember>) {
    val members = cast.take(15)
    if (members.isEmpty()) return
    Column {
        Text(stringResource(Res.string.details_cast), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(
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
                            .clip(CircleShape)
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
