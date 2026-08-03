package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lecture de l'API vidapi — **sans réseau**, sur une réponse réelle capturée.
 *
 * L'intérêt du provider tient à un détail vérifiable ici et nulle part ailleurs :
 * il étiquette en VO, seule langue que les catalogues francophones ne produisent
 * pas. Une régression silencieuse sur ce point reviendrait à reperdre le public
 * anglophone sans qu'aucun autre test ne bronche.
 */
class VidapiProviderTest {

    /** Passerelle scriptée : une URL, une réponse, et on note ce qui est demandé. */
    private class FakeGateway(private val body: String?) : HttpGateway {
        var lastUrl: String? = null
        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            lastUrl = request.url
            return HttpResponse(status = 200, url = request.url, body = body)
        }
    }

    /** Réponse réelle de `api.php?tmdb=550&type=movie`, abrégée aux champs lus. */
    private val movieJson = """
        {"status_code":"200","data":{
          "title":"Fight Club 1999","imdb_id":"tt0137523",
          "file_name":"Fight Club (1999) [1080p]/Fight.Club.1999.1080p.BrRip.x264.YIFY.mp4",
          "stream_urls":[
            "https://remoteconsultinggroup.site/a/pl/AAA/master.m3u8",
            "https://onlinevisibilitysystem.site/b/pl/BBB/master.m3u8"
          ]},
         "thumbnails_url":"https://vidapi.cloud/static/x/thumbnails.vtt"}
    """.trimIndent()

    @Test
    fun `un film rend ses miroirs en VO`() = runTest {
        val gateway = FakeGateway(movieJson)

        val links = VidapiProvider(gateway).sourcesFor(MediaRef.Movie(550, "Fight Club", "1999"))

        assertEquals(2, links.size)
        assertTrue(links.all { it.language == "VO" }, "le catalogue ne sert que la VO")
        assertTrue(links.all { it.hoster == "vidapi" })
        assertEquals(
            listOf(
                "https://remoteconsultinggroup.site/a/pl/AAA/master.m3u8",
                "https://onlinevisibilitysystem.site/b/pl/BBB/master.m3u8",
            ),
            links.map { it.url },
        )
    }

    /** L'ID TMDB suffit : aucun titre n'entre dans la requête, donc aucune langue d'UI. */
    @Test
    fun `un film s interroge par son seul ID TMDB`() = runTest {
        val gateway = FakeGateway(movieJson)

        VidapiProvider(gateway).sourcesFor(MediaRef.Movie(550, "Peu importe", "1999"))

        assertEquals("${VidapiProvider.API}?tmdb=550&type=movie", gateway.lastUrl)
    }

    @Test
    fun `un episode porte sa saison et son numero`() = runTest {
        val gateway = FakeGateway(movieJson)

        VidapiProvider(gateway).sourcesFor(MediaRef.Episode(1396, "Breaking Bad", "2008", 1, 1))

        assertEquals("${VidapiProvider.API}?tmdb=1396&type=tv&season=1&episode=1", gateway.lastUrl)
    }

    /** Trois miroirs du même flux : sans rang, la liste serait illisible. */
    @Test
    fun `les miroirs se distinguent par leur rang`() = runTest {
        val gateway = FakeGateway(movieJson)

        val links = VidapiProvider(gateway).sourcesFor(MediaRef.Movie(550, "Fight Club", "1999"))

        assertEquals(listOf("miroir 1", "miroir 2"), links.map { it.variant })
    }

    /** Un seul lien n'a rien à départager : « miroir 1 » n'apprendrait rien. */
    @Test
    fun `un miroir unique ne porte pas de rang`() {
        assertEquals(null, VidapiProvider.mirrorLabel(index = 0, total = 1))
    }

    /** Un catalogue qui n'a pas le titre est un cas normal, pas une erreur. */
    @Test
    fun `une reponse sans flux rend une liste vide`() = runTest {
        val empty = """{"status_code":"404","data":{"stream_urls":[]}}"""

        assertTrue(VidapiProvider(FakeGateway(empty)).sourcesFor(MediaRef.Movie(1, "X")).isEmpty())
    }

    @Test
    fun `une reponse illisible ne fait pas tomber le provider`() = runTest {
        assertTrue(VidapiProvider(FakeGateway("<html>503</html>")).sourcesFor(MediaRef.Movie(1, "X")).isEmpty())
        assertTrue(VidapiProvider(FakeGateway(null)).sourcesFor(MediaRef.Movie(1, "X")).isEmpty())
    }
}
