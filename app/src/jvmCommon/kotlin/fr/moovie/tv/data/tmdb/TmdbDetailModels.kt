package fr.moovie.tv.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MovieDetails(
    val id: Int,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null,
) {
    val year: String? get() = releaseDate?.take(4)?.ifBlank { null }
    fun backdropUrl() = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    fun posterUrl() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
}

@Serializable
data class TvDetails(
    val id: Int,
    val name: String = "",
    @SerialName("original_name") val originalName: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    val genres: List<Genre> = emptyList(),
    val seasons: List<SeasonSummary> = emptyList(),
    val credits: Credits? = null,
) {
    val year: String? get() = firstAirDate?.take(4)?.ifBlank { null }
    fun backdropUrl() = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    fun posterUrl() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
}

@Serializable
data class SeasonSummary(
    @SerialName("season_number") val seasonNumber: Int,
    val name: String = "",
    @SerialName("episode_count") val episodeCount: Int = 0,
)

@Serializable
data class SeasonDetails(
    @SerialName("season_number") val seasonNumber: Int = 0,
    val episodes: List<Episode> = emptyList(),
    /**
     * Résumé et date **de la saison**, que l'API renvoie depuis toujours mais
     * qu'on ne lisait pas : la fiche affichait donc le synopsis de la série
     * quelle que soit la saison choisie. Souvent vides sur les séries peu
     * documentées, d'où le repli sur ceux de la série côté écran.
     */
    val overview: String = "",
    @SerialName("air_date") val airDate: String? = null,
) {
    /** Année de diffusion de la saison, ou null si l'API ne la donne pas. */
    val year: String? get() = airDate?.take(4)?.takeIf { it.length == 4 }
}

@Serializable
data class Episode(
    @SerialName("episode_number") val episodeNumber: Int,
    val name: String = "",
    val overview: String = "",
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val runtime: Int? = null,
) {
    fun stillUrl() = stillPath?.let { "https://image.tmdb.org/t/p/w300$it" }

    /** Visuel large pour la fiche d'épisode (la vignette w300 y est floue). */
    fun stillUrlLarge() = stillPath?.let { "https://image.tmdb.org/t/p/w780$it" }
}

@Serializable
data class Genre(val id: Int, val name: String = "")

@Serializable
data class Credits(val cast: List<CastMember> = emptyList())

@Serializable
data class CastMember(
    /**
     * Identifiant TMDB de la **personne**, pas du rôle : c'est lui qui ouvre sa
     * filmographie. Zéro si TMDB ne le donne pas — la carte n'est alors pas
     * cliquable plutôt que de mener à une page vide.
     */
    val id: Int = 0,
    val name: String = "",
    val character: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
) {
    fun profileUrl() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}
