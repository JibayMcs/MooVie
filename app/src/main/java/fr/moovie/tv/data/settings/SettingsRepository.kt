package fr.moovie.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.moovie.tv.data.net.DohProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moovie_settings")

/** Langue de stream préférée (piste audio par défaut). */
enum class StreamLanguage { VF, VOSTFR, VO }

/**
 * Réglages utilisateur persistés. Tout ce qui doit être saisi/choisi par
 * l'utilisateur (clé TMDB, langue de stream, langue d'UI…) passe par ici.
 * À étendre par catégorie au fur et à mesure (sources, lecture, interface).
 */
class SettingsRepository(private val context: Context) {

    val tmdbApiKey: Flow<String> =
        context.dataStore.data.map { it[TMDB_API_KEY].orEmpty() }

    val uiLanguage: Flow<String> =
        context.dataStore.data.map { it[UI_LANGUAGE] ?: "fr-FR" }

    val streamLanguage: Flow<StreamLanguage> =
        context.dataStore.data.map {
            runCatching { StreamLanguage.valueOf(it[STREAM_LANGUAGE] ?: "VF") }
                .getOrDefault(StreamLanguage.VF)
        }

    /** Providers désactivés par l'utilisateur (par défaut : aucun). */
    val disabledProviders: Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            prefs[DISABLED_PROVIDERS]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }

    /**
     * Ordre de priorité des providers (les premiers sont joués en premier).
     * Les providers absents de la liste passent en fin, dans l'ordre du registre.
     */
    val providerOrder: Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[PROVIDER_ORDER]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        }

    /** DoH activé (par défaut oui : nécessaire au contournement du blocage FAI). */
    val dohEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[DOH_ENABLED] ?: true }

    /** Résolveur DoH choisi (par défaut Cloudflare). */
    val dohProvider: Flow<DohProvider> =
        context.dataStore.data.map {
            runCatching { DohProvider.valueOf(it[DOH_PROVIDER] ?: "CLOUDFLARE") }
                .getOrDefault(DohProvider.CLOUDFLARE)
        }

    suspend fun setDohEnabled(value: Boolean) =
        context.dataStore.edit { it[DOH_ENABLED] = value }

    suspend fun setDohProvider(value: DohProvider) =
        context.dataStore.edit { it[DOH_PROVIDER] = value.name }

    suspend fun setTmdbApiKey(value: String) =
        context.dataStore.edit { it[TMDB_API_KEY] = value.trim() }

    suspend fun setUiLanguage(value: String) =
        context.dataStore.edit { it[UI_LANGUAGE] = value }

    suspend fun setStreamLanguage(value: StreamLanguage) =
        context.dataStore.edit { it[STREAM_LANGUAGE] = value.name }

    suspend fun setProviderEnabled(name: String, enabled: Boolean) =
        context.dataStore.edit { prefs ->
            val current = prefs[DISABLED_PROVIDERS]?.split(',')?.filter { it.isNotBlank() }?.toMutableSet()
                ?: mutableSetOf()
            if (enabled) current.remove(name) else current.add(name)
            prefs[DISABLED_PROVIDERS] = current.joinToString(",")
        }

    suspend fun setProviderOrder(order: List<String>) =
        context.dataStore.edit { it[PROVIDER_ORDER] = order.joinToString(",") }

    private companion object {
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val STREAM_LANGUAGE = stringPreferencesKey("stream_language")
        val DISABLED_PROVIDERS = stringPreferencesKey("disabled_providers")
        val PROVIDER_ORDER = stringPreferencesKey("provider_order")
        val DOH_ENABLED = booleanPreferencesKey("doh_enabled")
        val DOH_PROVIDER = stringPreferencesKey("doh_provider")
    }
}
