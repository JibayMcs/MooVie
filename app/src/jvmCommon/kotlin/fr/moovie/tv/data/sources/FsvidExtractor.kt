package fr.moovie.tv.data.sources

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

    override fun canHandle(url: String): Boolean = url.contains("fsvid.lol", ignoreCase = true)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(link.url)
            .header("User-Agent", Ua.BROWSER)
            .header("Referer", "https://fsvid.lol/")
            .header("Accept", "text/html,*/*")
            .build()

        runCatching {
            val html = http.newCall(req).execute().use {
                if (it.isSuccessful) it.body?.string() else null
            } ?: return@runCatching null
            val m3u8 = PackedJs.findM3u8(html) ?: return@runCatching null
            PlayableStream(
                url = m3u8,
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://fsvid.lol/",
                    "Origin" to "https://fsvid.lol",
                    "User-Agent" to Ua.BROWSER,
                ),
                language = link.language,
            )
        }.getOrNull()
    }
}
