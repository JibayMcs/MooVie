package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import java.io.File
import java.util.Collections
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * anime-sama, format des liens du 12/11/2026 - **sans réseau**.
 *
 * Les deux fixtures sont de vraies captures du site (12/11/2026, sans cookie,
 * UA navigateur) : `naruto_saison1_vf_reelle.html`, la page de saison complète,
 * et `lien_reponse.json`, la réponse de `lien.php` au premier repère d'eps1.
 * Les listes `epsN` y portent des repères `lien:N`, résolus un par un par
 * `/api/lien.php`. La passerelle factice sert la recherche, la fiche, les pages
 * de saison et `lien.php`, et garde la trace de chaque requête : c'est elle qui
 * prouve qu'on ne résout que l'épisode demandé.
 *
 * Deux limites assumées. Seule la réponse de 187332 est capturée : les autres
 * repères répondent 404 (« inconnu ») dans les tests sur la page réelle. La fiche
 * du catalogue reste simulée, sa forme n'ayant pas été capturée ici.
 */
class AnimeSamaSeasonPageTest {

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

    // Lue sur le disque, comme les fixtures coflix : le répertoire de travail
    // des tests est le module `app`.
    private fun fixture(name: String): String {
        val f = File("src/jvmCommonTest/resources/animesama/$name")
        check(f.exists()) { "fixture $name absente (${f.absolutePath})" }
        return f.readText()
    }

    private val base = AnimeSamaProvider.BASE
    private val catalogue = "$base/catalogue/naruto/"
    private val dossierVf = "${catalogue}saison1/vf/"
    private val dossierVostfr = "${catalogue}saison1/vostfr/"
    // epsJeton de la page réelle du 12/11/2026.
    private val jeton = "5280.1794582000.ad6a3a368239a810993af1da"

    private val recherche = """
        <a href="$base/catalogue/naruto/" class="asn-search-result">
          <h3 class="asn-search-result-title">Naruto</h3>
          <p class="asn-search-result-subtitle">Naruto</p>
        </a>
    """.trimIndent()

    private val fiche = """
        <script>panneauAnime("Saison 1", "saison1/vostfr");</script>
    """.trimIndent()

    private class Fake(private val route: (HttpRequest) -> HttpResponse?) : HttpGateway {
        // Synchronisée : les appels lien.php partent en parallèle sur
        // dispatcherEs, une liste ordinaire perdait des entrées.
        val requested: MutableList<HttpRequest> = Collections.synchronizedList(mutableListOf())
        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            requested += request
            return route(request) ?: HttpResponse(404, request.url, body = "<html>404</html>")
        }

        fun lienCalls() = requested.filter { "/api/lien.php" in it.url }
    }

    private fun ok(req: HttpRequest, body: String) = HttpResponse(200, req.url, body = body)

    /**
     * La recherche et la fiche, puis [pages] (URL exacte vers corps) et
     * [lien] pour `lien.php` (repère et Referer vers réponse).
     */
    private fun gateway(
        pages: Map<String, String>,
        lien: (id: String, referer: String?, req: HttpRequest) -> HttpResponse? = { _, _, _ -> null },
    ) = Fake { req ->
        when {
            req.url.endsWith("/template-php/defaut/fetch.php") -> ok(req, recherche)
            req.url == catalogue -> ok(req, fiche)
            "/api/lien.php" in req.url ->
                Regex("""[?&]l=(\d+)""").find(req.url)?.groupValues?.get(1)
                    ?.let { lien(it, req.headers["Referer"], req) }
            else -> pages[req.url]?.let { ok(req, it) }
        }
    }

    /** Réponse réelle de `lien.php`, pour le seul repère capturé (187332, VF). */
    private fun lienReel(id: String, referer: String?, req: HttpRequest): HttpResponse? =
        if (id == "187332" && referer == dossierVf) ok(req, fixture("lien_reponse.json")) else null

    /**
     * `lien.php` synthétique : une URL d'embed propre à la langue et au repère.
     * Les hôtes suivent l'`epsMeta` réelle (187331 uqload, 187332 ansembed,
     * 187333 ashleyhardat, puis la même alternance). Seule la forme de l'appel
     * est mesurée ici, pas le contenu de la réponse.
     */
    private fun lienSynthetique(id: String, referer: String?, req: HttpRequest): HttpResponse? {
        val lang = referer?.trimEnd('/')?.substringAfterLast('/') ?: return null
        val hote = when (id.toInt() % 3) {
            2 -> "uqload.vc"
            0 -> "ansembed.net"
            else -> "ashleyhardat.com"
        }
        return ok(req, """{"u":"https://$hote/embed-$lang-$id.html"}""")
    }

    private val episode1 = MediaRef.Episode(tmdbId = 46260, title = "Naruto", season = 1, episode = 1)

    @Test
    fun `la page reelle se lit - trois lecteurs, hotes annonces et jeton`() {
        val page = AnimeSamaProvider.parseSeasonPage(fixture("naruto_saison1_vf_reelle.html"))
        assertEquals(3, page.lecteurs.size)
        assertEquals(listOf("lien:187332", "lien:187335", "lien:187338"), page.lecteurs[0].take(3))
        assertEquals(listOf("lien:187331", "lien:187334", "lien:187337"), page.lecteurs[1].take(3))
        assertEquals(listOf("lien:187333", "lien:187336", "lien:187339"), page.lecteurs[2].take(3))
        assertEquals(listOf("ansembed.net"), page.meta["187332"])
        assertEquals(listOf("ashleyhardat.com"), page.meta["187333"])
        assertEquals(jeton, page.jeton)
    }

    @Test
    fun `page reelle, episode 1 - repere lu par lien_php avec le Referer et le jeton reels`() = runTest {
        val gw = gateway(mapOf(dossierVf to fixture("naruto_saison1_vf_reelle.html")), ::lienReel)

        val links = AnimeSamaProvider(gw).sourcesFor(episode1)

        // Seul 187332 a une réponse capturée : les deux autres repères répondent 404 et tombent.
        assertEquals(
            listOf(EmbedLink("https://ansembed.net/embed-8fq7j3p33rt6.html", "ansembed", "VF")),
            links,
        )
        // Un appel par lecteur utile pour l'épisode 1 : les trois lecteurs sont des
        // hôtes que l'application sait lire, ashleyhardat compris.
        val calls = gw.lienCalls()
        assertEquals(3, calls.size, calls.joinToString { it.url })
        assertEquals(
            setOf(
                "$base/api/lien.php?j=$jeton&l=187332",
                "$base/api/lien.php?j=$jeton&l=187331",
                "$base/api/lien.php?j=$jeton&l=187333",
            ),
            calls.map { it.url }.toSet(),
        )
        assertTrue(calls.all { it.headers["Referer"] == dossierVf })
        assertTrue(gw.requested.all { it.headers["User-Agent"]?.contains("Mozilla") == true })
        // Page de saison trouvée : l'ancien episodes.js n'est pas demandé.
        assertTrue(gw.requested.none { it.url == "${dossierVf}episodes.js" })
    }

    @Test
    fun `mecanique - VF et VOSTFR, un appel par lecteur et par langue, Referer propre a chaque page`() = runTest {
        // La même page réelle sert aux deux langues : on éprouve la mécanique,
        // pas le contenu de la page VOSTFR, qui n'est pas capturée.
        val page = fixture("naruto_saison1_vf_reelle.html")
        val gw = gateway(mapOf(dossierVf to page, dossierVostfr to page), ::lienSynthetique)

        val links = AnimeSamaProvider(gw).sourcesFor(episode1)

        assertEquals(
            listOf(
                EmbedLink("https://ansembed.net/embed-vf-187332.html", "ansembed", "VF"),
                EmbedLink("https://uqload.vc/embed-vf-187331.html", "uqload", "VF"),
                EmbedLink("https://ashleyhardat.com/embed-vf-187333.html", "ashleyhardat", "VF"),
                EmbedLink("https://ansembed.net/embed-vostfr-187332.html", "ansembed", "VOSTFR"),
                EmbedLink("https://uqload.vc/embed-vostfr-187331.html", "uqload", "VOSTFR"),
                EmbedLink("https://ashleyhardat.com/embed-vostfr-187333.html", "ashleyhardat", "VOSTFR"),
            ),
            links,
        )
        val calls = gw.lienCalls()
        assertEquals(6, calls.size, calls.joinToString { it.url })
        val attendus = buildSet {
            for (dossier in listOf(dossierVf, dossierVostfr)) {
                for (id in listOf("187332", "187331", "187333")) {
                    add("$base/api/lien.php?j=$jeton&l=$id" to dossier)
                }
            }
        }
        assertEquals(attendus, calls.map { it.url to it.headers["Referer"] }.toSet())
        assertTrue(gw.requested.all { it.headers["User-Agent"]?.contains("Mozilla") == true })
        // Page de saison trouvée : l'ancien episodes.js n'est pas demandé.
        assertTrue(gw.requested.none { it.url == "${dossierVf}episodes.js" })
    }

    @Test
    fun `episode N - le repere de rang N de chaque lecteur, et lui seul`() = runTest {
        val gw = gateway(
            mapOf(dossierVf to fixture("naruto_saison1_vf_reelle.html")),
            ::lienSynthetique,
        )

        val links = AnimeSamaProvider(gw).sourcesFor(episode1.copy(episode = 3))

        assertEquals(
            listOf(
                "https://ansembed.net/embed-vf-187338.html",
                "https://uqload.vc/embed-vf-187337.html",
                "https://ashleyhardat.com/embed-vf-187339.html",
            ),
            links.map { it.url },
        )
        assertEquals(
            setOf("187338", "187337", "187339"),
            gw.lienCalls().map { it.url.substringAfter("l=") }.toSet(),
        )
    }

    @Test
    fun `episode hors saison - rien, et aucun appel`() = runTest {
        // La saison compte 220 épisodes : le 221e n'a aucun repère.
        val gw = gateway(
            mapOf(dossierVf to fixture("naruto_saison1_vf_reelle.html")),
            ::lienReel,
        )
        assertEquals(emptyList(), AnimeSamaProvider(gw).sourcesFor(episode1.copy(episode = 221)))
        assertEquals(0, gw.lienCalls().size)
    }

    @Test
    fun `un echec de lien_php ignore ce lien, les autres continuent`() = runTest {
        val echecs = listOf<(HttpRequest) -> HttpResponse?>(
            { null },                                                   // réseau
            { HttpResponse(500, it.url, body = """{"u":"https://x.net/e"}""") },
            { ok(it, "<html>Attention Required</html>") },              // pas de JSON
            { ok(it, "{}") },                                           // u absent
            { ok(it, """{"u":null}""") },
            { ok(it, """{"u":"http://ansembed.net/embed-clair.html"}""") }, // pas https
            { ok(it, """{"u":"lien:187332"}""") },
            { ok(it, """["https://ansembed.net/embed-tableau.html"]""") },
        )
        for ((i, echec) in echecs.withIndex()) {
            val gw = gateway(mapOf(dossierVf to fixture("naruto_saison1_vf_reelle.html"))) { id, ref, req ->
                if (id == "187332") echec(req) else lienSynthetique(id, ref, req)
            }
            val links = AnimeSamaProvider(gw).sourcesFor(episode1)
            assertEquals(
                listOf(
                    EmbedLink("https://uqload.vc/embed-vf-187331.html", "uqload", "VF"),
                    EmbedLink("https://ashleyhardat.com/embed-vf-187333.html", "ashleyhardat", "VF"),
                ),
                links,
                "cas d'échec n°$i",
            )
        }
    }

    @Test
    fun `ancien format dans la page - URL en clair, sans lien_php`() = runTest {
        val page = """
            <script data-source="bdd">
              var eps1 = ['https://video.sibnet.ru/shell.php?videoid=111', 'https://video.sibnet.ru/shell.php?videoid=112'];
              var eps2 = ["https://vidmoly.to/embed-aaa.html", "https://vidmoly.to/embed-bbb.html"];
              var eps3 = ["lien:900", "lien:901"];
              var epsJeton = "1.2.abc";
            </script>
        """.trimIndent()
        val gw = gateway(mapOf(dossierVf to page)) { id, _, req ->
            ok(req, """{"u":"https://ansembed.net/embed-$id.html"}""")
        }

        val links = AnimeSamaProvider(gw).sourcesFor(episode1.copy(episode = 2))

        assertEquals(
            listOf(
                EmbedLink("https://video.sibnet.ru/shell.php?videoid=112", "video", "VF"),
                EmbedLink("https://vidmoly.to/embed-bbb.html", "vidmoly", "VF"),
                EmbedLink("https://ansembed.net/embed-901.html", "ansembed", "VF"),
            ),
            links,
        )
        assertEquals(listOf("$base/api/lien.php?j=1.2.abc&l=901"), gw.lienCalls().map { it.url })
    }

    @Test
    fun `ancien format - une URL en clair doit etre en https, comme un lien resolu`() = runTest {
        val page = """
            <script data-source="bdd">
              var eps1 = ['http://vidmoly.to/embed-clair.html', 'https://vidmoly.to/embed-ok.html'];
            </script>
        """.trimIndent()
        val gw = gateway(mapOf(dossierVf to page))

        // Épisode 1 : l'URL en http est écartée, sans appel à lien.php.
        assertEquals(emptyList(), AnimeSamaProvider(gw).sourcesFor(episode1))
        // Épisode 2 : la même forme en https passe.
        assertEquals(
            listOf(EmbedLink("https://vidmoly.to/embed-ok.html", "vidmoly", "VF")),
            AnimeSamaProvider(gw).sourcesFor(episode1.copy(episode = 2)),
        )
        assertEquals(0, gw.lienCalls().size)
    }

    @Test
    fun `repli episodes_js - page de saison 404 ou sans tableau`() = runTest {
        val js = """
            var eps1 = ['https://video.sibnet.ru/shell.php?videoid=1',
              'https://video.sibnet.ru/shell.php?videoid=2',];
            var eps2 = ['https://vidmoly.to/embed-x1.html','https://vidmoly.to/embed-x2.html'];
        """.trimIndent()
        val attendu = listOf(
            EmbedLink("https://video.sibnet.ru/shell.php?videoid=1", "video", "VF"),
            EmbedLink("https://vidmoly.to/embed-x1.html", "vidmoly", "VF"),
        )

        // Page de saison absente (404) : seul episodes.js répond.
        val absente = gateway(mapOf("${dossierVf}episodes.js" to js))
        assertEquals(attendu, AnimeSamaProvider(absente).sourcesFor(episode1))
        assertEquals(
            dossierVf,
            absente.requested.first { it.url == "${dossierVf}episodes.js" }.headers["Referer"],
        )

        // Page de saison servie mais sans liste : même repli.
        val vide = gateway(
            mapOf(dossierVf to "<html><body>Naruto</body></html>", "${dossierVf}episodes.js" to js),
        )
        assertEquals(attendu, AnimeSamaProvider(vide).sourcesFor(episode1))
        assertEquals(0, vide.lienCalls().size)
    }

    @Test
    fun `epsMeta sert a economiser, jamais a etiqueter`() {
        assertTrue(AnimeSamaProvider.worthResolving(null))
        assertTrue(AnimeSamaProvider.worthResolving(emptyList()))
        assertTrue(AnimeSamaProvider.worthResolving(listOf("ansembed.net")))
        assertTrue(AnimeSamaProvider.worthResolving(listOf("vidzy.org", "ansembed.net")))
        assertTrue(!AnimeSamaProvider.worthResolving(listOf("vidzy.org")))
    }
}
