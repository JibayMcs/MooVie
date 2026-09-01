package fr.moovie.tv.data.sources

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Dé-obfuscation des players fsvid / vidzy. Trois couches, essayées dans l'ordre :
 *
 * 1. Packer « Dean Edwards » (p,a,c,k,e,d) — port de _deobfuscate_fsvid_script.
 * 2. Base64 + XOR à **clé fixe** (tableau d'octets répété), format de 07/2026.
 *    Port de _extract_m3u8_url (commit Movix 4d74237).
 * 3. Base64 + XOR à **clé glissante**, format de 08/2026— voir [extractRolling].
 *
 * ⚠️ **La page contient une URL leurre.** Le décodeur du site se termine par
 * `return /^https?:/.test(r) ? r : "https://s1.fsvid.lol/troll/master.m3u8"`, et
 * cette chaîne est présente en clair dans le script. Le motif hérité fourre-tout
 * (`"…m3u8"` n'importe où) la ramassait donc dès que le vrai décodage échouait :
 * on ne renvoyait pas « pas de source », on renvoyait **une source valide qui
 * n'est pas le film**. Elle répond 200 en `application/vnd.apple.mpegurl` et
 * dure une vingtaine de secondes — seul le contrôle de durée de `isStreamPlayable`
 * l'attrapait. D'où [isDecoy], appliqué à toutes les couches.
 */
@OptIn(ExperimentalEncodingApi::class)
object PackedJs {

    // `[\s\S]` plutôt que `.` sous RegexOption.DOT_MATCHES_ALL, qui n'existe
    // que sur la JVM : la classe de caractères dit la même chose à tous les
    // moteurs. Le script empaqueté tient sur plusieurs lignes, ces `.` doivent
    // donc bien franchir les retours à la ligne.
    private val PACKED = Regex(
        """eval\(function\(p,a,c,k,e,d\)\{[\s\S]*?\}\('([\s\S]+?)',(\d+),(\d+),'([\s\S]+?)'\.""",
    )

    // Nouveau format : (function(s){var k=[12,34,...], b=atob(s), ... XOR ...})("payload")
    // NB : les littéraux `]` et `}` sont échappés — le moteur regex ICU d'Android
    // (contrairement au JVM desktop) rejette un `]`/`}` isolé (PatternSyntaxException).
    private val XOR = Regex(
        """var\s+[A-Za-z_$][\w$]*\s*=\s*\[([0-9,\s]+)\]\s*,""" +
            """\s*[A-Za-z_$][\w$]*\s*=\s*atob\(\s*[A-Za-z_$][\w$]*\s*\)""" +
            """[\s\S]{0,2000}?\}\)\s*\(\s*["']([A-Za-z0-9+/_=-]+)["']\s*\)""",
    )

    /**
     * Littéraux Base64 candidats. Le seuil écarte le bruit du script (jetons de
     * licence, identifiants) : une URL HLS signée fait dans les 150 caractères
     * une fois encodée, jamais moins de 32.
     */
    private val B64_LITERAL = Regex("""["']([A-Za-z0-9+/=_-]{32,})["']""")
    private const val MIN_PAYLOAD = 24

    /** Masques plausibles, du plus courant au plus rare (255 observé sur fsvid/vidzy). */
    private val MASKS = listOf(255, 127, 63, 31)

    /** Le décodeur du site garantit lui-même un schéma HTTP (`/^https?:/`). */
    private val KNOWN_PREFIXES = listOf("https://", "http://")

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
        return extractXor(script, embedUrl)
            ?: extractRolling(script, embedUrl)
            ?: extractLegacy(script, embedUrl)
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
            }.decodeToString()

            normalize(decoded, embedUrl)?.let { return it }
        }
        return null
    }

    /**
     * Décode le format à **clé glissante** (fsvid / vidzy, 08/2026).
     *
     * Le player embarque son propre déchiffreur :
     *
     * ```js
     * (function(s){
     *   var h=(location&&location.hostname)||"",H=0;
     *   for(var j=0;j<h.length;j++){H=(H+h.charCodeAt(j))&255}
     *   var b=atob(s),a=b.split("").reverse().join(""),r="";
     *   for(var i=0;i<a.length;i++){var kk=(0x3d+i*89+H)&255;r+=String.fromCharCode(a.charCodeAt(i)^kk)}
     *   return/^https?:/.test(r)?r:"…/troll/master.m3u8"
     * })("shSgBDj1QiOHCC6…")
     * ```
     *
     * La clé n'est plus constante : elle avance d'un pas à chaque octet, et sa
     * graine dépend du **nom d'hôte de la page**.
     *
     * **On ne lit ni la graine, ni le pas, ni le masque dans la page — on les
     * retrouve.** Le clair commence forcément par `https://` (le décodeur du site
     * teste `/^https?:/` avant de rendre la main), ce qui donne autant d'équations
     * que d'octets connus. Deux suffisent à résoudre une clé affine ; les six
     * suivants la vérifient.
     *
     * C'est plus court que rejouer l'expression JavaScript, et surtout plus
     * robuste sur les deux points où elle casse : le renommage des variables à
     * chaque rotation, et le nom d'hôte. Ce dernier est un vrai piège — la graine
     * est celle du domaine pour lequel la page a été *servie*, que rien ne garantit
     * égal à celui par lequel on est arrivé (alias, redirection : le catalogue
     * nous donne `vidzy.cc` pendant que la page se présente comme `vidzy.org`).
     * Ne pas en dépendre, c'est ne pas avoir à le deviner.
     *
     * Les quatre variantes du site — inversion de la chaîne avant le XOR, après,
     * les deux, aucune — se ramènent à deux essais : quel que soit le sens de
     * parcours, la clé reste affine en l'indice, seuls les octets changent d'ordre.
     */
    private fun extractRolling(script: String, embedUrl: String): String? {
        for (m in B64_LITERAL.findAll(script)) {
            val bytes = decodeB64(m.groupValues[1]) ?: continue
            if (bytes.size < MIN_PAYLOAD) continue
            for (ordered in listOf(bytes, bytes.reversedArray())) {
                for (mask in MASKS) {
                    for (known in KNOWN_PREFIXES) {
                        solveRolling(ordered, mask, known)
                            ?.let { plain -> normalize(plain, embedUrl)?.let { return it } }
                    }
                }
            }
        }
        return null
    }

    /**
     * Retrouve `(graine, pas)` à partir des deux premiers octets connus, puis
     * déchiffre. Rend null dès que l'hypothèse ne tient pas.
     */
    private fun solveRolling(cipher: ByteArray, mask: Int, known: String): String? {
        val seed = (cipher[0].toInt() and 0xFF) xor known[0].code
        val next = (cipher[1].toInt() and 0xFF) xor known[1].code
        // Une clé masquée ne peut pas dépasser son masque : au-delà, c'est le
        // masque supposé qui est faux, inutile de déchiffrer 100 octets pour
        // s'en apercevoir.
        if (seed > mask || next > mask) return null
        val step = (next - seed) and mask

        val plain = ByteArray(cipher.size) { i ->
            ((cipher[i].toInt() and 0xFF) xor ((seed + i * step) and mask)).toByte()
        }.decodeToString()
        // Les octets connus non consommés par la résolution valident le tout.
        return plain.takeIf { it.startsWith(known) }
    }

    /** Base64 standard, puis URL-safe en secours. Padding ajouté à la main (cf. plus haut). */
    private fun decodeB64(raw: String): ByteArray? {
        val payload = raw + "=".repeat((4 - raw.length % 4) % 4)
        return runCatching { stdB64.decode(payload) }.getOrNull()
            ?: runCatching { urlB64.decode(payload) }.getOrNull()
    }

    /** Motifs hérités : URL m3u8 directe dans le script. */
    private fun extractLegacy(script: String, embedUrl: String): String? =
        M3U8_PATTERNS.firstNotNullOfOrNull { p ->
            p.find(script)?.groupValues?.get(1)?.let { normalize(it, embedUrl) }
        }

    /**
     * L'URL leurre plantée dans la page (`…/troll/master.m3u8`).
     *
     * Le site l'annonce lui-même comme sa valeur de repli : la reconnaître au
     * segment `troll` suffit, et c'est ce que fait aussi l'amont. Une URL
     * légitime qui contiendrait ce mot serait écartée à tort — on préfère perdre
     * ce cas improbable que servir un leurre, qui se lit à l'écran comme une
     * source qui marche jusqu'à ce qu'elle s'arrête au bout de vingt secondes.
     */
    private fun isDecoy(url: String) = url.contains("troll", ignoreCase = true)

    /** Nettoie une URL candidate ; ne retourne que si elle contient bien `.m3u8`. */
    private fun normalize(candidate: String, embedUrl: String): String? {
        val c = candidate.replace("""\/""", "/").replace("&amp;", "&").trim().trimEnd('\\')
        if (!c.contains(".m3u8", ignoreCase = true) || isDecoy(c)) return null
        if (c.startsWith("http://") || c.startsWith("https://")) return c
        if (c.startsWith("/") || c.startsWith("./") || c.startsWith("../")) {
            return resoudreRelatif(embedUrl, c)
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
