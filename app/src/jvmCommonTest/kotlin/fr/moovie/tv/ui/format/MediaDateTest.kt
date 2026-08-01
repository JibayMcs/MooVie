package fr.moovie.tv.ui.format

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaDateTest {

    private val original: Locale = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(original)

    @Test
    fun `date ISO rendue au format francais`() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("07/04/2013", formatMediaDate("2013-04-07"))
        assertEquals("01/08/2026", formatMediaDate("2026-08-01"))
    }

    @Test
    fun `date ISO rendue au format americain`() {
        Locale.setDefault(Locale.US)
        assertEquals("8/1/2026", formatMediaDate("2026-08-01"))
    }

    /** Le format court américain donnerait « 8/1/26 » : illisible pour une sortie. */
    @Test
    fun `l annee est toujours sur quatre chiffres`() {
        Locale.setDefault(Locale.US)
        assertEquals(true, formatMediaDate("2013-04-07")!!.endsWith("2013"))
    }

    /** TMDB rend parfois l'année seule : rien à reformater, rien à inventer. */
    @Test
    fun `annee seule laissee telle quelle`() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("2024", formatMediaDate("2024"))
    }

    @Test
    fun `valeur illisible rendue telle quelle`() {
        assertEquals("bientôt", formatMediaDate("bientôt"))
    }

    @Test
    fun `valeur absente ou vide rend null`() {
        assertNull(formatMediaDate(null))
        assertNull(formatMediaDate("   "))
    }

    @Test
    fun `annee extraite d une date complete`() {
        assertEquals("2013", mediaYear("2013-04-07"))
        assertEquals("2024", mediaYear("2024"))
        assertNull(mediaYear(null))
    }
}
