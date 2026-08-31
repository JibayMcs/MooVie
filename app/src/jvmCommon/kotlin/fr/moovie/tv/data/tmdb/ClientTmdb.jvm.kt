package fr.moovie.tv.data.tmdb

import fr.moovie.tv.data.store.moovieCacheDir
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TMDB_CACHE_BYTES = 20L * 1024 * 1024
private const val TMDB_TTL_SECONDS = 6 * 60 * 60
private const val TMDB_STALE_SECONDS = 7 * 24 * 60 * 60

/**
 * Le client OkHttp d'origine, **mot pour mot**.
 *
 * Il n'a pas été réécrit en Ktor et c'est délibéré : son cache disque, l'en-tête
 * de durée qu'il impose et son repli hors ligne sur une réponse périmée sont
 * trois comportements que les utilisateurs Android et desktop ont aujourd'hui.
 * Les retranscrire avec le plugin `HttpCache` de Ktor aurait été une réécriture
 * à risque pour un gain nul — le moteur OkHttp de Ktor sait prendre un client
 * déjà construit.
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
 * `preconfigured` passe le client ci-dessus au moteur : Ktor n'en construit pas
 * un second et hérite du cache, des interceptors et des timeouts. La pile
 * réseau d'Android et du desktop est donc rigoureusement celle d'avant, seule
 * la couche d'appel au-dessus a changé.
 */
actual val clientTmdb: HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = httpClient }
    // `expectSuccess` reproduit Retrofit, qui lève sur un statut non-2xx.
    // `TmdbRepository` s'en sert pour distinguer une clé refusée (401) d'une
    // panne réseau : sans cela le 401 passerait pour une réponse valide.
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
