package fr.moovie.tv.data.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Libellés de variante de french-stream — fonction pure, aucun réseau.
 *
 * Ces clés étaient toutes écrasées en « VF », ce qui produisait trois boutons
 * « Vidzy » indiscernables dans la liste des sources. Vérifié sur Dune : ces
 * trois liens sont en fait VF France, VF Québec et VF standard.
 */
class FstreamVariantTest {

    @Test
    fun `les doublages distincts sont nommes`() {
        assertEquals("VF France", FstreamProvider.variantLabel("vff"))
        assertEquals("VF Québec", FstreamProvider.variantLabel("vfq"))
        assertEquals("Premium", FstreamProvider.variantLabel("premium"))
    }

    @Test
    fun `la casse ne change rien`() {
        assertEquals("VF France", FstreamProvider.variantLabel("VFF"))
        assertEquals("VF Québec", FstreamProvider.variantLabel("VfQ"))
    }

    @Test
    fun `les cles redondantes avec la langue ne produisent pas de badge`() {
        // La section de langue affiche déjà « VF » : le répéter sur chaque ligne
        // n'apporterait rien et rechargerait la liste.
        assertNull(FstreamProvider.variantLabel("vf"))
        assertNull(FstreamProvider.variantLabel("vostfr"))
        assertNull(FstreamProvider.variantLabel("vo"))
        assertNull(FstreamProvider.variantLabel("default"))
    }

    @Test
    fun `une cle inconnue ne produit pas de badge`() {
        assertNull(FstreamProvider.variantLabel("xyz"))
        assertNull(FstreamProvider.variantLabel(""))
    }
}
