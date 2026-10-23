package fr.moovie.tv.data.net

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet6Address
import java.net.InetAddress

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
        ipv4First(
            runCatching { delegate.lookup(hostname) }.getOrElse { error ->
                // Filet de sécurité : si le DoH est injoignable (réseau HS, résolveur
                // down), on retombe sur le DNS système plutôt que de tout casser.
                if (delegate !== Dns.SYSTEM) Dns.SYSTEM.lookup(hostname) else throw error
            },
        )
}

/**
 * IPv4 d'abord, IPv6 ensuite, l'ordre de chaque famille conservé.
 *
 * `DnsOverHttps` lance les requêtes A et AAAA en parallèle et rend les adresses
 * dans leur ordre d'arrivée : une fois sur deux, l'IPv6 passe devant. Sur un
 * réseau qui annonce une adresse IPv6 sans la router (l'émulateur, mais aussi
 * des box mal configurées), la connexion part dans le vide jusqu'au
 * `connectTimeout` de dix secondes, et le catalogue dépasse son budget de douze
 * avant qu'OkHttp n'essaie l'IPv4. Mesuré le 23/10/2026 : un catalogue sur
 * trois tombait au hasard à chaque chargement ; IPv6 coupé, plus aucun.
 * L'IPv6 reste en repli, pour les réseaux qui n'ont qu'elle.
 */
internal fun ipv4First(addresses: List<InetAddress>): List<InetAddress> =
    addresses.sortedBy { it is Inet6Address }
