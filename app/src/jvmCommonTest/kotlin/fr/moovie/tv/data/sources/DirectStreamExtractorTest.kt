package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.StreamFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectStreamExtractorTest {

    private val extractor = DirectStreamExtractor()

    private fun link(url: String) =
        EmbedLink(url = url, hoster = "vidapi", language = "VO", variant = "1080p")

    @Test
    fun `il reconnait les playlists et fichiers directs`() {
        assertTrue(extractor.canHandle("https://x.site/a/master.m3u8"))
        assertTrue(extractor.canHandle("https://x.site/a/manifest.mpd"))
        assertTrue(extractor.canHandle("https://x.site/a/film.MP4"))
    }

    /**
     * Le piège que la reconnaissance sur l'extension du chemin évite : une page
     * d'hébergeur qui porte la playlist en paramètre est une page HTML, et la
     * servir au lecteur ouvrirait une vidéo qui n'existe pas.
     */
    @Test
    fun `une page qui cite un m3u8 en parametre n en est pas un`() {
        assertFalse(extractor.canHandle("https://hebergeur.tld/embed?file=https://x/a.m3u8"))
        assertFalse(extractor.canHandle("https://hebergeur.tld/e/abc123"))
    }

    @Test
    fun `le format se deduit de l extension`() = runTest {
        assertEquals(StreamFormat.HLS, extractor.extract(link("https://x.site/a.m3u8"))?.format)
        assertEquals(StreamFormat.DASH, extractor.extract(link("https://x.site/a.mpd"))?.format)
        assertEquals(StreamFormat.MP4, extractor.extract(link("https://x.site/a.mp4"))?.format)
    }

    /** La langue et la qualité viennent du catalogue : les perdre viderait le panneau. */
    @Test
    fun `il reporte la langue et la qualite du lien`() = runTest {
        val stream = extractor.extract(link("https://x.site/a/master.m3u8"))

        assertEquals("VO", stream?.language)
        assertEquals("1080p", stream?.quality)
        assertEquals("https://x.site/a/master.m3u8", stream?.url)
    }

    @Test
    fun `il refuse ce qu il ne reconnait pas`() = runTest {
        assertNull(extractor.extract(link("https://hebergeur.tld/e/abc123")))
    }
}
