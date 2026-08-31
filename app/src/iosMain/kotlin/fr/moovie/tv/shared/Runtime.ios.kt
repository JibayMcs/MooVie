package fr.moovie.tv.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.Foundation.NSDate
import platform.Foundation.NSString
import platform.Foundation.NSUUID
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.timeIntervalSince1970
import platform.posix.size_tVar

/**
 * **`Dispatchers.IO` n'est pas utilisable ici** : kotlinx-coroutines le déclare
 * `internal` sur les cibles Apple. Je l'avais cru simplement invisible depuis le
 * commun ; la première compilation iOS a dit le contraire.
 *
 * `Dispatchers.Default` ne convient pas non plus : son pool est dimensionné sur
 * le nombre de cœurs, et l'écriture d'un film de plusieurs gigaoctets par okio
 * est un appel **bloquant**. Quelques téléchargements suffiraient à l'épuiser,
 * et tout ce qui l'utilise gèlerait derrière — exactement la panne que le
 * commentaire de `ExtractorRegistry` décrit côté JVM.
 *
 * D'où un pool dédié, ce que `Dispatchers.IO` est sur la JVM. Quatre threads :
 * le réseau passe par NSURLSession, qui est asynchrone et n'en consomme aucun ;
 * seuls les accès fichiers bloquent réellement.
 */
@OptIn(DelicateCoroutinesApi::class)
actual val dispatcherEs: CoroutineDispatcher = newFixedThreadPoolContext(4, "moovie-es")

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

/**
 * `decomposedStringWithCanonicalMapping` **est** la NFD d'Unicode, celle que
 * `Normalizer.Form.NFD` applique côté JVM — pas une approximation.
 */
@Suppress("CAST_NEVER_SUCCEEDS")
actual fun enNfd(s: String): String = (s as NSString).decomposedStringWithCanonicalMapping

/**
 * `NSUUID` rend un UUID en **majuscules** là où `java.util.UUID` le rend en
 * minuscules. La valeur sert d'identifiant opaque comparé à lui-même, mais on
 * s'aligne quand même : un identifiant qui change de casse selon la plateforme
 * finirait par se comparer à travers une synchronisation.
 */
actual fun genererUuid(): String = NSUUID().UUIDString.lowercase()

/**
 * `NSURLVolumeAvailableCapacityForImportantUsageKey` et non
 * `systemFreeSize` : sur iOS, le second annonce l'espace brut alors que le
 * système peut en libérer davantage en purgeant ce qui est reconstructible.
 * C'est la clé qu'Apple recommande précisément pour décider si un
 * téléchargement volumineux tient.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun espaceLibre(chemin: okio.Path): Long = runCatching {
    val url = platform.Foundation.NSURL.fileURLWithPath(chemin.toString())
    val valeurs = url.resourceValuesForKeys(
        listOf(platform.Foundation.NSURLVolumeAvailableCapacityForImportantUsageKey),
        null,
    )
    (valeurs?.values?.firstOrNull() as? platform.Foundation.NSNumber)?.longLongValue
        ?: Long.MAX_VALUE
}.getOrDefault(Long.MAX_VALUE)

/**
 * `NSNumberFormatter` porte le séparateur décimal de la locale, comme
 * `String.format` le fait via `Locale.getDefault()` sur la JVM.
 */
actual fun formaterDecimal(valeur: Double, decimales: Int): String {
    val formateur = platform.Foundation.NSNumberFormatter().apply {
        numberStyle = platform.Foundation.NSNumberFormatterDecimalStyle
        minimumFractionDigits = decimales.toULong()
        maximumFractionDigits = decimales.toULong()
        // Pas de séparateur de milliers : ces valeurs sont des notes et des
        // tailles déjà réduites, « 1 234,5 » y serait du bruit.
        usesGroupingSeparator = false
    }
    return formateur.stringFromNumber(platform.Foundation.NSNumber(double = valeur))
        ?: valeur.toString()
}

/**
 * `NSURLVolumeTotalCapacityKey` : la capacité du volume, celle qu'annonce
 * Réglages > Général > Stockage. Symétrique de [espaceLibre] juste au-dessus,
 * qui prend la clé « importante » plutôt que la brute — les deux décrivent le
 * même volume, chacune sous l'angle de sa question.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun espaceTotal(chemin: okio.Path): Long = runCatching {
    val url = platform.Foundation.NSURL.fileURLWithPath(chemin.toString())
    val valeurs = url.resourceValuesForKeys(
        listOf(platform.Foundation.NSURLVolumeTotalCapacityKey),
        null,
    )
    (valeurs?.values?.firstOrNull() as? platform.Foundation.NSNumber)?.longLongValue ?: 0L
}.getOrDefault(0L)

/**
 * Voir le KDoc de l'`expect` : sans réflexion, aucune fabrique générique n'est
 * possible, et aucune n'est nécessaire — les `viewModel { … }` d'iOS portent
 * tous leur constructeur.
 */
actual fun fabriqueParDefaut(): androidx.lifecycle.ViewModelProvider.Factory =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(
            modelClass: kotlin.reflect.KClass<T>,
            extras: androidx.lifecycle.viewmodel.CreationExtras,
        ): T = error(
            "Aucune fabrique par défaut sur iOS : passez le constructeur, " +
                "comme dans `viewModel { HomeViewModel() }`.",
        )
    }
