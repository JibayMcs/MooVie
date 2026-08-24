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

    /**
     * Seule porte de sortie réseau des extracteurs, et **plafonnée dans le
     * temps**.
     *
     * `readTimeout` ne protège que des silences : tant que des octets arrivent,
     * il ne se déclenche jamais. Une requête documentaire lancée par erreur sur
     * un média (une page HTML attendue, un film de deux gigaoctets servi) tient
     * alors son fil indéfiniment — et comme le corps est lu par un appel
     * bloquant, annuler la coroutine ne l'interrompt pas. Mesuré : quelques
     * appels de ce genre suffisent à saturer le pool de coroutines partagé, et
     * tout ce qui l'utilise gèle derrière, DataStore compris.
     *
     * `callTimeout` borne l'appel entier, lecture du corps incluse. Trente
     * secondes sont très larges pour une page ou un manifeste, et laissent le
     * client brut — celui des segments et des téléchargements, qui transfèrent
     * de vrais médias — sans plafond.
     */
    val gateway: HttpGateway = OkHttpGateway(
        http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build(),
    )

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

/**
 * Liste des catalogues de sources. L'orchestration (chargement progressif, timeout,
 * fusion) est faite par l'appelant (DetailsViewModel) pour un affichage en streaming.
 */
object ProviderRegistry {
    val all: List<SourceProvider> = listOf(
        // En tête : c'est le seul catalogue qui rend le fichier lui-même plutôt
        // qu'un embed à désobfusquer. Rien à casser au prochain changement de
        // format, donc le candidat le plus sûr à essayer en premier.
        SwiftFlowProvider(ExtractorRegistry.gateway),
        FstreamProvider(ExtractorRegistry.http),
        AnimeSamaProvider(ExtractorRegistry.http),
        // coflix a été **retiré** le 24/08/2026, et pas parce qu'il était mal
        // écrit : coflix.trade s'est reconstruit sur coflix.esq, en WordPress,
        // derrière un compte et un Turnstile Cloudflare. Mesuré — `suggest.php`
        // rend une page d'erreur WordPress, et une fiche de film servie à un
        // visiteur anonyme ne contient **aucun lien d'hébergeur** : ni iframe
        // exploitable, ni `showVideo`, ni base64. Seul un JWT `cfPlayerToken`
        // (`uid:0, vip:false`, trente minutes) et une bannière « Devenir VIP ».
        //
        // Le garder aurait produit un catalogue qui rend une liste vide sur
        // chaque appareil, en coûtant une requête et son délai d'attente à
        // chaque ouverture de fiche — la panne muette que ce projet documente
        // déjà pour wiflix, mais livrée volontairement.
        CinestreamProvider(ExtractorRegistry.http),
        FrembedProvider(ExtractorRegistry.gateway),
        // Après les catalogues déjà éprouvés : wiflix couvre moins de titres
        // (il refuse ceux dont la date de sortie ne correspond pas à TMDB) mais
        // apporte des hébergeurs que personne d'autre ne sert.
        WiflixProvider(ExtractorRegistry.gateway),
        // Seul catalogue en version originale : c'est lui qui rend le réglage VO
        // utilisable, et l'app regardable pour un public non francophone.
        VidapiProvider(ExtractorRegistry.gateway),
    )
}
