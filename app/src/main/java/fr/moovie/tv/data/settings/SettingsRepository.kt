package fr.moovie.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    suspend fun setTmdbApiKey(value: String) =
        context.dataStore.edit { it[TMDB_API_KEY] = value.trim() }

    suspend fun setUiLanguage(value: String) =
        context.dataStore.edit { it[UI_LANGUAGE] = value }

    suspend fun setStreamLanguage(value: StreamLanguage) =
        context.dataStore.edit { it[STREAM_LANGUAGE] = value.name }

    private companion object {
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val STREAM_LANGUAGE = stringPreferencesKey("stream_language")
    }
}
