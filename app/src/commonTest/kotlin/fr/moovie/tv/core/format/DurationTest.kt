package fr.moovie.tv.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DurationTest {

    @Test
    fun `sous une heure on reste en minutes`() {
        assertEquals("45 min", formatDuration(45))
        assertEquals("59 min", formatDuration(59))
        assertEquals("1 min", formatDuration(1))
    }

    @Test
    fun `au dela d une heure on passe en heures`() {
        assertEquals("1h36", formatDuration(96))
        assertEquals("2h45", formatDuration(165))
        assertEquals("3h01", formatDuration(181))
    }

    /** Le zéro de tête compte : « 2h5 » se lirait comme 2 h 50. */
    @Test
    fun `les minutes sont sur deux chiffres`() {
        assertEquals("2h05", formatDuration(125))
    }

    @Test
    fun `une heure pile ne montre pas de minutes`() {
        assertEquals("1h", formatDuration(60))
        assertEquals("2h", formatDuration(120))
    }

    /** Durée inconnue : à l'appelant de ne rien afficher, pas d'afficher « 0 min ». */
    @Test
    fun `duree absente ou nulle rend null`() {
        assertNull(formatDuration(null))
        assertNull(formatDuration(0))
        assertNull(formatDuration(-5))
    }
}
