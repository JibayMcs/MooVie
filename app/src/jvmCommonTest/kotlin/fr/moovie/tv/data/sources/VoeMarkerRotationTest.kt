package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La rotation des marqueurs VOE, et ce qu'elle cassait.
 *
 * VOE régénère à chaque déploiement la table de bruit retirée à l'étape 2 de sa
 * pile, et la publie dans le bundle JS de la page. La figer en dur — ce que
 * faisait cet extracteur — condamne l'extraction à la prochaine mise en
 * production, **en silence** : une charge mal débruitée échoue au Base64
 * suivant et ressort en `null`, exactement comme la page d'un autre hébergeur.
 * Relevé du 23/09/2026 : un lien VOE résolu sur trois.
 *
 * D'où des fixtures **encodées à la demande** plutôt qu'une chaîne figée : le
 * test doit pouvoir fabriquer une page dont les marqueurs ne sont pas ceux
 * d'hier, ce qu'une constante ne permet pas.
 */
@OptIn(ExperimentalEncodingApi::class)
class VoeMarkerRotationTest {

    private val flux = "https://cdn.example/engine/hls2/01/1/xyz_,l,.urlset/master.m3u8?t=tok"

    /**
     * Inverse exact de [VoePayload.decode] : JSON → Base64 → inversion →
     * décalage +3 → Latin-1 → Base64 → bruit inséré → ROT13.
     */
    private fun encode(json: String, markers: List<String>): String {
        val b64 = Base64.Default.encode(json.encodeToByteArray())
        val shifted = b64.reversed().map { (it.code + 3).toChar() }.joinToString("")
        val octets = ByteArray(shifted.length) { (shifted[it].code and 0xFF).toByte() }
        val second = Base64.Default.encode(octets)

        // Bruit semé tous les cinq caractères, comme le fait VOE : le décodage
        // ne doit dépendre que de la table, pas d'un placement particulier.
        val bruite = buildString {
            second.forEachIndexed { i, c ->
                append(c)
                if (i % 5 == 4) append(markers[(i / 5) % markers.size])
            }
        }
        return rot13(bruite)
    }

    private fun rot13(s: String) = buildString(s.length) {
        for (c in s) append(
            when {
                c in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                c in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                else -> c
            },
        )
    }

    /** Les marqueurs d'un déploiement qui n'est pas celui de la liste figée. */
    private val nouveaux = listOf("#@", "%%", "~!")

    private fun pageAvecBundle(payload: String, bundle: String) = """<html><body>
        <script type="application/json">["$payload"]</script>
        <script src="$bundle"></script>
        </body></html>"""

    @Test
    fun `une charge chiffrée avec de nouveaux marqueurs résiste à la liste figée`() {
        val payload = encode("""{"source":"$flux"}""", nouveaux)

        assertNull(
            VoePayload.findSource("""<script type="application/json">["$payload"]</script>"""),
            "c'est précisément l'échec silencieux qu'on corrige : null, comme une page étrangère",
        )
        assertEquals(flux, VoePayload.decode(payload, nouveaux)?.let { extraitSource(it) })
    }

    @Test
    fun `la table est lue dans le bundle, quelle que soit sa forme`() {
        val bundle = """!function(e){var t=['#@','%%','~!'];return t.map(x=>x)}(window);"""

        assertEquals(nouveaux, VoePayload.markersFrom(bundle))
        assertNull(VoePayload.markersFrom("var t=[];// pas de table ici"))
    }

    @Test
    fun `bundleUrl trouve le script adjacent à la charge`() {
        val html = pageAvecBundle("peu importe", "/assets/app-4f2a.js")

        assertEquals("/assets/app-4f2a.js", VoePayload.bundleUrl(html))
        // Sans script adjacent, rien à aller chercher.
        assertNull(VoePayload.bundleUrl("""<script type="application/json">["x"]</script>"""))
    }

    @Test
    fun `le champ file est lu comme source, et le HLS passe devant le mp4`() {
        val fichier = encode("""{"file":"$flux"}""", VoePayload.LEGACY_MARKERS)
        assertEquals(flux, VoePayload.findSource("""<script type="application/json">["$fichier"]</script>"""))

        // Les deux présents : la master playlist gagne, le mp4 n'est qu'un repli.
        val deux = encode(
            """{"direct_access_url":"https://cdn.example/f.mp4","source":"$flux"}""",
            VoePayload.LEGACY_MARKERS,
        )
        assertEquals(flux, VoePayload.findSource("""<script type="application/json">["$deux"]</script>"""))
    }

    @Test
    fun `une page servie sans chiffrement rend quand même son flux`() {
        val html = """<html><body><script>var player={"hls":"$flux","poster":"/p.jpg"};</script></body></html>"""

        assertEquals(flux, VoePayload.plainSource(html))
        assertNull(VoePayload.plainSource("<html><body>rien</body></html>"))
    }

    // --- Bout en bout, à travers l'extracteur --------------------------------

    private class FakeGateway(private val routes: Map<String, HttpResponse>) : HttpGateway {
        val visited = mutableListOf<String>()
        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            visited += request.url
            return routes[request.url]
        }
    }

    @Test
    fun `l'extracteur va chercher la table dans le bundle quand la liste figée échoue`() = runTest {
        val payload = encode("""{"source":"$flux"}""", nouveaux)
        val gateway = FakeGateway(
            mapOf(
                "https://voe.example/e/abc" to HttpResponse(
                    status = 200,
                    url = "https://voe.example/e/abc",
                    body = pageAvecBundle(payload, "/assets/app.js"),
                ),
                "https://voe.example/assets/app.js" to HttpResponse(
                    status = 200,
                    url = "https://voe.example/assets/app.js",
                    body = "var markers=['#@','%%','~!'];",
                ),
            ),
        )

        val stream = VoeExtractor(gateway).extract(EmbedLink("https://voe.example/e/abc", "voe"))

        assertEquals(flux, stream?.url)
        assertTrue(
            gateway.visited.contains("https://voe.example/assets/app.js"),
            "le bundle doit être demandé : visité ${gateway.visited}",
        )
    }

    @Test
    fun `sans bundle exploitable, l'extracteur se tait toujours`() = runTest {
        // Le contrat du reniflage tient : ni table, ni source en clair, ni
        // domaine VOE — on ne revendique pas la page.
        val gateway = FakeGateway(
            mapOf(
                "https://inconnu.example/e/abc" to HttpResponse(
                    status = 200,
                    url = "https://inconnu.example/e/abc",
                    body = pageAvecBundle(encode("""{"source":"$flux"}""", nouveaux), "/a.js"),
                ),
                "https://inconnu.example/a.js" to HttpResponse(
                    status = 200,
                    url = "https://inconnu.example/a.js",
                    body = "var rien=1;",
                ),
            ),
        )

        assertNull(VoeExtractor(gateway).extract(EmbedLink("https://inconnu.example/e/abc", "?")))
    }

    /** Le champ `source` du JSON décodé, sans repasser par [VoePayload]. */
    private fun extraitSource(json: String): String? =
        Regex(""""(?:source|file|direct_access_url)":"([^"]+)"""").find(json)?.groupValues?.get(1)
}
