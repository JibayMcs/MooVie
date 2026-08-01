package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.core.sources.port.SourceExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracteur sibnet.ru — port de sibnet_extract_handler (proxiesembed).
 * Récupère la page embed, extrait le chemin `.mp4` via `player.src([{src:…}])`,
 * préfixe `https://video.sibnet.ru`. NB : Sibnet géo-bloque parfois hors RU ;
 * le backend passe par un SOCKS5 — en natif on tente en direct.
 */
class SibnetExtractor(private val http: HttpGateway) : SourceExtractor {

    override val hoster = "sibnet"

    override fun canHandle(url: String): Boolean = url.contains("sibnet.ru", ignoreCase = true)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        runCatching {
            val html = http.getBody(
                link.url,
                mapOf(
                    "User-Agent" to Ua.BROWSER,
                    "Referer" to "https://video.sibnet.ru/",
                    "Accept" to "text/html,*/*",
                ),
            ) ?: return@runCatching null

            val path = MP4.find(html)?.groupValues?.get(1) ?: return@runCatching null
            val mp4Url = if (path.startsWith("http")) path else "https://video.sibnet.ru$path"

            PlayableStream(
                url = mp4Url,
                format = StreamFormat.MP4,
                headers = mapOf("Referer" to "https://video.sibnet.ru/", "User-Agent" to Ua.BROWSER),
                language = link.language,
            )
        }.getOrNull()
    }

    companion object {
        private val MP4 = Regex("""player\.src\(\[\{\s*src:\s*["']([^"']+\.mp4[^"']*)["']""")
    }
}
