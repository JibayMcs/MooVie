package fr.moovie.tv.core.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.usecase.nextLinkFor
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
