package fr.moovie.tv.data.search

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.STORE_SEARCH_FILTERS
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Les filtres de recherche, retenus d'une recherche à l'autre.
 *
 * C'est la moitié de la fonctionnalité : quelqu'un qui ne veut que des films
 * bien notés le veut à chaque recherche, pas une fois. Les reposer à chaque
 * ouverture reviendrait à ne pas les proposer.
 *
 * Magasin séparé de l'historique et des réglages : ce sont des préférences
 * d'affichage propres à un écran, et le magasin de réglages porte déjà les
 * clés d'API et le réseau. Il suit le profil actif comme les autres — chaque
 * profil a ses habitudes de recherche.
 */
class SearchFiltersRepository(storeName: String = STORE_SEARCH_FILTERS) {

    /**
     * Le magasin est un paramètre : la recherche et le catalogue partagent le
     * même modèle de filtres et la même mécanique, mais pas les mêmes valeurs.
     * Dupliquer ce dépôt pour changer une constante aurait garanti que les deux
     * copies divergent à la première correction.
     */
    private val store = preferencesStore(storeName)

    val filters: Flow<SearchFilters> = store.data.map { prefs ->
        SearchFilters(
            // Chaque valeur retombe sur son défaut si elle est illisible : une
            // énumération renommée ne doit pas empêcher la recherche de
            // s'ouvrir.
            sortBy = runCatching { SortBy.valueOf(prefs[SORT_BY] ?: "") }
                .getOrDefault(SearchFilters.DEFAULT.sortBy),
            ascending = prefs[ASCENDING] ?: SearchFilters.DEFAULT.ascending,
            media = runCatching { MediaFilter.valueOf(prefs[MEDIA] ?: "") }
                .getOrDefault(SearchFilters.DEFAULT.media),
            minRating = prefs[MIN_RATING] ?: SearchFilters.DEFAULT.minRating,
            minYear = prefs[MIN_YEAR],
            maxYear = prefs[MAX_YEAR],
            includeAdult = prefs[INCLUDE_ADULT] ?: SearchFilters.DEFAULT.includeAdult,
        )
    }

    suspend fun set(filters: SearchFilters) {
        store.edit { prefs ->
            prefs[SORT_BY] = filters.sortBy.name
            prefs[ASCENDING] = filters.ascending
            prefs[MEDIA] = filters.media.name
            prefs[MIN_RATING] = filters.minRating
            // Retirées plutôt qu'écrites à zéro : une borne absente et une borne
            // posée à l'an zéro ne veulent pas dire la même chose.
            filters.minYear?.let { prefs[MIN_YEAR] = it } ?: prefs.remove(MIN_YEAR)
            filters.maxYear?.let { prefs[MAX_YEAR] = it } ?: prefs.remove(MAX_YEAR)
            prefs[INCLUDE_ADULT] = filters.includeAdult
        }
    }

    suspend fun reset() = set(SearchFilters.DEFAULT)

    private companion object {
        val SORT_BY = stringPreferencesKey("sort_by")
        val ASCENDING = booleanPreferencesKey("ascending")
        val MEDIA = stringPreferencesKey("media")
        val MIN_RATING = doublePreferencesKey("min_rating")
        val MIN_YEAR = intPreferencesKey("min_year")
        val MAX_YEAR = intPreferencesKey("max_year")
        val INCLUDE_ADULT = booleanPreferencesKey("include_adult")
    }
}
