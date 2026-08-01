package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest

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
    http: HttpGateway = ExtractorRegistry.gateway,
): Boolean {
    if (stream.url.isBlank()) return false

    // HEAD d'abord : rien à télécharger si l'hôte le supporte.
    probe(http, stream, head = true)?.let { return it }
    // Certains hôtes répondent 405/501 à HEAD, ou l'ignorent : on retombe sur un
    // GET borné aux deux premiers octets, assez pour valider l'accès.
    return probe(http, stream, head = false) ?: false
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
    return resp.isSuccessful
}
