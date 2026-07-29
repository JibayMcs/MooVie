package fr.moovie.tv.data.sources

/**
 * Dé-obfuscation du packer JavaScript « Dean Edwards » (p,a,c,k,e,d), utilisé
 * par plusieurs hébergeurs (fsvid, vidzy…). Port 1:1 de _deobfuscate_fsvid_script
 * (API/proxiesembed/server.py). Trouve l'URL m3u8 dans le script dé-obfusqué.
 */
object PackedJs {

    private val PACKED = Regex(
        """eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.+?)',(\d+),(\d+),'(.+?)'\.""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val M3U8_PATTERNS = listOf(
        Regex("""src:\s*["']([^"']+\.m3u8[^"']*)["']"""),
        Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']"""),
        Regex("""sources:\s*\[\s*\{[^}]*?["']([^"']+\.m3u8[^"']*)["']"""),
        Regex("""["']([^"']*\.m3u8[^"']*)["']"""),
    )

    /** Dé-obfusque puis extrait l'URL m3u8 du HTML d'un embed, ou null. */
    fun findM3u8(html: String): String? {
        val unpacked = unpack(html) ?: return null
        return M3U8_PATTERNS.firstNotNullOfOrNull { it.find(unpacked)?.groupValues?.get(1) }
    }

    private fun unpack(html: String): String? {
        val m = PACKED.find(html) ?: return null
        val payload = m.groupValues[1]
        val base = m.groupValues[2].toIntOrNull() ?: return null
        var count = m.groupValues[3].toIntOrNull() ?: return null
        val dict = m.groupValues[4].split("|")

        var result = payload
        while (count > 0) {
            count--
            val word = dict.getOrNull(count)
            if (!word.isNullOrEmpty()) {
                result = result.replace(Regex("\\b" + Regex.escape(toBase(count, base)) + "\\b"), word)
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
}
