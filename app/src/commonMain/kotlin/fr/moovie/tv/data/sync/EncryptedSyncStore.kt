package fr.moovie.tv.data.sync

import fr.moovie.tv.shared.Verrou
import fr.moovie.tv.shared.avec
import fr.moovie.tv.shared.chiffrerAesGcm
import fr.moovie.tv.shared.dechiffrerAesGcm
import fr.moovie.tv.shared.deriverCleAes
import fr.moovie.tv.shared.octetsAleatoires
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
@OptIn(ExperimentalEncodingApi::class)
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

    private suspend fun encrypt(plain: String): String {
        val salt = octetsAleatoires(SALT_BYTES)
        // `chiffrerAesGcm` rend `iv || chiffré || tag` : l'enveloppe reste donc
        // `sel || iv || corps`, à l'octet près ce qu'écrivait la version
        // précédente. Un appareil déjà synchronisé relit ses fichiers.
        return PREFIX + Base64.encode(salt + chiffrerAesGcm(keyFor(salt), plain.encodeToByteArray()))
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
    private suspend fun decrypt(raw: String): String? {
        if (!raw.startsWith(PREFIX)) return null
        return runCatching {
            val blob = Base64.decode(raw.removePrefix(PREFIX))
            val salt = blob.copyOfRange(0, SALT_BYTES)
            val reste = blob.copyOfRange(SALT_BYTES, blob.size)
            dechiffrerAesGcm(keyFor(salt), reste).decodeToString()
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
    private suspend fun keyFor(salt: ByteArray): ByteArray {
        val cle = passphrase to Base64.encode(salt)
        verrou.avec { keys[cle] }?.let { return it }
        // La dérivation est hors du verrou : elle prend deux cent mille
        // itérations, et la tenir bloquerait toutes les autres. Deux appels
        // concurrents sur le même sel la feront deux fois et écriront la même
        // valeur — un gaspillage rare, préférable à une TV figée.
        val derivee = deriverCleAes(passphrase, salt, ITERATIONS, KEY_BITS)
        verrou.avec { keys[cle] = derivee }
        return derivee
    }

    private companion object {
        /** Marqueur de version : un jour on changera de paramètres. */
        const val PREFIX = "MOOVIE-ENC1:"
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
        const val ITERATIONS = 210_000

        val verrou = Verrou()
        val keys = mutableMapOf<Pair<String, String>, ByteArray>()
    }
}
