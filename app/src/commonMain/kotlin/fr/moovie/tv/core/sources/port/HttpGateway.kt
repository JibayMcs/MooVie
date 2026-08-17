package fr.moovie.tv.core.sources.port

enum class HttpMethod { GET, HEAD, POST }

/**
 * Profil de connexion demandé pour une requête.
 *
 * Sert de couture pour l'empreinte réseau. Mesuré : notre client se présente
 * comme un client Java (11 suites de chiffrement, 14 extensions, pas de GREASE,
 * un seul SETTING HTTP/2) là où Chrome en présente 15/16. Tous les catalogues FR
 * visés s'en accommodent, mais deux hébergeurs — playmogo et savefiles —
 * répondent 403 sur l'endpoint d'embed lui-même. Le jour où on voudra les
 * atteindre, on branche un adaptateur usurpant l'empreinte sur [BROWSER] : les
 * extracteurs ne changent pas d'une ligne.
 */
enum class NetworkProfile {
    /** Client par défaut de l'app (DoH, timeouts, HTTP/2). */
    DEFAULT,

    /** Empreinte TLS/HTTP2 de navigateur. Pas encore implémenté. */
    BROWSER,
}

data class HttpRequest(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    /** Corps de formulaire pour [HttpMethod.POST]. */
    val form: Map<String, String>? = null,
    /**
     * Corps JSON brut pour [HttpMethod.POST], exclusif avec [form].
     *
     * Les hébergeurs de la cascade postent tous des formulaires ; l'API interne
     * de YouTube, elle, n'accepte que du JSON. Le corps arrive déjà sérialisé
     * parce que sa forme dépend du client usurpé, pas de la requête.
     */
    val json: String? = null,
    /**
     * Suivre les redirections. À passer à false quand la chaîne doit être
     * déroulée à la main : VOE fait rebondir le client sur 28 alias successifs,
     * bien au-delà du plafond de 20 codé en dur dans OkHttp.
     */
    val followRedirects: Boolean = true,
    val profile: NetworkProfile = NetworkProfile.DEFAULT,
    /**
     * Rendre le corps en **octets** plutôt qu'en texte.
     *
     * Tout le reste de la cascade lit des pages et des manifestes, donc du
     * texte ; l'en-tête d'un MP4 est le seul cas contraire, et il ne survit pas
     * au détour par une chaîne. OkHttp décode selon le `Content-Type`, à défaut
     * en UTF-8 : les octets invalides deviennent alors U+FFFD, et une taille de
     * boîte lue à travers cette substitution envoie l'analyseur n'importe où.
     *
     * Le piège est qu'il ne se voit pas : les définitions courantes (1080 =
     * `0x0438`, 720 = `0x02D0`) ne portent que des octets ASCII et traversent la
     * conversion intactes. Une mesure qui marche sur les cas qu'on essaie et
     * casse ailleurs est pire qu'une mesure absente.
     */
    val binary: Boolean = false,
)

data class HttpResponse(
    val status: Int,
    /**
     * URL réellement servie, après redirections. Elle diffère de celle demandée
     * chez les hébergeurs à alias tournants, et c'est elle qui doit servir de
     * base au Referer attendu par leur CDN.
     */
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    /** Renseigné à la place de [body] quand [HttpRequest.binary] le demande. */
    val bytes: ByteArray? = null,
) {
    val isSuccessful: Boolean get() = status in 200..299
    val isRedirect: Boolean get() = status in 300..399

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/**
 * Seule porte de sortie réseau du domaine des sources.
 *
 * Les extracteurs ne connaissent plus OkHttp : ils décrivent une requête, on
 * leur rend une réponse. Deux bénéfices concrets — un extracteur se teste avec
 * une passerelle en dur, sans réseau ni site vivant ; et la stratégie de
 * connexion (DNS, redirections, empreinte TLS) se change en un seul endroit.
 */
fun interface HttpGateway {
    /** null si la requête n'a pas abouti du tout (réseau, timeout, URL invalide). */
    suspend fun fetch(request: HttpRequest): HttpResponse?
}

/** Cas dominant : un GET dont on ne veut que le corps, ou null. */
suspend fun HttpGateway.getBody(
    url: String,
    headers: Map<String, String> = emptyMap(),
    profile: NetworkProfile = NetworkProfile.DEFAULT,
): String? = fetch(HttpRequest(url = url, headers = headers, profile = profile))
    ?.takeIf { it.isSuccessful }
    ?.body
