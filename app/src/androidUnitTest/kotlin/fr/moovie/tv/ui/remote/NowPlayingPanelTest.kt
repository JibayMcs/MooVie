package fr.moovie.tv.ui.remote

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Les deux calculs du mini-lecteur : où le doigt vise, et ce qu'affiche
 * l'horloge.
 *
 * Le premier envoie un ordre au téléviseur — se tromper d'un facteur, c'est
 * sauter à un endroit qu'on n'a pas demandé au milieu d'un film. Le second est
 * lu en permanence, et une heure mal reportée passe inaperçue à la relecture.
 */
class NowPlayingPanelTest {

    private val twoHours = 7_200_000L

    @Test
    fun `le doigt au milieu vise la moitie du film`() {
        assertEquals(3_600_000L, msAt(x = 500f, width = 1000, durationMs = twoHours))
    }

    @Test
    fun `le bord gauche vise le debut`() {
        assertEquals(0L, msAt(x = 0f, width = 1000, durationMs = twoHours))
    }

    /**
     * Un doigt déborde de la barre pendant un glissement — il suffit de sortir
     * par le côté. Sans bornage, on demanderait au lecteur une position hors du
     * film, que rien en aval ne rattrape.
     */
    @Test
    fun `un doigt sorti de la barre reste dans le film`() {
        assertEquals(twoHours, msAt(x = 1400f, width = 1000, durationMs = twoHours))
        assertEquals(0L, msAt(x = -200f, width = 1000, durationMs = twoHours))
    }

    @Test
    fun `sans duree connue, on ne vise nulle part`() {
        assertEquals(0L, msAt(x = 500f, width = 1000, durationMs = 0L))
    }

    @Test
    fun `sous une heure, l'horloge n'affiche pas d'heures`() {
        assertEquals("0:00", formatClock(0))
        assertEquals("2:05", formatClock(125_000))
        assertEquals("59:59", formatClock(3_599_000))
    }

    @Test
    fun `au-dela d'une heure, minutes et secondes restent sur deux chiffres`() {
        assertEquals("1:00:00", formatClock(3_600_000))
        assertEquals("2:03:07", formatClock(7_387_000))
    }

    /**
     * Le temps restant est calculé par soustraction, et l'interpolation locale
     * de la barre peut dépasser la durée d'une fraction de seconde avant que le
     * relevé suivant ne recale. Un « -0:-1 » à l'écran serait le symptôme.
     */
    @Test
    fun `une position au-dela de la fin ne rend pas un temps negatif`() {
        assertEquals("0:00", formatClock(-500))
    }
}
