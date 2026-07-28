package fr.moovie.tv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import fr.moovie.tv.data.tmdb.TmdbItem

@Composable
fun HomeScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Élément survolé (focus D-pad) → alimente le fond dynamique.
    var focused by remember { mutableStateOf<TmdbItem?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        // Fond : backdrop flouté de l'élément focalisé (blur no-op < API 31).
        focused?.backdropUrl()?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(28.dp),
            )
        }
        // Voile dégradé pour la lisibilité.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xCC0A0A0A), Color(0xF20A0A0A)),
                ),
            ),
        )

        Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Moo-vie", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onOpenSettings) { Text("Réglages") }
            }
            Spacer(Modifier.height(24.dp))

            when (val s = state) {
                HomeState.Loading -> Text("Chargement…")
                is HomeState.NeedsApiKey -> Column {
                    Text(s.reason)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenSettings) { Text("Ouvrir les réglages") }
                }
                is HomeState.Ready -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(bottom = 48.dp),
                ) {
                    items(s.rows) { row ->
                        CatalogRow(row = row, onOpenTitle = onOpenTitle, onFocusItem = { focused = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(
    row: HomeRow,
    onOpenTitle: (Int, Boolean) -> Unit,
    onFocusItem: (TmdbItem) -> Unit,
) {
    Column {
        Text(text = row.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(row.items) { item ->
                PosterCard(
                    item = item,
                    onClick = { onOpenTitle(item.id, item.isTv) },
                    onFocusItem = onFocusItem,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(item: TmdbItem, onClick: () -> Unit, onFocusItem: (TmdbItem) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "posterScale")

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocusItem(item)
            },
    ) {
        Column {
            AsyncImage(
                model = item.posterUrl(),
                contentDescription = item.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Text(
                text = item.displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
