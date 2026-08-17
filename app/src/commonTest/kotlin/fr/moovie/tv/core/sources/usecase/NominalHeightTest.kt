package fr.moovie.tv.core.sources.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * La classe de définition d'une image large.
 *
 * ## Le défaut, tel qu'il s'est présenté
 *
 * Sur *Reacher* S2E6 — série en 2,00:1 — le panneau annonçait « 720p » pour une
 * source de 2,42 Go sur 46 minutes. Le poids ne collait pas au libellé. Relevé :
 * uqload servait du **1920×960**, vidzy du **864×432**. Les deux sont des copies
 * pleine largeur, respectivement de classe 1080p et 480p ; classées sur leur
 * hauteur seule, elles tombaient une classe plus bas.
 *
 * Ce n'était pas un cas limite : **toute** source large était sous-évaluée, et
 * d'autant plus qu'elle était cinémascope. Un film en 2,40:1 perdait une classe
 * entière, ce qui rendait le tri par qualité incompréhensible — c'est de là que
 * venait l'impression d'aléatoire.
 */
class NominalHeightTest {

    /** Les deux mesures qui ont révélé le défaut. */
    @Test
    fun `une copie pleine largeur garde sa classe malgre son format large`() {
        assertEquals(1080, nominalHeight(1920, 960), "Reacher 2,00:1 chez uqload")
        assertEquals("1080p", qualityLabel(nominalHeight(1920, 960)))

        assertEquals(486, nominalHeight(864, 432), "Reacher 2,00:1 chez vidzy")
        assertEquals("480p", qualityLabel(nominalHeight(864, 432)))
    }

    /** Le cinémascope, où l'écart est le plus grand. */
    @Test
    fun `un 2 40 pour 1 reste du 1080p`() {
        assertEquals("1080p", qualityLabel(nominalHeight(1920, 800)))
        assertEquals("720p", qualityLabel(nominalHeight(1280, 536)))
        assertEquals("4K", qualityLabel(nominalHeight(3840, 1600)))
    }

    /** Le 16:9 ne doit rien changer : la largeur y donne exactement la hauteur. */
    @Test
    fun `un 16 9 est inchange`() {
        assertEquals(1080, nominalHeight(1920, 1080))
        assertEquals(720, nominalHeight(1280, 720))
        assertEquals(360, nominalHeight(640, 360))
    }

    /**
     * Le cas inverse, qui justifie de garder le maximum des deux : une image
     * **haute** est mieux décrite par sa hauteur.
     */
    @Test
    fun `un 4 3 est classe par sa hauteur`() {
        assertEquals(1080, nominalHeight(1440, 1080))
        assertEquals("1080p", qualityLabel(nominalHeight(1440, 1080)))
    }

    /** Largeur inconnue : on retombe exactement sur l'ancien comportement. */
    @Test
    fun `sans largeur, la hauteur fait foi`() {
        assertEquals(720, nominalHeight(null, 720))
        assertEquals(800, nominalHeight(0, 800))
    }

    @Test
    fun `l ordre des classes reste monotone`() {
        val scope1080 = nominalHeight(1920, 800)
        val plein720 = nominalHeight(1280, 720)

        // Un 1080p large vaut mieux qu'un 720p plein — ce que la hauteur brute
        // disait déjà, mais de justesse (800 contre 720) là où l'écart réel est
        // d'une classe entière.
        assertEquals(true, scope1080 > plein720)
    }
}
