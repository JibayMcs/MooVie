package fr.moovie.tv.data.sync

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffre ce qui part sur le dépôt, avec une phrase de passe.
 *
 * **Un décorateur du port, pas une option du moteur ni de l'adaptateur.** Dans
 * le moteur, ce serait une préoccupation de transport mêlée à la fusion ; dans
 * l'adaptateur, chaque fournisseur ajouté devrait la réimplémenter. Ici, B2
 * WebDAV et tout ce qui viendra en héritent sans le savoir.
 *
 * Ce que ça protège : l'historique de visionnage part sur le disque d'un tiers.
 * Ce que ça ne protège pas : les **noms de fichiers**, qui restent en clair et
 * révèlent le nombre d'appareils. Les chiffrer empêcherait de lister.
 *
 * **Perdre la phrase, c'est perdre les fichiers.** Il n'y a pas de récupération,
 * et c'est le prix d'un chiffrement que nous ne pouvons pas défaire non plus.
 * Les données locales, elles, sont intactes : seul le dépôt devient illisible.
 */
internal class EncryptedSyncStore(
    private val delegate: SyncStore,
    private val passphrase: String,
) : SyncStore {

    override suspend fun list(): List<SyncFile> = delegate.list()

    override suspend fun read(name: String): String? {
        val raw = delegate.read(name) ?: return null
        return decrypt(raw)
    }

    override suspend fun write(name: String, content: String): Long =
        delegate.write(name, encrypt(content))

    private fun encrypt(plain: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keyFor(salt), GCMParameterSpec(TAG_BITS, iv))
        }
        val body = cipher.doFinal(plain.toByteArray())
        return PREFIX + encoder.encodeToString(salt + iv + body)
    }

    /**
     * Rend null sur un fichier qu'on ne sait pas ouvrir, au lieu de lever.
     *
     * Trois cas mènent là, et aucun ne doit interrompre la synchro : un fichier
     * écrit en clair par un appareil sans phrase, un fichier chiffré avec une
     * autre phrase, ou un contenu abîmé. Le moteur passe alors au fichier
     * suivant — mieux vaut synchroniser avec deux appareils sur trois que pas du
     * tout.
     */
    private fun decrypt(raw: String): String? {
        if (!raw.startsWith(PREFIX)) return null
        return runCatching {
            val blob = Base64.getDecoder().decode(raw.removePrefix(PREFIX))
            val salt = blob.copyOfRange(0, SALT_BYTES)
            val iv = blob.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
            val body = blob.copyOfRange(SALT_BYTES + IV_BYTES, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, keyFor(salt), GCMParameterSpec(TAG_BITS, iv))
            }
            String(cipher.doFinal(body))
        }.getOrNull()
    }

    /**
     * Dérive la clé, et la retient par **(phrase, sel)**.
     *
     * La dérivation coûte volontairement cher — c'est ce qui rend une phrase de
     * passe humaine difficile à casser. La refaire à chaque fichier et à chaque
     * synchro rendrait une TV modeste très lente ; le sel voyageant dans
     * l'enveloppe, un même appareil distant retombe toujours sur le sien.
     *
     * **La phrase fait partie de la clé de cache**, et ce n'est pas cosmétique.
     * N'indexer que sur le sel paraissait suffisant, puisque c'est lui qui varie
     * d'un fichier à l'autre — mais le cache est statique : deux phrases
     * différentes tombaient sur la même entrée, et une **mauvaise phrase
     * déchiffrait**. C'est un test qui l'a dit, pas une relecture.
     */
    private fun keyFor(salt: ByteArray) = keys.getOrPut(passphrase to encoder.encodeToString(salt)) {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
    }

    private companion object {
        /** Marqueur de version : un jour on changera de paramètres. */
        const val PREFIX = "MOOVIE-ENC1:"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KDF = "PBKDF2WithHmacSHA256"
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val ITERATIONS = 210_000

        val random = SecureRandom()
        val encoder: Base64.Encoder = Base64.getEncoder()
        val keys = ConcurrentHashMap<Pair<String, String>, SecretKeySpec>()
    }
}
