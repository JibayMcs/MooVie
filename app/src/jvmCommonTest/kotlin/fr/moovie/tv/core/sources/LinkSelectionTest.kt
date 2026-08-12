package fr.moovie.tv.core.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.usecase.nextLinkFor
import fr.moovie.tv.core.sources.usecase.orderedLinksFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkSelectionTest {

    private fun link(hoster: String, language: String) =
        EmbedLink(url = "https://$hoster.test/$language", hoster = hoster, language = language)

    private val vf = link("uqload", "VF")
    private val vostfr = link("voe", "VOSTFR")
    private val vo = link("vidapi", "VO")

    @Test
    fun `il rend le premier lien de la langue demandee`() {
        assertEquals(vo, nextLinkFor(listOf(vf, vostfr, vo), preferred = "VO"))
        assertEquals(vf, nextLinkFor(listOf(vostfr, vf, vo), preferred = "VF"))
    }

    /**
     * Le cœur de la règle : le VOSTFR de ces catalogues est **hardsubbé**. Le
     * servir à qui a demandé la VO lui collerait des sous-titres français
     * indélébiles — une substitution qu'on ne fait donc jamais, aussi proche
     * que soit la piste audio.
     */
    @Test
    fun `le VOSTFR ne remplace jamais la VO`() {
        assertNull(nextLinkFor(listOf(vf, vostfr), preferred = "VO"))
    }

    @Test
    fun `la VO ne remplace jamais le VOSTFR ni la VF`() {
        assertNull(nextLinkFor(listOf(vo), preferred = "VOSTFR"))
        assertNull(nextLinkFor(listOf(vo, vostfr), preferred = "VF"))
    }

    /** Les hébergeurs déjà écartés sont sautés, sans changer de langue pour autant. */
    @Test
    fun `il saute les liens ecartes`() {
        val autreVo = link("mirror", "VO")

        assertEquals(autreVo, nextLinkFor(listOf(vo, autreVo), preferred = "VO", excluded = setOf(vo.url)))
        assertNull(
            nextLinkFor(listOf(vo, autreVo, vostfr), preferred = "VO", excluded = setOf(vo.url, autreVo.url)),
        )
    }

    /**
     * La fonction ne connaît aucune langue en dur : une étiquette inédite
     * (VES, VOSTA…) se sélectionne sans qu'on la touche.
     */
    @Test
    fun `une langue inconnue du code se selectionne comme les autres`() {
        val ves = link("hoster", "VES")

        assertEquals(ves, nextLinkFor(listOf(vf, ves, vo), preferred = "VES"))
        assertNull(nextLinkFor(listOf(vf, vo), preferred = "VES"))
    }

    @Test
    fun `sans lien il n y a rien a jouer`() {
        assertNull(nextLinkFor(emptyList(), preferred = "VO"))
    }
}

/**
 * Classement par qualité mesurée. Pur, donc testé sans réseau — c'est la moitié
 * de la promesse « la meilleure qualité qui marche » ; l'autre moitié est le
 * repli de la cascade, qui descend simplement cette liste.
 */
class LinkQualityOrderTest {

    private fun lien(url: String, lang: String = "VF") =
        EmbedLink(url = url, hoster = url, language = lang)

    private val liens = listOf(lien("a"), lien("b"), lien("c"))

    @Test
    fun `sans aucune mesure l'ordre des providers est intact`() {
        // Fiche à peine ouverte : rien n'est mesuré, on ne change rien.
        assertEquals(listOf("a", "b", "c"), orderedLinksFor(liens, "VF").map { it.url })
        assertEquals("a", nextLinkFor(liens, "VF")?.url)
    }

    @Test
    fun `la definition la plus haute passe devant`() {
        val h = mapOf("a" to 480, "b" to 1080, "c" to 720)
        assertEquals(listOf("b", "c", "a"), orderedLinksFor(liens, "VF", heights = h).map { it.url })
        assertEquals("b", nextLinkFor(liens, "VF", heights = h)?.url)
    }

    @Test
    fun `un lien non mesure ne passe pas derriere un mauvais lien mesure`() {
        // « a » est mesuré à 360p, « b » et « c » ne sont pas encore mesurés :
        // les reléguer ferait dégringoler le meilleur catalogue pour la seule
        // raison qu'on ne l'a pas encore interrogé.
        val h = mapOf("a" to 360)
        assertEquals(listOf("b", "c", "a"), orderedLinksFor(liens, "VF", heights = h).map { it.url })
    }

    @Test
    fun `une definition superieure a l'ordinaire promeut le lien`() {
        // Le pendant du test précédent : la mesure doit servir à *monter*,
        // sinon elle ne sert à rien.
        val h = mapOf("c" to 1080)
        assertEquals(listOf("c", "a", "b"), orderedLinksFor(liens, "VF", heights = h).map { it.url })
    }

    @Test
    fun `a definition egale la priorite des providers departage`() {
        val h = mapOf("a" to 720, "b" to 720, "c" to 720)
        assertEquals(listOf("a", "b", "c"), orderedLinksFor(liens, "VF", heights = h).map { it.url })
    }

    @Test
    fun `un lien ecarte ne revient pas, meme s'il est le meilleur`() {
        // C'est le repli : la cascade exclut ce qui vient d'échouer et redescend.
        val h = mapOf("a" to 480, "b" to 1080)
        val suite = orderedLinksFor(liens, "VF", excluded = setOf("b"), heights = h)
        assertEquals(listOf("c", "a"), suite.map { it.url })
    }

    @Test
    fun `le classement ne franchit jamais la barriere des langues`() {
        val melange = listOf(lien("vf", "VF"), lien("vo1080", "VO"))
        assertEquals(listOf("vf"), orderedLinksFor(melange, "VF", heights = mapOf("vo1080" to 1080)).map { it.url })
    }
}
