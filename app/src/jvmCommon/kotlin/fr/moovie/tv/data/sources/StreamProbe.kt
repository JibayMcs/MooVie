package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.PlayableStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Vérifie qu'un flux résolu est réellement joignable.
 *
 * Sans ce contrôle, la cascade de sources s'arrêtait dès qu'un extracteur
 * rendait une URL non vide — même si l'hébergeur répondait 403 derrière. Le
 * lecteur s'ouvrait alors sur un lien mort et affichait « lecture impossible »,
 * en laissant l'utilisateur tester les sources une par une.
 *
 * Les en-têtes du flux (Referer / Origin / User-Agent) sont indispensables :
 * ces hébergeurs refusent toute requête qui n'en vient pas.
 */
suspend fun isStreamPlayable(
    stream: PlayableStream,
    http: OkHttpClient = ExtractorRegistry.http,
): Boolean = withContext(Dispatchers.IO) {
    if (stream.url.isBlank()) return@withContext false

    // HEAD d'abord : rien à télécharger si l'hôte le supporte.
    probe(http, stream, head = true)?.let { return@withContext it }
    // Certains hôtes répondent 405/501 à HEAD, ou l'ignorent : on retombe sur un
    // GET borné aux deux premiers octets, assez pour valider l'accès.
    probe(http, stream, head = false) ?: false
}

/** Retourne null quand la méthode elle-même est refusée (à réessayer autrement). */
private fun probe(http: OkHttpClient, stream: PlayableStream, head: Boolean): Boolean? {
    val builder = Request.Builder().url(stream.url)
    stream.headers.forEach { (name, value) -> builder.header(name, value) }
    if (head) builder.head() else builder.header("Range", "bytes=0-1")

    return runCatching {
        http.newCall(builder.build()).execute().use { response ->
            // 405 / 501 : la méthode est refusée, pas la ressource.
            if (head && (response.code == 405 || response.code == 501)) return@use null
            response.isSuccessful
        }
    }.getOrElse {
        // Échec réseau : sur un HEAD on laisse sa chance au GET, sinon c'est mort.
        if (head) null else false
    }
}
