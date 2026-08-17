package fr.moovie.tv.desktop.mpv

import fr.moovie.tv.core.subtitles.model.SubtitleBackdrop
import fr.moovie.tv.core.subtitles.model.SubtitleColor
import fr.moovie.tv.core.subtitles.model.SubtitleSize
import fr.moovie.tv.core.subtitles.model.SubtitleStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Traduction d'une apparence en propriétés mpv.
 *
 * Le rendu lui-même ne se teste pas sans écran ; ce qui se teste, et qui casse
 * en silence, c'est la **table**. Deux invariants la gouvernent :
 *
 *  - **toutes les clés à chaque fois.** mpv conserve ce qu'on lui pose : ne
 *    renseigner que l'utile laisserait le contour d'un réglage précédent sous le
 *    bandeau du suivant, et l'utilisateur verrait les deux ;
 *  - **une seule décoration active à la fois.** Contour, ombre et bandeau
 *    s'excluent dans l'interface ; rien dans mpv ne l'impose, c'est ici que ça
 *    se tient.
 */
class MpvSubtitleStyleTest {

    private val allKeys = setOf(
        "sub-scale",
        "sub-color",
        "sub-border-size",
        "sub-shadow-offset",
        "sub-border-color",
        "sub-shadow-color",
        "sub-back-color",
    )

    @Test
    fun `chaque style renseigne toutes les cles`() {
        for (backdrop in SubtitleBackdrop.entries) {
            val props = mpvSubtitleProperties(SubtitleStyle(backdrop = backdrop))
            assertEquals(allKeys, props.keys, "fond $backdrop")
        }
    }

    @Test
    fun `le contour dessine une bordure et rien d autre`() {
        val props = mpvSubtitleProperties(SubtitleStyle(backdrop = SubtitleBackdrop.OUTLINE))

        assertEquals("3", props["sub-border-size"])
        assertEquals("0", props["sub-shadow-offset"])
        assertEquals("#00000000", props["sub-back-color"])
    }

    @Test
    fun `l ombre ne dessine pas de bordure`() {
        val props = mpvSubtitleProperties(SubtitleStyle(backdrop = SubtitleBackdrop.SHADOW))

        assertEquals("0", props["sub-border-size"])
        assertEquals("2", props["sub-shadow-offset"])
        assertEquals("#00000000", props["sub-back-color"])
    }

    /** Le bandeau est le seul à peindre derrière le texte, et il est opaque. */
    @Test
    fun `le bandeau peint un fond noir sans bordure ni ombre`() {
        val props = mpvSubtitleProperties(SubtitleStyle(backdrop = SubtitleBackdrop.BOX))

        assertEquals("#000000", props["sub-back-color"])
        assertEquals("0", props["sub-border-size"])
        assertEquals("0", props["sub-shadow-offset"])
    }

    @Test
    fun `aucun fond n active aucune decoration`() {
        val props = mpvSubtitleProperties(SubtitleStyle(backdrop = SubtitleBackdrop.NONE))

        assertEquals("0", props["sub-border-size"])
        assertEquals("0", props["sub-shadow-offset"])
        assertEquals("#00000000", props["sub-back-color"])
    }

    @Test
    fun `la taille est un facteur, pas une taille absolue`() {
        assertEquals("1.0", mpvSubtitleProperties(SubtitleStyle(size = SubtitleSize.NORMAL))["sub-scale"])
        assertEquals("1.6", mpvSubtitleProperties(SubtitleStyle(size = SubtitleSize.HUGE))["sub-scale"])
    }

    /**
     * Six chiffres, jamais quatre : mpv refuse une couleur trop courte sans le
     * dire, et un réglage ignoré passe pour un réglage non enregistré.
     */
    @Test
    fun `les couleurs sont ecrites sur six chiffres`() {
        assertEquals("#FFFFFF", mpvSubtitleProperties(SubtitleStyle(color = SubtitleColor.WHITE))["sub-color"])
        assertEquals("#F2C200", mpvSubtitleProperties(SubtitleStyle(color = SubtitleColor.YELLOW))["sub-color"])
    }

    /** Une couleur dont les octets de tête sont nuls ne doit pas raccourcir. */
    @Test
    fun `une couleur sombre garde ses six chiffres`() {
        val props = mpvSubtitleProperties(SubtitleStyle(backdrop = SubtitleBackdrop.BOX))

        assertTrue(props.values.none { it.startsWith("#") && it.length !in setOf(7, 9) })
    }
}
