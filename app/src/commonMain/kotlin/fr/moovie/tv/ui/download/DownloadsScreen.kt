package fr.moovie.tv.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.settings_cat_downloads
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.components.MoovieIconButton
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.components.MooviePageHeader
import fr.moovie.tv.ui.theme.ESPACE_LARGE
import fr.moovie.tv.ui.theme.margePage

/**
 * Les téléchargements, en écran de plein droit.
 *
 * Ils n'existaient que dans *Réglages → Téléchargements*, à trois niveaux de
 * profondeur : le seul endroit qui disait ce qui se passait était le plus dur à
 * atteindre. Or c'est la file d'attente d'une opération longue, pas un réglage —
 * on y va pour savoir où ça en est, comme on va dans l'historique.
 *
 * Le contenu reste [DownloadsSection] : le même code sert la section des
 * réglages et cet écran, sans quoi les deux dériveraient l'un de l'autre.
 *
 * Le bouton retour ne s'affiche qu'au pouce et à la souris, comme pour
 * l'historique : sur TV la télécommande a sa touche Retour, et un bouton à
 * l'écran ne ferait que voler le focus à la première ligne.
 */
@Composable
fun DownloadsScreen(
    onPlay: (Download) -> Unit,
    onBack: () -> Unit = {},
    showBackButton: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(ESPACE_LARGE),
    ) {
        // L'en-tête commun. Cette page avait le sien : titre en `headlineSmall`
        // et rose, marge de 56 points — trois écarts par rapport à ses voisines
        // pour un bloc qui fait exactement la même chose.
        MooviePageHeader(
            titre = stringResource(Res.string.settings_cat_downloads),
            onBack = onBack.takeIf { showBackButton },
        )
        // La marge de page passe aux enfants : l'en-tête porte déjà la sienne,
        // et la section défilante a besoin de déborder dedans pour que ses
        // éléments agrandis au focus ne soient pas rognés.
        Column(modifier = Modifier.padding(horizontal = margePage())) {
            DownloadsSection(onPlay = onPlay)
        }
    }
}
