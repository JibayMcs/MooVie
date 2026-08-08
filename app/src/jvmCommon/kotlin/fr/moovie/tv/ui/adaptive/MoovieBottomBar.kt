package fr.moovie.tv.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Download
import fr.moovie.tv.resources.settings_cat_downloads
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.catalog_open
import fr.moovie.tv.resources.history_title
import fr.moovie.tv.resources.home_search
import fr.moovie.tv.resources.home_settings
import fr.moovie.tv.resources.nav_home
import fr.moovie.tv.ui.navigation.Screen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Destinations de premier niveau, dans l'ordre où elles apparaissent au pouce.
 *
 * L'accueil en premier et les réglages en dernier : ce sont les deux extrémités
 * naturelles. Entre les deux, l'ordre suit la fréquence d'usage — on cherche un
 * titre plus souvent qu'on ne relit son historique.
 */
private enum class NavTab(val screen: Screen, val icon: ImageVector, val label: StringResource) {
    HOME(Screen.Home, Icons.Default.Home, Res.string.nav_home),
    SEARCH(Screen.Search, Icons.Default.Search, Res.string.home_search),
    CATALOG(Screen.Catalog(), Icons.Default.GridView, Res.string.catalog_open),
    HISTORY(Screen.History, Icons.Default.History, Res.string.history_title),
    DOWNLOADS(Screen.Downloads, Icons.Default.Download, Res.string.settings_cat_downloads),
    SETTINGS(Screen.Settings, Icons.Default.Settings, Res.string.home_settings),
}

/** Vrai si [screen] est une destination de premier niveau, donc porteuse d'onglet. */
fun isTopLevel(screen: Screen): Boolean = NavTab.entries.any { sameTab(it.screen, screen) }

/**
 * Deux écrans du même onglet. Comparé par **type** et non par égalité : depuis
 * qu'il porte une famille, `Catalog(true)` et `Catalog(null)` sont deux valeurs
 * distinctes du même onglet, et l'égalité stricte éteignait la sélection dès
 * qu'on y arrivait par « En voir plus ».
 */
private fun sameTab(a: Screen, b: Screen): Boolean = a::class == b::class

/**
 * Barre de navigation basse, pour la prise en main au pouce.
 *
 * Elle remplace les icônes de l'en-tête de l'accueil, qui restent le bon choix
 * en face d'une télécommande ou d'une souris mais tombent hors de portée sur un
 * téléphone tenu à une main. Elle ne s'affiche donc que sur tactile
 * ([useBottomNav]) et seulement sur une destination de premier niveau : dans une
 * fiche, un lecteur ou l'écran d'installation, elle n'aurait rien à proposer et
 * volerait de la hauteur.
 *
 * Les cibles font 56 dp de haut et se partagent la largeur à parts égales, au-
 * dessus du seuil de 48 dp en deçà duquel une cible tactile devient difficile à
 * viser sans regarder.
 */
@Composable
fun MoovieBottomBar(
    current: Screen,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
            // Laisse passer la barre de gestes du système : sans ça le dernier
            // onglet se retrouve sous la poignée et devient intappable.
            .navigationBarsPadding()
            .heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab.entries.forEach { tab ->
            NavTabItem(
                tab = tab,
                selected = sameTab(current, tab.screen),
                onClick = { onSelect(tab.screen) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavTabItem(
    tab: NavTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF9E9E9E)
    Column(
        modifier = modifier
            .clickable(
                // Pas d'ondulation : elle déborde d'une cible aussi étroite et
                // le reste de l'app n'en utilise nulle part.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(tab.label),
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
