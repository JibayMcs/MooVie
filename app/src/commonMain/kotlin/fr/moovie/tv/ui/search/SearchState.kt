package fr.moovie.tv.ui.search

import fr.moovie.tv.data.tmdb.TmdbItem

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data object NeedsKey : SearchState
    /**
     * Rien à montrer. [masques] : ce que TMDB avait trouvé et que les filtres
     * locaux (type, note, époque) ont écarté — zéro quand il n'y avait vraiment
     * rien. Un filtre « Séries » resté posé d'une recherche à l'autre rendait
     * un film introuvable sans que rien ne le dise.
     */
    data class Empty(val masques: Int = 0) : SearchState
    data class Results(val items: List<TmdbItem>) : SearchState
}
