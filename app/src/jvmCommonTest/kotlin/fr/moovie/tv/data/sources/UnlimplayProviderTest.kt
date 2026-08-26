package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lecture du catalogue unlimplay — **sans réseau**, sur une page réelle
 * (Spider-Man: Brand New Day, tmdbId 969681, capturée le jour de l'écriture
 * de ce provider).
 *
 * Une seule requête HTTP à couvrir : `const EMBEDS` arrive déjà résolu dans
 * le HTML, sans JS à exécuter.
 */
class UnlimplayProviderTest {

    private class FakeGateway(private val body: String?) : HttpGateway {
        var lastUrl: String? = null
        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            lastUrl = request.url
            return HttpResponse(status = 200, url = request.url, body = body)
        }
    }

    /** Extrait réel de `unlimplay.com/f/embed/movie/969681`, réduit aux lignes qui comptent. */
    private val embedPage = """
        <html><body><script>
        const EMBEDS = {"searched_names":["Spider-Man"],"latino":{"streamwish":"https://hglink.to/e/71dqgoc9y85f","voe":"https://voe.sx/e/s9jwcbqzhmpm","remux":"https://remux.unlimplay.com/remux?id=969681"},"subtitulado":{"streamwish":"https://streamwish.to/e/jzhv2wkk117u","voe":"https://voe.sx/e/3su0fsazmsnw","doodstream":"https://doodstream.com/e/7zbd1a3ii1qt"}};
        </script></body></html>
    """.trimIndent()

    @Test
    fun `un film s interroge par son ID TMDB seul`() = runTest {
        val gateway = FakeGateway(embedPage)

        UnlimplayProvider(gateway).sourcesFor(MediaRef.Movie(969681, "Peu importe"))

        assertEquals("${UnlimplayProvider.BASE}/f/embed/movie/969681", gateway.lastUrl)
    }

    @Test
    fun `un episode porte sa saison et son numero dans le chemin`() = runTest {
        val gateway = FakeGateway(embedPage)

        UnlimplayProvider(gateway).sourcesFor(MediaRef.Episode(1396, "Peu importe", season = 1, episode = 1))

        assertEquals("${UnlimplayProvider.BASE}/f/embed/tv/1396/1/1", gateway.lastUrl)
    }

    @Test
    fun `un film rend ses liens avec leur langue et un hoster deduit de l URL`() = runTest {
        val links = UnlimplayProvider(FakeGateway(embedPage)).sourcesFor(MediaRef.Movie(969681, "Peu importe"))

        assertEquals(6, links.size)
        assertTrue(links.any { it.url == "https://voe.sx/e/s9jwcbqzhmpm" && it.language == "LAT" && it.hoster == "voe" })
        assertTrue(
            links.any {
                it.url == "https://doodstream.com/e/7zbd1a3ii1qt" && it.language == "VOSE" && it.hoster == "doodstream"
            },
        )
        // `remux` n'est pas déduit du libellé de langue mais bien du domaine de l'URL.
        assertTrue(links.any { it.hoster == "remux" })
    }

    @Test
    fun `une page sans EMBEDS rend une liste vide`() = runTest {
        val links = UnlimplayProvider(FakeGateway("<html><body>Título no encontrado</body></html>"))
            .sourcesFor(MediaRef.Movie(1, "X"))

        assertTrue(links.isEmpty())
    }

    @Test
    fun `une reponse illisible ne fait pas tomber le provider`() = runTest {
        val media = MediaRef.Movie(1, "X")
        assertTrue(UnlimplayProvider(FakeGateway(null)).sourcesFor(media).isEmpty())
        assertTrue(UnlimplayProvider(FakeGateway("")).sourcesFor(media).isEmpty())
    }

    // ── Mapping de langue (pur, testable sans réseau) ───────────────────────

    @Test
    fun `languageOf reconnait latino, espanol avec et sans accent, et subtitulado`() {
        assertEquals("LAT", UnlimplayProvider.languageOf("latino"))
        assertEquals("CAST", UnlimplayProvider.languageOf("español"))
        assertEquals("CAST", UnlimplayProvider.languageOf("espanol"))
        assertEquals("CAST", UnlimplayProvider.languageOf("castellano"))
        assertEquals("VOSE", UnlimplayProvider.languageOf("subtitulado"))
    }

    @Test
    fun `languageOf ne devine pas une cle non reconnue`() {
        assertNull(UnlimplayProvider.languageOf("searched_names"))
        assertNull(UnlimplayProvider.languageOf(""))
    }

    // ── Découpage de l'objet EMBEDS par comptage d'accolades ────────────────

    @Test
    fun `extractEmbedsJson isole l objet malgre des accolades dans les URL signees`() {
        val body = """
            const EMBEDS = {"latino":{"direct":"https://s9.vimeos.net/hls2/x/master.m3u8?t=a}b&s=1&e=2"}};
            var autreChoseApres = {"peu": "importe"};
        """.trimIndent()

        val extracted = UnlimplayProvider.extractEmbedsJson(body)

        assertEquals(
            """{"latino":{"direct":"https://s9.vimeos.net/hls2/x/master.m3u8?t=a}b&s=1&e=2"}}""",
            extracted,
        )
    }

    @Test
    fun `extractEmbedsJson rend null si le marqueur est absent`() {
        assertNull(UnlimplayProvider.extractEmbedsJson("<html>rien ici</html>"))
    }
}
