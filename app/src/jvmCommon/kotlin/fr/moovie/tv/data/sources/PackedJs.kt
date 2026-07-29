package fr.moovie.tv.data.sources

import java.net.URI
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Dé-obfuscation des players fsvid / vidzy. Deux couches :
 *
 * 1. Packer « Dean Edwards » (p,a,c,k,e,d) — port de _deobfuscate_fsvid_script.
 * 2. Nouveau format (depuis 07/2026) : l'URL m3u8 n'est plus en clair dans le
 *    script dé-packé mais encodée en **Base64 URL-safe + XOR** (clé = tableau
 *    d'octets). Port de _extract_m3u8_url (API/proxiesembed/server.py, commit
 *    Movix 4d74237 « restore Fsvid, Vidzy and Uqload extraction »).
 *
 * On dé-packe puis on cherche l'URL : d'abord le format XOR, sinon les motifs
 * hérités (URL m3u8 directe).
 */
@OptIn(ExperimentalEncodingApi::class)
object PackedJs {

    private val PACKED = Regex(
        """eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.+?)',(\d+),(\d+),'(.+?)'\.""",
        RegexOption.DOT_MATCHES_ALL,
    )

    // Nouveau format : (function(s){var k=[12,34,...], b=atob(s), ... XOR ...})("payload")
    // NB : les littéraux `]` et `}` sont échappés — le moteur regex ICU d'Android
    // (contrairement au JVM desktop) rejette un `]`/`}` isolé (PatternSyntaxException).
    private val XOR = Regex(
        """var\s+[A-Za-z_$][\w$]*\s*=\s*\[([0-9,\s]+)\]\s*,""" +
            """\s*[A-Za-z_$][\w$]*\s*=\s*atob\(\s*[A-Za-z_$][\w$]*\s*\)""" +
            """[\s\S]{0,2000}?\}\)\s*\(\s*["']([A-Za-z0-9+/_=-]+)["']\s*\)""",
    )

    private val M3U8_PATTERNS = listOf(
        Regex("""src:\s*["']([^"']+\.m3u8[^"']*)["']"""),
        Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']"""),
        Regex("""sources:\s*\[\s*\{[^}]*?["']([^"']+\.m3u8[^"']*)["']"""),
        Regex("""["']([^"']*\.m3u8[^"']*)["']"""),
    )

    // fsvid/vidzy encodent en base64 standard (+/) ; on garde l'URL-safe (-_) en
    // secours. On n'utilise PAS Base64.withPadding()/PaddingOption (API Kotlin 2.0
    // absente du runtime Android bundlé → ExceptionInInitializerError) : on pade
    // le payload manuellement à un multiple de 4 avant decode().
    private val stdB64 = Base64.Default
    private val urlB64 = Base64.UrlSafe

    /**
     * Dé-obfusque le HTML d'un embed et en extrait l'URL m3u8, ou null.
     * @param embedUrl URL de la page d'embed, pour résoudre une URL relative.
     */
    fun findM3u8(html: String, embedUrl: String): String? {
        val script = (unpack(html) ?: html).replace("""\'""", "'").replace("""\"""", "\"")
        return extractXor(script, embedUrl) ?: extractLegacy(script, embedUrl)
    }

    /** Décode le format Base64/XOR. */
    private fun extractXor(script: String, embedUrl: String): String? {
        for (m in XOR.findAll(script)) {
            val key = m.groupValues[1].split(",").mapNotNull { it.trim().toIntOrNull() }
            if (key.isEmpty() || key.size > 64 || key.any { it < 0 || it > 255 }) continue

            var payload = m.groupValues[2]
            payload += "=".repeat((4 - payload.length % 4) % 4)
            val bytes = runCatching { stdB64.decode(payload) }.getOrNull()
                ?: runCatching { urlB64.decode(payload) }.getOrNull()
                ?: continue

            val decoded = ByteArray(bytes.size) { i ->
                (bytes[i].toInt() xor key[i % key.size]).toByte()
            }.toString(Charsets.UTF_8)

            normalize(decoded, embedUrl)?.let { return it }
        }
        return null
    }

    /** Motifs hérités : URL m3u8 directe dans le script. */
    private fun extractLegacy(script: String, embedUrl: String): String? =
        M3U8_PATTERNS.firstNotNullOfOrNull { p ->
            p.find(script)?.groupValues?.get(1)?.let { normalize(it, embedUrl) }
        }

    /** Nettoie une URL candidate ; ne retourne que si elle contient bien `.m3u8`. */
    private fun normalize(candidate: String, embedUrl: String): String? {
        val c = candidate.replace("""\/""", "/").replace("&amp;", "&").trim().trimEnd('\\')
        if (!c.contains(".m3u8", ignoreCase = true)) return null
        if (c.startsWith("http://") || c.startsWith("https://")) return c
        if (c.startsWith("/") || c.startsWith("./") || c.startsWith("../")) {
            return runCatching { URI(embedUrl).resolve(c).toString() }.getOrNull()
        }
        return null
    }

    /** Dé-packe un script « Dean Edwards » présent dans le HTML, ou null (réutilisé par uqload). */
    fun unpackHtml(html: String): String? = unpack(html)

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
