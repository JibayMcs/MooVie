package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La bascule de domaine de SwiftFlow, sans réseau.
 *
 * Ce partenaire a déménagé trois fois en un an — `deliciouss`,
 * `blinkflux.lol`, `swiftflow.lol` — et chaque déménagement a coûté une release
 * pour une ligne, en laissant entre-temps la source muette. Ce qui se teste ici
 * est donc la seule chose qui évite la quatrième : essayer les domaines connus
 * et retenir celui qui répond.
 *
 * Le piège que ces tests verrouillent est le **faux positif**. Un domaine
 * revendu répond 200 avec une page de parking, et le prendre pour l'API rendrait
 * la bascule pire qu'inutile : elle s'arrêterait sur le mauvais.
 */
class SwiftFlowBaseFallbackTest {

    @BeforeTest
    fun magasinsEnMemoire() {
        // `DomainBook` est un objet de processus : sans remise à zéro, l'annuaire
        // d'un test précédent décide des domaines de celui-ci. Et sans magasin en
        // mémoire, sa copie persistée irait dans la vraie configuration.
        useInMemoryStores()
        DomainBook.oublie()
    }

    @AfterTest
    fun magasinsSurFichier() {
        DomainBook.oublie()
        useFileStores()
        // L'objet est global : sans remise à zéro, l'ordre des tests déciderait
        // du Referer lu par les suivants.
        SwiftFlowBase.courant = SwiftFlowProvider.API_BASE
    }

    private val film = MediaRef.Movie(550, "Fight Club", "1999")

    private val jsonValide = """
        {"success":true,"data":{"sources":[
          {"url":"https://cdn.example/f.mp4","language":"VF","size":"3.55 GB"}
        ]}}
    """.trimIndent()

    /**
     * Passerelle scriptée par **domaine**, qui retient l'ordre des appels.
     *
     * Un domaine de [muets] ne répond jamais : c'est ce que fait un domaine mort
     * sur le réseau, qui laisse le client attendre son délai plutôt que de
     * refuser.
     */
    private class Gateway(
        private val parBase: Map<String, HttpResponse?>,
        private val muets: Set<String> = emptySet(),
    ) : HttpGateway {
        val visites = mutableListOf<String>()

        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            // L'annuaire n'est pas un domaine de catalogue : il ne compte pas
            // dans la marche qu'on observe ici, et le servir vide est le cas
            // nominal d'une application qui n'a encore rien appris.
            if (request.url.endsWith("domains.json")) return null
            val base = Regex("""^https://[^/]+""").find(request.url)?.value.orEmpty()
            visites += base
            if (base in muets) awaitCancellation()
            return parBase[base]
        }
    }

    private fun ok(base: String, corps: String) =
        base to HttpResponse(status = 200, url = base, body = corps)

    @Test
    fun `le domaine courant sert, et lui seul est interrogé ensuite`() = runTest {
        val gateway = Gateway(mapOf(ok("https://blinkflux.lol", jsonValide)))
        val provider = SwiftFlowProvider(gateway)

        assertEquals(1, provider.sourcesFor(film).size)
        gateway.visites.clear()
        provider.sourcesFor(MediaRef.Movie(603, "Matrix", "1999"))

        assertEquals(listOf("https://blinkflux.lol"), gateway.visites)
    }

    @Test
    fun `un domaine muet en tête ne consomme pas le budget du suivant`() = runTest {
        // La panne du 22/10/2026 : essayés l'un après l'autre, le premier
        // domaine attendait son délai de connexion, la cascade annulait l'appel
        // à douze secondes, et le domaine sain n'était jamais atteint.
        val gateway = Gateway(
            parBase = mapOf(ok("https://swiftflow.lol", jsonValide)),
            muets = setOf("https://blinkflux.lol"),
        )

        // Bien sous les douze secondes de la cascade : le suivant part après
        // l'échelon, sans attendre que le muet renonce.
        val liens = withTimeout(3_000) { SwiftFlowProvider(gateway).sourcesFor(film) }

        assertEquals("https://cdn.example/f.mp4", liens.single().url)
        assertEquals("https://swiftflow.lol/", SwiftFlowBase.referer)
    }

    @Test
    fun `un domaine injoignable bascule sur le suivant`() = runTest {
        val gateway = Gateway(
            mapOf(
                "https://blinkflux.lol" to null, // ce que rend un 522 ou un délai dépassé
                ok("https://swiftflow.lol", jsonValide),
            ),
        )

        val liens = SwiftFlowProvider(gateway).sourcesFor(film)

        assertEquals("https://cdn.example/f.mp4", liens.single().url)
        assertEquals(listOf("https://blinkflux.lol", "https://swiftflow.lol"), gateway.visites)
    }

    @Test
    fun `une page de parking ne passe pas pour l'API`() = runTest {
        // Le faux positif qui rendrait la bascule dangereuse : 200, donc
        // « joignable », mais ce n'est pas du JSON. On continue.
        val gateway = Gateway(
            mapOf(
                ok("https://blinkflux.lol", "<html><title>Domain for sale</title></html>"),
                ok("https://swiftflow.lol", jsonValide),
            ),
        )

        val liens = SwiftFlowProvider(gateway).sourcesFor(film)

        assertEquals(1, liens.size)
        assertEquals(listOf("https://blinkflux.lol", "https://swiftflow.lol"), gateway.visites)
    }

    @Test
    fun `le domaine retenu est réutilisé au titre suivant`() = runTest {
        val gateway = Gateway(
            mapOf(
                "https://blinkflux.lol" to null,
                ok("https://swiftflow.lol", jsonValide),
            ),
        )
        val provider = SwiftFlowProvider(gateway)

        provider.sourcesFor(film)
        gateway.visites.clear()
        provider.sourcesFor(MediaRef.Movie(603, "Matrix", "1999"))

        assertEquals(
            listOf("https://swiftflow.lol"),
            gateway.visites,
            "le domaine mort ne doit pas être resondé à chaque fiche",
        )
    }

    @Test
    fun `le Referer du CDN suit le domaine retenu`() = runTest {
        // Sans ça, l'extracteur poserait le Referer de l'ancien domaine et le
        // CDN rendrait 403 — avec une liste de sources parfaitement affichée,
        // ce qui rend la panne incompréhensible.
        val gateway = Gateway(
            mapOf(
                "https://blinkflux.lol" to null,
                ok("https://swiftflow.lol", jsonValide),
            ),
        )

        SwiftFlowProvider(gateway).sourcesFor(film)

        assertEquals("https://swiftflow.lol/", SwiftFlowBase.referer)
    }

    @Test
    fun `tous les domaines morts rendent une liste vide, sans relancer la marche`() = runTest {
        val gateway = Gateway(emptyMap()) // aucun domaine ne répond
        val provider = SwiftFlowProvider(gateway)

        assertTrue(provider.sourcesFor(film).isEmpty())
        val apresPremier = gateway.visites.size
        provider.sourcesFor(MediaRef.Movie(603, "Matrix", "1999"))

        assertEquals(2, apresPremier, "les deux domaines connus sont essayés une fois")
        assertEquals(
            apresPremier,
            gateway.visites.size,
            "pendant la fenêtre d'échec, plus aucune requête : deux domaines à 20 s " +
                "de délai sur le chemin de chaque fiche rendraient l'app inutilisable",
        )
    }
}
