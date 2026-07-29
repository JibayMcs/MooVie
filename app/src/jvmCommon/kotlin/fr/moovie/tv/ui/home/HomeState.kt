package fr.moovie.tv.ui.home

import fr.moovie.tv.data.tmdb.TmdbItem

data class HomeRow(val title: String, val items: List<TmdbItem>)

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val rows: List<HomeRow>) : HomeState
    data class NeedsApiKey(val reason: String) : HomeState
}
