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

    @GET("{media}/top_rated")
    suspend fun topRated(
        @Path("media") media: String, // "movie" | "tv"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1,
    ): TmdbPageResult

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1,
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
