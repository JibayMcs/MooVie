package fr.moovie.tv.core.subtitles

import fr.moovie.tv.core.subtitles.usecase.SubtitleTiming
import fr.moovie.tv.core.subtitles.usecase.normalizeFps
import fr.moovie.tv.core.subtitles.usecase.timingFor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubtitleTimingTest {

    /**
     * Le cas qui motive toute la fonctionnalité : sans correction, le sous-titre
     * finit plus de quatre minutes en avance sur un film d'1 h 45.
     */
    @Test
    fun `25 vers 23,976 derive de plus de quatre minutes en fin de film`() {
        val timing = timingFor(subtitleFps = 25.0, streamFps = 23.976)
        val endOfFilmMs = 105 * 60 * 1000L

        val drift = timing.applyTo(endOfFilmMs) - endOfFilmMs

        assertTrue(drift > 4 * 60 * 1000, "dérive attendue > 4 min, obtenue ${drift / 1000} s")
        assertTrue(drift < 5 * 60 * 1000, "dérive attendue < 5 min, obtenue ${drift / 1000} s")
    }

    /** Un horodatage vaut image/cadence : la même image, à l'autre cadence. */
    @Test
    fun `la mise a l echelle suit le rapport des cadences`() {
        val timing = timingFor(subtitleFps = 25.0, streamFps = 23.976)

        // 100 s en 25 i/s = image 2500, qui tombe à 2500/23,976 = 104,27 s.
        assertEquals(104_270, timing.applyTo(100_000), 20)
    }

    @Test
    fun `cadences identiques ne changent rien`() {
        val timing = timingFor(subtitleFps = 25.0, streamFps = 25.0)

        assertFalse(timing.scaled)
        assertEquals(90_000, timing.applyTo(90_000))
    }

    /** Corriger au jugé serait pire que ne rien corriger. */
    @Test
    fun `une cadence manquante ne declenche aucune correction`() {
        assertFalse(timingFor(subtitleFps = null, streamFps = 25.0).scaled)
        assertFalse(timingFor(subtitleFps = 25.0, streamFps = null).scaled)
        assertFalse(timingFor(subtitleFps = 0.0, streamFps = 25.0).scaled)
    }

    /** Le décalage manuel doit survivre à l'absence de cadence. */
    @Test
    fun `le decalage s applique meme sans cadence connue`() {
        val timing = timingFor(subtitleFps = null, streamFps = null, offsetMs = -2_000)

        assertEquals(58_000, timing.applyTo(60_000))
        assertFalse(timing.isIdentity)
    }

    @Test
    fun `le decalage s ajoute apres la mise a l echelle`() {
        val timing = timingFor(subtitleFps = 25.0, streamFps = 23.976, offsetMs = 1_000)

        assertEquals(105_270, timing.applyTo(100_000), 20)
    }

    /**
     * Le bruit de mesure ne doit pas introduire de dérive : un lecteur rend
     * 23.976023976… là où le catalogue déclare 23.976.
     */
    @Test
    fun `un ecart negligeable de cadence n est pas corrige`() {
        val timing = timingFor(subtitleFps = 23.976, streamFps = 23.976023976023978)

        assertFalse(timing.scaled)
    }

    @Test
    fun `sans reglage le sous-titre est joue tel quel`() {
        assertTrue(SubtitleTiming.None.isIdentity)
        assertEquals(42_000, SubtitleTiming.None.applyTo(42_000))
    }

    @Test
    fun `une cadence mesuree est ramenee a la valeur normalisee`() {
        assertEquals(23.976, normalizeFps(23.976023976023978))
        assertEquals(25.0, normalizeFps(24.999_9))
        assertEquals(29.97, normalizeFps(29.970_03))
    }

    /** Une cadence inhabituelle vaut mieux qu'une cadence inventée. */
    @Test
    fun `une cadence eloignee de toute valeur connue est gardee telle quelle`() {
        assertEquals(12.5, normalizeFps(12.5))
        assertEquals(null, normalizeFps(null))
        assertEquals(null, normalizeFps(0.0))
    }

    private fun assertEquals(expected: Long, actual: Long, toleranceMs: Long) {
        assertTrue(
            abs(expected - actual) <= toleranceMs,
            "attendu $expected ± $toleranceMs, obtenu $actual",
        )
    }
}
