package fr.moovie.tv.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Extracteur uqload — port de _extract_uqload_mp4_url (API/proxiesembed/server.py).
 * Normalise l'URL en `embed-<id>.html`, essaie la variante sans `embed-`, puis
 * extrait l'URL `.../v.mp4` du HTML.
 */
class UqloadExtractor(private val http: OkHttpClient) : SourceExtractor {

    override val hoster = "uqload"

    override fun canHandle(url: String): Boolean = url.contains("uqload", ignoreCase = true)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        val validated = validateUrl(link.url) ?: return@withContext null
        val candidates = listOf(validated, validated.replace("embed-", ""))

        val html = candidates.firstNotNullOfOrNull { url ->
            runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", Ua.BROWSER)
                    .header("Accept", "text/html,*/*")
                    .build()
                http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull()
        } ?: return@withContext null

        if ("File was deleted" in html) return@withContext null
        val mp4 = MP4.find(html)?.value ?: return@withContext null

        PlayableStream(
            url = mp4,
            format = StreamFormat.MP4,
            headers = mapOf(
                "Referer" to "https://uqload.net/",
                "User-Agent" to Ua.BROWSER,
            ),
            language = link.language,
        )
    }

    /** Normalise en `<base>/embed-<id>.html` (port de _validate_uqload_url). */
    private fun validateUrl(url: String): String? {
        if (url.length < 12) return null
        val parts = url.split("/")
        val base = parts.dropLast(1).joinToString("/").ifEmpty { "https://uqload.bz" }
        var videoId = parts.last()
        if (!videoId.contains(".html")) videoId += ".html"
        if (!videoId.contains("embed-")) videoId = "embed-$videoId"
        val full = "$base/$videoId"
        return if (full.contains("uqload")) full else null
    }

    companion object {
        private val MP4 = Regex("""https?://[^\s"']+/v\.mp4""")
    }
}
