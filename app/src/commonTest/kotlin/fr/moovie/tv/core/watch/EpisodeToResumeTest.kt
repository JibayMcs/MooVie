package fr.moovie.tv.core.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeToResumeTest {

    @Test
    fun `serie jamais commencee ouvre sur le premier episode`() {
        assertEquals(EpisodeRef(1, 1), episodeToResume(null, emptySet(), firstSeason = 1))
    }

    /** Certaines séries n'ont pas de saison 1 chez TMDB (hors-série numérotés 0). */
    @Test
    fun `la premiere saison n est pas forcement la saison 1`() {
        assertEquals(EpisodeRef(2, 1), episodeToResume(null, emptySet(), firstSeason = 2))
    }

    @Test
    fun `un episode entame l emporte sur les episodes vus`() {
        val watched = setOf(EpisodeRef(1, 1), EpisodeRef(5, 9))
        assertEquals(EpisodeRef(3, 4), episodeToResume(EpisodeRef(3, 4), watched, 1))
    }

    @Test
    fun `sans reprise on enchaine apres le dernier vu`() {
        val watched = setOf(EpisodeRef(1, 1), EpisodeRef(1, 2), EpisodeRef(1, 3))
        assertEquals(EpisodeRef(1, 4), episodeToResume(null, watched, 1))
    }

    /** Le tri est (saison, épisode), pas l'ordre alphabétique ni le nombre brut. */
    @Test
    fun `le dernier vu se calcule sur saison puis episode`() {
        val watched = setOf(EpisodeRef(2, 3), EpisodeRef(10, 1), EpisodeRef(9, 22))
        assertEquals(EpisodeRef(10, 2), episodeToResume(null, watched, 1))
    }

    /**
     * Rattraper un épisode oublié du milieu ne doit pas renvoyer au milieu de la
     * série : c'est bien le maximum qui compte, pas le dernier visionnage.
     */
    @Test
    fun `un episode rattrape au milieu ne fait pas reculer`() {
        val watched = setOf(EpisodeRef(1, 1), EpisodeRef(4, 8), EpisodeRef(2, 5))
        assertEquals(EpisodeRef(4, 9), episodeToResume(null, watched, 1))
    }

    @Test
    fun `cle d episode analysee`() {
        assertEquals(EpisodeRef(2, 5), parseEpisodeKey("tv:1399:s2e5", 1399))
        assertEquals(EpisodeRef(10, 22), parseEpisodeKey("tv:1399:s10e22", 1399))
    }

    @Test
    fun `cle d une autre serie ou d un film ignoree`() {
        assertNull(parseEpisodeKey("tv:9999:s2e5", 1399))
        assertNull(parseEpisodeKey("movie:1399", 1399))
        assertNull(parseEpisodeKey("tv:1399", 1399))
        assertNull(parseEpisodeKey("tv:1399:sXeY", 1399))
    }
}
