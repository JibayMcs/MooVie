package fr.moovie.tv.data.net

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Adresses d'amorçage (IP littérales → pas de DNS système nécessaire). */
internal fun DohProvider.bootstrapAddresses(): List<InetAddress> =
    bootstrap.map { InetAddress.getByName(it) }

internal fun DohProvider.dohUrl() = url.toHttpUrl()

/**
 * DNS de l'app pour l'extraction des sources. Par défaut en DoH (Cloudflare) :
 * contourne le blocage DNS des FAI qui rend les domaines sources introuvables.
 * Mutable et thread-safe — les réglages mettent à jour le résolveur à chaud.
 * Le TMDB reste sur le DNS système (non bloqué) ; seul le client d'extraction
 * utilise ce résolveur.
 *
 * Propre aux cibles JVM : le remplacement du résolveur est une capacité
 * d'OkHttp, et NSURLSession n'a pas d'équivalent. L'énumération du réglage,
 * elle, est commune — voir [DohProvider].
 *
 * ## Quand le résolveur DoH ne répond pas
 *
 * Mesuré sur le téléviseur le 10/10/2026 : les huit catalogues ont rendu
 * « délai dépassé » à 15–21 s, alors que le délai par catalogue est de 12 s et
 * que chaque site répondait en moins d'une seconde. Le temps partait **avant**
 * la requête, dans la résolution du nom : le client d'amorçage gardait les dix
 * secondes de connexion par défaut d'OkHttp, essayées sur chacune des deux
 * adresses de Cloudflare — vingt secondes par nom, pendant lesquelles rien
 * n'est annulable. Et comme chaque catalogue résout ses propres noms, la panne
 * se payait huit fois en parallèle, puis encore à chaque lien extrait.
 *
 * Deux garde-fous :
 *
 *  - le client d'amorçage est borné à [DOH_APPEL_MS] par requête, toutes
 *    adresses comprises ;
 *  - une panne **réseau** du résolveur (connexion refusée, délai, socket
 *    fermée) ouvre un disjoncteur : pendant [DOH_PANNE_MS], les résolutions
 *    passent directement par le DNS système, sans retenter le DoH à chaque
 *    nom. Un nom inconnu (NXDOMAIN) n'est pas une panne et ne l'ouvre pas.
 *
 * Le DNS système reste le filet de secours de chaque résolution ratée, comme
 * avant : mieux vaut un domaine que le FAI bloque peut-être qu'aucune source.
 */
object AppDns : Dns {

    // Client minimal dédié aux requêtes DoH elles-mêmes (DNS système, mais on
    // fournit les IP d'amorçage → aucune résolution FAI n'est nécessaire).
    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(DOH_CONNEXION_MS, TimeUnit.MILLISECONDS)
        .readTimeout(DOH_CONNEXION_MS, TimeUnit.MILLISECONDS)
        .callTimeout(DOH_APPEL_MS, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var delegate: Dns = buildDoh(DohProvider.CLOUDFLARE)

    /** Instant (epoch ms) jusqu'auquel le DoH est tenu pour en panne ; 0 sinon. */
    @Volatile private var dohEnPanneJusqua = 0L

    /** Applique la préférence utilisateur (DoH on/off + résolveur). */
    fun configure(enabled: Boolean, provider: DohProvider) {
        delegate = if (enabled) buildDoh(provider) else Dns.SYSTEM
        // Changer de résolveur, c'est lui redonner sa chance.
        dohEnPanneJusqua = 0L
    }

    private fun buildDoh(provider: DohProvider): Dns =
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(provider.dohUrl())
            .bootstrapDnsHosts(provider.bootstrapAddresses())
            .build()

    override fun lookup(hostname: String): List<InetAddress> {
        val doh = delegate
        if (doh === Dns.SYSTEM) return Dns.SYSTEM.lookup(hostname)
        if (System.currentTimeMillis() < dohEnPanneJusqua) return Dns.SYSTEM.lookup(hostname)
        return runCatching { doh.lookup(hostname) }.getOrElse { erreur ->
            if (panneReseau(erreur)) dohEnPanneJusqua = System.currentTimeMillis() + DOH_PANNE_MS
            // Filet de sécurité : si le DoH est injoignable (réseau HS, résolveur
            // down), on retombe sur le DNS système plutôt que de tout casser.
            Dns.SYSTEM.lookup(hostname)
        }
    }

    /**
     * Distingue la panne du résolveur de la réponse négative.
     *
     * OkHttp enveloppe tout dans une `UnknownHostException` : un NXDOMAIN est
     * une réponse en bonne et due forme, portée par le message et sans cause ;
     * un délai ou une connexion refusée garde l'`IOException` d'origine en
     * cause. Seule la seconde justifie d'écarter le résolveur.
     */
    internal fun panneReseau(erreur: Throwable): Boolean {
        if (erreur !is UnknownHostException) return erreur is IOException
        return erreur.cause is IOException
    }

    /** Connexion ou lecture du résolveur DoH : au-delà, il est tenu pour mort. */
    private const val DOH_CONNEXION_MS = 3_000L

    /** Plafond d'une requête DoH complète, toutes adresses d'amorçage comprises. */
    private const val DOH_APPEL_MS = 5_000L

    /** Durée pendant laquelle on ne retente pas le DoH après une panne réseau. */
    internal const val DOH_PANNE_MS = 60_000L
}
