package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceExtractor
import fr.moovie.tv.core.sources.usecase.StreamResolution

/**
 * Passerelle réseau des extracteurs, fournie par la plateforme.
 *
 * C'est la seule chose qui distingue encore les cibles dans ce domaine : la JVM
 * la construit sur OkHttp avec son résolveur DoH, iOS sur Ktor/NSURLSession.
 * Tout ce qui est au-dessus — quel extracteur pour quel domaine, dans quel
 * ordre, avec quel repli — est commun et se teste sans réseau.
 *
 * Le plafond de temps par appel appartient à cette construction et non au
 * registre : c'est une propriété du client. Voir chaque `actual`.
 */
expect val passerelleSources: HttpGateway

/**
 * Câblage des adaptateurs concrets.
 *
 * Ce registre ne décide pas *comment* on résout : cette politique (par domaine,
 * puis par reniflage) vit dans [StreamResolution], côté domaine, où elle se
 * teste sans réseau. Ici on ne fait que fournir la liste des implémentations.
 */
object ExtractorRegistry {

    /** Seule porte de sortie réseau des extracteurs. */
    val gateway: HttpGateway get() = passerelleSources

    private val voe = VoeExtractor(gateway)

    private val resolution = StreamResolution(
        extractors = listOf(
            // **Avant** DirectStream, et pas par préférence : les liens
            // SwiftFlow sont des `.mp4`, donc DirectStream les revendiquerait le
            // premier et repartirait sans le `Referer` que le CDN exige — 403
            // garanti. Voir SwiftFlowExtractor.
            SwiftFlowExtractor(),
            // En tête : un lien déjà jouable ne doit traverser aucune autre
            // règle, et la reconnaissance ne coûte qu'un test d'extension.
            DirectStreamExtractor(),
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
