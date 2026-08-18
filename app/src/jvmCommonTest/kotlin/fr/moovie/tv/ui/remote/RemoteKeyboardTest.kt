package fr.moovie.tv.ui.remote

import androidx.compose.ui.input.key.Key
import fr.moovie.tv.data.remote.RemoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ce que le clavier d'un poste de travail envoie au téléviseur.
 *
 * La correspondance se décide en un seul endroit, et se vérifie ici sans
 * fenêtre, sans téléviseur et sans avoir à regarder ce qui bouge à l'écran.
 */
class RemoteKeyboardTest {

    @Test
    fun `les fleches naviguent`() {
        assertEquals(RemoteKey.UP, remoteKeyFor(Key.DirectionUp))
        assertEquals(RemoteKey.DOWN, remoteKeyFor(Key.DirectionDown))
        assertEquals(RemoteKey.LEFT, remoteKeyFor(Key.DirectionLeft))
        assertEquals(RemoteKey.RIGHT, remoteKeyFor(Key.DirectionRight))
    }

    /** Rien ne dit sur quel Entrée on appuie : les deux valident. */
    @Test
    fun `les deux entrees valident`() {
        assertEquals(RemoteKey.OK, remoteKeyFor(Key.Enter))
        assertEquals(RemoteKey.OK, remoteKeyFor(Key.NumPadEnter))
    }

    @Test
    fun `espace met en pause et retour arriere revient`() {
        assertEquals(RemoteKey.PLAY_PAUSE, remoteKeyFor(Key.Spacebar))
        assertEquals(RemoteKey.BACK, remoteKeyFor(Key.Backspace))
    }

    /**
     * **Le test qui compte.** Échap est la sortie de l'écran, pas un ordre pour
     * la télé. Le traduire enfermerait : le seul geste qui ressemble à « je
     * quitte » piloterait l'appareil d'en face, et l'écran ne se fermerait
     * jamais — le même piège que la flèche ronde du bas, qui envoie déjà `BACK`
     * au téléviseur et qu'on prend pour une sortie.
     */
    @Test
    fun `echap n est pas une touche de telecommande`() {
        assertNull(remoteKeyFor(Key.Escape))
    }

    /**
     * Le volume appartient au système bien avant qu'une JVM le voie : prétendre
     * le capturer donnerait des touches qui n'atteignent ni la télé ni le poste.
     */
    @Test
    fun `le volume n est pas detourne`() {
        assertNull(remoteKeyFor(Key.VolumeUp))
        assertNull(remoteKeyFor(Key.VolumeDown))
    }

    // ── L'amortissement de la répétition ────────────────────────────────────

    /**
     * **Le test qui compte.** Mesuré avant correctif : une flèche maintenue deux
     * secondes envoyait **51 requêtes** à la box, soit 25 par seconde sur le
     * réseau local — pour une navigation que l'œil ne suit même pas. Le pavé
     * tactile et les touches de volume amortissaient déjà ; le chemin clavier
     * l'avait oublié.
     */
    @Test
    fun `une touche maintenue ne noie pas le reseau`() {
        val parSeconde = 1_000.0 / KEY_REPEAT_MS
        assertTrue(parSeconde <= 10, "encore $parSeconde requetes par seconde")
    }

    @Test
    fun `la premiere pression passe toujours`() {
        assertTrue(acceptKeyRepeat(now = 5_000, lastAt = 0))
    }

    @Test
    fun `une repetition trop rapprochee tombe`() {
        assertFalse(acceptKeyRepeat(now = 1_000 + KEY_REPEAT_MS - 1, lastAt = 1_000))
        assertTrue(acceptKeyRepeat(now = 1_000 + KEY_REPEAT_MS, lastAt = 1_000))
    }

    /**
     * L'appel se fait sur une horloge **monotone** : avec `currentTimeMillis`,
     * une horloge qui recule laisserait passer toutes les répétitions.
     */
    @Test
    fun `une horloge qui recule ne debride pas la repetition`() {
        assertFalse(acceptKeyRepeat(now = 900, lastAt = 1_000))
    }

    @Test
    fun `une touche ordinaire ne part pas vers la tele`() {
        listOf(Key.A, Key.Tab, Key.F5, Key.Delete).forEach {
            assertNull(remoteKeyFor(it), "$it ne devrait rien envoyer")
        }
    }
}
