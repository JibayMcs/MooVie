package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L'annuaire des domaines : ce qu'il corrige, et ce qu'il ne peut pas casser.
 *
 * Sa raison d'être est de rendre un déménagement de catalogue réparable **sans
 * release** — le cycle qui a coûté une version à chaque rotation de SwiftFlow,
 * de french-stream et de frembed. Mais un fichier distant qui pilote les
 * requêtes d'une application est aussi une surface : ces tests fixent les deux
 * moitiés du contrat, ce qu'il peut faire et ce dont il est incapable.
 */
class DomainBookTest {

    @BeforeTest
    fun avant() {
        useInMemoryStores()
        DomainBook.oublie()
    }

    @AfterTest
    fun apres() {
        DomainBook.oublie()
        useFileStores()
        SwiftFlowBase.courant = SwiftFlowProvider.API_BASE
    }

    private val annuaire = """
        {"version":1,"providers":{
          "swiftflow":["https://nouveau.example"],
          "fstream":["https://fs-nouveau.example"]
        }}
    """.trimIndent()

    /** Sert l'annuaire sur son URL, et ce qu'on veut sur les autres. */
    private class Gateway(
        private val book: String?,
        private val autres: Map<String, HttpResponse?> = emptyMap(),
    ) : HttpGateway {
        val visites = mutableListOf<String>()
        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            visites += request.url
            if (request.url.endsWith("domains.json")) {
                return book?.let { HttpResponse(status = 200, url = request.url, body = it) }
            }
            val base = Regex("""^https://[^/]+""").find(request.url)?.value.orEmpty()
            return autres[base]
        }
    }

    // --- Le décodage, sans réseau --------------------------------------------

    @Test
    fun `seules les origines https sont retenues`() {
        // Une entrée en clair accepterait de faire voyager une requête en http
        // sans que personne ne s'en aperçoive. Elle est écartée, silencieusement
        // mais sans faire tomber le reste du fichier.
        val lu = DomainBook.decode(
            """{"providers":{"x":["http://clair.example","https://sur.example","pas-une-url",""]}}""",
        )

        assertEquals(mapOf("x" to listOf("https://sur.example")), lu)
    }

    @Test
    fun `un fichier illisible ne rend rien plutôt que n'importe quoi`() {
        assertNull(DomainBook.decode("<html>404</html>"))
        assertNull(DomainBook.decode("""{"providers":"pas un objet"}"""))
        assertNull(DomainBook.decode(""))
    }

    @Test
    fun `la barre finale est normalisée`() {
        // Sinon « https://x.example/ » + « /api » donnerait une double barre,
        // que certains hôtes refusent.
        assertEquals(
            mapOf("x" to listOf("https://x.example")),
            DomainBook.decode("""{"providers":{"x":["https://x.example/"]}}"""),
        )
    }

    // --- Ce que ça change pour une source ------------------------------------

    @Test
    fun `l'annuaire passe devant le domaine compilé`() = runTest {
        val gateway = Gateway(
            book = annuaire,
            autres = mapOf(
                "https://nouveau.example" to HttpResponse(
                    status = 200,
                    url = "https://nouveau.example",
                    body = """{"success":true,"data":{"sources":[{"url":"https://cdn/f.mp4"}]}}""",
                ),
            ),
        )

        val liens = SwiftFlowProvider(gateway).sourcesFor(MediaRef.Movie(550, "Fight Club"))

        assertEquals("https://cdn/f.mp4", liens.single().url)
        assertTrue(
            gateway.visites.none { it.startsWith("https://swiftflow.lol") },
            "le domaine compilé ne devrait même pas être essayé : ${gateway.visites}",
        )
    }

    @Test
    fun `le domaine compilé reste le plancher quand l'annuaire est injoignable`() = runTest {
        // GitHub hors ligne, pare-feu, DNS : l'application doit se comporter
        // exactement comme avant l'annuaire, jamais moins bien.
        val gateway = Gateway(
            book = null,
            autres = mapOf(
                "https://swiftflow.lol" to HttpResponse(
                    status = 200,
                    url = "https://swiftflow.lol",
                    body = """{"success":true,"data":{"sources":[{"url":"https://cdn/f.mp4"}]}}""",
                ),
            ),
        )

        val liens = SwiftFlowProvider(gateway).sourcesFor(MediaRef.Movie(550, "Fight Club"))

        assertEquals(1, liens.size, "le plancher compilé doit rester servi")
    }

    @Test
    fun `un domaine annoncé mais mort ne condamne pas la source`() = runTest {
        // Le cas d'une erreur de saisie dans le fichier : la sonde de SwiftFlow
        // exige du JSON, donc le candidat est écarté et le plancher reprend la
        // main. C'est ce qui rend le fichier peu dangereux.
        val gateway = Gateway(
            book = annuaire,
            autres = mapOf(
                "https://nouveau.example" to null,
                "https://swiftflow.lol" to HttpResponse(
                    status = 200,
                    url = "https://swiftflow.lol",
                    body = """{"success":true,"data":{"sources":[{"url":"https://cdn/f.mp4"}]}}""",
                ),
            ),
        )

        val liens = SwiftFlowProvider(gateway).sourcesFor(MediaRef.Movie(550, "Fight Club"))

        assertEquals(1, liens.size)
    }

    @Test
    fun `une source à domaine unique suit l'annuaire`() = runTest {
        val gateway = Gateway(book = annuaire)

        FstreamProvider(gateway).sourcesFor(MediaRef.Movie(550, "Fight Club"))

        assertTrue(
            gateway.visites.any { it.startsWith("https://fs-nouveau.example/engine/ajax/search.php") },
            "fstream doit chercher sur le domaine de l'annuaire : ${gateway.visites}",
        )
    }

    @Test
    fun `une source absente du fichier garde son domaine`() = runTest {
        // Le fichier ne mentionne ni cinestream ni wiflix : ils ne doivent pas
        // pour autant perdre le leur.
        val gateway = Gateway(book = annuaire)

        CinestreamProvider(gateway).sourcesFor(MediaRef.Movie(550, "Fight Club"))

        assertTrue(
            gateway.visites.any { it.startsWith("https://cinestream.info/search") },
            "cinestream doit garder son domaine compilé : ${gateway.visites}",
        )
    }

    @Test
    fun `l'annuaire n'est téléchargé qu'une fois pour plusieurs titres`() = runTest {
        val gateway = Gateway(book = annuaire)
        val provider = FstreamProvider(gateway)

        provider.sourcesFor(MediaRef.Movie(550, "Fight Club"))
        provider.sourcesFor(MediaRef.Movie(603, "Matrix"))

        assertEquals(
            1,
            gateway.visites.count { it.endsWith("domains.json") },
            "une requête par fiche ouverte serait un coût permanent pour un fichier qui bouge trois fois par an",
        )
    }
}
