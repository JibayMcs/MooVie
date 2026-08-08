package fr.moovie.tv.ui.navigation

import fr.moovie.tv.ui.catalog.CatalogSelection

/** Destinations de l'app. Étendre au fur et à mesure (recherche, catalogue…). */
sealed interface Screen {
    data object Home : Screen

    /**
     * Accueil de première installation : restaurer une sauvegarde, ou saisir la
     * clé TMDB. C'est la racine de la pile tant qu'aucune clé n'est enregistrée —
     * sans elle l'accueil n'a rien à afficher, et y déposer l'utilisateur ne lui
     * apprend ni ce qui manque ni ce qu'il peut déjà récupérer.
     */
    data object Onboarding : Screen
    data object Settings : Screen
    data object Search : Screen
    data object History : Screen

    /**
     * La file des téléchargements. Écran de premier niveau, comme l'historique :
     * on y va pour savoir où en est une opération longue, pas pour régler quoi
     * que ce soit.
     */
    data object Downloads : Screen

    /**
     * Parcours du catalogue TMDB par genre (distinct de la recherche par texte).
     *
     * [select] est le genre à ouvrir d'emblée. C'est ce que veut dire « en voir
     * plus » depuis une rangée épinglée : elle **vient** de ce genre, elle sait
     * donc exactement où renvoyer. Null pour une ouverture normale, qui reprend
     * le dernier genre parcouru — présélectionner au hasard reviendrait à
     * inventer une intention que personne n'a exprimée.
     */
    data class Catalog(val select: CatalogSelection? = null) : Screen

    /**
     * Filmographie d'une personne, ouverte depuis le casting d'une fiche.
     *
     * [name] voyage avec l'identifiant plutôt que d'être rechargé : il est déjà
     * connu de la fiche d'où l'on vient, et l'écran peut donc afficher son titre
     * avant même la réponse de TMDB — plutôt qu'un en-tête vide le temps d'un
     * aller-retour réseau.
     */
    data class Person(val personId: Int, val name: String) : Screen

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
        /**
         * Le lien d'embed qui a produit ce flux, pour que le lecteur puisse
         * proposer le téléchargement.
         *
         * Le flux lui-même ne suffirait pas : son jeton expire en deux heures,
         * alors qu'un téléchargement dure plus longtemps et devra être
         * re-résolu. Vide quand la lecture vient d'un fichier local — il n'y a
         * alors plus rien à télécharger.
         */
        val sourceUrl: String = "",
        val hoster: String = "",
        val language: String = "",
    ) : Screen
}
