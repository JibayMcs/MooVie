package fr.moovie.tv.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.data.sources.EmbedLink
import fr.moovie.tv.data.tmdb.CastMember
import fr.moovie.tv.data.tmdb.Episode
import fr.moovie.tv.ui.components.MoovieButton

private val ACCENT = Color(0xFFB5302C)

@Composable
fun DetailsScreen(
    tmdbId: Int,
    isTv: Boolean,
    onPlay: (streamUrl: String, headers: Map<String, String>) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val resolved by viewModel.resolved.collectAsStateWithLifecycle()
    val streamLang by viewModel.streamLanguage.collectAsStateWithLifecycle()
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(tmdbId, isTv) { viewModel.start(tmdbId, isTv) }
    LaunchedEffect(state) {
        if (state is DetailsState.Movie || state is DetailsState.Tv) {
            runCatching { primaryFocus.requestFocus() }
        }
    }
    LaunchedEffect(resolved) {
        resolved?.let { s ->
            if (s.url.isNotBlank()) onPlay(s.url, s.headers)
            viewModel.consumeResolved()
        }
    }

    val backdrop = (state as? DetailsState.Movie)?.details?.backdropUrl()
        ?: (state as? DetailsState.Tv)?.details?.backdropUrl()
    val panelOpen = sources is SourcesState.Loaded

    // Retour ferme d'abord le panneau des sources (sinon retour à l'accueil).
    BackHandler(enabled = panelOpen) { viewModel.clearSources() }

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

        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                DetailsState.Loading -> Text("Chargement…")
                is DetailsState.Error -> {
                    Text(s.message)
                    MoovieButton(onClick = onBack) { Text("Retour") }
                }
                is DetailsState.Movie -> {
                    Text(s.details.title, style = MaterialTheme.typography.headlineMedium)
                    s.details.year?.let { Text("$it • ${s.details.runtime ?: "?"} min") }
                    Text(s.details.overview, style = MaterialTheme.typography.bodyMedium)
                    CastRow(s.details.credits?.cast.orEmpty())
                    MoovieButton(
                        onClick = { viewModel.loadMovieSources() },
                        modifier = Modifier.focusRequester(primaryFocus),
                    ) { Text("Sources") }
                    SourcesStatus(sources, streamLang)
                }
                is DetailsState.Tv -> {
                    Text(s.details.name, style = MaterialTheme.typography.headlineMedium)
                    s.details.year?.let { Text(it) }
                    Text(s.details.overview, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text("Saisons", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(s.details.seasons.filter { it.seasonNumber > 0 }) { index, season ->
                            MoovieButton(
                                onClick = { viewModel.selectSeason(season.seasonNumber) },
                                modifier = if (index == 0) Modifier.focusRequester(primaryFocus) else Modifier,
                            ) {
                                Text(if (season.seasonNumber == s.season) "● S${season.seasonNumber}" else "S${season.seasonNumber}")
                            }
                        }
                    }
                    Text("Épisodes (saison ${s.season})", style = MaterialTheme.typography.titleMedium)
                    s.episodes.forEach { ep ->
                        EpisodeRow(ep, onSources = { viewModel.loadEpisodeSources(ep.episodeNumber) })
                    }
                    SourcesStatus(sources, streamLang)
                }
            }
        }

        // Panneau des sources en slide-over depuis la droite (seulement si chargé).
        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val links = (sources as? SourcesState.Loaded)?.links.orEmpty()
            SourcesSlideOver(links = links, preferred = streamLang, onPick = viewModel::play)
        }
    }
}

/** Message inline sur la fiche pour les états non-chargés (recherche / vide / erreur). */
@Composable
private fun SourcesStatus(state: SourcesState, preferred: StreamLanguage) {
    when (state) {
        SourcesState.Loading -> Text("Recherche des sources…", color = Color(0xFFBBBBBB))
        SourcesState.Empty -> Text(
            "Aucune source disponible pour ce titre (recherché en ${preferred.name}).",
            color = Color(0xFFE0A0A0),
        )
        is SourcesState.Error -> Text(state.message, color = Color(0xFFE0A0A0))
        else -> Unit
    }
}

@Composable
private fun SourcesSlideOver(
    links: List<EmbedLink>,
    preferred: StreamLanguage,
    onPick: (EmbedLink) -> Unit,
) {
    // Groupement par langue, langue préférée de l'utilisateur en premier.
    val grouped = links.groupBy { it.language ?: "?" }
    val order = (listOf(preferred.name, "VF", "VOSTFR", "VO") + grouped.keys).distinct()
    val sections = order.filter { grouped.containsKey(it) }.map { it to grouped.getValue(it) }
    val prefMissing = !grouped.containsKey(preferred.name)
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(links) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(560.dp)
            .background(Color(0xF2121212))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sources", style = MaterialTheme.typography.titleLarge)
        Text("Retour pour fermer", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A8A8A))
        if (prefMissing) {
            Text(
                "Aucune source en ${preferred.name} — autres langues disponibles.",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE0A0A0),
            )
        }
        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.forEachIndexed { sectionIndex, (lang, sourcesInLang) ->
                item(key = "h_$lang") {
                    Text(
                        lang,
                        style = MaterialTheme.typography.titleMedium,
                        color = ACCENT,
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeRow(ep: Episode, onSources: () -> Unit) {
    // Toute la carte épisode est cliquable → charge les sources de l'épisode.
    Card(
        onClick = onSources,
        modifier = Modifier.fillMaxWidth(),
        scale = CardDefaults.scale(focusedScale = 1.02f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, ACCENT), shape = RoundedCornerShape(8.dp)),
        ),
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
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${ep.episodeNumber}. ${ep.name}", style = MaterialTheme.typography.titleSmall)
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
        Text("Casting", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
