package fr.moovie.tv.data.sources

import fr.moovie.tv.data.net.AppDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Registre central des extracteurs. La résolution se fait en deux temps :
 *
 * 1. **par domaine** — le premier extracteur dont [SourceExtractor.canHandle]
 *    reconnaît l'URL. C'est le chemin normal, sans requête inutile.
 * 2. **par reniflage** — si personne ne revendique l'URL (ou si le premier
 *    échoue), les extracteurs de [sniffers] sont essayés à leur tour : ils
 *    téléchargent la page et se reconnaissent à sa structure.
 *
 * Le deuxième temps existe pour les hébergeurs à domaines tournants, VOE en
 * tête, qui distribue un même lien sur trois domaines renouvelés en continu.
 * Une liste de motifs figée dans le binaire y perd toujours la course : au
 * prochain alias, l'hébergeur le plus fréquent des sites FR redeviendrait
 * invisible jusqu'à la release suivante. Le reniflage coûte une requête sur des
 * liens qui, sans lui, seraient de toute façon perdus.
 */
object ExtractorRegistry {

    val http: OkHttpClient = OkHttpClient.Builder()
        // DoH : les domaines sources sont bloqués par DNS chez les FAI.
        .dns(AppDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val voe = VoeExtractor(http)

    private val extractors: List<SourceExtractor> = listOf(
        FsvidExtractor(http),
        VidzyExtractor(http),
        UqloadExtractor(http),
        DoodStreamExtractor(http),
        SibnetExtractor(http),
        SeekStreamingExtractor(http),
        AnsembedExtractor(http),
        voe,
        LuluExtractor(http),
    )

    /**
     * Extracteurs capables de s'identifier sans connaître le domaine. Ils sont
     * essayés en dernier recours, dans l'ordre, et doivent renvoyer null sans
     * effet de bord quand la page n'est pas la leur.
     *
     * Du plus spécifique au plus générique : VOE se reconnaît à une charge utile
     * qui n'appartient qu'à lui, [PackedM3u8Extractor] à une simple forme de
     * page, donc en dernier.
     */
    private val sniffers: List<SourceExtractor> = listOf(voe, PackedM3u8Extractor(http))

    fun extractorFor(url: String): SourceExtractor? =
        extractors.firstOrNull { it.canHandle(url) }

    /** true si un extracteur revendique ce domaine. Un lien non revendiqué reste
     *  résoluble par reniflage — ceci ne préjuge donc pas de l'échec. */
    fun canResolve(url: String): Boolean = extractorFor(url) != null

    // try/catch défensif : un extracteur qui lève (regex, parsing, réseau) ne doit
    // pas tuer la boucle de résolution — on passe simplement au suivant.
    suspend fun resolve(link: EmbedLink): PlayableStream? {
        val matched = extractorFor(link.url)
        runCatching { matched?.extract(link) }.getOrNull()?.let { return it }

        for (sniffer in sniffers) {
            if (sniffer === matched) continue // déjà tenté à l'étape 1
            runCatching { sniffer.extract(link) }.getOrNull()?.let { return it }
        }
        return null
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
