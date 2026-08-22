package fr.moovie.tv.data.cast

import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Ouvre la socket TLS d'un récepteur Cast — **et rien d'autre**.
 *
 * ## Pourquoi une vérification désactivée
 *
 * Un Chromecast présente un certificat signé par une autorité interne à Google,
 * émis pour un nom qui n'a rien à voir avec l'adresse IP qu'on compose. Aucun
 * magasin de confiance du système ne le validera jamais, et le vérifier est
 * impossible sans embarquer la chaîne de Google — c'est-à-dire sans faire
 * exactement ce que ce fichier existe pour éviter.
 *
 * ## Pourquoi c'est acceptable ici, et nulle part ailleurs
 *
 * Ce que le chiffrement protégerait n'a rien de confidentiel : l'URL d'un flux
 * que l'on vient soi-même de publier en clair sur son propre réseau local. Un
 * attaquant capable de s'interposer sur ce lien lit déjà le flux directement
 * depuis le relais, qui est en HTTP.
 *
 * **Le risque n'est donc pas dans ce fichier, il est dans sa réutilisation.** Un
 * `TrustManager` permissif qui se retrouve six mois plus tard dans le client
 * HTTP de l'application ouvrirait tout le trafic — TMDB, GitHub, la synchro
 * chiffrée. D'où trois garde-fous délibérés :
 *
 * 1. la fonction **refuse toute adresse non privée** : on ne parle qu'au réseau
 *    local, jamais à Internet ;
 * 2. le port est **imposé**, pas paramétrable ;
 * 3. le `SSLContext` est local à l'appel — il n'est ni exposé, ni installé par
 *    défaut, et ne peut donc contaminer aucun autre client.
 */
internal object CastTls {

    /** Le port du protocole CASTV2. Non négociable, et c'est voulu. */
    const val PORT = 8009

    /**
     * Ouvre la connexion, ou lève si l'hôte n'est pas une adresse privée.
     *
     * @param host adresse IP du récepteur, telle que la découverte l'a rendue.
     */
    fun connect(host: String, timeoutMs: Int = 8_000): SSLSocket {
        val adresse = InetAddress.getByName(host)
        require(adresse.isSiteLocalAddress || adresse.isLinkLocalAddress || adresse.isLoopbackAddress) {
            // Sans cette borne, une découverte empoisonnée — ou un jour un champ
            // d'adresse laissé libre dans les réglages — ferait parler ce code
            // à Internet sans aucune vérification de certificat.
            "CastTls refuse une adresse non privée : $host"
        }

        val contexte = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(ToutAccepter), SecureRandom())
        }
        val socket = contexte.socketFactory.createSocket() as SSLSocket
        socket.connect(java.net.InetSocketAddress(adresse, PORT), timeoutMs)
        socket.soTimeout = timeoutMs
        socket.startHandshake()
        return socket
    }

    /**
     * Accepte tout — voir la note de l'objet pour ce qui rend ça tenable.
     *
     * `private` et anonyme : il n'existe aucune façon d'y faire référence depuis
     * un autre fichier, ce qui est la moitié de la protection.
     */
    private object ToutAccepter : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
