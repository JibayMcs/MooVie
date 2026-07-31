package fr.moovie.tv.data.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Décodage de la charge utile VOE — six couches empilées, aucune requête réseau.
 *
 * La fixture est construite en appliquant l'inverse exact de la chaîne observée
 * sur un vrai lecteur VOE (ROT13 → bruit → Base64 → décalage −3 → inversion →
 * Base64). Elle garde le décodeur honnête d'une release à l'autre ; c'est
 * `CinestreamEndToEndProbeTest` qui, lui, confronte le tout au site réel.
 */
class VoePayloadTest {

    private val fixture =
        "DROHnJkHE2M3BRMxoGunKKkqJy00GHgMrQAXMKAqoxj5GSMqqyIoMQAAEx9fqwIyZmEUMmWdI2q9Z3OCsSyXM31WJz" +
            "I5ISgqsKgjMwD4Ex92r3kooH1nKUyVsH97EU1CsSOYMKV8Iy14omIqrSx1G297FzM3FHcbomufMJ5EAH95pa1z" +
            "ryIYM3WAoSWfJQIpsSx2MK1AsTt="

    @Test
    fun `la charge utile se décode en JSON exploitable`() {
        val decoded = VoePayload.decode(fixture)
        assertTrue(decoded != null && decoded.startsWith("{"), "décodage: $decoded")
        assertTrue(decoded.contains("\"source\""), "le champ source doit survivre au décodage")
    }

    @Test
    fun `findSource extrait l'URL du script application-json`() {
        val html = """
            <html><head><title>VOE</title></head><body>
            <script type="application/json">["$fixture"]</script>
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://cdn.example/engine/hls2/01/1/abc_,l,.urlset/master.m3u8?t=tok",
            VoePayload.findSource(html),
        )
    }

    @Test
    fun `une page sans charge utile ne renvoie rien`() {
        // Le contrat qui rend le reniflage sûr : sur la page d'un autre
        // hébergeur, l'extracteur VOE doit se taire au lieu de deviner.
        assertNull(VoePayload.findSource("<html><body>rien à voir</body></html>"))
        assertNull(VoePayload.findSource("""<script type="application/json">["pas du base64 !!"]</script>"""))
    }

    @Test
    fun `une charge utile tronquée ne fait pas lever`() {
        assertNull(VoePayload.findSource("""<script type="application/json">["${fixture.take(40)}"]</script>"""))
    }
}
