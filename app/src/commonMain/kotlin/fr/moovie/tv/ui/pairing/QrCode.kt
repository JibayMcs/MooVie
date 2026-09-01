package fr.moovie.tv.ui.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * QR code dessiné à même le canevas Compose.
 *
 * On encode avec [Encoder] plutôt qu'avec `QRCodeWriter` pour récupérer la
 * matrice à sa taille réelle en modules — une trentaine de côté — au lieu d'une
 * image déjà mise à l'échelle. Chaque module tombe alors sur un rectangle exact,
 * et le code reste net à n'importe quelle taille d'affichage.
 *
 * **Toujours sombre sur clair, même en thème sombre.** Les lecteurs de QR
 * attendent cette polarité ; un code blanc sur fond noir ne se lit pas sur la
 * plupart des téléphones. D'où le fond blanc explicite, assumé au milieu d'une
 * interface sombre.
 *
 * La zone de silence n'est pas décorative : sans marge autour, l'appareil photo
 * ne distingue pas le motif du reste de l'écran.
 */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = 220.dp) {
    // La correction d'erreur moyenne suffit : l'écran ne se salit pas. Monter
    // en correction densifierait la matrice, donc rétrécirait les modules — à
    // taille d'écran égale, c'est plus dur à lire, pas plus robuste.
    val matrix = remember(content) { matriceQr(content) } ?: return

    Canvas(modifier.size(size)) {
        val modules = matrix.taille
        val total = modules + QUIET_ZONE * 2
        val cell = this.size.minDimension / total

        drawRect(color = Color.White, size = this.size)
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (!matrix.sombre(x, y)) continue
                drawRect(
                    color = Color.Black,
                    topLeft = Offset((x + QUIET_ZONE) * cell, (y + QUIET_ZONE) * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

/** En modules. La norme en demande quatre ; deux passent à l'écran, où le contraste est net. */
private const val QUIET_ZONE = 2

/**
 * Matrice de modules d'un QR, **sans zone de silence** : celle-ci est ajoutée au
 * dessin, et les deux encodeurs ne la produisent pas de la même façon.
 */
internal class MatriceQr(val taille: Int, private val modules: BooleanArray) {
    fun sombre(x: Int, y: Int): Boolean = modules[y * taille + x]
}

/**
 * Encode le contenu, ou rend null s'il ne tient pas dans un QR.
 *
 * `expect` plutôt qu'une bibliothèque multiplateforme : l'encodage QR est du
 * Reed-Solomon et du masquage, on ne l'écrit pas à la main, mais chaque
 * plateforme en a déjà un. La JVM garde zxing — le code d'Android et du desktop
 * ne change pas — et iOS utilise `CIQRCodeGenerator`, intégré au système. Aucune
 * dépendance ajoutée d'un côté comme de l'autre.
 *
 * La correction d'erreur est **moyenne** des deux côtés : l'écran ne se salit
 * pas. Monter en correction densifierait la matrice, donc rétrécirait les
 * modules — à taille d'écran égale, c'est plus dur à lire, pas plus robuste.
 */
internal expect fun matriceQr(contenu: String): MatriceQr?
