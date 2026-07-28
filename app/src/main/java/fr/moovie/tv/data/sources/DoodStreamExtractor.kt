package fr.moovie.tv.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Extracteur DoodStream (dood.* / d000d.com…) — port de doodstream_extract_handler
 * (API/proxiesembed/server.py). Récupère la page d'embed, extrait le lien
 * `/pass_md5/…/token`, appelle ce endpoint pour obtenir l'URL de base, puis
 * construit l'URL finale du mp4 (base + 10 chars aléatoires + token + expiry).
 */
class DoodStreamExtractor(private val http: OkHttpClient) : SourceExtractor {

    override val hoster = "dood"

    override fun canHandle(url: String): Boolean =
        DOOD_HOST.containsMatchIn(url)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        runCatching {
            val domain = DOMAIN.find(link.url)?.groupValues?.get(1) ?: return@runCatching null

            val page = get(link.url, referer = "https://d0000d.com/") ?: return@runCatching null
            val match = PASS_MD5.find(page) ?: return@runCatching null
            val passPath = match.value                 // /pass_md5/xxx/token
            val token = match.groupValues[1]           // token

            val base = get("$domain$passPath", referer = domain)?.trim().orEmpty()
            if (base.isBlank()) return@runCatching null

            val rnd = (1..10).map { ALPHANUM.random() }.joinToString("")
            val expiry = System.currentTimeMillis()
            val videoUrl = "$base$rnd?token=$token&expiry=$expiry"

            PlayableStream(
                url = videoUrl,
                format = StreamFormat.MP4,
                headers = mapOf("Referer" to domain, "User-Agent" to Ua.BROWSER),
                language = link.language,
            )
        }.getOrNull()
    }

    private fun get(url: String, referer: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", Ua.BROWSER)
            .header("Referer", referer)
            .build()
        return http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }

    companion object {
        private val DOOD_HOST = Regex("""dood|d0{3,4}d|dooood|ds2play|doods""", RegexOption.IGNORE_CASE)
        private val DOMAIN = Regex("""^(https?://[^/]+)""")
        private val PASS_MD5 = Regex("""/pass_md5/[\w-]+/([\w-]+)""")
        private val ALPHANUM = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    }
}
