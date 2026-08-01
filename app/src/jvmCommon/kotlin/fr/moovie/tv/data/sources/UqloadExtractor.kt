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
 * Extracteur uqload — port de uqload_utils.py + _extract_uqload_media_url
 * (commit Movix 4d74237 « restore Fsvid, Vidzy and Uqload extraction »).
 *
 * Nouveautés vs l'ancien portage : multi-TLD (uqload.is/.bz/.cx/…), extraction
 * **HLS** (master.m3u8) en plus du MP4, dé-package Dean Edwards, et en-têtes
 * Referer/Origin alignés sur le domaine réel du lien.
 */
class UqloadExtractor(private val http: HttpGateway) : SourceExtractor {

    override val hoster = "uqload"

    override fun canHandle(url: String): Boolean = url.contains("uqload", ignoreCase = true)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        val validated = normalizeEmbedUrl(link.url) ?: return@withContext null
        val origin = siteOrigin(validated) ?: return@withContext null
        // On tente l'URL embed puis sa variante sans `/embed-` (page directe).
        val candidates = listOf(validated, validated.replace("/embed-", "/"))

        val html = candidates.firstNotNullOfOrNull { url ->
            http.getBody(
                url,
                mapOf(
                    "User-Agent" to Ua.BROWSER,
                    "Accept" to "text/html,*/*",
                    "Referer" to "$origin/",
                    "Origin" to origin,
                ),
            )
        } ?: return@withContext null

        if ("File was deleted" in html) return@withContext null
        val media = extractMediaUrl(html) ?: return@withContext null
        val isHls = media.contains(".m3u8", ignoreCase = true)

        PlayableStream(
            url = media,
            format = if (isHls) StreamFormat.HLS else StreamFormat.MP4,
            headers = mapOf(
                "Referer" to "$origin/",
                "Origin" to origin,
                "User-Agent" to Ua.BROWSER,
            ),
            language = link.language,
        )
    }

    /** Normalise en `https://<host>/embed-<id>.html`, ou null si domaine non autorisé. */
    private fun normalizeEmbedUrl(raw: String): String? {
        val url = raw.trim()
        val host = hostOf(url) ?: return null
        if (rootOf(host) == null) return null
        val last = url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        val id = last.replace(EMBED_PREFIX, "").replace(HTML_SUFFIX, "")
        if (!VIDEO_ID.matches(id)) return null
        return "https://$host/embed-$id.html"
    }

    private fun siteOrigin(url: String): String? = rootOf(hostOf(url))?.let { "https://$it" }

    /** Collecte les URLs uqload (HTML brut + script dé-packé) et privilégie le HLS. */
    private fun extractMediaUrl(html: String): String? {
        val candidates = mutableListOf<String>()
        collectUrls(html, candidates)
        PackedJs.unpackHtml(html)?.let { collectUrls(it, candidates) }
        for (pat in MEDIA_PRIORITY) {
            candidates.firstOrNull { pat.containsMatchIn(it) }?.let { return it }
        }
        return null
    }

    private fun collectUrls(text: String, out: MutableList<String>) {
        val norm = text.replace("""\/""", "/")
        for (m in HTTPS_URL.findAll(norm)) {
            val c = m.value.trimEnd(')', ',', ';')
            if (rootOf(hostOf(c)) != null) out.add(c)
        }
    }

    private fun hostOf(url: String): String? =
        HOST.find(url)?.groupValues?.get(1)?.lowercase()?.trimEnd('.')

    private fun rootOf(host: String?): String? {
        if (host == null) return null
        return ROOT_DOMAINS.firstOrNull { host == it || host.endsWith(".$it") }
    }

    companion object {
        private val ROOT_DOMAINS = listOf(
            "uqload.is", "uqload.bz", "uqload.cx", "uqload.com",
            "uqload.net", "uqload.org", "uqload.to", "uqload.io", "uqload.co",
        )
        private val HOST = Regex("""^https://([^/:]+)""", RegexOption.IGNORE_CASE)
        private val EMBED_PREFIX = Regex("""^embed-""", RegexOption.IGNORE_CASE)
        private val HTML_SUFFIX = Regex("""\.html$""", RegexOption.IGNORE_CASE)
        private val VIDEO_ID = Regex("""^[a-z0-9_-]+$""", RegexOption.IGNORE_CASE)
        private val HTTPS_URL = Regex("""https://[^\s"'\\<>]+""", RegexOption.IGNORE_CASE)
        private val MEDIA_PRIORITY = listOf(
            Regex("""/master\.m3u8([?#]|$)""", RegexOption.IGNORE_CASE),
            Regex("""\.m3u8([?#]|$)""", RegexOption.IGNORE_CASE),
            Regex("""/v\.mp4([?#]|$)""", RegexOption.IGNORE_CASE),
        )
    }
}
