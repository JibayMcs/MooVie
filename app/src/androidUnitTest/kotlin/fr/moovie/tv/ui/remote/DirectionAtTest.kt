package fr.moovie.tv.ui.remote

import androidx.compose.ui.geometry.Offset
import fr.moovie.tv.data.remote.RemoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La géométrie du joystick — la seule partie de la télécommande qui se teste
 * sans doigt.
 *
 * Elle décide de tout ce qui se ressent : traverser le centre sans valider,
 * tourner d'une flèche à l'autre sans relâcher, et ne pas osciller sur la
 * diagonale. Les trois ont déjà été faux dans la version web, et deux d'entre
 * eux ne se voyaient pas à la relecture.
 */
class DirectionAtTest {

    private val centre = Offset(150f, 150f)
    private val dead = 300f * 0.19f

    private fun at(dx: Float, dy: Float) = Offset(centre.x + dx * 150f, centre.y + dy * 150f)

    @Test
    fun `chaque quadrant rend sa direction`() {
        assertEquals(RemoteKey.UP, directionAt(at(0f, -0.8f), centre, dead, null))
        assertEquals(RemoteKey.DOWN, directionAt(at(0f, 0.8f), centre, dead, null))
        assertEquals(RemoteKey.LEFT, directionAt(at(-0.8f, 0f), centre, dead, null))
        assertEquals(RemoteKey.RIGHT, directionAt(at(0.8f, 0f), centre, dead, null))
    }

    /**
     * La zone morte est ce qui permet de traverser le centre en glissant sans
     * rien déclencher. Sans elle, tourner le pouce d'une flèche à l'autre en
     * passant par le milieu validerait.
     */
    @Test
    fun `le centre ne rend aucune direction`() {
        assertNull(directionAt(centre, centre, dead, null))
        assertNull(directionAt(at(0.05f, 0.05f), centre, dead, RemoteKey.UP))
    }

    /**
     * On ne quitte une flèche qu'en s'en écartant nettement. Sans hystérésis,
     * un pouce posé sur la diagonale fait osciller la direction, et la TV reçoit
     * une rafale de touches contradictoires.
     */
    @Test
    fun `la diagonale ne fait pas basculer tant qu'on reste proche`() {
        // 35° sous l'axe du bas : au-delà de la frontière des 45°, mais pas
        // assez pour quitter BAS.
        val near = at(0.45f, 0.65f)
        assertEquals(RemoteKey.DOWN, directionAt(near, centre, dead, RemoteKey.DOWN))
        // Sans direction en cours, le même point tombe du côté le plus proche.
        assertEquals(RemoteKey.DOWN, directionAt(near, centre, dead, null))
    }

    @Test
    fun `passé l'hystérésis, la direction change`() {
        val far = at(0.8f, 0.25f)
        assertEquals(RemoteKey.RIGHT, directionAt(far, centre, dead, RemoteKey.DOWN))
    }

    /** Le tour complet : chaque quart doit se retrouver en tournant. */
    @Test
    fun `on peut tourner d'une flèche à l'autre sans relâcher`() {
        var current: RemoteKey? = null
        val path = listOf(
            at(0f, -0.8f) to RemoteKey.UP,
            at(-0.8f, 0f) to RemoteKey.LEFT,
            at(0f, 0.8f) to RemoteKey.DOWN,
            at(0.8f, 0f) to RemoteKey.RIGHT,
        )
        path.forEach { (point, expected) ->
            current = directionAt(point, centre, dead, current)
            assertEquals(expected, current)
        }
    }
}
