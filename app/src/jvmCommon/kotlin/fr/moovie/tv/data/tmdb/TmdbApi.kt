package fr.moovie.tv.data.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoints TMDB v3 utilisés par la V1. La clé API est passée par requête
 * (paramétrable dans les réglages) — pas de clé en dur dans le binaire.
 */
interface TmdbApi {
    @GET("trending/{media}/week")
    suspend fun trending(
        @Path("media") media: String, // "movie" | "tv" | "all"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): TmdbPageResult

    /** Titres proches d'un titre donné : la rangée « Parce que tu as regardé… ». */
    @GET("{media}/{id}/recommendations")
    suspend fun recommendations(
        @Path("media") media: String, // "movie" | "tv"
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): TmdbPageResult

    @GET("{media}/top_rated")
    suspend fun topRated(
        @Path("media") media: String, // "movie" | "tv"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1,
    ): TmdbPageResult

    /**
     * `include_adult` est le **seul** filtre que cet endpoint accepte, avec la
     * langue et la page : ni tri, ni année, ni note. Tout le reste se fait donc
     * sur les résultats rapportés (voir `SearchFilters`).
     */
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPageResult

    @GET("genre/{media}/list")
    suspend fun genres(
        @Path("media") media: String, // "movie" | "tv"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): GenreListResult

    /** Catalogue filtré par genre — la page « explorer » de la recherche. */
    @GET("discover/{media}")
    suspend fun discover(
        @Path("media") media: String, // "movie" | "tv"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("with_genres") genreId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        /**
         * Plancher de note. **Filtré par le service**, contrairement à la
         * recherche texte où il faut rapporter plusieurs pages et trancher
         * soi-même : `discover` sait le faire, et le fait sur tout le catalogue
         * plutôt que sur les soixante premiers résultats.
         */
        @Query("vote_average.gte") minRating: Double? = null,
        /**
         * Bornes d'année. Deux jeux de paramètres parce que TMDB nomme la date
         * différemment selon le média — un film a une sortie, une série une
         * première diffusion. Ceux qui ne concernent pas le média demandé
         * restent nuls, et Retrofit les omet de l'URL.
         */
        @Query("primary_release_date.gte") movieFrom: String? = null,
        @Query("primary_release_date.lte") movieTo: String? = null,
        @Query("first_air_date.gte") tvFrom: String? = null,
        @Query("first_air_date.lte") tvTo: String? = null,
    ): TmdbPageResult

    @GET("movie/{id}")
    suspend fun movieDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("append_to_response") append: String = "credits",
    ): MovieDetails

    @GET("tv/{id}")
    suspend fun tvDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("append_to_response") append: String = "credits",
    ): TvDetails

    /**
     * Filmographie d'une personne, films **et** séries en une requête.
     *
     * `combined_credits` plutôt que `movie_credits` + `tv_credits` : un acteur
     * partage rarement sa carrière entre les deux de façon nette, et deux appels
     * auraient obligé à fusionner et retrier deux listes pour un résultat
     * identique. Les entrées portent déjà `media_type`, ce dont [TmdbItem] sait
     * déduire film ou série.
     */
    @GET("person/{id}/combined_credits")
    suspend fun personCredits(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): PersonCredits

    @GET("tv/{id}/season/{season}")
    suspend fun season(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): SeasonDetails

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
    }
}
