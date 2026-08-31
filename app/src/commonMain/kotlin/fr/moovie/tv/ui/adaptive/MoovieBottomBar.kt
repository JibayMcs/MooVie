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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import fr.moovie.tv.ui.download.DownloadCountBadge
import fr.moovie.tv.ui.download.rememberActiveDownloadCount
import fr.moovie.tv.data.net.Connectivity
import fr.moovie.tv.data.download.DownloadState
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import fr.moovie.tv.ui.theme.MoovieShape
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
 * Onglets sans objet sans réseau.
 *
 * Le catalogue seul : il *est* TMDB, page par page, et rien n'en subsiste hors
 * ligne. La recherche, elle, reste — elle cherche alors dans la bibliothèque
 * locale, ce qui est exactement ce qu'on attend d'une recherche quand on n'a
 * que ses propres fichiers.
 */
private val HORS_LIGNE_MASQUES = setOf(NavTab.CATALOG)

/**
 * Vrai là où la barre doit s'effacer.
 *
 * Elle ne s'affichait que sur les six destinations de premier niveau, ce qui
 * paraît raisonnable jusqu'à ce qu'on regarde où l'on passe son temps : sur une
 * **fiche**. La barre y disparaissait, et avec elle tout repère — il fallait
 * revenir à l'accueil pour retrouver où l'on était.
 *
 * Deux écrans seulement la font disparaître, et pour la même raison : ils
 * prennent tout l'écran et ne mènent nulle part. Le lecteur, parce qu'une barre
 * d'onglets par-dessus une vidéo n'a aucun sens ; la première installation,
 * parce qu'il n'y a encore rien à naviguer.
 */
fun hidesBottomBar(screen: Screen): Boolean =
    screen is Screen.Player || screen is Screen.Onboarding

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
 *
 * **Six, et pas sept.** La télécommande y a eu son onglet, le temps de constater
 * ce que coûte un septième. Elle est passée en bouton flottant, qui n'a rien à
 * partager avec personne — voir `RemoteFab`.
 *
 * ### Des icônes seules, sans libellé
 *
 * Six parts sur les 448 dp d'un portrait font 74 dp chacune. « Téléchargements »
 * en demande le double : il s'affichait « Téléchargeme », coupé net. Un mot
 * tronqué n'aide personne — il occupe la place d'une aide sans en rendre le
 * service, et il salit une rangée qu'on lit d'un coup d'œil.
 *
 * Le libellé n'est pas perdu pour autant : il devient la description de l'icône
 * (accessibilité), et la sélection se lit sur un fond plein plutôt que sur une
 * nuance de gris.
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
        // Le même compteur que le rail de l'accueil : voir
        // rememberActiveDownloadCount. Les deux comptaient séparément, et l'un
        // des deux ne comptait pas du tout.
        val active = rememberActiveDownloadCount()
        // Hors ligne, deux onglets ne mènent nulle part : la recherche
        // interroge TMDB et le catalogue en vient tout entier. Les laisser
        // grisés serait pire que les retirer — une cible qu'on vise et qui ne
        // répond pas se lit comme une panne de l'application.
        val online by Connectivity.online.collectAsState()
        NavTab.entries.filter { online || it !in HORS_LIGNE_MASQUES }.forEach { tab ->
            NavTabItem(
                tab = tab,
                selected = sameTab(current, tab.screen),
                onClick = { onSelect(tab.screen) },
                badge = if (tab == NavTab.DOWNLOADS) active else 0,
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
    /** Compteur posé sur l'icône. Zéro = rien du tout, pas un « 0 ». */
    badge: Int = 0,
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
        // Conteneur **non découpé**, et c'est tout l'enjeu : le compteur déborde
        // volontairement de l'icône, or un enfant ne peut pas sortir d'un parent
        // sur lequel on a posé un `clip`. Le fond de sélection étant découpé, y
        // laisser le compteur le rognait — le chiffre s'y coupait en deux dès
        // que l'onglet était sélectionné. Ce n'est pas une histoire d'ordre de
        // dessin : aucun z-index ne fait sortir d'une zone de découpe.
        Box(contentAlignment = Alignment.Center) {
            // Fond de sélection. Sans libellé, la seule teinte ne suffit plus à
            // dire où l'on est : elle demande de comparer six icônes entre elles
            // pour trouver celle qui est colorée. Un fond franc se voit du coin
            // de l'œil, ce qui est l'usage qu'on fait d'une barre d'onglets.
            //
            // `MoovieShape` et non un cercle : c'est le jeton de forme de
            // l'application, celui qu'utilise déjà la sélection du rail des
            // réglages. Un `CircleShape` sur une zone plus large que haute ne
            // rend pas un rond mais un ovale, qui n'appartient à aucun autre
            // écran.
            Box(
                modifier = Modifier
                    .clip(MoovieShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else Color.Transparent,
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    // Le libellé a disparu de l'écran, pas du sens. Il devient
                    // la description de l'icône, sans quoi la barre entière
                    // serait muette pour un lecteur d'écran — six boutons sans
                    // nom, et plus aucun moyen de naviguer.
                    contentDescription = stringResource(tab.label),
                    tint = tint,
                    modifier = Modifier.size(26.dp),
                )
            }

            // Le compteur se pose **sur** l'icône plutôt qu'à côté : il ne doit
            // pas élargir l'onglet, sinon les six se décalent dès qu'un
            // téléchargement démarre — sous le pouce, au pire moment. Un
            // `offset` ne participe pas à la mesure, donc la largeur ne bouge
            // pas non plus quand le chiffre passe à deux caractères.
            // La même pastille que le rail de l'accueil : voir DownloadCountBadge.
            // Elle était dessinée ici à la main, en ovale — `CircleShape` sur une
            // boîte plus large que haute donne un stade, pas un disque.
            if (badge > 0) {
                DownloadCountBadge(
                    count = badge,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp),
                ) {}
            }
        }
    }
}
