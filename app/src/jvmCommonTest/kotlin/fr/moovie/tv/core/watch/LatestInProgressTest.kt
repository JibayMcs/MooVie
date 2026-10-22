package fr.moovie.tv.core.watch

import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.latestInProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LatestInProgressTest {

    private fun episode(tmdbId: Int, season: Int, ep: Int, updatedAt: Long, queued: Boolean = false) =
        ResumeEntry(
            key = "tv:$tmdbId:s${season}e$ep",
            tmdbId = tmdbId,
            isTv = true,
            season = season,
            episode = ep,
            positionMs = if (queued) 0 else 1_000,
            durationMs = if (queued) 0 else 100_000,
            updatedAt = updatedAt,
            queued = queued,
        )

    /** Le cas rapporté : Adults, S1E9 « à suivre » (bonus) et S2E1 réellement en cours. */
    @Test
    fun `deux entrees en cours pour une serie, la plus recente l'emporte`() {
        val s1e9 = episode(260458, 1, 9, updatedAt = 100, queued = true)
        val s2e1 = episode(260458, 2, 1, updatedAt = 200)

        // Quel que soit l'ordre de la liste fournie.
        assertEquals(EpisodeRef(2, 1), latestInProgress(listOf(s1e9, s2e1), 260458))
        assertEquals(EpisodeRef(2, 1), latestInProgress(listOf(s2e1, s1e9), 260458))
    }

    @Test
    fun `les autres series et les films sont ignores`() {
        val autre = episode(1, 3, 3, updatedAt = 900)
        val film = ResumeEntry(key = "movie:260458", tmdbId = 260458, isTv = false, updatedAt = 999)
        assertEquals(
            EpisodeRef(2, 1),
            latestInProgress(listOf(autre, film, episode(260458, 2, 1, 10)), 260458),
        )
        assertNull(latestInProgress(listOf(autre, film), 260458))
    }
}
