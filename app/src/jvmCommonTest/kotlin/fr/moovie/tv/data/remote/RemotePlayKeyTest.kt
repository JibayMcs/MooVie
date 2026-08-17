package fr.moovie.tv.data.remote

import fr.moovie.tv.ui.player.parseMediaKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La clé média, qui est le pivot du va-et-vient entre le téléphone et la TV.
 *
 * Elle est **recalculée des deux côtés** plutôt que transmise : le téléphone la
 * fabrique pour lire sa reprise, le téléviseur la refabrique pour écrire la
 * sienne, et le relevé d'état la renvoie pour que le téléphone enregistre à son
 * tour. Trois endroits, une seule formule — si elle diverge d'un caractère, la
 * position est écrite sous une clé que personne ne relit, sans erreur ni
 * journal.
 *
 * Ce test tient l'aller-retour : ce qu'on écrit doit se relire.
 */
class RemotePlayKeyTest {

    /** La formule employée par les deux bouts. */
    private fun keyOf(request: PlayRequest): String = if (request.isTv) {
        "tv:${request.tmdbId}:s${request.season}e${request.episode}"
    } else {
        "movie:${request.tmdbId}"
    }

    @Test
    fun `un episode fait un aller-retour sans perte`() {
        val request = PlayRequest(tmdbId = 66765, isTv = true, season = 2, episode = 6)

        val id = parseMediaKey(keyOf(request))

        assertEquals(66765, id?.tmdbId)
        assertEquals(true, id?.isTv)
        assertEquals(2, id?.season)
        assertEquals(6, id?.episode)
    }

    @Test
    fun `un film fait un aller-retour sans perte`() {
        val request = PlayRequest(tmdbId = 693134, isTv = false)

        val id = parseMediaKey(keyOf(request))

        assertEquals(693134, id?.tmdbId)
        assertEquals(false, id?.isTv)
    }

    /** La forme exacte, parce que c'est elle qui doit coïncider avec le magasin. */
    @Test
    fun `la forme de la cle est celle du magasin`() {
        assertEquals("tv:1396:s2e1", keyOf(PlayRequest(1396, true, 2, 1)))
        assertEquals("movie:550", keyOf(PlayRequest(550, false)))
    }

    /**
     * Un téléviseur d'avant cette version ne renvoie pas de clé. Le téléphone
     * doit alors s'abstenir d'écrire plutôt que d'inventer une entrée.
     */
    @Test
    fun `une cle absente ne donne rien a enregistrer`() {
        assertNull(parseMediaKey(NowPlaying().mediaKey))
        assertNull(parseMediaKey(""))
    }

    /** Les valeurs par défaut gardent un ancien téléviseur lisible. */
    @Test
    fun `un releve sans les champs recents reste decodable`() {
        val ancien = NowPlaying(title = "Reacher", positionMs = 1000)

        assertEquals("", ancien.mediaKey)
        assertEquals(0, ancien.durationMs)
    }

    /** Une demande sans position ne doit pas se transformer en reprise à zéro. */
    @Test
    fun `une demande sans position n en porte pas`() {
        val neuf = PlayRequest(tmdbId = 1, isTv = false)

        assertEquals(0L, neuf.positionMs)
        assertEquals(0L, neuf.durationMs)
    }
}
