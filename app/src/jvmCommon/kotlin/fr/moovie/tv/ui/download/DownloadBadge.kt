package fr.moovie.tv.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.download.TitleDownloads
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.player_download_done
import fr.moovie.tv.resources.settings_cat_downloads
import fr.moovie.tv.ui.components.MoovieProgressBar
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import org.jetbrains.compose.resources.stringResource

/** Vert de « prêt hors ligne », le même que partout ailleurs. */
private val READY = Color(0xFF7DDC7D)

/**
 * L'état de téléchargement d'un titre, posé sur son affiche.
 *
 * Répond à une question qu'on se pose **avant** d'ouvrir une fiche : cherchant
 * un film sans réseau, on veut voir tout de suite ce qui est disponible, comme
 * on voit déjà d'un coup d'œil ce qui est vu et ce qui est en liste.
 *
 * Un seul composable pour la recherche, le catalogue et l'accueil : trois
 * implémentations d'affiche existent déjà dans ce projet, et trois pastilles
 * dessinées séparément auraient divergé à la première retouche.
 *
 * ### Deux états, jamais les deux à la fois
 *
 * En cours l'emporte sur prêt : c'est ce qui bouge, donc ce qu'on regarde. Une
 * série dont un épisode se télécharge pendant que trois sont prêts montre la
 * barre — l'icône verte reviendra quand tout sera posé.
 *
 * La pastille est **sur fond opaque** : une icône claire à même une affiche
 * disparaît dès que l'image est claire. En bas à gauche, à l'opposé du badge
 * « vu » qui occupe le haut à droite.
 */
@Composable
fun BoxScope.DownloadPosterBadge(summary: TitleDownloads?, compact: Boolean = false) {
    if (summary == null || !summary.any) return

    if (summary.active > 0) {
        MoovieProgressBar(
            progress = summary.progress,
            trackColor = Color(0x66000000),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 3.dp else 4.dp)
                .align(Alignment.BottomCenter),
        )
    }
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(if (compact) 4.dp else 6.dp)
            .background(Color(0xCC000000), CircleShape)
            .padding(if (compact) 3.dp else 4.dp),
    ) {
        Icon(
            imageVector = if (summary.active > 0) Icons.Default.Download else Icons.Default.DownloadDone,
            contentDescription = stringResource(
                if (summary.active > 0) Res.string.settings_cat_downloads
                else Res.string.player_download_done,
            ),
            tint = if (summary.active > 0) MOOVIE_ACCENT else READY,
            modifier = Modifier.size(if (compact) 12.dp else 16.dp),
        )
    }
}
