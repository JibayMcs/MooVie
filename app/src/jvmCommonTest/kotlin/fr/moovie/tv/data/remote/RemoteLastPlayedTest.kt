package fr.moovie.tv.data.remote

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ce que le téléviseur retient après avoir cessé de jouer.
 *
 * L'invariant tient en une phrase : **« rien ne joue » n'efface pas « voilà où
 * en était la dernière chose jouée »**. C'est toute la différence entre un
 * téléphone qui rattrape ce qu'il a manqué et un téléphone dont le rail
 * « Reprendre » ment indéfiniment — le cas quand les deux appareils n'ont pas le
 * même compte de synchronisation, où rien d'autre ne les réconcilie.
 */
class RemoteLastPlayedTest {

    @AfterTest
    fun cleanup() = RemoteNowPlaying.clear()

    @Test
    fun `la derniere lecture survit a l arret`() {
        RemoteNowPlaying.publish(
            NowPlaying(title = "Reacher", positionMs = 1_072_513, mediaKey = "tv:108978:s2e6"),
        )
        RemoteNowPlaying.clear()

        assertNull(RemoteNowPlaying.state.value, "plus rien ne joue")
        assertEquals(1_072_513, RemoteNowPlaying.last?.positionMs, "mais on sait où on en était")
        assertEquals("tv:108978:s2e6", RemoteNowPlaying.last?.mediaKey)
    }

    /**
     * Sans clé, le téléphone ne saurait pas de quoi il s'agit : retenir un
     * relevé anonyme ne ferait que lui donner une position à écrire au hasard.
     */
    @Test
    fun `un releve sans cle n est pas retenu`() {
        RemoteNowPlaying.publish(NowPlaying(title = "Inconnu", positionMs = 5000))

        assertNull(RemoteNowPlaying.last)
    }

    @Test
    fun `la derniere lecture se remplace par la suivante`() {
        RemoteNowPlaying.publish(NowPlaying(positionMs = 100, mediaKey = "movie:550"))
        RemoteNowPlaying.publish(NowPlaying(positionMs = 200, mediaKey = "movie:551"))

        assertEquals("movie:551", RemoteNowPlaying.last?.mediaKey)
    }

    /** Le relevé transporte bien le champ jusqu'au téléphone. */
    @Test
    fun `l etat expose la derniere lecture`() {
        val last = NowPlaying(positionMs = 42, mediaKey = "movie:550")

        assertEquals(last, RemoteState(lastPlayed = last).lastPlayed)
        assertNull(RemoteState().lastPlayed, "un téléviseur d'avant cette version n'en envoie pas")
    }
}
