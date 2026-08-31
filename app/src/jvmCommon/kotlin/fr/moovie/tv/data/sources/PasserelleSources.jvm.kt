package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.data.net.AppDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Client HTTP brut des cibles JVM.
 *
 * Portait le nom `ExtractorRegistry.http` avant que le registre ne devienne
 * commun : un `OkHttpClient` ne peut pas vivre dans un source set que
 * Kotlin/Native compile aussi. Le déplacement ne change ni sa configuration ni
 * son cycle de vie — c'est le même client, au même réglage, sous un autre nom.
 *
 * Reste exposé parce que trois providers, le relais local et le client de
 * télécommande s'en servent directement ; ils passeront au port à leur tour, et
 * c'est ce passage qui les rendra portables.
 */
object ClientExtraction {
    val http: OkHttpClient = OkHttpClient.Builder()
        // DoH : les domaines sources sont bloqués par DNS chez les FAI.
        .dns(AppDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

/**
 * **Plafonnée dans le temps**, contrairement au client brut.
 *
 * `readTimeout` ne protège que des silences : tant que des octets arrivent, il
 * ne se déclenche jamais. Une requête documentaire lancée par erreur sur un
 * média (une page HTML attendue, un film de deux gigaoctets servi) tient alors
 * son fil indéfiniment — et comme le corps est lu par un appel bloquant,
 * annuler la coroutine ne l'interrompt pas. Mesuré : quelques appels de ce
 * genre suffisent à saturer le pool de coroutines partagé, et tout ce qui
 * l'utilise gèle derrière, DataStore compris.
 *
 * `callTimeout` borne l'appel entier, lecture du corps incluse. Trente secondes
 * sont très larges pour une page ou un manifeste, et laissent le client brut —
 * celui des segments et des téléchargements, qui transfèrent de vrais médias —
 * sans plafond.
 */
actual val passerelleSources: HttpGateway = OkHttpGateway(
    ClientExtraction.http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build(),
)

/**
 * Liste des catalogues de sources. L'orchestration (chargement progressif,
 * timeout, fusion) est faite par l'appelant (DetailsViewModel) pour un
 * affichage en streaming.
 *
 * Reste côté JVM : trois de ces providers prennent un `OkHttpClient` en direct
 * plutôt que le port, ce qui les cloue à cette plateforme jusqu'à leur propre
 * portage.
 */
object ProviderRegistry {
    val all: List<SourceProvider> = listOf(
        // En tête : c'est le seul catalogue qui rend le fichier lui-même plutôt
        // qu'un embed à désobfusquer. Rien à casser au prochain changement de
        // format, donc le candidat le plus sûr à essayer en premier.
        SwiftFlowProvider(ExtractorRegistry.gateway),
        FstreamProvider(ClientExtraction.http),
        AnimeSamaProvider(ClientExtraction.http),
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
        CinestreamProvider(ClientExtraction.http),
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
