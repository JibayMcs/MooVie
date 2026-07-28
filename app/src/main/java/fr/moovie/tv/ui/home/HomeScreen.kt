package fr.moovie.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import fr.moovie.tv.data.tmdb.TmdbItem

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Moo-vie", style = androidx.tv.material3.MaterialTheme.typography.headlineMedium)
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
            is HomeState.Ready -> Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                s.rows.forEach { row ->
                    CatalogRow(row = row, onOpenTitle = onOpenTitle)
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(row: HomeRow, onOpenTitle: (Int, Boolean) -> Unit) {
    Column {
        Text(text = row.title, style = androidx.tv.material3.MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(row.items) { item ->
                PosterCard(item = item, onClick = { onOpenTitle(item.id, item.isTv) })
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(item: TmdbItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(140.dp)) {
        Column {
            Box(modifier = Modifier.size(width = 140.dp, height = 210.dp)) {
                AsyncImage(
                    model = item.posterUrl(),
                    contentDescription = item.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = item.displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
                style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
}
