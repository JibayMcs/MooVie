package fr.moovie.tv.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.posix.size_tVar

/**
 * `Dispatchers.IO` existe bien en Kotlin/Native depuis kotlinx-coroutines 1.7 —
 * il n'est simplement pas visible depuis le source set commun.
 */
actual val dispatcherEs: CoroutineDispatcher = Dispatchers.IO

actual fun maintenantMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * AES-CBC par CommonCrypto, la pile du système.
 *
 * Écrire un AES à la main aurait été vérifiable sur cette machine, ce que
 * CommonCrypto n'est pas — mais du chiffrement maison reste du chiffrement
 * maison, et l'implémentation d'Apple est auditée là où la mienne ne le serait
 * pas. La compilation de ce fichier est vérifiée par le job CI macOS.
 *
 * `kCCOptionPKCS7Padding` couvre PKCS#5 : les deux ne diffèrent que par la
 * taille de bloc qu'ils autorisent, identique ici.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun dechiffrerAesCbc(donnees: ByteArray, cle: ByteArray, iv: ByteArray): ByteArray? {
    if (donnees.isEmpty()) return null
    // Le déchiffrement retire le remplissage, la sortie est donc au plus de la
    // taille de l'entrée. Le bloc supplémentaire est la marge que réclame
    // CCCrypt pour ne pas rendre `kCCBufferTooSmall`.
    val sortie = ByteArray(donnees.size + kCCBlockSizeAES128.toInt())
    return memScoped {
        val ecrits = alloc<size_tVar>()
        val statut = CCCrypt(
            kCCDecrypt,
            kCCAlgorithmAES,
            kCCOptionPKCS7Padding,
            cle.refTo(0),
            cle.size.convert(),
            iv.refTo(0),
            donnees.refTo(0),
            donnees.size.convert(),
            sortie.refTo(0),
            sortie.size.convert(),
            ecrits.ptr,
        )
        // Une clé qui ne correspond pas se manifeste par un remplissage
        // invalide, donc par un statut d'erreur — pas par des octets aléatoires.
        if (statut.toInt() != kCCSuccess) null else sortie.copyOf(ecrits.value.toInt())
    }
}
