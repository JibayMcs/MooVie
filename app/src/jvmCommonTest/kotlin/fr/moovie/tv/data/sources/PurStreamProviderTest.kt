package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PurStream - **sans réseau**.
 *
 * Les fixtures reprennent la forme mesurée le 23/10/2026 (chemins de master
 * `/movies/603-h0ts7/`, `/tv/1396-yh3cz/S01/E01/`, audio `fr` DEFAULT + `en`).
 * L'enveloppe JSON exacte n'ayant pu être recapturée, le provider la parcourt
 * sans en dépendre : ce test fige ce comportement, pas un schéma.
 */
class PurStreamProviderTest {

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

    private val search = """
        {"data":{"items":{"movies":{"items":[
          {"id":9001,"title":"Matrix (remake)","type":"movie"},
          {"id":2633,"title":"Matrix","type":"movie"},
          {"id":3852,"title":"Matrix show","type":"tv"}]}}}}
    """.trimIndent()

    private fun stream(url: String) = """{"data":{"items":{"sources":[{"stream_url":"$url"}]}}}"""

    private val movieMaster = "https://free.finepulfe.xyz/movies/603-h0ts7/master.m3u8"
    private val wrongMaster = "https://free.finepulfe.xyz/movies/999-abcde/master.m3u8"
    private val tvMaster = "https://free.finepulfe.xyz/tv/1396-yh3cz/S01/E01/master.m3u8"

    private val manifest = """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Français",LANGUAGE="fr",DEFAULT=YES,URI="fr.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",DEFAULT=NO,URI="en.m3u8"
        #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="sub",NAME="Français",LANGUAGE="fre",URI="s.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=2560x1440,AUDIO="aud"
        1440.m3u8
    """.trimIndent()

    private val frOnly = manifest.lines().filterNot { it.contains("LANGUAGE=\"en\"") }.joinToString("\n")

    private class Fake(private val routes: Map<String, String>) : HttpGateway {
        val requested = mutableListOf<HttpRequest>()
        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            requested += request
            val body = routes.entries.firstOrNull { request.url.contains(it.key) }?.value
                ?: return HttpResponse(404, request.url)
            return HttpResponse(200, request.url, body = body)
        }
    }

    private fun matrixGateway(master: String = manifest) = Fake(
        mapOf(
            "/stream/9001" to stream(wrongMaster),
            "/stream/2633" to stream(movieMaster),
            "master.m3u8" to master,
            "/search-bar/search/Matrix" to search,
        ),
    )

    @Test
    fun `un film anglophone confirme rend un lien VF et un lien VO aux URL distinctes`() = runTest {
        val links = PurStreamProvider(matrixGateway())
            .sourcesFor(MediaRef.Movie(603, "Matrix", originalLanguage = "en"))

        assertEquals(listOf("VF", "VO"), links.map { it.language })
        assertEquals(setOf("$movieMaster#audio=fr", "$movieMaster#audio=en"), links.map { it.url }.toSet())
        assertTrue(links.all { it.hoster == "purstream" })
    }

    @Test
    fun `un film francais ne fait pas passer la piste anglaise pour la VO`() = runTest {
        val links = PurStreamProvider(matrixGateway())
            .sourcesFor(MediaRef.Movie(603, "Matrix", originalLanguage = "fr"))

        assertEquals(listOf("VF"), links.map { it.language })
        assertEquals(listOf("$movieMaster#audio=fr"), links.map { it.url })
    }

    @Test
    fun `langue originale inconnue - seule la VF est emise`() = runTest {
        val links = PurStreamProvider(matrixGateway()).sourcesFor(MediaRef.Movie(603, "Matrix"))

        assertEquals(listOf("VF"), links.map { it.language })
        assertEquals(listOf("$movieMaster#audio=fr"), links.map { it.url })
    }

    @Test
    fun `l'etiquette suit la langue originale TMDB`() {
        assertEquals("VF", PurStreamProvider.labelOf("fr", null))
        assertEquals("VF", PurStreamProvider.labelOf("fr", "fr"))
        assertEquals("VO", PurStreamProvider.labelOf("en", "en"))
        assertEquals("VO", PurStreamProvider.labelOf("ja", "ja"))
        assertNull(PurStreamProvider.labelOf("en", "fr"))
        assertNull(PurStreamProvider.labelOf("en", "ko"))
        assertNull(PurStreamProvider.labelOf("en", null))
        assertNull(PurStreamProvider.labelOf("en", ""))
    }

    @Test
    fun `un titre homonyme dont l'id TMDB ne correspond pas ne rend rien`() = runTest {
        val gw = Fake(
            mapOf(
                "/stream/9001" to stream(wrongMaster),
                "/stream/2633" to stream(wrongMaster),
                "master.m3u8" to manifest,
                "/search-bar/search/Matrix" to search,
            ),
        )
        assertTrue(PurStreamProvider(gw).sourcesFor(MediaRef.Movie(603, "Matrix")).isEmpty())
    }

    @Test
    fun `l'id TMDB doit etre suivi du tiret`() {
        assertTrue(PurStreamProvider.confirms(movieMaster, MediaRef.Movie(603, "x")))
        assertTrue(!PurStreamProvider.confirms(movieMaster, MediaRef.Movie(60, "x")))
        assertTrue(!PurStreamProvider.confirms(tvMaster, MediaRef.Movie(1396, "x")))
    }

    @Test
    fun `seule la piste reellement presente est emise et aucun VOSTFR deduit`() = runTest {
        val gw = Fake(
            mapOf(
                "/stream/2633" to stream(movieMaster),
                "master.m3u8" to frOnly,
                "/search-bar/search/Matrix" to search,
            ),
        )
        val links = PurStreamProvider(gw).sourcesFor(MediaRef.Movie(603, "Matrix"))
        assertEquals(listOf("VF"), links.map { it.language })
    }

    @Test
    fun `un episode interroge la route d'episode et se confirme sur le chemin tv`() = runTest {
        val gw = Fake(
            mapOf(
                "/stream/3852/episode?season=1&episode=1" to stream(tvMaster),
                "master.m3u8" to manifest,
                "/search-bar/search/Breaking%20Bad" to search,
            ),
        )
        val links = PurStreamProvider(gw)
            .sourcesFor(MediaRef.Episode(1396, "Breaking Bad", season = 1, episode = 1, originalLanguage = "en"))
        assertEquals(listOf("VF", "VO"), links.map { it.language })
    }

    @Test
    fun `l'extracteur retire le fragment, pose l'UA navigateur et la piste`() = runTest {
        val gw = Fake(
            mapOf(
                "/stream/2633" to stream(movieMaster),
                "master.m3u8" to manifest,
                "/search-bar/search/Matrix" to search,
            ),
        )
        val links = PurStreamProvider(gw).sourcesFor(MediaRef.Movie(603, "Matrix", originalLanguage = "en"))
        val extractor = PurStreamExtractor()
        val vo = links.first { it.language == "VO" }

        assertTrue(extractor.canHandle(vo.url))
        val stream = assertNotNull(extractor.extract(vo))
        assertEquals(movieMaster, stream.url)
        assertEquals(StreamFormat.HLS, stream.format)
        assertEquals("en", stream.audioLanguage)
        assertEquals("VO", stream.language)
        assertEquals(Ua.BROWSER, stream.headers["User-Agent"])
        assertNull(extractor.extract(vo.copy(url = movieMaster)))
    }

    @Test
    fun `sans selection de piste, seul le lien de la piste par defaut est emis, sans fragment`() = runTest {
        val gw = Fake(
            mapOf(
                "/stream/2633" to stream(movieMaster),
                "master.m3u8" to manifest,
                "/search-bar/search/Matrix" to search,
            ),
        )
        val links = PurStreamProvider(gw, audioTrackSelection = false)
            .sourcesFor(MediaRef.Movie(603, "Matrix"))

        assertEquals(listOf(movieMaster), links.map { it.url })
        assertEquals(listOf("VF"), links.map { it.language })
    }

    @Test
    fun `un master sans rendition audio rend un seul lien, sans fragment ni langue`() = runTest {
        val multiplexe = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=8000000\n1440.m3u8"
        val gw = Fake(
            mapOf(
                "/stream/2633" to stream(movieMaster),
                "master.m3u8" to multiplexe,
                "/search-bar/search/Matrix" to search,
            ),
        )
        val links = PurStreamProvider(gw).sourcesFor(MediaRef.Movie(603, "Matrix"))

        assertEquals(listOf(movieMaster), links.map { it.url })
        assertEquals(listOf<String?>(null), links.map { it.language })
    }

    @Test
    fun `sans selection de piste, un master multiplexe rend aussi un lien nu`() = runTest {
        val multiplexe = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=8000000\n1440.m3u8"
        val links = PurStreamProvider(matrixGateway(multiplexe), audioTrackSelection = false)
            .sourcesFor(MediaRef.Movie(603, "Matrix", originalLanguage = "en"))

        assertEquals(listOf(movieMaster), links.map { it.url })
        assertEquals(listOf<String?>(null), links.map { it.language })
    }

    @Test
    fun `sans selection de piste, une piste par defaut innommable rend un lien nu`() = runTest {
        // Piste par défaut anglaise d'un film français : ni VF ni VO.
        val enParDefaut = manifest.replace("DEFAULT=YES", "DEFAULT=NO")
            .replace("LANGUAGE=\"en\",DEFAULT=NO", "LANGUAGE=\"en\",DEFAULT=YES")
        val links = PurStreamProvider(matrixGateway(enParDefaut), audioTrackSelection = false)
            .sourcesFor(MediaRef.Movie(603, "Matrix", originalLanguage = "fr"))

        assertEquals(listOf(movieMaster), links.map { it.url })
        assertEquals(listOf<String?>(null), links.map { it.language })
    }

    @Test
    fun `avec selection de piste, aucune piste nommable rend un lien nu`() = runTest {
        val enSeul = manifest.lines().filterNot { it.contains("LANGUAGE=\"fr\"") }.joinToString("\n")
        val links = PurStreamProvider(matrixGateway(enSeul))
            .sourcesFor(MediaRef.Movie(603, "Matrix", originalLanguage = "fr"))

        assertEquals(listOf(movieMaster), links.map { it.url })
        assertEquals(listOf<String?>(null), links.map { it.language })
    }

    @Test
    fun `sans selection de piste, la premiere piste sert si aucune n'est DEFAULT`() = runTest {
        val sansDefaut = manifest.replace("DEFAULT=YES", "DEFAULT=NO")
        val gw = Fake(
            mapOf(
                "/stream/2633" to stream(movieMaster),
                "master.m3u8" to sansDefaut,
                "/search-bar/search/Matrix" to search,
            ),
        )
        val links = PurStreamProvider(gw, audioTrackSelection = false)
            .sourcesFor(MediaRef.Movie(603, "Matrix"))

        assertEquals(listOf(movieMaster), links.map { it.url })
        assertEquals(listOf("VF"), links.map { it.language })
    }

    @Test
    fun `le registre route le lien vers l'extracteur PurStream`() {
        val e = ExtractorRegistry.extractorFor("$movieMaster#audio=fr")
        assertEquals("purstream", e?.hoster)
    }
}
