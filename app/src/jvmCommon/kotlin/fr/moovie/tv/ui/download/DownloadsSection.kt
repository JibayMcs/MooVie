package fr.moovie.tv.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.download.DownloadQueue
import fr.moovie.tv.data.download.DownloadRepository
import fr.moovie.tv.data.download.DownloadState
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.downloads_empty
import fr.moovie.tv.resources.downloads_failed
import fr.moovie.tv.resources.downloads_help
import fr.moovie.tv.resources.downloads_paused
import fr.moovie.tv.resources.downloads_pause
import fr.moovie.tv.resources.downloads_queued
import fr.moovie.tv.resources.downloads_ready
import fr.moovie.tv.resources.downloads_remove
import fr.moovie.tv.resources.downloads_resume
import fr.moovie.tv.resources.downloads_used
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MoovieShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private val DIM = Color(0xFF9A9A9A)
private val PANEL = Color(0xFF1A1A1A)

/**
 * Gestion des téléchargements.
 *
 * On y vient pour **faire de la place**, pas pour regarder : lire un titre
 * téléchargé passe par sa fiche, comme n'importe quel autre, puisque la lecture
 * choisit d'elle-même la copie locale. D'où une liste qui montre d'abord ce que
 * chaque titre occupe, et pas ses affiches.
 */
@Composable
fun DownloadsSection() {
    val repo = remember { DownloadRepository() }
    val downloads by repo.downloads.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // Mesuré sur le disque, et recalculé à chaque changement de la liste : un
    // téléchargement interrompu par une coupure laisse des octets que personne
    // n'a comptés, et c'est quand le disque se remplit que le chiffre doit être
    // juste.
    val used by produceState(0L, downloads.size) {
        value = withContext(Dispatchers.IO) { repo.bytesOnDisk() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.downloads_help), style = MaterialTheme.typography.bodySmall, color = DIM)
        Text(
            stringResource(Res.string.downloads_used, formatSize(used)),
            style = MaterialTheme.typography.titleMedium,
        )

        if (downloads.isEmpty()) {
            Text(stringResource(Res.string.downloads_empty), style = MaterialTheme.typography.bodyMedium, color = DIM)
            return@Column
        }

        downloads.forEach { download ->
            DownloadRow(
                download = download,
                onPause = { DownloadQueue.pause(download.key) },
                onResume = { scope.launch { DownloadQueue.resumePending() } },
                onRemove = { scope.launch { DownloadQueue.remove(download.key) } },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    download: Download,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PANEL, MoovieShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            listOfNotNull(download.title, download.subtitle.takeIf { it.isNotBlank() })
                .joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(statusOf(download), style = MaterialTheme.typography.bodySmall, color = DIM)

        // La barre n'apparaît que pendant le travail : sur un titre terminé elle
        // serait une décoration pleine qui n'apprend rien.
        if (download.state == DownloadState.RUNNING || download.state == DownloadState.QUEUED) {
            LinearProgressIndicator(
                progress = { download.progress },
                color = MOOVIE_ACCENT,
                trackColor = Color(0xFF2A2A2A),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        download.error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFFE06A6A))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (download.state) {
                DownloadState.RUNNING, DownloadState.QUEUED ->
                    MoovieButton(onClick = onPause) { Text(stringResource(Res.string.downloads_pause)) }

                DownloadState.PAUSED, DownloadState.FAILED ->
                    MoovieButton(onClick = onResume) { Text(stringResource(Res.string.downloads_resume)) }

                DownloadState.DONE -> Unit
            }
            MoovieButton(onClick = onRemove) { Text(stringResource(Res.string.downloads_remove)) }
        }
    }
}

@Composable
private fun statusOf(download: Download): String = when (download.state) {
    DownloadState.DONE -> stringResource(Res.string.downloads_ready, formatSize(download.bytes))
    DownloadState.QUEUED -> stringResource(Res.string.downloads_queued)
    DownloadState.PAUSED -> stringResource(Res.string.downloads_paused)
    DownloadState.FAILED -> stringResource(Res.string.downloads_failed)
    DownloadState.RUNNING -> "${(download.progress * 100).toInt()} % · ${formatSize(download.bytes)}"
}

/**
 * Taille lisible.
 *
 * En base 1000 et non 1024 : c'est ce qu'annoncent les fabricants de disques et
 * les réglages d'Android, et une app qui compte autrement fait douter de son
 * chiffre plutôt que de celui du système.
 */
internal fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f Go".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f Mo".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f Ko".format(bytes / 1_000.0)
    else -> "$bytes o"
}
