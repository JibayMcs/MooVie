package fr.moovie.tv.ui.navigation

/** Destinations de l'app. Étendre au fur et à mesure (recherche, catalogue…). */
sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Search : Screen
    data object History : Screen

    /** Parcours du catalogue TMDB par genre (distinct de la recherche par texte). */
    data object Catalog : Screen

    /**
     * Fiche d'un titre. Si [autoSources] est vrai (reprise depuis l'accueil),
     * le panneau des sources s'ouvre directement — sur [resumeSeason]/[resumeEpisode]
     * pour une série.
     */
    data class Details(
        val tmdbId: Int,
        val isTv: Boolean,
        val autoSources: Boolean = false,
        val resumeSeason: Int = 0,
        val resumeEpisode: Int = 0,
    ) : Screen

    /**
     * Lecture d'un flux. [title]/[subtitle] ne servent qu'à l'affichage dans le
     * lecteur (« Film », « Série » + « S1 · E3 — Nom de l'épisode »).
     */
    data class Player(
        val streamUrl: String,
        val headers: Map<String, String> = emptyMap(),
        val mediaKey: String = "",
        val subtitles: Map<String, String> = emptyMap(),
        val title: String = "",
        val subtitle: String = "",
        /** Épisode à enchaîner en fin de lecture (0 = aucun : film ou fin de série). */
        val nextSeason: Int = 0,
        val nextEpisode: Int = 0,
        /** Affiche du titre, utilisée par l'écran de veille. */
        val posterUrl: String = "",
        /**
         * Durée annoncée par TMDB, en minutes (0 = inconnue).
         *
         * Sert au garde-fou du lecteur : une source qui rend un flux bien plus
         * court que le média n'est pas le média. La cascade filtre déjà ce
         * qu'elle peut mesurer avant d'ouvrir, mais elle ne sait le faire que
         * sur du HLS — le lecteur, lui, connaît la durée quel que soit le format.
         */
        val expectedMinutes: Int = 0,
    ) : Screen
}
