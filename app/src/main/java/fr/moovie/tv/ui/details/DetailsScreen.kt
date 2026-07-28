package fr.moovie.tv.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import fr.moovie.tv.data.sources.EmbedLink
import fr.moovie.tv.data.tmdb.CastMember
import fr.moovie.tv.data.tmdb.Episode

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
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(tmdbId, isTv) { viewModel.start(tmdbId, isTv) }
    // Donne le focus au bouton principal dès que la fiche est chargée, sinon
    // aucun élément n'est focalisé et le premier appui D-pad semble sans effet.
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

    // Fond : backdrop de l'élément, flouté et bien assombri, pour une fiche
    // immersive tout en gardant le texte lisible.
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
        // Voile assombri mais laissant transparaître l'affiche floutée.
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
                Button(onClick = onBack) { Text("Retour") }
            }
            is DetailsState.Movie -> {
                Text(s.details.title, style = MaterialTheme.typography.headlineMedium)
                s.details.year?.let { Text("$it • ${s.details.runtime ?: "?"} min") }
                Text(s.details.overview, style = MaterialTheme.typography.bodyMedium)
                CastRow(s.details.credits?.cast.orEmpty())
                Button(
                    onClick = { viewModel.loadMovieSources() },
                    modifier = Modifier.focusRequester(primaryFocus),
                ) { Text("Sources") }
                SourcesPanel(sources, onPick = viewModel::play)
            }
            is DetailsState.Tv -> {
                Text(s.details.name, style = MaterialTheme.typography.headlineMedium)
                s.details.year?.let { Text(it) }
                Text(s.details.overview, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text("Saisons", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(s.details.seasons.filter { it.seasonNumber > 0 }) { index, season ->
                        Button(
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
                SourcesPanel(sources, onPick = viewModel::play)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, onSources: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.width(520.dp)) {
            Text("${ep.episodeNumber}. ${ep.name}", style = MaterialTheme.typography.titleSmall)
            if (ep.overview.isNotBlank()) {
                Text(ep.overview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Button(onClick = onSources) { Text("Sources") }
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

@Composable
private fun SourcesPanel(state: SourcesState, onPick: (EmbedLink) -> Unit) {
    when (state) {
        SourcesState.Idle -> Unit
        SourcesState.Loading -> Text("Recherche des sources…")
        is SourcesState.Error -> Text(state.message)
        is SourcesState.Loaded -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sources trouvées (${state.links.size})", style = MaterialTheme.typography.titleMedium)
            state.links.forEach { link ->
                Button(onClick = { onPick(link) }) {
                    Text("${link.hoster} • ${link.language ?: "?"}")
                }
            }
        }
    }
}

