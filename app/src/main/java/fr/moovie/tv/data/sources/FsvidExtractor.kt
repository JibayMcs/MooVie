package fr.moovie.tv.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Extracteur fsvid.lol — portage de fsvid_extract_handler / _deobfuscate_fsvid_script
 * (API/proxiesembed/server.py). Récupère la page d'embed, dé-obfusque le JS packé
 * (packer Dean Edwards `eval(function(p,a,c,k,e,d)…)`), puis extrait l'URL m3u8.
 */
class FsvidExtractor(private val http: OkHttpClient) : SourceExtractor {

    override val hoster = "fsvid"

    override fun canHandle(url: String): Boolean = url.contains("fsvid.lol", ignoreCase = true)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(link.url)
            .header("User-Agent", UA)
            .header("Referer", "https://fsvid.lol/")
            .header("Accept", "text/html,*/*")
            .build()

        runCatching {
            val html = http.newCall(req).execute().use {
                if (it.isSuccessful) it.body?.string() else null
            } ?: return@runCatching null

            val packed = PACKED.find(html)?.value ?: return@runCatching null
            val unpacked = unpack(packed) ?: return@runCatching null
            val m3u8 = M3U8_PATTERNS.firstNotNullOfOrNull { it.find(unpacked)?.groupValues?.get(1) }
                ?: return@runCatching null

            PlayableStream(
                url = m3u8,
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://fsvid.lol/",
                    "Origin" to "https://fsvid.lol",
                    "User-Agent" to UA,
                ),
                language = link.language,
            )
        }.getOrNull()
    }

    /** Dé-obfuscation du packer p,a,c,k,e,d (réécriture 1:1 du handler Python). */
    private fun unpack(script: String): String? {
        val m = UNPACK.find(script) ?: return null
        val payload = m.groupValues[1]
        val base = m.groupValues[2].toIntOrNull() ?: return null
        var count = m.groupValues[3].toIntOrNull() ?: return null
        val dict = m.groupValues[4].split("|")

        var result = payload
        while (count > 0) {
            count--
            val word = dict.getOrNull(count)
            if (!word.isNullOrEmpty()) {
                val token = toBase(count, base)
                result = result.replace(Regex("\\b" + Regex.escape(token) + "\\b"), word)
            }
        }
        return result
    }

    private fun toBase(num: Int, base: Int): String {
        if (num == 0) return "0"
        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var n = num
        val sb = StringBuilder()
        while (n > 0) {
            sb.insert(0, chars[n % base])
            n /= base
        }
        return sb.toString()
    }

    companion object {
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val PACKED = Regex(
            """eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.+?)',\d+,\d+,'[^']+'\.""",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val UNPACK = Regex(
            """eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.+?)',(\d+),(\d+),'(.+?)'\.""",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val M3U8_PATTERNS = listOf(
            Regex("""src:\s*["']([^"']+\.m3u8[^"']*)["']"""),
            Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']"""),
            Regex("""sources:\s*\[\s*\{[^}]*?["']([^"']+\.m3u8[^"']*)["']"""),
            Regex("""["']([^"']*\.m3u8[^"']*)["']"""),
        )
    }
}
