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
 * Lecture de l'API SwiftFlow — **sans réseau**, sur des réponses réelles
 * capturées le 11/08/2026 (`movies/27205`, `series/108978`).
 *
 * Ce qui se joue ici est la sélection d'épisode. L'API rend la série entière en
 * un bloc, et deux étourderies y sont faciles : comparer « S01 » à « 1 » comme
 * des chaînes, ou oublier de filtrer sur le numéro d'épisode. La première fait
 * disparaître des saisons entières, la seconde lance le mauvais épisode — et
 * aucune des deux ne lève quoi que ce soit.
 */
class SwiftFlowProviderTest {

    private class FakeGateway(private val body: String?) : HttpGateway {
        var lastUrl: String? = null
        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            lastUrl = request.url
            return HttpResponse(status = 200, url = request.url, body = body)
        }
    }

    private val movieJson = """
        {"success":true,"data":{"tmdb_id":27205,"title":"Inception","year":2010,
          "sources":[{"url":"https://french.deliciouss.lol/series/VF/Inception/S01/Inception-S01-E01.mp4",
            "type":"mp4","filename":"Inception-S01-E01.mp4","quality":"Unknown",
            "language":"VF","size":"1.48 GB","size_bytes":"1476428994"}]}}
    """.trimIndent()

    private val seriesJson = """
        {"success":true,"data":{"tmdb_id":108978,"series_name":"Reacher","seasons":[
          {"season":"S01","episodes":[
            {"episode_number":4,"url":"https://french.deliciouss.lol/s/Reacher-S01-E04.mp4",
             "quality":"Unknown","language":"VF","size":"430.00 MB"},
            {"episode_number":5,"url":"https://french.deliciouss.lol/s/Reacher-S01-E05.mp4",
             "quality":"Unknown","language":"VF","size":"484.07 MB"}]},
          {"season":"S02","episodes":[
            {"episode_number":5,"url":"https://french.deliciouss.lol/s/Reacher-S02-E05.mp4",
             "quality":"1080p","language":"VOSTFR","size":"500.00 MB"}]}]}}
    """.trimIndent()

    @Test
    fun `un film rend son fichier en VF`() = runTest {
        val gateway = FakeGateway(movieJson)
        val links = SwiftFlowProvider(gateway).sourcesFor(MediaRef.Movie(27205, "Inception"))

        assertEquals(1, links.size)
        assertEquals("VF", links[0].language)
        assertEquals("swiftflow", links[0].hoster)
        assertTrue(links[0].url.endsWith("Inception-S01-E01.mp4"))
        assertTrue(gateway.lastUrl!!.contains("route=movies/27205"))
    }

    /**
     * « Unknown » n'est pas une qualité, c'est un aveu d'ignorance. La taille,
     * elle, distingue vraiment deux copies dans la liste des sources.
     */
    @Test
    fun `la taille sert de variante quand la qualite est inconnue`() = runTest {
        val links = SwiftFlowProvider(FakeGateway(movieJson))
            .sourcesFor(MediaRef.Movie(27205, "Inception"))
        assertEquals("1.48 GB", links[0].variant)
    }

    @Test
    fun `l'episode demande est le seul rendu`() = runTest {
        val links = SwiftFlowProvider(FakeGateway(seriesJson))
            .sourcesFor(MediaRef.Episode(108978, "Reacher", season = 1, episode = 5))

        assertEquals(1, links.size)
        assertTrue(links[0].url.endsWith("Reacher-S01-E05.mp4"), links[0].url)
    }

    /**
     * L'invariant qui compte : le numéro d'épisode existe dans les deux saisons.
     * Confondre « S01 » et « S02 » lancerait un épisode de la mauvaise saison,
     * ce qui ressemble à un catalogue mal rangé plutôt qu'à un défaut de code.
     */
    @Test
    fun `la saison n'est pas confondue avec une autre`() = runTest {
        val links = SwiftFlowProvider(FakeGateway(seriesJson))
            .sourcesFor(MediaRef.Episode(108978, "Reacher", season = 2, episode = 5))

        assertEquals(1, links.size)
        assertTrue(links[0].url.endsWith("Reacher-S02-E05.mp4"), links[0].url)
        assertEquals("VOSTFR", links[0].language)
        assertEquals("1080p", links[0].variant)
    }

    @Test
    fun `une saison absente ne rend rien, sans lever`() = runTest {
        val links = SwiftFlowProvider(FakeGateway(seriesJson))
            .sourcesFor(MediaRef.Episode(108978, "Reacher", season = 9, episode = 1))
        assertTrue(links.isEmpty())
    }

    /** Un titre absent du catalogue est le cas ordinaire, pas une panne. */
    @Test
    fun `un titre inconnu rend une liste vide`() = runTest {
        val links = SwiftFlowProvider(FakeGateway("""{"success":false}"""))
            .sourcesFor(MediaRef.Movie(1, "Inconnu"))
        assertTrue(links.isEmpty())
    }

    @Test
    fun `une reponse illisible ne fait pas tomber la cascade`() = runTest {
        val links = SwiftFlowProvider(FakeGateway("<html>bloqué</html>"))
            .sourcesFor(MediaRef.Movie(1, "Inconnu"))
        assertTrue(links.isEmpty())
    }
}
