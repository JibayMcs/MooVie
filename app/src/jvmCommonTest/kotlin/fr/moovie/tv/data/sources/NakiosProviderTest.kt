package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import fr.moovie.tv.core.sources.usecase.StreamResolution
import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Nakios - **sans réseau**.
 *
 * Les fixtures reprennent la forme mesurée le 23/10/2026 (`lang`, URL MP4 sur
 * `zen-blk.store`, `isPremium`). L'enveloppe exacte n'ayant pu être recapturée,
 * le provider parcourt le JSON sans en dépendre.
 */
class NakiosProviderTest {

    @BeforeTest
    fun magasinsEnMemoire() {
        useInMemoryStores()
        DomainBook.oublie()
    }

    @AfterTest
    fun magasinsSurFichier() {
        DomainBook.oublie()
        useFileStores()
    }

    private val matrix = """
        {"sources":[
          {"lang":"VF","isPremium":true,"url":"https://cdn80.zen-blk.store/movies/603/matrix.mp4"},
          {"lang":"ES","isPremium":true,"url":"https://cdn80.zen-blk.store/movies/603/matrix-es.mp4"},
          {"isPremium":true,"url":"https://cdn80.zen-blk.store/movies/603/sans-langue.mp4"},
          {"lang":"VF","isPremium":true,"url":"https://cdn80.zen-blk.store/movies/603/matrix.mp4"}]}
    """.trimIndent()

    private class Fake(private val routes: Map<String, String>) : HttpGateway {
        val requested = mutableListOf<HttpRequest>()
        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            requested += request
            val body = routes.entries.firstOrNull { request.url.contains(it.key) }?.value
                ?: return HttpResponse(404, request.url)
            return HttpResponse(200, request.url, body = body)
        }
    }

    @Test
    fun `un film rend ses liens VF, sans doublon, langues inconnues ecartees`() = runTest {
        val gw = Fake(mapOf("/api/sources/movie/603" to matrix))
        val links = NakiosProvider(gw).sourcesFor(MediaRef.Movie(603, "Matrix"))
        assertEquals(
            listOf(EmbedLink("https://cdn80.zen-blk.store/movies/603/matrix.mp4", "nakios", "VF")),
            links,
        )
        assertTrue(gw.requested.all { it.headers["User-Agent"]?.contains("Mozilla") == true })
    }

    @Test
    fun `un episode interroge la route tv saison episode`() = runTest {
        val gw = Fake(
            mapOf(
                "/api/sources/tv/1396/1/1" to
                    """{"data":[{"lang":"vostfr","url":"https://cdn80.zen-blk.store/tv/1396/s01e01.mp4?x=1"}]}""",
            ),
        )
        val links = NakiosProvider(gw).sourcesFor(MediaRef.Episode(1396, "Breaking Bad", season = 1, episode = 1))
        assertEquals(listOf("VOSTFR"), links.map { it.language })
    }

    @Test
    fun `404 ou corps illisible rendent une liste vide`() = runTest {
        assertTrue(NakiosProvider(Fake(emptyMap())).sourcesFor(MediaRef.Movie(1, "x")).isEmpty())
        assertTrue(NakiosProvider(Fake(mapOf("/movie/1" to "<html>"))).sourcesFor(MediaRef.Movie(1, "x")).isEmpty())
    }

    @Test
    fun `l'extracteur pose le Referer et le User-Agent`() = runTest {
        val link = EmbedLink("https://cdn80.zen-blk.store/movies/603/matrix.mp4", "nakios", "VF")
        val stream = assertNotNull(NakiosExtractor().extract(link))
        assertEquals(StreamFormat.MP4, stream.format)
        assertEquals("https://nakios.rent/", stream.headers["Referer"])
        assertTrue(stream.headers["User-Agent"]!!.contains("Mozilla"))
        assertEquals("VF", stream.language)
    }

    @Test
    fun `le registre resout hoster nakios par son nom, Referer pose`() = runTest {
        val link = EmbedLink("https://cdn80.zen-blk.store/movies/603/matrix.mp4", "nakios", "VF")
        // NakiosExtractor ne revendique aucune URL : c'est DirectStream qui
        // prendrait ce .mp4 par domaine. Seul le nom peut le choisir.
        assertTrue(ExtractorRegistry.extractorFor(link.url) !is NakiosExtractor)
        val stream = assertNotNull(ExtractorRegistry.resolve(link))
        assertEquals("https://nakios.rent/", stream.headers["Referer"])
        assertEquals(link.url, stream.url)

        val named = StreamResolution(listOf(DirectStreamExtractor(), NakiosExtractor())).extractorNamed("nakios")
        assertTrue(named is NakiosExtractor)
    }

    @Test
    fun `un lien zen-blk d'un autre hebergeur ne recoit pas le Referer nakios`() = runTest {
        val url = "https://cdn80.zen-blk.store/movies/603/matrix.mp4"
        for (hoster in listOf("direct", "autre", "")) {
            val stream = ExtractorRegistry.resolve(EmbedLink(url, hoster, "VF"))
            assertTrue(stream?.headers?.get("Referer") != "https://nakios.rent/", "hoster=$hoster")
        }
        // SwiftFlow non plus : il garde le sien.
        val sf = SwiftFlowExtractor().extract(EmbedLink(url, "swiftflow", "VF"))
        assertTrue(sf.headers["Referer"] != "https://nakios.rent/")
    }

    @Test
    fun `seules les URL https sont retenues, et le Referer ne part pas en clair`() = runTest {
        val root = Json.parseToJsonElement(
            """{"sources":[{"lang":"VF","url":"http://cdn80.zen-blk.store/a.mp4"},
               {"lang":"VO","url":"https://cdn80.zen-blk.store/b.mp4"}]}""",
        )
        assertEquals(listOf("https://cdn80.zen-blk.store/b.mp4"), NakiosProvider.linksOf(root).map { it.url })

        val clair = NakiosExtractor().extract(EmbedLink("http://cdn80.zen-blk.store/a.mp4", "nakios", "VF"))
        assertNull(clair.headers["Referer"])
    }

    @Test
    fun `SwiftFlow garde son propre Referer`() = runTest {
        val sf = SwiftFlowExtractor().extract(EmbedLink("https://alpa.edge-n2.site/a.mp4", "swiftflow", "VF"))
        assertTrue(sf.headers["Referer"] != "https://nakios.rent/")
    }
}
