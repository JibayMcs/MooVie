package fr.moovie.tv.ui.pairing

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * On encode avec [Encoder] plutôt qu'avec `QRCodeWriter` pour récupérer la
 * matrice à sa taille réelle en modules — une trentaine de côté — au lieu d'une
 * image déjà mise à l'échelle. Chaque module tombe alors sur un rectangle
 * exact, et le code reste net à n'importe quelle taille d'affichage.
 *
 * C'est exactement l'appel d'avant le portage : Android et desktop produisent
 * le même QR qu'hier.
 */
internal actual fun matriceQr(contenu: String): MatriceQr? = runCatching {
    val matrice = Encoder.encode(contenu, ErrorCorrectionLevel.M).matrix
    val taille = matrice.width
    val modules = BooleanArray(taille * taille) { i ->
        matrice.get(i % taille, i / taille).toInt() == 1
    }
    MatriceQr(taille, modules)
}.getOrNull()
