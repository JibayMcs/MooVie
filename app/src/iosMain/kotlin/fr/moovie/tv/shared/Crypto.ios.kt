package fr.moovie.tv.shared

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA1
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * `CryptographyProvider.Default` résout vers le provider présent — ici celui
 * d'Apple, seule dépendance déclarée pour ce source set. Il s'adosse à
 * CommonCrypto et CryptoKit, donc à du code audité par Apple.
 */
private val fournisseur = CryptographyProvider.Default

actual fun octetsAleatoires(taille: Int): ByteArray =
    CryptographyRandom.nextBytes(taille)

actual suspend fun deriverCleAes(
    phrase: String,
    sel: ByteArray,
    iterations: Int,
    bitsCle: Int,
): ByteArray = fournisseur.get(PBKDF2).secretDerivation(
    digest = SHA256,
    iterations = iterations,
    outputSize = (bitsCle / 8).bytes,
    salt = sel,
).deriveSecretToByteArray(phrase.encodeToByteArray())

/**
 * `encrypt` de cryptography-kotlin tire son propre IV et le place **en tête**
 * du résultat, suivi du chiffré et du tag — la disposition qu'écrit aussi la
 * version JVM. Les deux plateformes produisent donc le même format, ce dont
 * dépend la lecture croisée d'un même dépôt.
 */
actual suspend fun chiffrerAesGcm(cle: ByteArray, clair: ByteArray): ByteArray =
    fournisseur.get(AES.GCM)
        .keyDecoder()
        .decodeFromByteArray(AES.Key.Format.RAW, cle)
        .cipher()
        .encrypt(clair)

actual suspend fun dechiffrerAesGcm(cle: ByteArray, blob: ByteArray): ByteArray =
    fournisseur.get(AES.GCM)
        .keyDecoder()
        .decodeFromByteArray(AES.Key.Format.RAW, cle)
        .cipher()
        .decrypt(blob)

/**
 * SHA-1 est marqué « API délicate » par la bibliothèque, à raison : il ne vaut
 * plus rien contre les collisions. Son usage ici n'est pas de la sécurité — B2
 * l'impose comme somme de contrôle d'intégrité d'un envoi, et c'est le
 * protocole qui décide, pas nous.
 */
@OptIn(DelicateCryptographyApi::class)
actual fun sha1(donnees: ByteArray): ByteArray =
    fournisseur.get(SHA1).hasher().hashBlocking(donnees)
