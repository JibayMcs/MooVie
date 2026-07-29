package fr.moovie.tv.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Extracteur vidzy.org — même packer que fsvid (port de vidzy_extract_handler).
 */
class VidzyExtractor(private val http: OkHttpClient) : SourceExtractor {

    override val hoster = "vidzy"

    override fun canHandle(url: String): Boolean = url.contains("vidzy", ignoreCase = true)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        val origin = originOf(link.url, "https://vidzy.org")
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
