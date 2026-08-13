package fr.moovie.tv.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.download.DownloadRepository
import fr.moovie.tv.data.net.Connectivity
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.home_settings
import fr.moovie.tv.resources.offline_empty_help
import fr.moovie.tv.resources.offline_empty_title
import fr.moovie.tv.resources.offline_library_help
import fr.moovie.tv.resources.offline_retry
import fr.moovie.tv.resources.offline_title
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.download.DownloadsSection
import org.jetbrains.compose.resources.stringResource

private val DIM = Color(0xFF9A9A9A)

/**
 * L'application quand elle n'a plus de réseau : une bibliothèque, pas un
 * catalogue.
 *
 * ### Pourquoi remplacer l'accueil plutôt que l'habiller d'un bandeau
 *
 * Un accueil hors ligne est un mur de vignettes dont **aucune** ne se lance :
 * les rangées viennent de TMDB, et une source se résout sur le réseau. Le
 * garder en le coiffant d'un avertissement, c'est laisser quelqu'un parcourir
 * vingt affiches avant de comprendre, à la vingtième, qu'il n'y avait rien à
 * regarder. Ce qui reste réellement disponible — les téléchargements — était
 * pendant ce temps à trois niveaux de profondeur.
 *
 * L'écran s'inverse donc : ce qui marche prend toute la place, et le reste
 * disparaît jusqu'au retour du réseau. La bascule est automatique dans les deux
 * sens, sans réglage à connaître.
 *
 * ### Le corps est celui des téléchargements, exprès
 *
 * [DownloadsSection] portait déjà le seul chemin de lecture qui ne dépende pas
 * de TMDB. En écrire un second pour l'occasion aurait fabriqué deux listes de
 * la même chose, qui auraient divergé au premier correctif — et celle-ci est
 * déjà éprouvée : reprise, pause, suppression, place occupée.
 */
@Composable
fun OfflineScreen(
    onPlay: (Download) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = remember { DownloadRepository() }
    val downloads by repo.downloads.collectAsState(initial = emptyList())

    // Tout ce qui est sur le disque, pas seulement ce qui est terminé : un
    // téléchargement interrompu par la coupure elle-même a sa place ici, et
    // c'est de cet écran qu'on le reprendra quand le réseau reviendra.
    if (downloads.isEmpty()) {
        OfflineEmpty(onOpenSettings = onOpenSettings, modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (useBottomNav) 20.dp else 56.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = DIM)
            Text(
                stringResource(Res.string.offline_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(Res.string.offline_library_help),
            style = MaterialTheme.typography.bodyMedium,
            color = DIM,
        )
        DownloadsSection(onPlay = onPlay)
    }
}

/**
 * Hors ligne et rien de téléchargé : le seul cas où l'application n'a
 * strictement rien à offrir.
 *
 * On le dit, plutôt que d'afficher une bibliothèque vide qui laisserait croire
 * à une perte de données. Les réglages restent atteignables — ils sont locaux,
 * et c'est là qu'on va vérifier une clé ou changer de DNS quand plus rien ne
 * passe.
 */
@Composable
private fun OfflineEmpty(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = DIM)
        Text(
            stringResource(Res.string.offline_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.offline_empty_help),
            style = MaterialTheme.typography.bodyMedium,
            color = DIM,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Re-teste tout de suite : voir Connectivity.recheck. Sans lui, on
            // rebranche son câble et on attend devant un écran qui a déjà tort.
            MoovieButton(onClick = { Connectivity.recheck() }) {
                Text(stringResource(Res.string.offline_retry))
            }
            MoovieButton(onClick = onOpenSettings) {
                Text(stringResource(Res.string.home_settings))
            }
        }
    }
}
