package fr.moovie.tv.ui.pairing

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.NSISOLatin1StringEncoding
import platform.posix.uint8_tVar

/**
 * Encodage par `CIQRCodeGenerator`, le générateur de Core Image.
 *
 * Le filtre rend une image dont **un module vaut un pixel**, ce qui est
 * exactement ce qu'il faut : on relit les pixels pour reconstituer la matrice,
 * et le dessin Compose commun s'occupe de la mise à l'échelle. Passer par
 * l'image redimensionnée aurait donné des modules flous aux bords.
 *
 * `NSISOLatin1StringEncoding` et non UTF-8 : c'est l'encodage que le filtre
 * attend pour son `inputMessage`, et le contenu encodé ici — une URL
 * d'appairage — est de l'ASCII.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun matriceQr(contenu: String): MatriceQr? {
    val donnees: NSData = (contenu as NSString)
        .dataUsingEncoding(NSISOLatin1StringEncoding) ?: return null

    val filtre = CIFilter.filterWithName("CIQRCodeGenerator") ?: return null
    filtre.setValue(donnees, forKey = "inputMessage")
    // « M » : correction moyenne, le même niveau que zxing côté JVM.
    filtre.setValue("M", forKey = "inputCorrectionLevel")
    val sortie: CIImage = filtre.outputImage ?: return null

    val cgImage = CIContext().createCGImage(sortie, sortie.extent) ?: return null
    val largeur = CGImageGetWidth(cgImage).toInt()
    val hauteur = CGImageGetHeight(cgImage).toInt()
    if (largeur <= 0 || largeur != hauteur) return null

    return memScoped {
        // Un octet par pixel en niveaux de gris : on ne cherche qu'à distinguer
        // sombre de clair.
        val tampon = allocArray<uint8_tVar>(largeur * hauteur)
        val contexte = CGBitmapContextCreate(
            data = tampon,
            width = largeur.toULong(),
            height = hauteur.toULong(),
            bitsPerComponent = 8uL,
            bytesPerRow = largeur.toULong(),
            space = CGColorSpaceCreateDeviceGray(),
            bitmapInfo = 0u,
        ) ?: return@memScoped null
        CGContextDrawImage(
            contexte,
            CGRectMake(0.0, 0.0, largeur.toDouble(), hauteur.toDouble()),
            cgImage,
        )

        val sombre = { x: Int, y: Int -> tampon[y * largeur + x].toInt() < 128 }

        // Core Image ajoute sa propre zone de silence, que le dessin commun
        // ajoute déjà de son côté. On la retire, sans quoi les modules seraient
        // deux fois plus petits que nécessaire — un QR plus dur à lire pour
        // rien.
        var marge = 0
        while (marge < largeur / 2) {
            var vide = true
            for (i in marge until largeur - marge) {
                if (sombre(i, marge) || sombre(marge, i) ||
                    sombre(i, largeur - 1 - marge) || sombre(largeur - 1 - marge, i)
                ) {
                    vide = false
                    break
                }
            }
            if (!vide) break
            marge++
        }

        val taille = largeur - marge * 2
        if (taille <= 0) return@memScoped null
        MatriceQr(
            taille = taille,
            modules = BooleanArray(taille * taille) { i ->
                sombre(marge + i % taille, marge + i / taille)
            },
        )
    }
}
