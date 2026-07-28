package fr.moovie.tv.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Extracteur VOE.sx — EXEMPLE DE PATTERN (portage de voe_m3u8_handler dans
 * API/proxiesembed/server.py). Récupère la page d'embed avec un User-Agent
 * navigateur puis extrait l'URL m3u8 par regex.
 *
 * NOTE : VOE obfusque régulièrement son JS ; la regex ci-dessous est un point
 * de départ à ajuster contre le vrai handler Python. C'est le prototype qui
 * montre comment chaque hébergeur se porte en Kotlin (OkHttp + regex/crypto),
 * sans navigateur ni proxy CORS.
 */
class VoeExtractor(private val http: OkHttpClient) : SourceExtractor {

    override val hoster = "voe"

    private val hostPattern = Regex("""voe\.sx|voe-un-block|robertordercharacterbetter""", RegexOption.IGNORE_CASE)
    private val m3u8Pattern = Regex(""""(https?://[^"']+\.m3u8[^"']*)"""")

    override fun canHandle(url: String): Boolean = hostPattern.containsMatchIn(url)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(link.url)
            .header("User-Agent", BROWSER_UA)
            .header("Referer", link.url)
            .build()

        runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val m3u8 = m3u8Pattern.find(body)?.groupValues?.get(1) ?: return@use null
                PlayableStream(
                    url = m3u8,
                    format = StreamFormat.HLS,
                    headers = mapOf("Referer" to link.url, "User-Agent" to BROWSER_UA),
                    language = link.language,
                )
            }
        }.getOrNull()
    }

    companion object {
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
    }
}
