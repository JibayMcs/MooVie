package fr.moovie.tv.core.sources.model

/**
 * Ce qu'on cherche à lire, tel que les sources doivent le comprendre.
 *
 * L'identifiant TMDB est **la** clé : les catalogues qui l'exposent (cinestream,
 * frembed, j1f, cpasmal…) s'y accrochent et le rapprochement par titre disparaît
 * — avec lui, la confusion « Dune » / « Dune Dreams ». `title` et `year` restent
 * fournis pour les sites qui n'ont qu'un moteur de recherche interne.
 */
sealed interface MediaRef {

    val tmdbId: Int
    val title: String
    val year: String?

    data class Movie(
        override val tmdbId: Int,
        override val title: String,
        override val year: String? = null,
    ) : MediaRef

    data class Episode(
        override val tmdbId: Int,
        override val title: String,
        override val year: String? = null,
        val season: Int,
        val episode: Int,
    ) : MediaRef
}
