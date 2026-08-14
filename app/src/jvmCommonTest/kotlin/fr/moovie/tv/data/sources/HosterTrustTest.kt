package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.usecase.HosterTrust
import fr.moovie.tv.core.sources.usecase.UNKNOWN_HEIGHT
import fr.moovie.tv.core.sources.usecase.orderedLinksFor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * La mémoire d'hébergeurs, et son effet sur l'ordre de la cascade.
 *
 * Deux règles s'y croisent et peuvent se contredire : la définition d'image
 * décide, la fiabilité départage. Les inverser ferait passer un hébergeur
 * fidèle en 480p devant un 1080p jamais essayé — un défaut qu'on ne verrait pas
 * en lisant le code, seulement en regardant un film flou.
 */
class HosterTrustTest {

    private fun lien(hoster: String, url: String = "https://$hoster/x") =
        EmbedLink(url = url, hoster = hoster, language = "VF")

    private fun ordre(
        links: List<EmbedLink>,
        heights: Map<String, Int> = emptyMap(),
        trust: Map<String, HosterTrust> = emptyMap(),
    ) = orderedLinksFor(links, preferred = "VF", heights = heights, trust = trust)
        .map { it.hoster }

    // ── Le verdict ───────────────────────────────────────────────────────────

    @Test
    fun `une seule reussite suffit a sortir du purgatoire`() {
        assertEquals(HosterTrust.GOOD, verdict(reussites = 1, echecs = 0))
        // Même après une série d'échecs : ce qui a joué ici peut rejouer.
        assertEquals(HosterTrust.GOOD, verdict(reussites = 1, echecs = 99))
    }

    @Test
    fun `quelques echecs ne condamnent pas`() {
        // Une panne passagère ne doit pas reléguer pour autant.
        (0 until SEUIL_ECHECS).forEach { n ->
            assertEquals(HosterTrust.UNKNOWN, verdict(reussites = 0, echecs = n), "échecs=$n")
        }
        assertEquals(HosterTrust.BAD, verdict(reussites = 0, echecs = SEUIL_ECHECS))
    }

    // ── L'ordre ──────────────────────────────────────────────────────────────

    /** Le cas mesuré : netu proposé 18 fois, jamais jouable. */
    @Test
    fun `un hebergeur condamne passe en dernier`() {
        val links = listOf(lien("netu"), lien("uqload"), lien("voe"))
        assertEquals(
            listOf("uqload", "voe", "netu"),
            ordre(links, trust = mapOf("netu" to HosterTrust.BAD)),
        )
    }

    /**
     * Il passe même derrière un lien **mesuré moins bon** : sans cette règle il
     * siégeait au pivot des 720, donc devant des liens qui, eux, jouent.
     */
    @Test
    fun `un hebergeur condamne passe derriere un lien de moindre definition`() {
        val links = listOf(lien("netu"), lien("uqload"))
        val heights = mapOf("https://uqload/x" to 480)
        assertEquals(
            listOf("uqload", "netu"),
            ordre(links, heights = heights, trust = mapOf("netu" to HosterTrust.BAD)),
        )
    }

    /** Mais il reste **proposé** : si tout le reste échoue, on le tente. */
    @Test
    fun `un hebergeur condamne n est pas exclu`() {
        val links = listOf(lien("netu"))
        assertEquals(listOf("netu"), ordre(links, trust = mapOf("netu" to HosterTrust.BAD)))
    }

    /** La fiabilité départage à définition égale, et seulement là. */
    @Test
    fun `un hebergeur eprouve passe devant un inconnu de meme definition`() {
        val links = listOf(lien("inconnu"), lien("fidele"))
        assertEquals(
            listOf("fidele", "inconnu"),
            ordre(links, trust = mapOf("fidele" to HosterTrust.GOOD)),
        )
    }

    /**
     * **La définition reste maîtresse.** C'est l'inversion qu'on ferait
     * naturellement en écrivant la fiabilité en premier critère.
     */
    @Test
    fun `la definition l emporte sur la fidelite`() {
        val links = listOf(lien("fidele"), lien("inconnu"))
        val heights = mapOf(
            "https://fidele/x" to 480,
            "https://inconnu/x" to 1080,
        )
        assertEquals(
            listOf("inconnu", "fidele"),
            ordre(links, heights = heights, trust = mapOf("fidele" to HosterTrust.GOOD)),
        )
    }

    /** Sans mémoire, l'ordre est exactement celui d'avant : ordre des providers. */
    @Test
    fun `une memoire vide ne change rien`() {
        val links = listOf(lien("a"), lien("b"), lien("c"))
        assertEquals(listOf("a", "b", "c"), ordre(links))
        assertEquals(UNKNOWN_HEIGHT, 720)
    }
}
