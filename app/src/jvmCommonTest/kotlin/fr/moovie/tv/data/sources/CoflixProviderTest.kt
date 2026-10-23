package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coflix - **sans réseau**, sur des pages réelles capturées le 23/10/2026
 * (réduites à leurs fragments utiles, dans `resources/coflix/`).
 */
class CoflixProviderTest {

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

    // Lues sur le disque (le répertoire de travail des tests est le module `app`) :
    // les tests unitaires Android ne voient pas les ressources de jvmCommonTest.
    private fun fixture(name: String): String {
        val f = File("src/jvmCommonTest/resources/coflix/$name")
        check(f.exists()) { "fixture $name absente (${f.absolutePath})" }
        return f.readText()
    }

    private class Fake(private val routes: Map<String, String>) : HttpGateway {
        val requested = mutableListOf<HttpRequest>()
        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            requested += request
            val body = routes.entries.firstOrNull { request.url.contains(it.key) }?.value
                ?: return HttpResponse(404, request.url)
            return HttpResponse(200, request.url, body = body)
        }
    }

    private fun gateway() = Fake(
        mapOf(
            "/?s=Matrix" to fixture("search_matrix.html"),
            "/film/matrix/" to fixture("film_matrix.html"),
            "id=48579" to fixture("embed_matrix.html"),
            "/?s=Breaking+Bad" to fixture("search_bb.html"),
            "/episode/breaking-bad-1x1/" to fixture("episode_bb_1x1.html"),
            "id=10501" to fixture("embed_bb_1x1.html"),
        ),
    )

    @Test
    fun `Matrix rend des liens jouables, hebergeur deduit du domaine, VF d'apres le site`() = runTest {
        val gw = gateway()
        val links = CoflixProvider(gw).sourcesFor(MediaRef.Movie(603, "Matrix", "1999"))
        assertTrue(links.isNotEmpty())
        val hosters = links.map { it.hoster }
        assertTrue("lulustream" in hosters, "hébergeurs : $hosters")
        assertTrue("voe" in hosters, "hébergeurs : $hosters")
        // Ceux que l'app ne sait pas résoudre sont écartés.
        assertTrue(links.all { ExtractorRegistry.canResolve(it.url) })
        assertTrue(links.none { "emmmmbed" in it.url || "waaw" in it.url || "veev" in it.url })
        // Chaque serveur de la fixture est annoncé « French ».
        assertTrue(links.all { it.language == "VF" })
        assertEquals(links.map { it.url }.distinct(), links.map { it.url })
        assertTrue(gw.requested.all { it.headers["User-Agent"]?.contains("Mozilla") == true })
    }

    @Test
    fun `un episode passe par la serie puis la page episode`() = runTest {
        val gw = gateway()
        val links = CoflixProvider(gw)
            .sourcesFor(MediaRef.Episode(1396, "Breaking Bad", "2008", season = 1, episode = 1))
        assertTrue(links.isNotEmpty())
        assertTrue(gw.requested.any { it.url == "https://coflix.esq/episode/breaking-bad-1x1/" })
    }

    @Test
    fun `titre exact seulement - pas de Matrix Reloaded pour Matrix`() {
        val html = fixture("search_matrix.html")
        assertEquals("matrix", CoflixProvider.findSlug(html, "Matrix", "1999", movie = true))
        assertEquals("matrix-reloaded", CoflixProvider.findSlug(html, "Matrix Reloaded", null, movie = true))
        assertNull(CoflixProvider.findSlug(html, "Matri", null, movie = true))
        assertNull(CoflixProvider.findSlug(html, "Matrix", "2021", movie = true))
        // Un film n'est pas une série.
        assertNull(CoflixProvider.findSlug(html, "Matrix", "1999", movie = false))
    }

    @Test
    fun `une serie ne se confond pas avec un film au titre voisin`() {
        val html = fixture("search_bb.html")
        assertEquals("breaking-bad", CoflixProvider.findSlug(html, "Breaking Bad", null, movie = false))
        assertNull(CoflixProvider.findSlug(html, "Breaking Bad", "2008", movie = true))
    }

    @Test
    fun `cfServers est lu depuis la fiche`() {
        assertEquals(
            listOf("https://lecteurvideo.com/embed.php?id=48579"),
            CoflixProvider.playersOf(fixture("film_matrix.html")),
        )
        assertTrue(CoflixProvider.playersOf("<html></html>").isEmpty())
    }

    @Test
    fun `un serveur non annonce French reste sans etiquette`() {
        // base64 de https://voe.sx/e/o8wbdtbxzp2g
        val b64 = "aHR0cHM6Ly92b2Uuc3gvZS9vOHdiZHRieHpwMmc="
        val html = """<li onclick="showVideo('$b64', '2')"><span>x</span><p>English - Server</p></li>"""
        val links = CoflixProvider.linksOf(html)
        assertEquals(1, links.size)
        assertNull(links.single().language)
    }

    @Test
    fun `l'onglet Telecharger est ignore`() {
        val b64 = "aHR0cHM6Ly92b2Uuc3gvZS9vOHdiZHRieHpwMmc="
        val html = """<div class="OD OD_down"><li onclick="showVideo('$b64', '2')"><p>French - Telecharger</p></li>"""
        assertTrue(CoflixProvider.linksOf(html).isEmpty())
    }

    // Carte sans année : la seule garde est la fiche.
    private val carteSansAnnee =
        """<div class="md-manga-card-cover"><a href="https://coflix.esq/film/matrix/"></a></div>""" +
            """<p class="md-manga-card-name">Matrix</p>"""

    private fun gatewayCarteSansAnnee(page: String) = Fake(
        mapOf(
            "/?s=Matrix" to carteSansAnnee,
            "/film/matrix/" to page,
            "id=48579" to fixture("embed_matrix.html"),
        ),
    )

    // Fiche réelle complète, servie à un anonyme le 23/10/2026.
    private val ficheReelle get() = fixture("film_matrix_full.html")

    // Fiche réduite sans `data-cfp` : seule reste la statistique « Año ».
    private fun sansCfp(page: String) = page.replace(Regex("data-cfp=\"[^\"]*\""), "")

    @Test
    fun `fiche reelle - data-cfp, annee d'en-tete et cfServers sont lus`() {
        val page = ficheReelle
        assertEquals(603, CoflixProvider.tmdbIdOf(page))
        assertEquals("1999", CoflixProvider.ficheYearOf(page))
        // `idioma` vaut « Autre » : sans incidence sur la lecture des lecteurs.
        assertEquals(listOf("https://lecteurvideo.com/embed.php?id=48579"), CoflixProvider.playersOf(page))
        // La fixture réduite reflète le même balisage.
        assertEquals(603, CoflixProvider.tmdbIdOf(fixture("film_matrix.html")))
        assertEquals("1999", CoflixProvider.ficheYearOf(fixture("film_matrix.html")))
    }

    @Test
    fun `fiche reelle - le bon id TMDB est accepte`() = runTest {
        val links = CoflixProvider(gatewayCarteSansAnnee(ficheReelle))
            .sourcesFor(MediaRef.Movie(603, "Matrix", "1999"))
        assertTrue(links.isNotEmpty())
    }

    @Test
    fun `fiche reelle - un autre id TMDB est rejete, meme carte et annee identiques`() = runTest {
        assertTrue(
            CoflixProvider(gatewayCarteSansAnnee(ficheReelle))
                .sourcesFor(MediaRef.Movie(604, "Matrix", "1999")).isEmpty(),
        )
        // La carte affiche l'année : l'id de la fiche tranche quand même.
        assertTrue(CoflixProvider(gateway()).sourcesFor(MediaRef.Movie(604, "Matrix", "1999")).isEmpty())
    }

    @Test
    fun `sans data-cfp, carte sans annee confirmee par l'annee d'en-tete`() = runTest {
        val links = CoflixProvider(gatewayCarteSansAnnee(sansCfp(ficheReelle)))
            .sourcesFor(MediaRef.Movie(603, "Matrix", "1999"))
        assertTrue(links.isNotEmpty())
        assertTrue(
            CoflixProvider(gatewayCarteSansAnnee(sansCfp(ficheReelle)))
                .sourcesFor(MediaRef.Movie(603, "Matrix", "2021")).isEmpty(),
        )
    }

    @Test
    fun `l'annee d'une recommandation ne confirme jamais la fiche`() = runTest {
        // Ni id ni statistique « Año » : il ne reste que les badges des
        // recommandations (2003 dans la fixture réduite), qui ne valent rien.
        val page = sansCfp(fixture("film_matrix.html"))
            .replace(Regex("<span class=\"cf-stat-item\".*?</span>", RegexOption.DOT_MATCHES_ALL), "")
        assertNull(CoflixProvider.ficheYearOf(page))
        assertTrue(page.contains("md-card-badge year\">2003<"))
        assertTrue(
            CoflixProvider(gatewayCarteSansAnnee(page))
                .sourcesFor(MediaRef.Movie(603, "Matrix", "2003")).isEmpty(),
        )
        assertTrue(
            CoflixProvider(gatewayCarteSansAnnee(page))
                .sourcesFor(MediaRef.Movie(603, "Matrix", "1999")).isEmpty(),
        )
    }

    // Page épisode réelle complète, servie à un anonyme le 23/10/2026.
    private fun gatewayEpisodeReel() = Fake(
        mapOf(
            "/?s=Breaking+Bad" to fixture("search_bb.html"),
            "/episode/breaking-bad-1x1/" to fixture("episode_bb_1x1_full.html"),
            "id=10501" to fixture("embed_bb_1x1.html"),
        ),
    )

    @Test
    fun `page episode reelle - data-cfp et cfServers sont lus`() {
        val page = fixture("episode_bb_1x1_full.html")
        assertEquals(1396, CoflixProvider.tmdbIdOf(page))
        assertEquals(listOf("https://lecteurvideo.com/embed.php?id=10501"), CoflixProvider.playersOf(page))
        assertTrue(CoflixProvider.episodeConfirme(page, MediaRef.Episode(1396, "Breaking Bad", "2008", 1, 1)))
        // Saison ou épisode contredits par le lecteur : rejet.
        assertTrue(!CoflixProvider.episodeConfirme(page, MediaRef.Episode(1396, "Breaking Bad", "2008", 2, 1)))
        assertTrue(!CoflixProvider.episodeConfirme(page, MediaRef.Episode(1396, "Breaking Bad", "2008", 1, 2)))
    }

    @Test
    fun `page episode reelle - le bon id TMDB est accepte`() = runTest {
        val links = CoflixProvider(gatewayEpisodeReel())
            .sourcesFor(MediaRef.Episode(1396, "Breaking Bad", "2008", season = 1, episode = 1))
        assertTrue(links.isNotEmpty())
    }

    @Test
    fun `page episode reelle - une serie homonyme (autre id TMDB) est rejetee`() = runTest {
        val gw = gatewayEpisodeReel()
        val links = CoflixProvider(gw)
            .sourcesFor(MediaRef.Episode(99999, "Breaking Bad", "2008", season = 1, episode = 1))
        assertTrue(links.isEmpty())
        // Le titre exact a bien mené à la page épisode : c'est l'id qui tranche.
        assertTrue(gw.requested.any { it.url == "https://coflix.esq/episode/breaking-bad-1x1/" })
        assertTrue(gw.requested.none { "id=10501" in it.url })
    }

    @Test
    fun `seules les URL https sont retenues`() {
        // base64 de http://voe.sx/e/o8wbdtbxzp2g
        val b64 = "aHR0cDovL3ZvZS5zeC9lL284d2JkdGJ4enAyZw=="
        val html = """<li onclick="showVideo('$b64', '2')"><span>x</span><p>French - Server</p></li>"""
        assertTrue(CoflixProvider.linksOf(html).isEmpty())
    }

    @Test
    fun `recherche vide ou lecteur absent rendent une liste vide`() = runTest {
        assertTrue(CoflixProvider(Fake(emptyMap())).sourcesFor(MediaRef.Movie(1, "Matrix", "1999")).isEmpty())
    }
}
