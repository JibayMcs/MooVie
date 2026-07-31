package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.SourceExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Extracteur fsvid.lol — page d'embed → dé-obfuscation du packer → m3u8.
 * Port de fsvid_extract_handler (API/proxiesembed/server.py).
 */
class FsvidExtractor(private val http: OkHttpClient) : SourceExtractor {

    override val hoster = "fsvid"

    // "fsvid" en substring (et non "fsvid.lol") : l'hôte peut changer de TLD.
    override fun canHandle(url: String): Boolean = url.contains("fsvid", ignoreCase = true)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        val origin = originOf(link.url, "https://fsvid.lol")
        val req = Request.Builder()
            .url(link.url)
            .header("User-Agent", Ua.BROWSER)
            .header("Referer", "$origin/")
            .header("Accept", "text/html,*/*")
            .build()

        runCatching {
            val html = http.newCall(req).execute().use {
                if (it.isSuccessful) it.body?.string() else null
            } ?: return@runCatching null
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
