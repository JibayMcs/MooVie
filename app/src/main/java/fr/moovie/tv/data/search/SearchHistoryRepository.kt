package fr.moovie.tv.data.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchStore: DataStore<Preferences> by preferencesDataStore(name = "moovie_search")

/**
 * Historique de recherche persistant : liste des dernières requêtes, plus récente
 * en tête, dédupliquée (insensible à la casse), plafonnée à [MAX].
 */
class SearchHistoryRepository(private val context: Context) {

    val history: Flow<List<String>> =
        context.searchStore.data.map { parse(it[KEY]) }

    suspend fun add(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        context.searchStore.edit { prefs ->
            val current = parse(prefs[KEY])
            val updated = (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
            prefs[KEY] = updated.joinToString(SEP)
        }
    }

    suspend fun remove(query: String) {
        context.searchStore.edit { prefs ->
            prefs[KEY] = parse(prefs[KEY]).filterNot { it.equals(query, ignoreCase = true) }.joinToString(SEP)
        }
    }

    suspend fun clear() {
        context.searchStore.edit { it.remove(KEY) }
    }

    private fun parse(raw: String?): List<String> =
        raw?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private companion object {
        val KEY = stringPreferencesKey("search_history")
        const val SEP = "\n"
        const val MAX = 12
    }
}
