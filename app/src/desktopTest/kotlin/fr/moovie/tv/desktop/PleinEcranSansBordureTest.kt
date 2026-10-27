package fr.moovie.tv.desktop

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PleinEcranSansBordureTest {

    /** Un RECT Win32 porte des bords, pas une taille : 2560 × 1600 à l'origine. */
    @Test
    fun `un RECT devient x, y, largeur, hauteur`() {
        assertEquals(
            Rectangle(0, 0, 2560, 1600),
            PleinEcranSansBordure.rectangle(0, 0, 2560, 1600),
        )
    }

    /** Second écran à gauche du principal : origine négative, taille intacte. */
    @Test
    fun `un ecran a gauche garde une origine negative`() {
        assertEquals(
            Rectangle(-1920, 0, 1920, 1080),
            PleinEcranSansBordure.rectangle(-1920, 0, 0, 1080),
        )
    }

    @Test
    fun `un RECT vide est refuse`() {
        assertNull(PleinEcranSansBordure.rectangle(0, 0, 0, 0))
    }
}
