package fr.moovie.tv.ui.navigation

/** Destinations de l'app. Étendre au fur et à mesure (recherche, catalogue…). */
sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Search : Screen
    data class Details(val tmdbId: Int, val isTv: Boolean) : Screen
    data class Player(val streamUrl: String, val headers: Map<String, String> = emptyMap()) : Screen
}
