package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.port.SourceProvider

/**
 * Liste des catalogues de sources. L'orchestration (chargement progressif,
 * timeout, fusion) est faite par l'appelant (DetailsViewModel) pour un
 * affichage en streaming.
 *
 * Commun depuis que les sept catalogues passent tous par le port `HttpGateway` :
 * plus aucun ne prend un `OkHttpClient` en direct, plus rien ne le cloue à la JVM.
 */
object ProviderRegistry {
    val all: List<SourceProvider> = listOf(
        // En tête : c'est le seul catalogue qui rend le fichier lui-même plutôt
        // qu'un embed à désobfusquer. Rien à casser au prochain changement de
        // format, donc le candidat le plus sûr à essayer en premier.
        SwiftFlowProvider(ExtractorRegistry.gateway),
        FstreamProvider(ExtractorRegistry.gateway),
        AnimeSamaProvider(ExtractorRegistry.gateway),
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
        CinestreamProvider(ExtractorRegistry.gateway),
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
