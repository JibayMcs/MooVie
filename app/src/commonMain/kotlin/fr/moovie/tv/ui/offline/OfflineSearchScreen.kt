package fr.moovie.tv.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.offline_search_help
import fr.moovie.tv.resources.search_title
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.download.DownloadsSection
import fr.moovie.tv.ui.search.SearchField
import org.jetbrains.compose.resources.stringResource

private val DIM = Color(0xFF9A9A9A)

/**
 * Chercher, quand il n'y a que ses propres fichiers.
 *
 * ### Pourquoi l'onglet reste, alors que le catalogue disparaît
 *
 * Une recherche hors ligne a un sens : on possède parfois trois saisons d'une
 * série, et retrouver l'épisode voulu dans la liste est exactement le geste
 * qu'un champ de recherche sert. Le catalogue, lui, *est* TMDB — il n'en reste
 * rien sans réseau, et son onglet s'efface. Masquer les deux aurait retiré une
 * fonction qui marche encore parfaitement, sous prétexte que sa version en
 * ligne ne marche plus.
 *
 * Le champ est **celui de la recherche en ligne** ([SearchField]) : même
 * saisie depuis le téléphone appairé, même sortie de focus à la télécommande.
 * Un second champ aurait divergé du premier au premier correctif.
 *
 * La liste, elle, est celle des téléchargements, filtrée. Rien n'est réécrit :
 * lire, mettre en pause, supprimer et reprendre restent au même endroit qu'en
 * ligne, ce qui évite qu'un écran hors ligne devienne un dialecte de l'autre.
 */
@Composable
fun OfflineSearchScreen(
    onPlay: (Download) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (useBottomNav) 20.dp else 56.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.search_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(Res.string.offline_search_help),
            style = MaterialTheme.typography.bodySmall,
            color = DIM,
        )
        SearchField(
            value = query,
            onValueChange = { query = it },
            // Rien à envoyer : le filtrage suit la frappe, il n'y a pas de
            // requête à lancer. Valider referme simplement le clavier.
            onSubmit = {},
            modifier = Modifier.fillMaxWidth(),
        )
        DownloadsSection(onPlay = onPlay, filter = query)
    }
}
