package fr.moovie.tv.data.sync

/**
 * Un fichier du dépôt distant, tel que le domaine le voit.
 *
 * [modifiedAt] vient du **serveur**, pas de l'appareil qui a écrit : c'est ce
 * qui permet de repérer un fichier plus frais que le nôtre sans dépendre de
 * l'horloge de celui d'en face.
 */
data class SyncFile(val name: String, val modifiedAt: Long)

/**
 * Port de stockage distant — la frontière de l'hexagone.
 *
 * **Rien de propre à un fournisseur ne doit la traverser** : ni bucket, ni
 * jeton, ni identifiant de fichier, ni code HTTP. Un adaptateur qui aurait
 * besoin d'exposer l'un des trois se trompe de découpe, et le jour où on ajoute
 * WebDAV ou S3 ce serait au domaine de s'adapter — exactement ce que cette
 * interface existe pour empêcher.
 *
 * Trois opérations suffisent parce que la synchro repose sur **un fichier par
 * appareil** : chacun n'écrit que le sien, et lit ceux des autres. Il n'y a donc
 * jamais deux écrivains sur un même fichier, et aucun verrou à demander au
 * stockage — ce qu'aucun de ces services ne sait offrir de toute façon.
 */
interface SyncStore {

    /** Les fichiers de synchro présents, le nôtre compris. */
    suspend fun list(): List<SyncFile>

    /** Contenu d'un fichier, ou null s'il a disparu entre le listage et la lecture. */
    suspend fun read(name: String): String?

    /**
     * Écrit, et rend **l'heure du serveur** au moment de l'écriture.
     *
     * Ce retour n'est pas décoratif : c'est notre horloge de référence. Chaque
     * appareil peut comparer cette valeur à la sienne, en déduire sa dérive et
     * corriger ses horodatages. Sans lui, deux appareils mal réglés éliraient la
     * mauvaise version d'une même clé — voir `HybridClock`.
     */
    suspend fun write(name: String, content: String): Long
}

/** Ce qui peut mal tourner, en termes qu'un écran sait montrer. */
enum class SyncFailure {
    /** Aucun fournisseur choisi, ou identifiants incomplets. */
    NOT_CONFIGURED,

    /** Identifiants refusés — le seul cas où l'utilisateur doit agir. */
    CREDENTIALS,

    /** Réseau injoignable : on retentera, rien à dire à l'utilisateur. */
    NETWORK,

    /** Le service a répondu autre chose que ce qu'on attendait. */
    STORE,
}

/**
 * Panne de synchro.
 *
 * Portée par le port, et non par l'adaptateur : le domaine doit pouvoir
 * distinguer « identifiants faux » de « pas de réseau » sans savoir qu'il existe
 * des codes HTTP.
 */
class SyncException(
    val failure: SyncFailure,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
