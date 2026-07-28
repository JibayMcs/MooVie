package fr.moovie.tv.data.sources

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Registre central des extracteurs. On enregistre ici chaque hébergeur porté
 * en Kotlin ; la résolution choisit le premier extracteur capable de traiter
 * l'URL. Le client OkHttp partagé sert à toutes les requêtes d'extraction.
 */
object ExtractorRegistry {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val extractors: List<SourceExtractor> = listOf(
        FsvidExtractor(http),
        VidzyExtractor(http),
        UqloadExtractor(http),
        VoeExtractor(http),
        // À compléter : DoodStreamExtractor, SibnetExtractor, SeekStreamingExtractor…
        // (portage des handlers de API/proxiesembed).
    )

    fun extractorFor(url: String): SourceExtractor? =
        extractors.firstOrNull { it.canHandle(url) }

    fun canResolve(url: String): Boolean = extractorFor(url) != null

    suspend fun resolve(link: EmbedLink): PlayableStream? =
        extractorFor(link.url)?.extract(link)
}

/** Providers de sources disponibles (portage des routes backend). */
object ProviderRegistry {
    val fstream: SourceProvider = FstreamProvider(ExtractorRegistry.http)
}
