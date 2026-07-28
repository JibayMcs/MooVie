package fr.moovie.tv.data.tmdb

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Accès TMDB. La clé API et la langue viennent des réglages (fournies par
 * l'appelant), pour rester configurables et hors du binaire.
 */
class TmdbRepository(
    private val language: String = "fr-FR",
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val api: TmdbApi = Retrofit.Builder()
        .baseUrl(TmdbApi.BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TmdbApi::class.java)

    suspend fun trendingMovies(apiKey: String): List<TmdbItem> =
        api.trending("movie", apiKey, language).results

    suspend fun trendingTv(apiKey: String): List<TmdbItem> =
        api.trending("tv", apiKey, language).results

    suspend fun topRatedMovies(apiKey: String): List<TmdbItem> =
        api.topRated("movie", apiKey, language).results

    suspend fun search(apiKey: String, query: String): List<TmdbItem> =
        api.searchMulti(query, apiKey, language)
            .results
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }

    suspend fun movieDetails(apiKey: String, id: Int): MovieDetails =
        api.movieDetails(id, apiKey, language)

    suspend fun tvDetails(apiKey: String, id: Int): TvDetails =
        api.tvDetails(id, apiKey, language)

    suspend fun season(apiKey: String, id: Int, season: Int): SeasonDetails =
        api.season(id, season, apiKey, language)
}
