package fr.moovie.tv.data.pairing

import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.sync.SyncProvider
import fr.moovie.tv.data.sync.SyncProviders
import fr.moovie.tv.data.sync.SyncSettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Un réglage saisissable depuis le téléphone.
 *
 * [filled] dit qu'une valeur existe, **jamais laquelle**. La page d'appairage
 * écrit, elle ne divulgue pas : renvoyer une clé B2 en clair sur du HTTP de
 * réseau local ferait de la commodité de saisie une fuite de secret.
 */
data class PairingField(
    val id: String,
    val label: String,
    val filled: Boolean,
)

/**
 * Ce que la page d'appairage demande, et ce qu'elle en fait.
 *
 * Séparé du serveur exprès : c'est la moitié qui porte des décisions (quels
 * réglages, comment les écrire, que faire d'un champ laissé vide) et elle se
 * teste sans ouvrir de socket.
 *
 * **Les champs de synchro ne sont pas listés en dur.** On lit le descripteur du
 * fournisseur choisi, comme l'écran de réglages : ajouter WebDAV demain ajoutera
 * ses champs ici sans qu'on y touche. Ils n'apparaissent que si un fournisseur
 * est actif — proposer de saisir une clé B2 alors que la synchro est éteinte
 * demanderait un effort pour un réglage sans effet.
 */
class PairingFields(
    private val settings: SettingsRepository = SettingsRepository(),
    private val sync: SyncSettingsRepository = SyncSettingsRepository(),
) {

    /** Identifiants stables : ils voyagent jusqu'au formulaire HTML et en reviennent. */
    companion object {
        const val TMDB = "tmdb"
        const val INTRODB = "introdb"
        const val OS_USER = "os_user"
        const val OS_PASS = "os_pass"
        const val PASSPHRASE = "passphrase"

        /**
         * Préfixe des champs venant d'un descripteur de fournisseur.
         *
         * Sans lui, un fournisseur qui déclarerait un champ nommé `tmdb`
         * écraserait la clé TMDB. Le préfixe rend la collision impossible sans
         * imposer de règle de nommage aux adaptateurs.
         */
        const val SYNC_PREFIX = "sync."
    }

    /**
     * L'état courant, libellés résolus par l'appelant.
     *
     * Les libellés arrivent de l'extérieur parce qu'ils vivent dans les
     * ressources Compose, que cette couche n'a pas à connaître — même raison
     * qui garde le libellé hors de `CredentialField`.
     */
    suspend fun snapshot(labels: Map<String, String>): List<PairingField> {
        val out = mutableListOf<PairingField>()
        fun add(id: String, filled: Boolean) {
            out += PairingField(id, labels[id] ?: id, filled)
        }

        add(TMDB, settings.tmdbApiKey.first().isNotBlank())
        add(INTRODB, settings.introDbApiKey.first().isNotBlank())
        add(OS_USER, settings.osUsername.first().isNotBlank())
        add(OS_PASS, settings.osPassword.first().isNotBlank())

        val provider = sync.provider.first()
        if (provider != SyncProvider.NONE) {
            val credentials = sync.credentials.first()
            SyncProviders.descriptor(provider)?.fields?.forEach { field ->
                add(SYNC_PREFIX + field.id, credentials[field.id]?.isNotBlank() == true)
            }
            add(PASSPHRASE, sync.passphrase.first().isNotBlank())
        }
        return out
    }

    /**
     * Écrit les valeurs reçues et rend le nombre de réglages modifiés.
     *
     * **Un champ vide ne efface rien.** Le formulaire est renvoyé en entier par
     * le navigateur, y compris les champs auxquels on n'a pas touché : les
     * traiter comme des effacements viderait la clé TMDB à chaque envoi qui ne
     * la concerne pas.
     */
    suspend fun apply(values: Map<String, String>): Int {
        val given = values.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
        if (given.isEmpty()) return 0

        given[TMDB]?.let { settings.setTmdbApiKey(it) }
        given[INTRODB]?.let { settings.setIntroDbApiKey(it) }

        // Les identifiants OpenSubtitles s'écrivent d'un bloc : ne renseigner
        // que le mot de passe ne doit pas effacer le nom d'utilisateur.
        if (given.containsKey(OS_USER) || given.containsKey(OS_PASS)) {
            settings.setOsCredentials(
                username = given[OS_USER] ?: settings.osUsername.first(),
                password = given[OS_PASS] ?: settings.osPassword.first(),
                remember = settings.osRemember.first(),
            )
        }

        given[PASSPHRASE]?.let { sync.setPassphrase(it) }

        val credentials = given.filterKeys { it.startsWith(SYNC_PREFIX) }
            .mapKeys { it.key.removePrefix(SYNC_PREFIX) }
        if (credentials.isNotEmpty()) {
            // Fusion, pas remplacement : le magasin ne porte qu'une carte pour
            // tout le fournisseur, et n'envoyer que la clé d'application
            // effacerait l'identifiant de clé saisi juste avant.
            sync.setCredentials(sync.credentials.first() + credentials)
        }

        return given.size
    }
}
