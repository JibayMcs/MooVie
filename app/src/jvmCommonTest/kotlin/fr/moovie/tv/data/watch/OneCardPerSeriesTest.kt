package fr.moovie.tv.data.watch

import kotlin.test.Test
import kotlin.test.assertEquals

class OneCardPerSeriesTest {

    private fun episode(tmdbId: Int, season: Int, ep: Int, updatedAt: Long) = ResumeEntry(
        key = "tv:$tmdbId:s${season}e$ep",
        tmdbId = tmdbId,
        isTv = true,
        season = season,
        episode = ep,
        positionMs = 1_000,
        durationMs = 100_000,
        updatedAt = updatedAt,
    )

    private fun movie(tmdbId: Int, updatedAt: Long) = ResumeEntry(
        key = "movie:$tmdbId",
        tmdbId = tmdbId,
        isTv = false,
        positionMs = 1_000,
        durationMs = 100_000,
        updatedAt = updatedAt,
    )

    /** Le cas rapporté : L'Attaque des Titans occupait deux places du rail. */
    @Test
    fun `une serie ne garde que son episode le plus recent`() {
        val entries = listOf(
            episode(1429, 1, 1, updatedAt = 100),
            episode(1429, 4, 13, updatedAt = 500),
        )

        val collapsed = entries.oneCardPerSeries()

        assertEquals(1, collapsed.size)
        assertEquals("tv:1429:s4e13", collapsed.single().key)
    }

    /** Deux séries distinctes gardent chacune leur carte. */
    @Test
    fun `des series differentes ne se confondent pas`() {
        val entries = listOf(episode(1429, 4, 13, 500), episode(1396, 1, 1, 400))

        assertEquals(listOf(1429, 1396), entries.oneCardPerSeries().map { it.tmdbId })
    }

    /** Chaque film est déjà seul de son espèce : rien ne doit disparaître. */
    @Test
    fun `les films passent inchanges`() {
        val entries = listOf(movie(550, 300), movie(27205, 200))

        assertEquals(entries, entries.oneCardPerSeries())
    }

    /** Le rail reste ordonné du plus récent au plus ancien, séries et films mêlés. */
    @Test
    fun `l ordre suit la derniere lecture`() {
        val entries = listOf(
            episode(1429, 1, 1, updatedAt = 100),
            movie(550, updatedAt = 400),
            episode(1429, 4, 13, updatedAt = 500),
            movie(27205, updatedAt = 200),
        )

        assertEquals(
            listOf("tv:1429:s4e13", "movie:550", "movie:27205"),
            entries.oneCardPerSeries().map { it.key },
        )
    }

    /**
     * Le tri est refait dans la fonction : elle ne doit pas dépendre d'un ordre
     * que l'appelant aurait — ou n'aurait pas — établi.
     */
    @Test
    fun `un ordre d entree quelconque donne le meme resultat`() {
        val desordre = listOf(
            episode(1429, 4, 13, updatedAt = 500),
            episode(1429, 2, 3, updatedAt = 700),
            episode(1429, 1, 1, updatedAt = 100),
        )

        assertEquals("tv:1429:s2e3", desordre.oneCardPerSeries().single().key)
    }

    @Test
    fun `une liste vide reste vide`() {
        assertEquals(emptyList(), emptyList<ResumeEntry>().oneCardPerSeries())
    }
}
