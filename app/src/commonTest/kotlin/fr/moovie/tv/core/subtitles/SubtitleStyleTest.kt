package fr.moovie.tv.core.subtitles

import fr.moovie.tv.core.subtitles.model.SubtitleBackdrop
import fr.moovie.tv.core.subtitles.model.SubtitleColor
import fr.moovie.tv.core.subtitles.model.SubtitleSize
import fr.moovie.tv.core.subtitles.model.SubtitleStyle
import fr.moovie.tv.core.subtitles.model.toHexColor
import fr.moovie.tv.core.subtitles.model.toOpaqueArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le modèle d'apparence, et surtout ses deux conversions.
 *
 * Elles ont l'air triviales et ne le sont pas : chacune sert une plateforme qui
 * échoue **en silence** sur une valeur mal formée — mpv ignore une couleur trop
 * courte, Android dessine du transparent quand l'octet d'alpha est nul. Dans les
 * deux cas l'utilisateur conclut que le réglage n'a pas été pris en compte.
 */
class SubtitleStyleTest {

    @Test
    fun `le defaut est lisible sur n importe quelle scene`() {
        // Pas de fond : le texte disparaît sur une scène claire, et rien
        // n'indique qu'un réglage existe pour ça.
        assertEquals(SubtitleBackdrop.OUTLINE, SubtitleStyle.Default.backdrop)
        assertEquals(SubtitleSize.NORMAL, SubtitleStyle.Default.size)
        assertEquals(SubtitleColor.WHITE, SubtitleStyle.Default.color)
    }

    @Test
    fun `une couleur se rend sur six chiffres pour mpv`() {
        assertEquals("#FFFFFF", 0xFFFFFF.toHexColor())
        assertEquals("#F2C200", 0xF2C200.toHexColor())
    }

    /**
     * Le cas qui casse : sans remplissage, `0x0000FF` sortirait en `#FF` — que
     * mpv lit comme une couleur invalide, donc ignore.
     */
    @Test
    fun `une couleur a octets de tete nuls est completee`() {
        assertEquals("#0000FF", 0x0000FF.toHexColor())
        assertEquals("#000000", 0x000000.toHexColor())
    }

    @Test
    fun `une couleur devient opaque pour Android`() {
        assertEquals(0xFFFFFFFF.toInt(), 0xFFFFFF.toOpaqueArgb())
        assertEquals(0xFF000000.toInt(), 0x000000.toOpaqueArgb())
    }

    /** Un ARGB dont l'alpha est nul est invisible : c'est tout l'enjeu. */
    @Test
    fun `l alpha vaut toujours 255`() {
        for (color in SubtitleColor.entries) {
            val alpha = (color.rgb.toOpaqueArgb() ushr 24) and 0xFF
            assertEquals(255, alpha, "couleur $color")
        }
    }

    @Test
    fun `les tailles sont ordonnees et positives`() {
        val scales = SubtitleSize.entries.map { it.scale }

        assertTrue(scales.all { it > 0f })
        assertEquals(scales.sorted(), scales, "l'ordre de l'enum est celui des boutons")
        assertEquals(1.0f, SubtitleSize.NORMAL.scale, "la taille « normale » ne doit rien changer")
    }
}
