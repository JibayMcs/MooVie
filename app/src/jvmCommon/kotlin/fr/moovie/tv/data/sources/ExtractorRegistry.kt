package fr.moovie.tv.data.sources

import fr.moovie.tv.data.net.AppDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Registre central des extracteurs. On enregistre ici chaque hébergeur porté
 * en Kotlin ; la résolution choisit le premier extracteur capable de traiter
 * l'URL. Le client OkHttp partagé sert à toutes les requêtes d'extraction.
 */
object ExtractorRegistry {

    val http: OkHttpClient = OkHttpClient.Builder()
        // DoH : les domaines sources sont bloqués par DNS chez les FAI.
        .dns(AppDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val extractors: List<SourceExtractor> = listOf(
        FsvidExtractor(http),
        VidzyExtractor(http),
        UqloadExtractor(http),
        DoodStreamExtractor(http),
        SibnetExtractor(http),
        SeekStreamingExtractor(http),
        AnsembedExtractor(http),
        VoeExtractor(http),
        LuluExtractor(http),
    )

    fun extractorFor(url: String): SourceExtractor? =
        extractors.firstOrNull { it.canHandle(url) }

    fun canResolve(url: String): Boolean = extractorFor(url) != null

    // try/catch défensif : un extracteur qui lève (regex, parsing, réseau) ne doit
    // pas tuer la boucle de résolution — on passe simplement au lien suivant.
    suspend fun resolve(link: EmbedLink): PlayableStream? =
        try {
            extractorFor(link.url)?.extract(link)
        } catch (_: Throwable) {
            null
        }
}

/**
 * Liste des providers de sources. L'orchestration (chargement progressif, timeout,
 * fusion) est faite par l'appelant (DetailsViewModel) pour un affichage en streaming.
 */
object ProviderRegistry {
    val all: List<SourceProvider> = listOf(
        FstreamProvider(ExtractorRegistry.http),
        AnimeSamaProvider(ExtractorRegistry.http),
        CoflixProvider(ExtractorRegistry.http),
        CinestreamProvider(ExtractorRegistry.http),
    )
}
