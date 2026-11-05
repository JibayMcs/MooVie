package fr.moovie.tv.data.sources

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * La source qui a joué la dernière fois, par titre (clé de lecture).
 *
 * Reprendre un épisode relançait toute la recherche de sources, et une
 * recherche qui repart de zéro peut retenir **un autre lien** : un autre
 * hébergeur, une autre langue. Un épisode commencé en VO revenait en VF à la
 * reprise. Reprendre, c'est reprendre la même source.
 *
 * Ce qu'on garde est le lien d'embed, stable, et non le flux résolu, qui
 * expire : la reprise le re-résout. Un lien qui ne répond plus est oublié, et
 * la cascade reprend la main.
 */
class LastSourceRepository {
    private val store = preferencesStore(STORE)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(key: String): EmbedLink? {
        if (key.isBlank()) return null
        return store.data.first()[stringPreferencesKey(key)]
            ?.let { raw -> runCatching { json.decodeFromString<EmbedLink>(raw) }.getOrNull() }
    }

    suspend fun remember(key: String, link: EmbedLink) {
        if (key.isBlank()) return
        store.edit { it[stringPreferencesKey(key)] = json.encodeToString(link) }
    }

    suspend fun forget(key: String) {
        if (key.isBlank()) return
        store.edit { it.remove(stringPreferencesKey(key)) }
    }

    private companion object {
        const val STORE = "moovie_last_source"
    }
}
