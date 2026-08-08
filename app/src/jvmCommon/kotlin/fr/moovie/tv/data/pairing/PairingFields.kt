package fr.moovie.tv.data.pairing

import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.sync.SyncProvider
import fr.moovie.tv.data.sync.SyncProviders
import fr.moovie.tv.data.sync.SyncSettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Un réglage saisissable depuis le téléphone.
 *
 * [value] porte la valeur **actuellement en place sur le téléviseur**, pour que
 * la page soit pré-remplie.
 *
 * Un premier jet ne renvoyait qu'un « déjà renseigné », par prudence. C'était
 * une prudence de façade : l'écran de réglages affiche ces mêmes valeurs en
 * clair sur le téléviseur, œil de révélation à l'appui. Les masquer sur le
 * téléphone pendant qu'elles sont lisibles à trois mètres ne protégeait rien et
 * empêchait le geste le plus courant — relire une clé pour vérifier, ou en
 * corriger un caractère sans la retaper en entier.
 */
data class PairingField(
    val id: String,
    val label: String,
    /**
     * Titre de la section qui porte le champ, déjà résolu.
     *
     * Sans lui, « Identifiant » et « Mot de passe » flottent seuls au milieu du
     * formulaire : rien ne dit de quel service ils sont les identifiants, et
     * deux champs nommés `b2_key_id` ne se rattachent à rien de visible.
     */
    val group: String,
    val value: String,
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

        /** Sections du formulaire, dans l'ordre où elles sont servies. */
        const val GROUP_API = "api"
        const val GROUP_SUBTITLES = "subtitles"
        const val GROUP_SYNC = "sync"
    }

    /**
     * L'état courant, libellés résolus par l'appelant.
     *
     * Les libellés arrivent de l'extérieur parce qu'ils vivent dans les
     * ressources Compose, que cette couche n'a pas à connaître — même raison
     * qui garde le libellé hors de `CredentialField`.
     */
    suspend fun snapshot(
        labels: Map<String, String>,
        groups: Map<String, String>,
    ): List<PairingField> {
        val out = mutableListOf<PairingField>()
        fun add(group: String, id: String, value: String) {
            out += PairingField(id, labels[id] ?: id, groups[group] ?: group, value)
        }

        add(GROUP_API, TMDB, settings.tmdbApiKey.first())
        add(GROUP_API, INTRODB, settings.introDbApiKey.first())
        add(GROUP_SUBTITLES, OS_USER, settings.osUsername.first())
        add(GROUP_SUBTITLES, OS_PASS, settings.osPassword.first())

        val provider = sync.provider.first()
        if (provider != SyncProvider.NONE) {
            val credentials = sync.credentials.first()
            SyncProviders.descriptor(provider)?.fields?.forEach { field ->
                add(GROUP_SYNC, SYNC_PREFIX + field.id, credentials[field.id].orEmpty())
            }
            add(GROUP_SYNC, PASSPHRASE, sync.passphrase.first())
        }
        return out
    }

    /**
     * Écrit les valeurs reçues et rend le nombre de réglages **réellement**
     * modifiés.
     *
     * Le formulaire étant pré-rempli, ce qu'on voit est ce qui sera enregistré :
     * un champ **présent et vidé est un effacement**, voulu et explicite. C'est
     * ce que la version précédente ne savait pas faire — un champ vide y voulait
     * dire « ne touche à rien », si bien que retirer une clé obligeait à revenir
     * à la télécommande.
     *
     * Un champ **absent** reste intouché : les champs de synchro ne sont pas
     * servis quand aucun fournisseur n'est choisi, et leur absence ne doit pas
     * valoir effacement.
     *
     * On compare avant d'écrire pour ne compter que les vrais changements :
     * renvoyer le formulaire sans rien toucher annoncerait sinon sept réglages
     * enregistrés sur le téléviseur, ce qui est faux et inquiétant.
     */
    suspend fun apply(values: Map<String, String>): Int {
        val given = values.mapValues { it.value.trim() }
        if (given.isEmpty()) return 0
        var changed = 0

        given[TMDB]?.let { v ->
            if (v != settings.tmdbApiKey.first()) { settings.setTmdbApiKey(v); changed++ }
        }
        given[INTRODB]?.let { v ->
            if (v != settings.introDbApiKey.first()) { settings.setIntroDbApiKey(v); changed++ }
        }

        // Les identifiants OpenSubtitles s'écrivent d'un bloc : n'en renvoyer
        // qu'un ne doit pas effacer l'autre.
        if (given.containsKey(OS_USER) || given.containsKey(OS_PASS)) {
            val user = settings.osUsername.first()
            val pass = settings.osPassword.first()
            val newUser = given[OS_USER] ?: user
            val newPass = given[OS_PASS] ?: pass
            if (newUser != user || newPass != pass) {
                settings.setOsCredentials(newUser, newPass, settings.osRemember.first())
                if (newUser != user) changed++
                if (newPass != pass) changed++
            }
        }

        given[PASSPHRASE]?.let { v ->
            if (v != sync.passphrase.first()) { sync.setPassphrase(v); changed++ }
        }

        val credentials = given.filterKeys { it.startsWith(SYNC_PREFIX) }
            .mapKeys { it.key.removePrefix(SYNC_PREFIX) }
        if (credentials.isNotEmpty()) {
            // Fusion, pas remplacement : le magasin ne porte qu'une carte pour
            // tout le fournisseur, et n'écrire que les champs reçus laisse
            // intact ce qu'un autre fournisseur y aurait déposé.
            val current = sync.credentials.first()
            val diff = credentials.count { (id, v) -> current[id].orEmpty() != v }
            if (diff > 0) { sync.setCredentials(current + credentials); changed += diff }
        }

        return changed
    }
}
