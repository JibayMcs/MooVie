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
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null,
    val videos: VideoList? = null,
    // ── Onglet « En savoir plus » ────────────────────────────────────────────
    val tagline: String = "",
    val status: String = "",
    val budget: Long = 0,
    val revenue: Long = 0,
    val homepage: String = "",
    @SerialName("original_language") val originalLanguage: String = "",
    @SerialName("production_companies") val companies: List<ProductionCompany> = emptyList(),
    @SerialName("production_countries") val countries: List<ProductionCountry> = emptyList(),
    @SerialName("spoken_languages") val spokenLanguages: List<SpokenLanguage> = emptyList(),
    @SerialName("release_dates") val releaseDates: ReleaseDateResults? = null,
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
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    val genres: List<Genre> = emptyList(),
    val seasons: List<SeasonSummary> = emptyList(),
    val credits: Credits? = null,
    val videos: VideoList? = null,
    // ── Onglet « En savoir plus » ────────────────────────────────────────────
    val tagline: String = "",
    /** "Returning Series", "Ended", "Canceled"… traduit à l'affichage. */
    val status: String = "",
    val homepage: String = "",
    @SerialName("original_language") val originalLanguage: String = "",
    @SerialName("number_of_episodes") val numberOfEpisodes: Int = 0,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("last_air_date") val lastAirDate: String? = null,
    @SerialName("in_production") val inProduction: Boolean = false,
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("created_by") val createdBy: List<CreatedBy> = emptyList(),
    val networks: List<ProductionCompany> = emptyList(),
    @SerialName("production_companies") val companies: List<ProductionCompany> = emptyList(),
    /**
     * Prochain épisode annoncé. C'est l'information qu'on vient chercher sur une
     * série en cours, et TMDB la donne — on ne la lisait simplement pas.
     */
    @SerialName("next_episode_to_air") val nextEpisode: AiringEpisode? = null,
    @SerialName("content_ratings") val contentRatings: ContentRatingResults? = null,
) {
    val year: String? get() = firstAirDate?.take(4)?.ifBlank { null }
    fun backdropUrl() = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    fun posterUrl() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
}

@Serializable
data class CreatedBy(val id: Int = 0, val name: String = "")

@Serializable
data class ProductionCompany(
    val id: Int = 0,
    val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
) {
    fun logoUrl() = logoPath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

@Serializable
data class ProductionCountry(
    @SerialName("iso_3166_1") val code: String = "",
    val name: String = "",
)

@Serializable
data class SpokenLanguage(
    @SerialName("iso_639_1") val code: String = "",
    val name: String = "",
    @SerialName("english_name") val englishName: String = "",
)

@Serializable
data class AiringEpisode(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
)

@Serializable
data class ContentRatingResults(val results: List<ContentRating> = emptyList())

@Serializable
data class ContentRating(
    @SerialName("iso_3166_1") val country: String = "",
    val rating: String = "",
)

@Serializable
data class ReleaseDateResults(val results: List<ReleaseDateCountry> = emptyList())

@Serializable
data class ReleaseDateCountry(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("release_dates") val dates: List<ReleaseDateEntry> = emptyList(),
)

@Serializable
data class ReleaseDateEntry(
    val certification: String = "",
    val type: Int = 0,
)

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
data class Credits(
    val cast: List<CastMember> = emptyList(),
    val crew: List<CrewMember> = emptyList(),
) {
    /**
     * Réalisateur(s). Plusieurs sont possibles et c'est la règle, pas
     * l'exception (les Wachowski, les Coen, les Russo) : rendre le premier
     * amputerait la moitié d'un duo.
     */
    val directors: List<String>
        get() = crew.filter { it.job == "Director" }.map { it.name }.distinct()

    val writers: List<String>
        get() = crew.filter { it.job in WRITING }.map { it.name }.distinct()

    val composers: List<String>
        get() = crew.filter { it.job == "Original Music Composer" }.map { it.name }.distinct()

    private companion object {
        val WRITING = setOf("Screenplay", "Writer", "Story", "Novel")
    }
}

@Serializable
data class CrewMember(
    val id: Int = 0,
    val name: String = "",
    val job: String = "",
    val department: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
)

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
