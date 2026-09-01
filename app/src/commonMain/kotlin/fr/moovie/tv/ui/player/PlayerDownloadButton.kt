package fr.moovie.tv.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.download.DownloadQueue
import fr.moovie.tv.data.download.DownloadRepository
import fr.moovie.tv.data.download.DownloadState
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.player_download
import fr.moovie.tv.resources.player_download_done
import fr.moovie.tv.resources.player_download_pause
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import org.jetbrains.compose.resources.stringResource

/**
 * Télécharger le titre en cours, depuis le lecteur.
 *
 * C'est en regardant qu'on décide de garder — envoyer quelqu'un rouvrir la fiche
 * puis le panneau des sources pour y faire un appui long est un détour que la
 * découverte de la feature ne justifie pas.
 *
 * **L'avancement est sur le bouton**, en anneau autour de l'icône. Une barre
 * séparée aurait pris une ligne de chrome au-dessus d'une vidéo, et la chrome
 * du lecteur se juge à ce qu'elle cache. L'anneau ne capte pas les appuis : le
 * bouton reste le bouton, l'anneau n'est qu'un dessin par-dessus.
 */
@Composable
fun PlayerDownloadButton(mediaKey: String, onEnqueue: () -> Unit) {
    val repo = remember { DownloadRepository() }
    val downloads by repo.downloads.collectAsState(initial = emptyList())
    val download = downloads.firstOrNull { it.key == mediaKey }

    val working = download?.state == DownloadState.RUNNING || download?.state == DownloadState.QUEUED

    // Cible **carrée**. MoovieIconButton mesure 44 × 40 dp (icône de 20, plus un
    // contentPadding de 12 horizontal et 10 vertical) : un cercle centré dans ce
    // rectangle a 3 dp de jeu sur les côtés contre 1 en haut et en bas, et ne
    // peut donc pas être concentrique à l'icône. Le problème n'était pas
    // l'anneau, c'était qu'on posait un cercle sur un rectangle.
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
        MoovieIconButton(
            onClick = {
                when (download?.state) {
                    // En cours : le même bouton met en pause. Ce qui est
                    // téléchargé reste, la reprise s'en servira.
                    DownloadState.RUNNING, DownloadState.QUEUED -> DownloadQueue.pause(mediaKey)
                    // Déjà là : rien à faire. On ne propose pas de supprimer
                    // depuis le lecteur — effacer un gigaoctet ne doit pas être
                    // à un appui de l'endroit où l'on regarde.
                    DownloadState.DONE -> Unit
                    else -> onEnqueue()
                }
            },
            icon = when (download?.state) {
                DownloadState.DONE -> Icons.Default.DownloadDone
                DownloadState.RUNNING, DownloadState.QUEUED -> Icons.Default.Downloading
                else -> Icons.Default.Download
            },
            contentDescription = when (download?.state) {
                DownloadState.DONE -> stringResource(Res.string.player_download_done)
                // Ce que le bouton *fait*, pas où en est le téléchargement :
                // l'anneau porte déjà l'avancement, et cette chaîne devient
                // l'infobulle sur desktop.
                DownloadState.RUNNING, DownloadState.QUEUED ->
                    stringResource(Res.string.player_download_pause)
                else -> stringResource(Res.string.player_download)
            },
            selected = download?.state == DownloadState.DONE,
        )

        if (working) {
            // Déterminé dès qu'on connaît le nombre de segments, indéterminé
            // avant : au tout début la playlist n'est pas encore lue, et un
            // anneau figé à zéro se lit comme « bloqué » plutôt que « démarre ».
            if (download.totalSegments > 0) {
                CircularProgressIndicator(
                    progress = { download.progress },
                    color = MOOVIE_ACCENT,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                CircularProgressIndicator(
                    color = MOOVIE_ACCENT,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}
