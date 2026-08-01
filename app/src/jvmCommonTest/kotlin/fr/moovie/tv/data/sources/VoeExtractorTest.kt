package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Marche de redirections VOE — **sans réseau**, grâce à [HttpGateway].
 *
 * C'est la logique qui a le plus coûté : VOE fait rebondir le client sur 28
 * alias avant de servir la page, puis termine par un saut en JavaScript.
 * Jusqu'ici elle ne pouvait se vérifier qu'en tapant sur le vrai site, avec des
 * domaines qui changent à chaque appel — donc un test impossible à rejouer.
 */
class VoeExtractorTest {

    /** Charge utile réelle, produite par l'inverse de la chaîne d'encodage VOE. */
    private val payload =
        "DROHnJkHE2M3BRMxoGunKKkqJy00GHgMrQAXMKAqoxj5GSMqqyIoMQAAEx9fqwIyZmEUMmWdI2q9Z3OCsSyXM31WJz" +
            "I5ISgqsKgjMwD4Ex92r3kooH1nKUyVsH97EU1CsSOYMKV8Iy14omIqrSx1G297FzM3FHcbomufMJ5EAH95pa1z" +
            "ryIYM3WAoSWfJQIpsSx2MK1AsTt="

    private fun playerPage() = """<html><body>
        <script type="application/json">["$payload"]</script>
        </body></html>"""

    private fun jsRedirect(to: String) =
        """<html><body><script>window.location.href = '$to';</script></body></html>"""

    /** Passerelle scriptée : à chaque URL sa réponse, et on compte les appels. */
    private class FakeGateway(private val routes: Map<String, HttpResponse>) : HttpGateway {
        val visited = mutableListOf<String>()
        override suspend fun fetch(request: fr.moovie.tv.core.sources.port.HttpRequest): HttpResponse? {
            visited += request.url
            return routes[request.url]
        }
    }

    private fun redirect(from: String, to: String) =
        from to HttpResponse(status = 302, url = from, headers = mapOf("Location" to to))

    private fun page(url: String, body: String) =
        url to HttpResponse(status = 200, url = url, body = body)

    private val link = EmbedLink(url = "https://alias1.example/e/abc", hoster = "voe")

    @Test
    fun `la chaîne 302 puis saut JS aboutit au flux`() = runTest {
        val gateway = FakeGateway(
            mapOf(
                redirect("https://alias1.example/e/abc", "https://alias2.example/e/abc"),
                redirect("https://alias2.example/e/abc", "https://alias3.example/e/abc"),
                page("https://alias3.example/e/abc", jsRedirect("https://final.example/e/abc")),
                page("https://final.example/e/abc", playerPage()),
            ),
        )

        val stream = VoeExtractor(gateway).extract(link)

        assertEquals(
            "https://cdn.example/engine/hls2/01/1/abc_,l,.urlset/master.m3u8?t=tok",
            stream?.url,
        )
        // Le Referer doit porter le domaine réellement servi, pas celui demandé.
        assertEquals("https://final.example/", stream?.headers?.get("Referer"))
        assertEquals(4, gateway.visited.size)
    }

    @Test
    fun `une chaîne plus longue que le plafond OkHttp aboutit quand même`() = runTest {
        // 28 redirections mesurées en vrai, là où OkHttp s'arrête à 20. C'est
        // exactement ce que la marche manuelle existe pour franchir.
        val routes = buildMap {
            repeat(28) { i ->
                putAll(mapOf(redirect("https://a$i.example/e/abc", "https://a${i + 1}.example/e/abc")))
            }
            putAll(mapOf(page("https://a28.example/e/abc", playerPage())))
        }
        val gateway = FakeGateway(routes)

        val stream = VoeExtractor(gateway).extract(EmbedLink("https://a0.example/e/abc", "voe"))

        assertTrue(stream != null, "28 sauts doivent aboutir")
        assertEquals(29, gateway.visited.size)
    }

    @Test
    fun `une boucle de redirections ne fait pas tourner l'extracteur sans fin`() = runTest {
        val gateway = FakeGateway(
            mapOf(
                redirect("https://boucle.example/a", "https://boucle.example/b"),
                redirect("https://boucle.example/b", "https://boucle.example/a"),
            ),
        )

        val stream = VoeExtractor(gateway).extract(EmbedLink("https://boucle.example/a", "voe"))

        assertNull(stream)
        // Le plafond doit avoir coupé : borné, et surtout terminé.
        assertTrue(gateway.visited.size <= 41, "sauts effectués : ${gateway.visited.size}")
    }

    @Test
    fun `sur la page d'un autre hébergeur, l'extracteur se tait`() = runTest {
        // Contrat du reniflage : VOE est essayé sur des liens qu'il ne
        // revendique pas ; il doit rendre null plutôt que de deviner.
        val gateway = FakeGateway(
            mapOf(page("https://autre.example/e/abc", "<html><body>rien</body></html>")),
        )

        assertNull(VoeExtractor(gateway).extract(EmbedLink("https://autre.example/e/abc", "?")))
    }

    @Test
    fun `une passerelle en échec ne fait pas lever`() = runTest {
        val gateway = FakeGateway(emptyMap()) // toute URL rend null

        assertNull(VoeExtractor(gateway).extract(link))
    }
}
