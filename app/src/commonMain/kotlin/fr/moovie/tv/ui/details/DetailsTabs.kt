package fr.moovie.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.details_tab_episodes
import fr.moovie.tv.resources.details_tab_info
import fr.moovie.tv.resources.details_tab_similar
import fr.moovie.tv.resources.details_tab_trailers
import fr.moovie.tv.ui.adaptive.HeightClass
import fr.moovie.tv.ui.adaptive.LocalHeightClass
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Les onglets d'une fiche, sur grand écran.
 *
 * ## Ce qu'ils remplacent
 *
 * La fiche empilait tout : synopsis, casting, et — pour une série — la liste
 * des épisodes, avec deux icônes isolées en haut à droite (bande-annonce et
 * « en savoir plus ») pour ce qui ne rentrait nulle part. Trois façons
 * d'atteindre trois contenus de même rang, dont une invisible tant qu'on ne
 * regardait pas le coin de l'écran.
 *
 * Une barre d'onglets les met sur le même plan, à la même place, et dit du même
 * coup **ce que la fiche contient** — ce qu'un empilement ne révèle qu'à qui
 * défile jusqu'au bout.
 *
 * ## Pourquoi seulement les onglets garnis
 *
 * Un onglet qui s'ouvre sur du vide est pire que pas d'onglet : il promet
 * quelque chose et se dédit. « À voir aussi » disparaît donc quand TMDB ne
 * recommande rien, « Bandes-annonces » quand aucune n'a pu être résolue, et
 * « Épisodes » n'existe que pour les séries. La barre se réduit à ce qui est
 * réellement là.
 *
 * ## Le soulignement plutôt qu'un fond
 *
 * C'est déjà la langue du projet — les saisons, les genres du catalogue
 * marquent leur sélection ainsi. L'onglet actif passe donc simplement
 * `selected` à `MoovieButton`, qui trace le trait lui-même (`moovieSurface`).
 *
 * Un second trait dessiné ici par-dessus donnait **deux marques de sélection**
 * superposées, et trois états qui se ressemblaient tous : survolé, focalisé,
 * actif. Le thème sait déjà les distinguer — le verre et le halo pour le focus,
 * le trait seul pour la sélection — et le mieux qu'on puisse faire est de ne
 * pas le contredire.
 */
@Composable
internal fun DetailsTabs(
    onglets: List<DetailsTab>,
    actif: DetailsTab,
    onSelect: (DetailsTab) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Le focus arrive-t-il ici. Vrai sur la fiche série, où les onglets sont le
     * premier arrêt sous le hero ; ailleurs le focus reste au bouton principal.
     */
    premierFocus: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onglets.forEachIndexed { index, onglet ->
            OngletFiche(
                onglet = onglet,
                actif = onglet == actif,
                onClick = { onSelect(onglet) },
                modifier = if (index == 0) premierFocus else Modifier,
            )
        }
    }
}

/** Un onglet : son libellé, et le trait du thème quand il est celui qu'on regarde. */
@Composable
private fun OngletFiche(
    onglet: DetailsTab,
    actif: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoovieButton(onClick = onClick, modifier = modifier, selected = actif) {
        Text(
            // Capitales comme la maquette. Appliquées ici et non dans les
            // ressources : une traduction s'écrit dans sa casse normale, et
            // c'est la mise en page qui décide de crier.
            stringResource(onglet.libelle).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * Les contenus qu'une fiche peut présenter sous son hero.
 *
 * Une énumération et non des index : l'ordre de la barre change avec le titre
 * (une série a « Épisodes », un film non ; « À voir aussi » dépend de TMDB), et
 * un onglet retenu par sa position aurait désigné un autre contenu d'une fiche
 * à l'autre.
 */
internal enum class DetailsTab(val libelle: StringResource) {
    EPISODES(Res.string.details_tab_episodes),
    SIMILAIRES(Res.string.details_tab_similar),
    BANDES_ANNONCES(Res.string.details_tab_trailers),
    INFOS(Res.string.details_tab_info),
}

/**
 * La hauteur que la barre prend sous le hero, et l'amorce laissée dessous.
 *
 * Le hero s'en sert pour se dimensionner : la maquette montre l'image jusqu'au
 * bas de l'écran **et** la barre posée dessous, visible sans défiler. L'amorce,
 * elle, découvre le haut de ce que la barre ouvre — une affiche, une ligne
 * d'épisode — et c'est elle, pas la barre, qui donne envie de défiler.
 *
 * **Deux jeux de valeurs, parce que ce sont deux budgets.** Ensemble elles
 * prennent 104 dp au cadre. Sur une fenêtre de bureau haute de 1 045 dp, c'est
 * un dixième et personne ne le remarque ; sur un téléviseur 1080p, qui n'en
 * fait que 540, c'est un cinquième de l'écran — un cinquième de noir sous
 * l'image, qui la fait lire comme un bandeau posé sur un bloc. La barre reste
 * confortable à viser à la télécommande : le bouton qu'elle porte fait sa
 * propre hauteur, on ne réduit que l'air autour.
 */
@Composable
internal fun hauteurOnglets(): Dp =
    if (LocalHeightClass.current == HeightClass.EXPANDED) 64.dp else 52.dp

@Composable
internal fun amorceSousOnglets(): Dp =
    if (LocalHeightClass.current == HeightClass.EXPANDED) 40.dp else 24.dp

/** Bandeau plein pour la barre, comme la maquette : elle sort de l'image. */
@Composable
internal fun BandeauOnglets(modifier: Modifier = Modifier, contenu: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(hauteurOnglets())
            .background(Color(0xFF0A0A0A))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        contenu()
    }
}
