package fr.moovie.tv.data.search

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Historique de recherche persistant : liste des dernières requêtes, plus récente
 * en tête, dédupliquée (insensible à la casse), plafonnée à [MAX].
 */
class SearchHistoryRepository {

    private val store = preferencesStore("moovie_search")

    val history: Flow<List<String>> =
        store.data.map { parse(it[KEY]) }

    suspend fun add(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        store.edit { prefs ->
            val current = parse(prefs[KEY])
            val updated = (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
            prefs[KEY] = updated.joinToString(SEP)
        }
    }

    suspend fun remove(query: String) {
        store.edit { prefs ->
            prefs[KEY] = parse(prefs[KEY]).filterNot { it.equals(query, ignoreCase = true) }.joinToString(SEP)
        }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    private fun parse(raw: String?): List<String> =
        raw?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private companion object {
        val KEY = stringPreferencesKey("search_history")
        const val SEP = "\n"
        const val MAX = 12
    }
}
