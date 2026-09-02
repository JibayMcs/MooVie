package fr.moovie.tv.ui.download

import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.download.DownloadRepository
import fr.moovie.tv.data.download.DownloadState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.offset
import fr.moovie.tv.data.download.TitleDownloads
import fr.moovie.tv.data.download.byTitle
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.player_download_done
import fr.moovie.tv.resources.settings_cat_downloads
import fr.moovie.tv.ui.components.MoovieProgressBar
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.theme.MOOVIE_READY

/** Vert de « prêt hors ligne », le même que partout ailleurs. */
private val READY = MOOVIE_READY

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
fun BoxScope.DownloadPosterBadge(
    summary: TitleDownloads?,
    compact: Boolean = false,
    /**
     * Faux quand le bas de la vignette porte déjà une barre — celle de la
     * lecture en cours, sur une carte « Reprendre ». Deux barres empilées au
     * même endroit ne se lisent plus ni l'une ni l'autre ; l'icône suffit à
     * dire que le titre est là.
     */
    bar: Boolean = true,
) {
    if (summary == null || !summary.any) return

    if (bar && summary.active > 0) {
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

/**
 * Résumé des téléchargements par titre, à disposition des cartes d'un écran.
 *
 * L'accueil empile écran → rangée → carte, et chacune de ses trois sortes de
 * cartes est au bout d'un chemin différent. Faire descendre la table par les
 * signatures demandait huit paramètres traversants pour une donnée dont aucun
 * niveau intermédiaire n'a que faire. La recherche et le catalogue, eux, sont
 * une grille plate : ils gardent le passage direct.
 *
 * Vide par défaut : une carte sortie de tout contexte n'affiche simplement
 * aucune pastille.
 */
val LocalTitleDownloads = compositionLocalOf<Map<String, TitleDownloads>> { emptyMap() }

/**
 * Abonne l'écran au dépôt **une fois** et met le résumé à disposition de ses
 * cartes. Le faire carte par carte rouvrirait le flux par affiche à l'écran.
 */
@Composable
fun ProvideTitleDownloads(content: @Composable () -> Unit) {
    val downloads by remember { DownloadRepository().downloads }.collectAsState(initial = emptyList())
    val byTitle = remember(downloads) { downloads.byTitle() }
    CompositionLocalProvider(LocalTitleDownloads provides byTitle, content = content)
}

/** Pastille d'une carte qui connaît sa clé de titre mais pas son résumé. */
@Composable
fun BoxScope.DownloadPosterBadge(mediaKey: String, compact: Boolean = false, bar: Boolean = true) {
    DownloadPosterBadge(LocalTitleDownloads.current[mediaKey], compact, bar)
}

/**
 * Combien de téléchargements travaillent ou attendent leur tour.
 *
 * Un seul point de vérité pour les deux endroits qui l'affichent — la barre
 * basse du téléphone et le rail de l'accueil. Ils comptaient chacun de leur
 * côté, et le rail ne comptait pas du tout : l'icône y était muette alors que
 * la même icône, sur la même application, portait une pastille au pouce.
 */
@Composable
fun rememberActiveDownloadCount(): Int {
    val downloads by remember { DownloadRepository().downloads }
        .collectAsState(initial = emptyList())
    return downloads.count {
        it.state == DownloadState.RUNNING || it.state == DownloadState.QUEUED
    }
}

/**
 * Pose le compteur sur un contenu, en débordant de son coin haut-droit.
 *
 * Le conteneur ne doit **pas** être découpé : le chiffre sort volontairement de
 * l'icône, et aucun ordre de dessin ne fait sortir d'une zone de `clip`. C'est
 * la leçon déjà payée par la barre basse, où le compteur se coupait en deux dès
 * que l'onglet était sélectionné.
 */
@Composable
fun DownloadCountBadge(count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        content()
        if (count > 0) CountPill(
            count = count,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp),
        )
    }
}

/**
 * Le compteur lui-même : un disque, pas une pilule.
 *
 * ### Ce qui le rendait ovale
 *
 * `CircleShape` ne dessine un cercle que sur un carré — c'est un coin arrondi à
 * 50 %, pas une forme fixe. Avec pour seule taille un chiffre et une marge
 * horizontale, la boîte était plus large que haute et la forme s'étirait en
 * stade. Le plancher carré ([TAILLE]) rend au chiffre unique son cercle ; à deux
 * chiffres la pastille s'allonge, et c'est alors la bonne réponse.
 *
 * ### Le chiffre, lisible
 *
 * Pas de style hérité : `labelSmall` apporte son interligne et son espacement
 * de caractères, tous deux calculés pour une ligne de texte et non pour un
 * glyphe seul au centre d'un disque de seize pixels — le chiffre s'y retrouvait
 * décentré et fin. Taille, graisse et interligne sont donc posées ici, et
 * l'interligne est calée sur la taille pour que le centrage de la boîte tombe
 * juste.
 *
 * Pas de contour non plus : sur un fond sombre, le disque rouge se détache
 * déjà, et l'anneau noir ne faisait qu'épaissir un objet de seize pixels.
 */
@Composable
private fun CountPill(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = TAILLE, minHeight = TAILLE)
            .background(MOOVIE_ACCENT, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Diamètre du disque à un chiffre. En deçà, le chiffre touche le bord. */
private val TAILLE = 18.dp
