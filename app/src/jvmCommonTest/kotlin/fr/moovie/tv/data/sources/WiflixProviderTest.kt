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
 * Lecture du catalogue wiflix — **sans réseau**, sur des réponses réelles.
 *
 * Deux invariants qui ne se voient qu'ici :
 *
 *  - la **forme de l'URL d'épisode**. La saison va dans le chemin de `id` et
 *    l'épisode en paramètre séparé ; `?id=1429&season=1&episode=1`, qui est la
 *    forme qu'on écrirait spontanément, répond 200 avec une page sans sources.
 *    Un provider muet ressemblant trait pour trait à un provider dont le titre
 *    est absent, la faute se serait vue comme une simple couverture faible.
 *  - un titre **refusé** par wiflix rend une phrase en français, pas du JSON.
 *    C'est le cas nominal le plus fréquent, il doit donner une liste vide.
 */
class WiflixProviderTest {

    private class FakeGateway(private val body: String?) : HttpGateway {
        var lastUrl: String? = null
        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            lastUrl = request.url
            return HttpResponse(status = 200, url = request.url, body = body)
        }
    }

    /** Page réelle de `apiwiflix.php?id=693134`, réduite au tableau lu. */
    private val filmPage = """
        <html><body><script>
        let allSources = [{"url":"https:\/\/uqload.is\/embed-t5cyinl3xtpt.html","name":"uqload.is","language":"VF","color":"#28a745"},{"url":"https:\/\/luluvdo.com\/e\/vbw0yjo7001s","name":"luluvdo.com","language":"VF","color":"#28a745"},{"url":"https:\/\/playmogo.com\/e\/gbz0t2lkxqzp","name":"playmogo.com","language":"VOSTFR","color":"#ffc107"}];
        const AD_URLS = [];
        </script></body></html>
    """.trimIndent()

    @Test
    fun `un film rend ses liens avec leur langue`() = runTest {
        val links = WiflixProvider(FakeGateway(filmPage))
            .sourcesFor(MediaRef.Movie(693134, "Dune : Deuxième partie", "2024"))

        assertEquals(3, links.size)
        assertEquals(listOf("uqload", "luluvdo", "playmogo"), links.map { it.hoster })
        assertEquals(listOf("VF", "VF", "VOSTFR"), links.map { it.language })
        // Les `\/` du JSON sont déséchappés : une URL qui les garderait est injouable.
        assertEquals("https://uqload.is/embed-t5cyinl3xtpt.html", links.first().url)
    }

    @Test
    fun `un film s interroge par son seul ID TMDB`() = runTest {
        val gateway = FakeGateway(filmPage)

        WiflixProvider(gateway).sourcesFor(MediaRef.Movie(693134, "Peu importe", "2024"))

        assertEquals("${WiflixProvider.BASE}/apiwiflix.php?id=693134", gateway.lastUrl)
    }

    /** La forme exacte de l'URL d'épisode : saison dans le chemin, épisode à part. */
    @Test
    fun `un episode porte sa saison dans le chemin et son numero en parametre`() = runTest {
        val gateway = FakeGateway(filmPage)

        WiflixProvider(gateway).sourcesFor(MediaRef.Episode(1429, "L'Attaque des Titans", null, 1, 5))

        assertEquals("${WiflixProvider.BASE}/apiwiflix.php?id=1429/1&episode=5", gateway.lastUrl)
    }

    /** Réponse réelle d'un titre que wiflix ne veut pas servir. */
    @Test
    fun `un titre refuse rend une liste vide`() = runTest {
        val refus = "⚠️ Serie non disponible sur Wiflix (date de sortie differente)"

        assertTrue(WiflixProvider(FakeGateway(refus)).sourcesFor(MediaRef.Movie(1396, "X")).isEmpty())
        assertTrue(
            WiflixProvider(FakeGateway("⚠️ Serie non trouvee sur Wiflix"))
                .sourcesFor(MediaRef.Movie(94997, "X")).isEmpty(),
        )
    }

    @Test
    fun `une reponse illisible ne fait pas tomber le provider`() = runTest {
        val tronque = "<script>let allSources = [{\"url\":\"https://a.b/c\","

        assertTrue(WiflixProvider(FakeGateway(tronque)).sourcesFor(MediaRef.Movie(1, "X")).isEmpty())
        assertTrue(WiflixProvider(FakeGateway(null)).sourcesFor(MediaRef.Movie(1, "X")).isEmpty())
    }

    /**
     * Une entrée sans URL exploitable ne doit pas emporter les autres : la page
     * mélange parfois des liens relatifs de son propre lecteur aux embeds.
     */
    @Test
    fun `une entree sans URL absolue est ignoree sans perdre les autres`() = runTest {
        val mixte = """
            let allSources = [{"url":"/player.php?x=1","name":"interne","language":"VF"},
            {"url":"https:\/\/uqload.is\/embed-abc.html","name":"uqload.is","language":"VF"}];
        """.trimIndent()

        val links = WiflixProvider(FakeGateway(mixte)).sourcesFor(MediaRef.Movie(1, "X"))

        assertEquals(listOf("https://uqload.is/embed-abc.html"), links.map { it.url })
    }

    /** Une langue inconnue reste null : deviner ferait démarrer la mauvaise piste. */
    @Test
    fun `une langue non reconnue n est pas rangee en VF`() {
        assertEquals("VF", WiflixProvider.languageOf("VF"))
        assertEquals("VOSTFR", WiflixProvider.languageOf("vostfr"))
        assertEquals(null, WiflixProvider.languageOf("MULTI"))
        assertEquals(null, WiflixProvider.languageOf(null))
    }
}
