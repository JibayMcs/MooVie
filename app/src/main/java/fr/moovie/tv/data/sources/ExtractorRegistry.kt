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
        VoeExtractor(http),
        // À compléter : UqloadExtractor, DoodStreamExtractor, SibnetExtractor,
        // SeekStreamingExtractor… (portage 1:1 des handlers de API/proxiesembed).
    )

    fun extractorFor(url: String): SourceExtractor? =
        extractors.firstOrNull { it.canHandle(url) }

    suspend fun resolve(link: EmbedLink): PlayableStream? =
        extractorFor(link.url)?.extract(link)
}
