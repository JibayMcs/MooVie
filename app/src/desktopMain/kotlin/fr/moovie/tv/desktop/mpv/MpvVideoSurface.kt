package fr.moovie.tv.desktop.mpv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Trames du moteur mpv exposées à Compose : la vidéo est dessinée comme
 * n'importe quelle image, les contrôles sont de vrais overlays et le plein
 * écran est du Compose natif — le modèle hérité de la surface libVLC.
 *
 * Deux fils se croisent ici : le fil de rendu du moteur publie pendant que
 * Compose dessine depuis le fil AWT. L'invariant qui protège du SIGSEGV en
 * plein visionnage tient en une ligne : **chaque trame publiée a son propre
 * bitmap, figé en immuable avant d'être exposé**.
 *
 * Le tableau de pixels de [TrameVideo], lui, est **réutilisé par le moteur** :
 * il doit être recopié avant de rendre la main — c'est ce que fait
 * `installPixels`, de façon synchrone. Voir le contrat de [TrameVideo] : la
 * copie par trame côté moteur a coûté un OutOfMemoryError.
 */
internal class MpvVideoSurface {

    /** Trame courante, immuable : Compose la dessine pendant que la suivante se prépare. */
    var image: ImageBitmap? by mutableStateOf(null)
        private set

    /** Compteur de trames : lu par la composition pour forcer le redessin. */
    var frameTick: Int by mutableStateOf(0)
        private set

    /** Appelée par le fil de rendu du moteur — voir `MpvEngine.surImage`. */
    fun publie(trame: TrameVideo) {
        val info = ImageInfo(trame.largeur, trame.hauteur, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        val bmp = Bitmap()
        if (!bmp.installPixels(info, trame.pixels, trame.largeur * ROW_BYTES_PAR_PIXEL)) return
        bmp.setImmutable()
        image = bmp.asComposeImageBitmap()
        frameTick++
    }

    /** Rend l'écran au fond (fin de bande-annonce, changement de média). */
    fun efface() {
        image = null
    }

    private companion object {
        const val ROW_BYTES_PAR_PIXEL = 4
    }
}
