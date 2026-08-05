package fr.moovie.tv.data.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repère « à suivre » posé en fin d'épisode.
 *
 * Le lecteur ne connaît de la série que sa clé de média : toute la construction
 * de l'entrée suivante repose sur son analyse. Une erreur ici écrirait une
 * entrée sous une clé qui ne correspond à rien — invisible au build, et le rail
 * « Reprendre » afficherait une carte qui n'ouvre nulle part.
 */
class NextUpEntryTest {

    @Test
    fun `un episode donne la cle du suivant`() {
        val next = nextUpEntry("tv:1396:s1e1", "Breaking Bad", "poster", season = 1, episode = 2)

        assertEquals("tv:1396:s1e2", next?.key)
        assertEquals(1396, next?.tmdbId)
        assertEquals(1, next?.season)
        assertEquals(2, next?.episode)
        assertTrue(next?.isTv == true)
    }

    /** Changement de saison : la clé suit, elle n'est pas déduite de la précédente. */
    @Test
    fun `le passage a la saison suivante est porte par les parametres`() {
        val next = nextUpEntry("tv:1396:s1e13", "Breaking Bad", null, season = 2, episode = 1)

        assertEquals("tv:1396:s2e1", next?.key)
    }

    /** Un film n'a pas de suite : rien à poser. */
    @Test
    fun `un film ne produit rien`() {
        assertNull(nextUpEntry("movie:603", "Matrix", null, season = 1, episode = 2))
    }

    /** Clé absente ou abîmée : on n'invente pas d'identifiant. */
    @Test
    fun `une cle inexploitable ne produit rien`() {
        assertNull(nextUpEntry("", "", null, 1, 2))
        assertNull(nextUpEntry("tv:", "", null, 1, 2))
        assertNull(nextUpEntry("tv:abc:s1e1", "", null, 1, 2))
    }

    /**
     * Le repère doit gagner sa place dans le rail : c'est le plus récent, donc
     * c'est lui que [oneCardPerSeries] garde pour la série.
     */
    @Test
    fun `le repere remplace l'episode termine dans le rail`() {
        val finished = ResumeEntry(
            key = "tv:1396:s1e1", tmdbId = 1396, isTv = true, season = 1, episode = 1,
            positionMs = 100, durationMs = 100, updatedAt = 1_000,
        )
        val queued = nextUpEntry("tv:1396:s1e1", "Breaking Bad", null, 1, 2)!!
            .copy(queued = true, updatedAt = 2_000)

        val rail = listOf(finished, queued).oneCardPerSeries()

        assertEquals(1, rail.size)
        assertEquals("tv:1396:s1e2", rail.first().key)
    }
}
