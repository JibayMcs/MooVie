package fr.moovie.tv.ui.search

import fr.moovie.tv.data.tmdb.TmdbItem

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data object NeedsKey : SearchState
    data object Empty : SearchState
    data class Results(val items: List<TmdbItem>) : SearchState
}
