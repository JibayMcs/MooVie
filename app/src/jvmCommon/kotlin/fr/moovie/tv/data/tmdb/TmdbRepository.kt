package fr.moovie.tv.data.tmdb

import fr.moovie.tv.data.store.moovieCacheDir
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Taille max du cache disque des réponses TMDB. */
private const val TMDB_CACHE_BYTES = 20L * 1024 * 1024

/**
 * Durée pendant laquelle une réponse TMDB est resservie sans appel réseau.
 * Le catalogue bouge lentement (tendances hebdomadaires, fiches quasi figées) :
 * quelques heures suffisent pour rendre instantané le retour sur une fiche.
 */
private const val TMDB_TTL_SECONDS = 6 * 60 * 60

/** Âge max toléré quand le réseau est indisponible (mieux qu'un écran d'erreur). */
private const val TMDB_STALE_SECONDS = 7 * 24 * 60 * 60

/**
 * Client HTTP partagé par toutes les instances du repository.
 *
 * Il **doit** être unique : un `Cache` OkHttp verrouille son répertoire, et deux
 * instances concurrentes sur le même dossier le corrompent. Or ce repository est
 * construit à la demande un peu partout (accueil, recherche, fiche).
 *
 * Il n'utilise **pas** le DNS-over-HTTPS d'AppDns : TMDB n'est pas bloqué par les
 * FAI, seuls les domaines des sources le sont.
 */
private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .cache(Cache(moovieCacheDir("tmdb-http"), TMDB_CACHE_BYTES))
        // TMDB renvoie des en-têtes de cache très courts : on impose notre durée.
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder()
                .header("Cache-Control", "public, max-age=$TMDB_TTL_SECONDS")
                .removeHeader("Pragma")
                .build()
        }
        // Repli hors ligne : si la requête réseau échoue, on ressert la réponse
        // en cache même périmée plutôt que de casser l'écran.
        .addInterceptor { chain ->
            try {
                chain.proceed(chain.request())
            } catch (io: IOException) {
                val stale = chain.request().newBuilder()
                    .cacheControl(
                        CacheControl.Builder()
                            .onlyIfCached()
                            .maxStale(TMDB_STALE_SECONDS, TimeUnit.SECONDS)
                            .build(),
                    )
                    .build()
                chain.proceed(stale)
            }
        }
        .build()
}

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
        .client(httpClient)
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
