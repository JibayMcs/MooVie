package fr.moovie.tv.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modèles TMDB minimaux (seulement les champs utilisés).
 * `ignoreUnknownKeys = true` côté Json permet d'ignorer le reste.
 */
@Serializable
data class TmdbPageResult(
    val page: Int = 1,
    val results: List<TmdbItem> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
data class TmdbItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
) {
    /** Titre affichable, quel que soit le type (film ou série). */
    val displayTitle: String get() = title ?: name ?: "Sans titre"

    /** true si c'est une série (heuristique : présence de `name`/`first_air_date`). */
    val isTv: Boolean get() = mediaType == "tv" || (title == null && name != null)

    /** Année de sortie (film ou série), ou null. */
    val year: String? get() = (releaseDate ?: firstAirDate)?.take(4)?.ifBlank { null }

    fun posterUrl(size: String = "w342"): String? =
        posterPath?.let { "https://image.tmdb.org/t/p/$size$it" }

    fun backdropUrl(size: String = "w1280"): String? =
        backdropPath?.let { "https://image.tmdb.org/t/p/$size$it" }
}
