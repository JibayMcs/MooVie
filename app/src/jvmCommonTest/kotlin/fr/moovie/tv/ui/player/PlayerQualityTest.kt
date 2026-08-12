package fr.moovie.tv.ui.player

import fr.moovie.tv.ui.navigation.AltSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contenu du menu « Qualité ». Fonction pure : ni lecteur, ni réseau, ni Compose.
 */
class PlayerQualityTest {

    private fun ids(t: List<PlayerTrack>) = t.map { it.id }

    @Test
    fun `un flux a variante unique et sans meilleure source n'ouvre pas de menu`() {
        // Proposer un choix unique donne l'illusion d'une possibilité qui
        // n'existe pas.
        assertTrue(qualityOptions(listOf(720), emptyList(), QualityChoice.Auto).isEmpty())
        assertTrue(qualityOptions(emptyList(), emptyList(), QualityChoice.Auto).isEmpty())
    }

    @Test
    fun `les variantes du flux sortent de la plus haute a la plus basse, apres Automatique`() {
        val o = qualityOptions(listOf(360, 1080, 720), emptyList(), QualityChoice.Auto)
        assertEquals(listOf("auto", "h:1080", "h:720", "h:360"), ids(o))
        assertTrue(o.first().selected)
    }

    @Test
    fun `une source qui fait mieux est propose, avec son nom`() {
        val o = qualityOptions(
            currentHeights = listOf(480),
            alternatives = listOf(AltSource("u1", "vidzy", 1080)),
            selected = QualityChoice.Auto,
        )
        assertEquals(listOf("auto", "src:u1"), ids(o))
        // Le nom de l'hébergeur explique pourquoi ce choix coupe la lecture.
        assertTrue(o[1].label.contains("vidzy") && o[1].label.contains("1080p"))
    }

    @Test
    fun `une source moins bonne que le flux courant n'est pas proposee`() {
        // Offrir de dégrader l'image au prix d'une interruption n'intéresse
        // personne, et doublerait la longueur du menu.
        val o = qualityOptions(
            currentHeights = listOf(1080, 720),
            alternatives = listOf(AltSource("u1", "uqload", 480), AltSource("u2", "voe", 720)),
            selected = QualityChoice.Auto,
        )
        assertEquals(listOf("auto", "h:1080", "h:720"), ids(o))
    }

    @Test
    fun `une source non mesuree n'est jamais propose comme meilleure`() {
        // hauteur 0 = inconnue : on ne la vend pas pour ce qu'on ignore.
        val o = qualityOptions(listOf(480), listOf(AltSource("u1", "lulu", 0)), QualityChoice.Auto)
        assertTrue(o.isEmpty())
    }

    @Test
    fun `la selection courante est cochee`() {
        val h = qualityOptions(listOf(1080, 720), emptyList(), QualityChoice.Height(720))
        assertTrue(h.single { it.id == "h:720" }.selected)
        assertTrue(!h.single { it.id == "auto" }.selected)

        val s = qualityOptions(listOf(480), listOf(AltSource("u1", "v", 1080)), QualityChoice.Source("u1"))
        assertTrue(s.single { it.id == "src:u1" }.selected)
    }

    @Test
    fun `l'identifiant se relit en choix`() {
        assertEquals(QualityChoice.Auto, QualityChoice.parse("auto"))
        assertEquals(QualityChoice.Height(1080), QualityChoice.parse("h:1080"))
        // Une URL contient « : » et « / » : le préfixe ne doit pas s'y perdre.
        assertEquals(
            QualityChoice.Source("https://x.com/a?b=1"),
            QualityChoice.parse("src:https://x.com/a?b=1"),
        )
        assertEquals(null, QualityChoice.parse("n'importe quoi"))
    }
}
