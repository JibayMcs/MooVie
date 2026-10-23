package fr.moovie.tv.data.watch

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Le seuil « terminé », et son plancher contre les vidéos de remplacement. */
class IsFinishedTest {

    private val episode = 42 * 60_000L

    @Test
    fun `les derniers huit pour cent valent termine`() {
        assertTrue(isFinished(positionMs = (episode * 0.93).toLong(), durationMs = episode))
        assertTrue(isFinished(positionMs = episode - 5_000, durationMs = episode))
        assertFalse(isFinished(positionMs = (episode * 0.80).toLong(), durationMs = episode))
    }

    @Test
    fun `une video de trente secondes jouee jusqu au bout ne termine rien`() {
        val remplacement = 30_000L
        assertFalse(isFinished(positionMs = remplacement, durationMs = remplacement))
        assertFalse(isFinished(positionMs = remplacement - 1_000, durationMs = remplacement))
    }

    @Test
    fun `une duree inconnue ne termine rien`() {
        assertFalse(isFinished(positionMs = 10_000, durationMs = 0))
    }

    @Test
    fun `le plancher est a deux minutes`() {
        assertFalse(isFinished(positionMs = FINISHED_MIN_DURATION_MS - 1, durationMs = FINISHED_MIN_DURATION_MS - 1))
        assertTrue(isFinished(positionMs = FINISHED_MIN_DURATION_MS, durationMs = FINISHED_MIN_DURATION_MS))
    }
}
