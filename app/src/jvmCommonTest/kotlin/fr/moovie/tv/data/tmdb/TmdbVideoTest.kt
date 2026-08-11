package fr.moovie.tv.data.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verrouille le choix de « la » bande-annonce. C'est de la politique d'affichage
 * pure : elle se teste sans clé TMDB ni réseau, et c'est elle qui décide ce que
 * l'utilisateur voit derrière un bouton unique.
 */
class TmdbVideoTest {

    private fun video(
        key: String,
        lang: String = "en",
        type: String = "Trailer",
        size: Int = 1080,
        official: Boolean = true,
        site: String = "YouTube",
        published: String = "2024-01-01T00:00:00.000Z",
    ) = TmdbVideo(
        id = key, key = key, name = key, site = site, type = type,
        size = size, official = official, language = lang, publishedAt = published,
    )

    @Test
    fun `la langue de l'utilisateur passe avant la definition`() {
        val choix = listOf(
            video("en1080", lang = "en", size = 1080),
            video("fr720", lang = "fr", size = 720),
        ).bestTrailer("fr-FR")
        assertEquals("fr720", choix?.key)
    }

    @Test
    fun `un code court est accepte comme un code complet`() {
        val videos = listOf(video("en1", lang = "en"), video("fr1", lang = "fr"))
        assertEquals("fr1", videos.bestTrailer("fr")?.key)
        assertEquals("fr1", videos.bestTrailer("fr-FR")?.key)
    }

    @Test
    fun `une vraie bande-annonce passe avant un teaser`() {
        val choix = listOf(
            video("teaser", type = "Teaser"),
            video("trailer", type = "Trailer"),
        ).bestTrailer("en-US")
        assertEquals("trailer", choix?.key)
    }

    @Test
    fun `les coulisses et extraits ne sont jamais proposes`() {
        val videos = listOf(
            video("bts", type = "Behind the Scenes"),
            video("clip", type = "Clip"),
            video("feat", type = "Featurette"),
        )
        // Rien de jouable ne veut pas dire « prends le moins pire » : le bouton
        // ne doit pas s'afficher du tout.
        assertNull(videos.bestTrailer("fr-FR"))
    }

    @Test
    fun `ce qui n'est pas sur YouTube est ecarte`() {
        // On ne sait résoudre que YouTube : proposer un Vimeo mène à un échec.
        assertNull(listOf(video("v", site = "Vimeo")).bestTrailer("fr-FR"))
        assertNull(listOf(video("", site = "YouTube")).bestTrailer("fr-FR"))
    }

    @Test
    fun `a egalite l'officielle puis la plus recente gagnent`() {
        val choix = listOf(
            video("fanmade", official = false, published = "2025-01-01T00:00:00.000Z"),
            video("officielle", official = true, published = "2024-01-01T00:00:00.000Z"),
        ).bestTrailer("en-US")
        assertEquals("officielle", choix?.key)

        val recent = listOf(
            video("vieille", published = "2023-01-01T00:00:00.000Z"),
            video("recente", published = "2025-06-01T00:00:00.000Z"),
        ).bestTrailer("en-US")
        assertEquals("recente", recent?.key)
    }

    @Test
    fun `le classement complet reste utilisable pour un rail`() {
        val rang = listOf(
            video("en_teaser", lang = "en", type = "Teaser"),
            video("fr_trailer", lang = "fr", type = "Trailer"),
            video("clip", type = "Clip"),
            video("en_trailer", lang = "en", type = "Trailer"),
        ).rankedTrailers("fr-FR")
        assertEquals(listOf("fr_trailer", "en_trailer", "en_teaser"), rang.map { it.key })
        assertTrue(rang.none { it.key == "clip" })
    }

    @Test
    fun `une liste vide ne rend rien plutot que de lever`() {
        assertNull(emptyList<TmdbVideo>().bestTrailer("fr-FR"))
    }
}
