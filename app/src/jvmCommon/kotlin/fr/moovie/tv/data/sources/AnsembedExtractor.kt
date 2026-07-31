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
 * Extracteur ansembed.net (lecteur d'anime-sama) — jwplayer avec le m3u8 en clair
 * dans `sources: [{ file: '…m3u8' }]`. Pas d'obfuscation.
 */
class AnsembedExtractor(private val http: OkHttpClient) : SourceExtractor {

    override val hoster = "ansembed"

    override fun canHandle(url: String): Boolean = url.contains("ansembed", ignoreCase = true)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(link.url)
                .header("User-Agent", Ua.BROWSER)
                .header("Referer", "https://anime-sama.to/")
                .build()
            val html = http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                ?: return@runCatching null
            val m3u8 = FILE.find(html)?.groupValues?.get(1) ?: return@runCatching null
            PlayableStream(
                url = m3u8,
                format = StreamFormat.HLS,
                headers = mapOf("Referer" to "https://ansembed.net/", "Origin" to "https://ansembed.net", "User-Agent" to Ua.BROWSER),
                language = link.language,
            )
        }.getOrNull()
    }

    companion object {
        private val FILE = Regex("""file:\s*['"]([^'"]+\.m3u8[^'"]*)['"]""")
    }
}
