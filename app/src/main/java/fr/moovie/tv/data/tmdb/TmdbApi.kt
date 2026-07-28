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

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
    }
}
