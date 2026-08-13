package fr.moovie.tv.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.downloads_storage_free
import fr.moovie.tv.resources.downloads_storage_other
import fr.moovie.tv.ui.theme.MoovieGradient
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** Ce qu'occupe le volume qui porte les téléchargements. */
data class StorageUsage(
    /** Capacité totale du volume. 0 = inconnue : la barre ne se dessine pas. */
    val total: Long,
    /** Octets réellement disponibles pour l'application. */
    val free: Long,
    /** Ce que Moo-vie occupe, mesuré sur le disque. */
    val mine: Long,
) {
    /** Le reste : le système et les autres applications. */
    val other: Long get() = (total - free - mine).coerceAtLeast(0L)
}

/**
 * Mesure le volume qui porte [dir].
 *
 * `usableSpace` et non `freeSpace` : le second compte les blocs réservés au
 * superutilisateur, que l'application ne pourra jamais écrire. Annoncer de la
 * place qu'on n'a pas, c'est promettre un téléchargement qui échouera à la fin.
 *
 * Un volume qui ne répond pas rend 0, et la barre disparaît plutôt que de
 * dessiner un disque vide.
 */
fun storageUsage(dir: File, mine: Long): StorageUsage = runCatching {
    StorageUsage(
        total = dir.totalSpace,
        free = dir.usableSpace,
        mine = mine.coerceIn(0L, dir.totalSpace),
    )
}.getOrDefault(StorageUsage(0L, 0L, mine))

/**
 * Ce que le disque contient, d'un coup d'œil.
 *
 * ### Pourquoi trois parts et non une
 *
 * « 30,1 Go occupés » ne dit pas ce qui compte. La question qu'on se pose devant
 * cette page est « puis-je encore en télécharger un ? », et elle demande deux
 * autres nombres : ce qu'il reste, et ce que le reste de l'appareil prend. Une
 * barre de remplissage simple aurait répondu à la première en laissant croire
 * que tout l'occupé est à nous — sur un téléphone où les photos pèsent plus que
 * les films, c'est faux et ça se voit.
 *
 * La part de Moo-vie porte le dégradé de l'application, celle des autres un gris
 * neutre : la couleur dit à qui appartient quoi sans qu'il faille lire une
 * légende. Le libre est un creux, pas une couleur — c'est l'absence qui se lit.
 */
@Composable
fun StorageBar(usage: StorageUsage, modifier: Modifier = Modifier) {
    if (usage.total <= 0L) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                // Un contour, sinon le libre se confond avec le fond de l'écran
                // et la barre semble s'arrêter là où les données s'arrêtent —
                // exactement l'inverse de ce qu'elle doit montrer.
                .border(1.dp, CONTOUR, CircleShape),
        ) {
            // Les trois parts sont **dessinées**, y compris le libre : en le
            // laissant au fond de la barre, sa part ne participait pas au
            // partage de la largeur et le plancher ci-dessous n'aurait rien eu
            // à répartir.
            Part(poids(usage.mine, usage.total), MoovieGradient)
            Part(poids(usage.other, usage.total), uni(AUTRE))
            Part(poids(usage.free, usage.total), uni(LIBRE))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legende(
                couleur = AUTRE,
                texte = stringResource(
                    Res.string.downloads_storage_other,
                    formatSize(usage.other),
                ),
            )
            Legende(
                couleur = LIBRE,
                creuse = true,
                texte = stringResource(
                    Res.string.downloads_storage_free,
                    formatSize(usage.free),
                    formatSize(usage.total),
                ),
            )
        }
    }
}

@Composable
private fun RowScope.Part(poids: Float, brush: Brush) {
    if (poids <= 0f) return
    Box(
        modifier = Modifier
            .weight(poids)
            .fillMaxHeight()
            .background(brush),
    )
}

/**
 * Le poids d'une part, plancher compris.
 *
 * **Une part non nulle ne doit jamais devenir invisible.** 1,5 Go sur un disque
 * de 993 Go font 0,15 % de la largeur : moins d'un pixel, donc rien du tout à
 * l'écran — la barre affirmait alors que Moo-vie n'occupe rien, ce qui est faux
 * et décrédibilise le reste. Le plancher déforme légèrement les proportions des
 * très petites parts ; c'est le prix, et il se paie là où l'exactitude au pixel
 * n'apprenait déjà plus rien.
 *
 * `Row` normalise les poids par leur somme : ajouter au plancher d'une part
 * rétrécit les autres d'autant, sans qu'on ait à refaire la division.
 */
private fun poids(bytes: Long, total: Long): Float {
    if (bytes <= 0L || total <= 0L) return 0f
    return maxOf(bytes.toFloat() / total, PART_MINI)
}

/** Une part unie : `background` demande un pinceau, pas une couleur. */
private fun uni(couleur: Color): Brush = SolidColor(couleur)

/**
 * Largeur minimale d'une part, en fraction de la barre.
 *
 * Environ cinq pixels sur la largeur d'un téléphone en portrait : assez pour
 * qu'un liseré se voie, assez peu pour ne pas mentir sur un disque presque vide.
 */
private const val PART_MINI = 0.015f

/**
 * Une pastille et son libellé.
 *
 * [creuse] dessine un anneau au lieu d'un disque, et c'est ce qui sépare le
 * libre du reste. Deux pastilles pleines ne se distinguaient que par une nuance
 * de gris : à huit pixels de diamètre, sur un fond noir, l'œil ne fait pas la
 * différence. Un vide se montre par un vide, pas par un gris plus clair.
 */
@Composable
private fun Legende(couleur: Color, texte: String, creuse: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(couleur)
                .then(if (creuse) Modifier.border(1.dp, CONTOUR_POINT, CircleShape) else Modifier),
        )
        Text(texte, style = MaterialTheme.typography.bodySmall, color = DIM_LEGENDE)
    }
}

/**
 * Trois zones, trois registres — et non trois gris.
 *
 * La part de Moo-vie porte le dégradé chaud de l'application. Les autres données
 * prennent un **bleu ardoise franchement clair** : une teinte, pas une nuance,
 * pour qu'aucun œil n'ait à comparer deux gris voisins. Le libre est un creux
 * quasi noir, cerné d'un trait qui lui donne une frontière.
 *
 * L'écart a été mesuré à l'œil sur la capture précédente : `#4A4A52` contre
 * `#1F1F24` se lisait comme une seule barre grise un peu inégale.
 */
private val AUTRE = Color(0xFF7C8AA6)
private val LIBRE = Color(0xFF121216)
private val CONTOUR = Color(0xFF3A3A45)
private val CONTOUR_POINT = Color(0xFF6A6A78)
private val DIM_LEGENDE = Color(0xFF9A9A9A)
