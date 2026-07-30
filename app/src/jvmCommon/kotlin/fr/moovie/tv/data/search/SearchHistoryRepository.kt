package fr.moovie.tv.data.search

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Dernier genre exploré : type de contenu + identifiant TMDB du genre. */
data class ExploreChoice(val isTv: Boolean, val genreId: Int)

/**
 * Historique de recherche persistant : liste des dernières requêtes, plus récente
 * en tête, dédupliquée (insensible à la casse), plafonnée à [MAX].
 *
 * Porte aussi le dernier genre exploré : la page de recherche est également la
 * page « explorer », et on la rouvre là où on l'avait laissée.
 */
class SearchHistoryRepository {

    private val store = preferencesStore("moovie_search")

    val history: Flow<List<String>> =
        store.data.map { parse(it[KEY]) }

    /** Dernier genre exploré, ou null si aucun n'a encore été choisi. */
    val lastExplore: Flow<ExploreChoice?> =
        store.data.map { prefs ->
            // Format "tv:18" / "movie:18" : deux valeurs dans une clé, plutôt
            // qu'une préférence par champ pour un couple qui va toujours ensemble.
            val parts = prefs[EXPLORE_KEY]?.split(":") ?: return@map null
            val id = parts.getOrNull(1)?.toIntOrNull() ?: return@map null
            ExploreChoice(isTv = parts.firstOrNull() == "tv", genreId = id)
        }

    /** Mémorise (ou oublie, avec null) le genre en cours d'exploration. */
    suspend fun setLastExplore(choice: ExploreChoice?) {
        store.edit { prefs ->
            if (choice == null) {
                prefs.remove(EXPLORE_KEY)
            } else {
                prefs[EXPLORE_KEY] = "${if (choice.isTv) "tv" else "movie"}:${choice.genreId}"
            }
        }
    }

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
        val EXPLORE_KEY = stringPreferencesKey("explore_last_genre")
        const val SEP = "\n"
        const val MAX = 12
    }
}
