package fr.moovie.tv.core.sources.usecase

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Garde-fou de durée — fonction pure, aucun réseau.
 *
 * Né d'un cas réel : sur Dune (155 min annoncées par TMDB), trois liens
 * « premium » servaient un logo de moins d'une minute. Le lecteur s'ouvrait,
 * lisait quelques secondes et s'arrêtait, alors que d'autres sources du même
 * titre étaient bonnes.
 */
class StreamDurationGuardTest {

    private val dune = 155 // minutes annoncées par TMDB

    @Test
    fun `un flux à la durée attendue passe`() {
        assertTrue(isDurationAcceptable(155 * 60.0, dune))
    }

    @Test
    fun `un logo de quelques secondes est écarté`() {
        assertFalse(isDurationAcceptable(12.0, dune))
        assertFalse(isDurationAcceptable(58.0, dune))
    }

    @Test
    fun `une playlist vide est écartée`() {
        // Mesurée à zéro : la playlist existe mais ne contient aucun segment.
        assertFalse(isDurationAcceptable(0.0, dune))
    }

    @Test
    fun `une copie un peu plus courte reste acceptée`() {
        // Générique coupé, montage différent, arrondi de la durée annoncée : on
        // ne vise pas ces écarts-là, seulement les flux de remplacement.
        assertTrue(isDurationAcceptable(148 * 60.0, dune), "148 min pour 155 attendues")
        assertTrue(isDurationAcceptable(120 * 60.0, dune), "120 min pour 155 attendues")
    }

    @Test
    fun `une durée inconnue laisse passer`() {
        // Flux non HLS ou playlist illisible : on n'écarte que ce qu'on a mesuré.
        assertTrue(isDurationAcceptable(null, dune))
    }

    @Test
    fun `sans durée attendue le contrôle est inactif`() {
        // TMDB n'annonce pas toujours la durée d'un épisode.
        assertTrue(isDurationAcceptable(12.0, null))
        assertTrue(isDurationAcceptable(12.0, 0))
    }

    @Test
    fun `un flux plus long que prévu passe`() {
        // Version longue, ou durée TMDB sous-estimée.
        assertTrue(isDurationAcceptable(200 * 60.0, dune))
    }

    @Test
    fun `un épisode court reste correctement filtré`() {
        val episode = 22
        assertTrue(isDurationAcceptable(21 * 60.0, episode))
        assertFalse(isDurationAcceptable(30.0, episode))
    }
}
