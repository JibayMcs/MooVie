package fr.moovie.tv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

/**
 * Réception des frames libVLC (RV32/BGRA) exposées à Compose : la vidéo est
 * dessinée comme n'importe quelle image → les contrôles sont de vrais overlays
 * (pas d'interop Swing, pas de clignotement au masquage) et le plein écran est
 * natif Compose.
 *
 * **Deux threads se croisent ici** : libVLC produit les frames depuis son thread
 * vidéo pendant que Compose dessine depuis le thread AWT. Un bitmap unique
 * réutilisé entre les deux se faisait réécrire — ou carrément réallouer par un
 * changement de résolution en cours de stream adaptatif (HLS/DASH) — pendant que
 * Skia le copiait pour le dessiner : lecture hors du tampon, SIGSEGV en plein
 * visionnage. D'où les deux invariants ci-dessous, à ne pas casser :
 *
 * 1. le format et son tampon sont remplacés **d'un seul bloc** ([Frame]), jamais
 *    champ par champ : plus de `rowBytes` désaccordé avec l'`ImageInfo` ;
 * 2. chaque frame publiée a **son propre bitmap, figé en immuable** avant d'être
 *    exposée. Skia partage alors ses pixels au lieu de les recopier au dessin, et
 *    surtout plus personne ne les réécrit dans le dos du thread AWT.
 */
internal class ComposeVideoSurface {

    /**
     * Format négocié avec libVLC + tampon de réception réutilisé d'une frame à
     * l'autre. Immuable : un changement de résolution crée une nouvelle instance
     * plutôt que de modifier celle que le thread vidéo est en train de lire.
     */
    private class Frame(width: Int, height: Int) {
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        val rowBytes = width * 4
        val pixels = ByteArray(width * height * 4)
    }

    /** Publié par `getBufferFormat`, lu par `renderCallback` — threads libVLC distincts. */
    @Volatile
    private var frame: Frame? = null

    /** Frame courante, immuable : Compose la dessine pendant que libVLC prépare la suivante. */
    var image: ImageBitmap? by mutableStateOf(null)
        private set

    /** Compteur de frames : lu par la composition pour forcer le redessin. */
    var frameTick: Int by mutableStateOf(0)
        private set

    val bufferFormatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            frame = Frame(sourceWidth, sourceHeight)
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
    }

    val renderCallback = RenderCallback { _, nativeBuffers, bufferFormat ->
        val current = frame ?: return@RenderCallback
        // Le format a changé entre la négociation et cette frame : on la jette
        // plutôt que de la lire avec les mauvaises dimensions. La suivante
        // arrivera avec le tampon accordé.
        if (bufferFormat.width != current.info.width ||
            bufferFormat.height != current.info.height
        ) {
            return@RenderCallback
        }

        val src = nativeBuffers[0]
        src.rewind()
        if (src.remaining() < current.pixels.size) return@RenderCallback
        src.get(current.pixels, 0, current.pixels.size)

        // Un bitmap par frame, figé avant publication (voir le contrat plus haut).
        val bmp = Bitmap()
        if (!bmp.installPixels(current.info, current.pixels, current.rowBytes)) {
            return@RenderCallback
        }
        bmp.setImmutable()

        image = bmp.asComposeImageBitmap()
        frameTick++
    }
}
