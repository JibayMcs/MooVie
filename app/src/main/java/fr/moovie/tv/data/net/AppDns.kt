package fr.moovie.tv.data.net

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/** Résolveurs DNS-over-HTTPS proposés dans les réglages. */
enum class DohProvider(
    val label: String,
    private val url: String,
    private val bootstrap: List<String>,
) {
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112"));

    /** Adresses d'amorçage (IP littérales → pas de DNS système nécessaire). */
    internal fun bootstrapAddresses(): List<InetAddress> =
        bootstrap.map { InetAddress.getByName(it) }

    internal fun dohUrl() = url.toHttpUrl()
}

/**
 * DNS de l'app pour l'extraction des sources. Par défaut en DoH (Cloudflare) :
 * contourne le blocage DNS des FAI qui rend les domaines sources introuvables.
 * Mutable et thread-safe — les réglages mettent à jour le résolveur à chaud.
 * Le TMDB reste sur le DNS système (non bloqué) ; seul le client d'extraction
 * utilise ce résolveur.
 */
object AppDns : Dns {

    // Client minimal dédié aux requêtes DoH elles-mêmes (DNS système, mais on
    // fournit les IP d'amorçage → aucune résolution FAI n'est nécessaire).
    private val bootstrapClient = OkHttpClient.Builder().build()

    @Volatile private var delegate: Dns = buildDoh(DohProvider.CLOUDFLARE)

    /** Applique la préférence utilisateur (DoH on/off + résolveur). */
    fun configure(enabled: Boolean, provider: DohProvider) {
        delegate = if (enabled) buildDoh(provider) else Dns.SYSTEM
    }

    private fun buildDoh(provider: DohProvider): Dns =
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(provider.dohUrl())
            .bootstrapDnsHosts(provider.bootstrapAddresses())
            .build()

    override fun lookup(hostname: String): List<InetAddress> =
        runCatching { delegate.lookup(hostname) }.getOrElse { error ->
            // Filet de sécurité : si le DoH est injoignable (réseau HS, résolveur
            // down), on retombe sur le DNS système plutôt que de tout casser.
            if (delegate !== Dns.SYSTEM) Dns.SYSTEM.lookup(hostname) else throw error
        }
}
