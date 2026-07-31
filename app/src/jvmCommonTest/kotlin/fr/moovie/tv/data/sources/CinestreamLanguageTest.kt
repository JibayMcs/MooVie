package fr.moovie.tv.data.sources

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Classement de langue des players cinestream — fonction pure, aucun réseau.
 *
 * C'est la règle qui décide si un lien alimente la cascade VF ou la cascade
 * VOSTFR : s'y tromper ne casse rien visiblement, ça donne juste la mauvaise
 * langue à l'utilisateur. D'où le test.
 */
class CinestreamLanguageTest {

    private fun lang(name: String) = CinestreamProvider.languageOf(name)

    @Test
    fun `les libellés vostfr numérotés sont du VOSTFR`() {
        assertEquals("VOSTFR", lang("vostfr 1"))
        assertEquals("VOSTFR", lang("vostfr 2"))
        assertEquals("VOSTFR", lang("VOSTFR"))
    }

    @Test
    fun `tout le reste est du VF`() {
        // Libellés réels relevés sur cinestream pour Dune (2021).
        listOf("Voe", "FMX", "LuLuTV", "DdStream", "Save", "uqload", "Filelions", "netu")
            .forEach { assertEquals("VF", lang(it), "player « $it »") }
    }

    @Test
    fun `la casse et les espaces autour ne changent rien`() {
        assertEquals("VOSTFR", lang("  VoStFr 3  "))
        assertEquals("VF", lang("  Voe  "))
    }

    @Test
    fun `un nom qui contient vostfr sans commencer par lui reste VF`() {
        // La règle porte sur le PRÉFIXE : cinestream nomme ses pistes sous-titrées
        // « vostfr N ». Un hébergeur dont le nom contiendrait la sous-chaîne ne
        // doit pas basculer toute une piste VF en VOSTFR.
        assertEquals("VF", lang("player-vostfr-mirror"))
    }
}
