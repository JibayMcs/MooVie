package fr.moovie.tv.core.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.usecase.languageCascade
import fr.moovie.tv.core.sources.usecase.nextLinkFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageCascadeTest {

    private fun link(hoster: String, language: String) =
        EmbedLink(url = "https://$hoster.test/$language", hoster = hoster, language = language)

    private val vf = link("uqload", "VF")
    private val vostfr = link("voe", "VOSTFR")
    private val vo = link("vidzy", "VO")

    /** Le doublage n'a pas de substitut : on ne lance pas une VO à qui veut du français. */
    @Test
    fun `la VF ne se rabat sur rien`() {
        assertEquals(listOf("VF"), languageCascade("VF"))
        assertNull(nextLinkFor(listOf(vo, vostfr), preferred = "VF"))
    }

    /** Même piste audio : le repli rend à l'utilisateur la langue qu'il a demandée. */
    @Test
    fun `VO et VOSTFR se replient l une sur l autre`() {
        assertEquals(listOf("VO", "VOSTFR"), languageCascade("VO"))
        assertEquals(listOf("VOSTFR", "VO"), languageCascade("VOSTFR"))
    }

    @Test
    fun `la langue demandee passe avant le repli`() {
        assertEquals(vo, nextLinkFor(listOf(vf, vostfr, vo), preferred = "VO"))
        assertEquals(vostfr, nextLinkFor(listOf(vf, vo, vostfr), preferred = "VOSTFR"))
    }

    /**
     * Le cas qui motivait tout : catalogues francophones, aucun lien étiqueté
     * VO, mais du VOSTFR qui porte exactement l'audio demandé.
     */
    @Test
    fun `sans VO on joue le VOSTFR plutot que rien`() {
        assertEquals(vostfr, nextLinkFor(listOf(vf, vostfr), preferred = "VO"))
    }

    /**
     * L'ordre des langues prime sur celui des liens : un repli ne passe qu'une
     * fois la langue demandée épuisée, hébergeurs écartés compris.
     */
    @Test
    fun `on epuise la langue demandee avant de se replier`() {
        val secondVo = link("netu", "VO")
        val links = listOf(vostfr, vo, secondVo)

        assertEquals(secondVo, nextLinkFor(links, preferred = "VO", excluded = setOf(vo.url)))
        assertEquals(
            vostfr,
            nextLinkFor(links, preferred = "VO", excluded = setOf(vo.url, secondVo.url)),
        )
    }

    @Test
    fun `plus rien a jouer rend null`() {
        assertNull(nextLinkFor(emptyList(), preferred = "VO"))
        assertNull(nextLinkFor(listOf(vf), preferred = "VO"))
    }
}
