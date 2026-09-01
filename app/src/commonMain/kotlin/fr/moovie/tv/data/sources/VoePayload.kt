package fr.moovie.tv.data.sources

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Décodage de la charge utile VOE, isolé du réseau pour être testable.
 *
 * La page finale du lecteur contient
 * `<script type="application/json">["…"]</script>` dont le contenu passe par six
 * transformations empilées :
 *
 *   1. ROT13
 *   2. retrait de séquences de bruit (`@$`, `^^`, `~@`, `%?`, `*~`, `!!`, `#&`)
 *   3. décodage Base64
 *   4. décalage de chaque code de caractère de −3
 *   5. inversion de la chaîne
 *   6. décodage Base64 → JSON, dont le champ `source` est une master playlist
 *      HLS directement jouable (elle contient déjà `master.m3u8`, ne rien y
 *      concaténer).
 */
@OptIn(ExperimentalEncodingApi::class)
object VoePayload {

    // `[\s\S]` plutôt que `.` sous RegexOption.DOT_MATCHES_ALL : cette option
    // n'existe que sur la JVM. La classe de caractères dit la même chose à tous
    // les moteurs, sans dépendre d'un drapeau.
    private val PAYLOAD = Regex(
        """<script[^>]+type=["']application/json["'][^>]*>\s*\[\s*"([\s\S]*?)"\s*]\s*</script>""",
    )

    private val JUNK = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")

    private val json = Json { ignoreUnknownKeys = true }

    /** URL du flux VOE contenue dans cette page, ou null si ce n'est pas une page VOE. */
    fun findSource(html: String): String? {
        val raw = PAYLOAD.find(html)?.groupValues?.get(1) ?: return null
        val decoded = decode(raw) ?: return null
        val obj = runCatching { json.parseToJsonElement(decoded) as? JsonObject }.getOrNull() ?: return null
        val source = (obj["source"] ?: obj["direct_access_url"])?.jsonPrimitive?.contentOrNull()
        return source?.takeIf { it.startsWith("http") }
    }

    /** Applique les six couches. Retourne null dès qu'une étape échoue. */
    fun decode(payload: String): String? {
        var step = rot13(payload)
        JUNK.forEach { step = step.replace(it, "") }

        // Latin-1 et non UTF-8 : l'étape suivante décale des codes de caractères,
        // elle doit voir un octet = un caractère. Un décodage UTF-8 fusionnerait
        // des paires d'octets et fausserait le décalage.
        val first = decodeBase64(step)?.enLatin1() ?: return null

        val shifted = buildString(first.length) {
            for (c in first) append((c.code - 3).toChar())
        }.reversed()

        return decodeBase64(shifted)?.decodeToString()
    }

    /**
     * Décodage Latin-1 : un octet donne un caractère, de code égal à l'octet.
     *
     * `Charsets.ISO_8859_1` n'existe pas en commun — seul `decodeToString()`,
     * qui est de l'UTF-8, y est disponible. Or c'est précisément ce qu'il ne
     * faut pas ici : le décalage de codes qui suit exige la correspondance
     * un octet = un caractère, qu'un décodage UTF-8 détruirait en fusionnant
     * les paires d'octets au-delà de 0x7F.
     */
    private fun ByteArray.enLatin1(): String =
        buildString(size) {
            for (octet in this@enLatin1) append((octet.toInt() and 0xFF).toChar())
        }

    private fun rot13(s: String): String = buildString(s.length) {
        for (c in s) append(
            when {
                c in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                c in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                else -> c
            },
        )
    }

    // Padding manuel : on n'utilise pas Base64.withPadding()/PaddingOption, absent
    // du runtime Android bundlé (ExceptionInInitializerError) — même contrainte
    // que PackedJs.
    private fun decodeBase64(s: String): ByteArray? {
        val padded = s + "=".repeat((4 - s.length % 4) % 4)
        return runCatching { Base64.Default.decode(padded) }.getOrNull()
            ?: runCatching { Base64.UrlSafe.decode(padded) }.getOrNull()
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        content.takeIf { it.isNotBlank() && it != "null" }
}
