package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.core.sources.port.SourceExtractor
import fr.moovie.tv.shared.dispatcherEs
import kotlinx.coroutines.withContext

/**
 * Extracteur vidzy.org — même packer que fsvid (port de vidzy_extract_handler).
 */
class VidzyExtractor(private val http: HttpGateway) : SourceExtractor {

    override val hoster = "vidzy"

    override fun canHandle(url: String): Boolean = url.contains("vidzy", ignoreCase = true)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(dispatcherEs) {
        val origin = originOf(link.url, "https://vidzy.org")

        runCatching {
            val html = http.getBody(
                link.url,
                mapOf(
                    "User-Agent" to Ua.BROWSER,
                    "Referer" to "$origin/",
                    "Accept" to "text/html,*/*",
                ),
            ) ?: return@runCatching null
            val m3u8 = PackedJs.findM3u8(html, link.url) ?: return@runCatching null
            PlayableStream(
                url = m3u8,
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "$origin/",
                    "Origin" to origin,
                    "User-Agent" to Ua.BROWSER,
                ),
                language = link.language,
            )
        }.getOrNull()
    }
}
