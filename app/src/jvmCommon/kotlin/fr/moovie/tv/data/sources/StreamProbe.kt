package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.core.sources.usecase.isDurationAcceptable
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.usecase.hlsHeight
import fr.moovie.tv.core.sources.usecase.qualityLabel

/**
 * Vérifie qu'un flux résolu est réellement joignable.
 *
 * Sans ce contrôle, la cascade de sources s'arrêtait dès qu'un extracteur
 * rendait une URL non vide — même si l'hébergeur répondait 403 derrière, ou si
 * l'extracteur avait ramassé une URL de gabarit périmée laissée dans la page
 * (constaté sur waaw.to : jeton expiré en 2020, hôte qui ne résout plus). Le
 * lecteur s'ouvrait alors sur un lien mort et affichait « lecture impossible »,
 * en laissant l'utilisateur tester les sources une par une.
 *
 * Les en-têtes du flux (Referer / Origin / User-Agent) sont indispensables :
 * ces hébergeurs refusent toute requête qui n'en vient pas.
 */
suspend fun isStreamPlayable(
    stream: PlayableStream,
    expectedMinutes: Int? = null,
    http: HttpGateway = ExtractorRegistry.gateway,
): Boolean {
    if (stream.url.isBlank()) return false

    // HEAD d'abord : rien à télécharger si l'hôte le supporte.
    val reachable = probe(http, stream, head = true)
    // Certains hôtes répondent 405/501 à HEAD, ou l'ignorent : on retombe sur un
    // GET borné aux deux premiers octets, assez pour valider l'accès.
        ?: probe(http, stream, head = false) ?: false
    if (!reachable) return false

    // Joignable ne veut pas dire « c'est bien le média demandé ». Certaines
    // sources servent un logo animé ou une bande-annonce de quelques secondes à
    // la place du film : le lecteur s'ouvre, lit dix secondes et s'arrête.
    // Relevé sur Dune (155 min) : trois liens « premium » mesuraient moins
    // d'une minute. La durée n'est mesurable que sur HLS ; ailleurs on laisse
    // passer plutôt que d'écarter à l'aveugle.
    if (expectedMinutes == null) return true
    return isDurationAcceptable(hlsDurationSeconds(http, stream), expectedMinutes)
}

/**
 * Durée totale d'un flux HLS, en secondes, ou null si non mesurable.
 *
 * Somme des `#EXTINF` de la playlist média. Une master playlist ne liste que des
 * variantes : on descend alors dans la première, qui suffit — toutes les
 * variantes d'un même média ont la même durée.
 */
private suspend fun hlsDurationSeconds(http: HttpGateway, stream: PlayableStream): Double? {
    // **Avant de télécharger quoi que ce soit.** Le corps est lu en entier, en
    // mémoire, pour vérifier ces sept caractères — anodin sur une playlist de
    // quelques kilo-octets, fatal sur un fichier. Un MP4 progressif de 1,24 Go a
    // tué l'application sur la box : `OutOfMemoryError` après avoir rempli les
    // 500 Mo du tas, pendant l'ouverture du lecteur.
    //
    // Le format était déjà la bonne garde — la fonction s'appelle « hls » et sa
    // documentation dit que la durée n'est mesurable que là — mais elle n'était
    // écrite nulle part. `streamQuality`, juste en dessous, la pose au même
    // endroit et pour la même raison.
    if (stream.format != StreamFormat.HLS) return null
    val body = http.getBody(stream.url, stream.headers) ?: return null
    if (!body.startsWith("#EXTM3U")) return null

    sumExtInf(body)?.let { return it }

    val variant = body.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        ?: return null
    val absolute = if (variant.startsWith("http")) variant
    else stream.url.substringBefore('?').substringBeforeLast('/') + "/" + variant

    return http.getBody(absolute, stream.headers)?.let { sumExtInf(it) }
}

private fun sumExtInf(playlist: String): Double? {
    val total = EXTINF.findAll(playlist).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
    return if (EXTINF.containsMatchIn(playlist)) total else null
}

private val EXTINF = Regex("""#EXTINF:\s*([\d.]+)""")

/**
 * Qualité annoncée par un flux, ou null si illisible.
 *
 * Coûteuse par nature : aucun catalogue n'annonce la qualité en listant ses
 * liens, il faut donc résoudre l'embed puis lire la master playlist. À n'appeler
 * qu'en arrière-plan, et à mettre en cache — sans quoi on doublerait le trafic
 * vers les hébergeurs pour un simple libellé.
 */
suspend fun streamQuality(
    stream: PlayableStream,
    http: HttpGateway = ExtractorRegistry.gateway,
): String? {
    if (stream.format != StreamFormat.HLS) return null
    val body = http.getBody(stream.url, stream.headers) ?: return null
    return qualityLabel(hlsHeight(body))
}

/** Retourne null quand la méthode elle-même est refusée (à réessayer autrement). */
private suspend fun probe(http: HttpGateway, stream: PlayableStream, head: Boolean): Boolean? {
    val resp = http.fetch(
        HttpRequest(
            url = stream.url,
            method = if (head) HttpMethod.HEAD else HttpMethod.GET,
            headers = if (head) stream.headers else stream.headers + ("Range" to "bytes=0-1"),
        ),
    ) // Échec réseau : sur un HEAD on laisse sa chance au GET, sinon c'est mort.
        ?: return if (head) null else false

    // 405 / 501 : la méthode est refusée, pas la ressource.
    if (head && (resp.status == 405 || resp.status == 501)) return null
    if (!resp.isSuccessful) return false

    // Un 200 ne suffit pas. Un extracteur peut produire une URL bien formée que
    // l'hébergeur sert en page d'erreur HTML : ExoPlayer la refuse ensuite en
    // UnrecognizedInputFormatException, et l'utilisateur voit « lecture
    // impossible » sur une source annoncée bonne. Constaté sur DoodStream, dont
    // l'URL calculée renvoie du text/html.
    return isPlayableContentType(resp.header("Content-Type"))
}

/**
 * Un type de contenu compatible avec un flux vidéo.
 *
 * Volontairement permissif sur l'absence d'en-tête : beaucoup de CDN n'en
 * envoient pas sur un HEAD, et refuser par défaut écarterait des sources
 * valides. On ne rejette que ce qui est *manifestement* autre chose.
 */
internal fun isPlayableContentType(contentType: String?): Boolean {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return true
    if (type.isEmpty()) return true
    return when {
        type.startsWith("video/") || type.startsWith("audio/") -> true
        type.startsWith("application/") -> "html" !in type && "json" !in type && "xhtml" !in type
        type.startsWith("binary/") -> true
        // text/html, text/plain… : page d'erreur, jamais un flux.
        else -> false
    }
}
