package fr.moovie.tv.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Fiche film/série. STUB : affiche l'id et un bouton Lecture. À câbler ensuite
 * sur TMDB (détails, saisons/épisodes) + résolution de source via
 * ExtractorRegistry, qui fournira l'URL passée à onPlay.
 */
@Composable
fun DetailsScreen(
    tmdbId: Int,
    isTv: Boolean,
    onPlay: (streamUrl: String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Text(
            text = if (isTv) "Série #$tmdbId" else "Film #$tmdbId",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Fiche détaillée à venir (synopsis, casting, saisons/épisodes, sélection de source).",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // URL de démonstration (flux HLS de test) tant que l'extraction n'est pas câblée.
            Button(onClick = { onPlay(DEMO_HLS) }) { Text("Lecture (démo)") }
            Button(onClick = onBack) { Text("Retour") }
        }
    }
}

private const val DEMO_HLS =
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
