package fr.moovie.tv.data.sync

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Fournisseur choisi, identifiants, et ce que la dernière synchro a appris.
 *
 * Magasin **global** : la synchro appartient à l'installation, pas à une
 * personne. Un profil « Enfants » n'a pas ses propres identifiants B2 — mais ses
 * données voyagent, puisque le fichier porte tous les profils.
 */
class SyncSettingsRepository {

    private val store = preferencesStore("moovie_sync")
    private val json = Json { ignoreUnknownKeys = true }

    val provider: Flow<SyncProvider> = store.data.map { prefs ->
        prefs[PROVIDER]?.let { name ->
            runCatching { SyncProvider.valueOf(name) }.getOrNull()
        } ?: SyncProvider.NONE
    }

    /**
     * Les identifiants du fournisseur actif, par identifiant de champ.
     *
     * Stockés en clair, comme les clés TMDB et TheIntroDB : le dépôt est public,
     * tout chiffrement embarqué serait réversible par quiconque lit le code, et
     * une fausse sécurité vaut moins qu'une contrainte annoncée.
     */
    val credentials: Flow<Map<String, String>> = store.data.map { prefs ->
        prefs[CREDENTIALS]?.let {
            runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
        }.orEmpty()
    }

    /** Millisecondes epoch de la dernière synchro réussie. 0 = jamais. */
    val lastSyncAt: Flow<Long> = store.data.map { it[LAST_SYNC] ?: 0L }

    /**
     * Écart mesuré avec l'horloge du serveur, à ajouter à nos horodatages.
     *
     * Voir [SyncReport.clockOffset] : c'est ce qui rend la dérive d'horloge
     * inoffensive sans exiger que chaque appareil soit à l'heure.
     */
    val clockOffset: Flow<Long> = store.data.map { it[CLOCK_OFFSET] ?: 0L }

    /**
     * Identité stable de l'appareil, forgée au premier besoin.
     *
     * Elle nomme le fichier publié, donc elle doit survivre à tout sauf à une
     * réinstallation : changer d'identifiant abandonnerait l'ancien fichier sur
     * le dépôt, où il continuerait d'être lu par les autres comme s'il venait
     * d'un appareil de plus.
     */
    suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = UUID.randomUUID().toString().take(12)
        store.edit { it[DEVICE_ID] = fresh }
        return fresh
    }

    suspend fun setProvider(provider: SyncProvider) {
        store.edit { it[PROVIDER] = provider.name }
    }

    suspend fun setCredentials(values: Map<String, String>) {
        store.edit { it[CREDENTIALS] = json.encodeToString(values) }
    }

    suspend fun recordSync(at: Long, clockOffset: Long) {
        store.edit {
            it[LAST_SYNC] = at
            it[CLOCK_OFFSET] = clockOffset
        }
    }

    /** Ouvre le dépôt configuré, ou null si la synchro est éteinte ou incomplète. */
    suspend fun openStore(): SyncStore? {
        val active = provider.first()
        if (active == SyncProvider.NONE) return null
        return SyncProviders.open(active, credentials.first())
    }

    private companion object {
        val PROVIDER = stringPreferencesKey("provider")
        val CREDENTIALS = stringPreferencesKey("credentials")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LAST_SYNC = longPreferencesKey("last_sync_at")
        val CLOCK_OFFSET = longPreferencesKey("clock_offset")
    }
}
