package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceExtractor
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.usecase.StreamResolution
import fr.moovie.tv.data.net.AppDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Câblage des adaptateurs concrets.
 *
 * Ce registre ne décide plus *comment* on résout : cette politique (par domaine,
 * puis par reniflage) vit dans [StreamResolution], côté domaine, où elle se teste
 * sans réseau. Ici on ne fait que fournir le client HTTP et la liste des
 * implémentations.
 */
object ExtractorRegistry {

    /**
     * Client HTTP brut. Encore exposé parce que les providers s'en servent
     * directement ; ils passeront au port à leur tour.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        // DoH : les domaines sources sont bloqués par DNS chez les FAI.
        .dns(AppDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Seule porte de sortie réseau des extracteurs. */
    val gateway: HttpGateway = OkHttpGateway(http)

    private val voe = VoeExtractor(gateway)

    private val resolution = StreamResolution(
        extractors = listOf(
            FsvidExtractor(gateway),
            VidzyExtractor(gateway),
            UqloadExtractor(gateway),
            DoodStreamExtractor(gateway),
            SibnetExtractor(gateway),
            SeekStreamingExtractor(gateway),
            AnsembedExtractor(gateway),
            voe,
            LuluExtractor(gateway),
        ),
        // Du plus spécifique au plus générique : VOE se reconnaît à une charge
        // utile qui n'appartient qu'à lui, PackedM3u8Extractor à une simple forme
        // de page, donc en dernier.
        sniffers = listOf(voe, PackedM3u8Extractor(gateway)),
    )

    fun extractorFor(url: String): SourceExtractor? = resolution.extractorFor(url)

    /** true si un extracteur revendique ce domaine ; le reniflage peut réussir sans. */
    fun canResolve(url: String): Boolean = resolution.claimsDomain(url)

    suspend fun resolve(link: EmbedLink): PlayableStream? = resolution.resolve(link)
}

/**
 * Liste des catalogues de sources. L'orchestration (chargement progressif, timeout,
 * fusion) est faite par l'appelant (DetailsViewModel) pour un affichage en streaming.
 */
object ProviderRegistry {
    val all: List<SourceProvider> = listOf(
        FstreamProvider(ExtractorRegistry.http),
        AnimeSamaProvider(ExtractorRegistry.http),
        CoflixProvider(ExtractorRegistry.http),
        CinestreamProvider(ExtractorRegistry.http),
        FrembedProvider(ExtractorRegistry.gateway),
    )
}
