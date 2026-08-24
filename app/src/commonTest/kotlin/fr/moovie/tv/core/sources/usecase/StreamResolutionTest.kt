package fr.moovie.tv.core.sources.usecase

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.SourceExtractor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Politique de résolution — aucun réseau, aucune plateforme.
 *
 * C'est la logique la plus subtile de la couche sources : elle décide dans quel
 * ordre les extracteurs sont sollicités et ce qu'il advient quand l'un échoue.
 * Jusqu'ici elle ne pouvait se vérifier qu'en lançant l'app contre de vrais
 * hébergeurs ; sortie dans le domaine, elle se teste avec des doublures.
 */
class StreamResolutionTest {

    // --- Doublures ------------------------------------------------------------

    private class FakeExtractor(
        override val hoster: String,
        private val claims: (String) -> Boolean = { false },
        private val onExtract: suspend (EmbedLink) -> PlayableStream? = { null },
    ) : SourceExtractor {
        var calls = 0
            private set

        override fun canHandle(url: String) = claims(url)

        override suspend fun extract(link: EmbedLink): PlayableStream? {
            calls++
            return onExtract(link)
        }
    }

    private fun stream(url: String) = PlayableStream(url = url, format = StreamFormat.HLS)

    private fun claiming(hoster: String, host: String, result: PlayableStream?) =
        FakeExtractor(hoster, claims = { host in it }, onExtract = { result })

    private val link = EmbedLink(url = "https://acme.example/e/abc", hoster = "acme")

    // --- Règle 1 : le domaine d'abord ----------------------------------------

    @Test
    fun `l'extracteur qui revendique le domaine est utilisé`() = runTest {
        val good = claiming("acme", "acme.example", stream("https://cdn/ok.m3u8"))
        val other = claiming("autre", "autre.example", stream("https://cdn/nope.m3u8"))

        val resolved = StreamResolution(extractors = listOf(other, good)).resolve(link)

        assertEquals("https://cdn/ok.m3u8", resolved?.url)
        assertEquals(0, other.calls, "un extracteur d'un autre domaine ne doit pas être appelé")
    }

    @Test
    fun `sans extracteur ni renifleur, on ne résout rien`() = runTest {
        assertNull(StreamResolution(extractors = emptyList()).resolve(link))
    }

    // --- Règle 2 : le reniflage en dernier recours ---------------------------

    @Test
    fun `un domaine que personne ne revendique passe aux renifleurs`() = runTest {
        // Le cas VOE : un alias inédit, qu'aucun motif codé en dur ne connaît.
        val sniffer = FakeExtractor("voe", onExtract = { stream("https://cdn/sniffed.m3u8") })

        val resolved = StreamResolution(extractors = emptyList(), sniffers = listOf(sniffer))
            .resolve(link)

        assertEquals("https://cdn/sniffed.m3u8", resolved?.url)
    }

    @Test
    fun `un extracteur qui revendique mais échoue laisse sa chance aux renifleurs`() = runTest {
        val failing = claiming("acme", "acme.example", null)
        val sniffer = FakeExtractor("voe", onExtract = { stream("https://cdn/rattrape.m3u8") })

        val resolved = StreamResolution(listOf(failing), listOf(sniffer)).resolve(link)

        assertEquals("https://cdn/rattrape.m3u8", resolved?.url)
        assertEquals(1, failing.calls)
    }

    @Test
    fun `un renifleur déjà tenté par domaine n'est pas rejoué`() = runTest {
        // VOE est enregistré dans les deux listes : il revendique ses domaines
        // connus ET renifle les inconnus. Il ne doit pas payer deux requêtes.
        val voe = FakeExtractor("voe", claims = { "acme.example" in it }, onExtract = { null })

        val resolved = StreamResolution(listOf(voe), listOf(voe)).resolve(link)

        assertNull(resolved)
        assertEquals(1, voe.calls, "le même extracteur ne doit être sollicité qu'une fois")
    }

    @Test
    fun `les renifleurs sont essayés dans l'ordre et on s'arrête au premier succès`() = runTest {
        val specific = FakeExtractor("voe", onExtract = { null })
        val generic = FakeExtractor("packed", onExtract = { stream("https://cdn/generic.m3u8") })
        val jamais = FakeExtractor("jamais", onExtract = { stream("https://cdn/jamais.m3u8") })

        val resolved = StreamResolution(emptyList(), listOf(specific, generic, jamais)).resolve(link)

        assertEquals("https://cdn/generic.m3u8", resolved?.url)
        assertEquals(1, specific.calls)
        assertEquals(0, jamais.calls, "rien ne doit être tenté après un succès")
    }

    // --- Robustesse : une exception ne casse pas la chaîne -------------------

    @Test
    fun `un extracteur qui lève est traité comme un échec`() = runTest {
        // Régression réelle : une regex invalide sur Android levait au milieu de
        // la boucle. Une source cassée ne doit jamais emporter les autres.
        val explosif = FakeExtractor("boum", claims = { true }, onExtract = { error("regex invalide") })
        val sniffer = FakeExtractor("voe", onExtract = { stream("https://cdn/survivant.m3u8") })

        val resolved = StreamResolution(listOf(explosif), listOf(sniffer)).resolve(link)

        assertEquals("https://cdn/survivant.m3u8", resolved?.url)
    }

    @Test
    fun `un renifleur qui lève n'empêche pas le suivant`() = runTest {
        val explosif = FakeExtractor("boum", onExtract = { error("réseau") })
        val ok = FakeExtractor("packed", onExtract = { stream("https://cdn/ok.m3u8") })

        val resolved = StreamResolution(emptyList(), listOf(explosif, ok)).resolve(link)

        assertEquals("https://cdn/ok.m3u8", resolved?.url)
    }

    // --- claimsDomain ne préjuge de rien -------------------------------------

    @Test
    fun `claimsDomain dit seulement si un domaine est revendiqué`() = runTest {
        val resolution = StreamResolution(
            extractors = listOf(claiming("acme", "acme.example", null)),
            sniffers = listOf(FakeExtractor("voe", onExtract = { stream("https://cdn/x.m3u8") })),
        )

        assertTrue(resolution.claimsDomain("https://acme.example/e/1"))
        // Non revendiqué, et pourtant résoluble : c'est tout l'intérêt du reniflage.
        val inconnu = EmbedLink(url = "https://inconnu.example/e/1", hoster = "inconnu")
        assertEquals(false, resolution.claimsDomain(inconnu.url))
        assertEquals("https://cdn/x.m3u8", resolution.resolve(inconnu)?.url)
    }

    // --- Routage par nom d'hébergeur -----------------------------------------

    /**
     * **Le test qui compte, et il a été payé.** SwiftFlow a changé le domaine de
     * son CDN. Son extracteur ne reconnaissait plus l'URL, le lien est tombé sur
     * celui qui revendique tout `.mp4`, et il est reparti sans le `Referer` que
     * le CDN exige — lequel répond alors une page HTML **en 200**. La source la
     * plus fiable du catalogue s'est éteinte sans qu'aucun test n'échoue.
     *
     * Le lien portait pourtant le nom de son hébergeur depuis le catalogue.
     * Router dessus rend la résolution insensible aux rotations de CDN.
     */
    @Test
    fun `un lien nomme va a son extracteur, meme si l URL a change de domaine`() = runTest {
        val flux = PlayableStream("https://cdn/f.mp4", StreamFormat.MP4)
        // Il ne revendique plus l'URL : le domaine a tourné sous lui.
        val nomme = FakeExtractor("swiftflow", claims = { false }, onExtract = { flux })
        // Et celui-ci ramasse tout .mp4, comme le fait DirectStreamExtractor.
        val attrapeTout = FakeExtractor("direct", claims = { it.endsWith(".mp4") })

        val resolution = StreamResolution(listOf(attrapeTout, nomme))
        val rendu = resolution.resolve(
            EmbedLink(url = "https://nouveau-cdn.test/f.mp4", hoster = "swiftflow"),
        )

        assertEquals(flux, rendu)
        assertEquals(1, nomme.calls, "l'extracteur nommé n'a pas été sollicité")
        assertEquals(0, attrapeTout.calls, "l'attrape-tout est passé devant le nom")
    }

    /** Un nom inconnu ne bloque rien : on retombe sur la reconnaissance d'URL. */
    @Test
    fun `un hebergeur sans extracteur retombe sur le domaine`() = runTest {
        val flux = PlayableStream("https://cdn/f.mp4", StreamFormat.MP4)
        val parDomaine = FakeExtractor("direct", claims = { true }, onExtract = { flux })

        val rendu = StreamResolution(listOf(parDomaine))
            .resolve(EmbedLink(url = "https://x.test/f.mp4", hoster = "premium"))

        assertEquals(flux, rendu)
        assertEquals(1, parDomaine.calls)
    }

    /** L'extracteur nommé échoue : le chemin par domaine reste disponible. */
    @Test
    fun `un extracteur nomme qui echoue laisse sa chance au domaine`() = runTest {
        val flux = PlayableStream("https://cdn/f.mp4", StreamFormat.MP4)
        val nomme = FakeExtractor("voe", claims = { false }, onExtract = { null })
        val parDomaine = FakeExtractor("direct", claims = { true }, onExtract = { flux })

        val rendu = StreamResolution(listOf(nomme, parDomaine))
            .resolve(EmbedLink(url = "https://x.test/f.mp4", hoster = "voe"))

        assertEquals(flux, rendu)
        assertEquals(1, nomme.calls)
        assertEquals(1, parDomaine.calls)
    }
}
