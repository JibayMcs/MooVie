package fr.moovie.tv.core.sources.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Lecture de la qualité d'un flux HLS — fonctions pures, aucun réseau.
 *
 * Les hauteurs sont tirées de playlists réelles relevées sur les hébergeurs :
 * 536 (LuLu, uqload, minochinos), 480 (un alias Voe), 720 (frembed).
 */
class StreamQualityTest {

    @Test
    fun `la hauteur est lue dans la master playlist`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=614015,RESOLUTION=1280x536,CODECS="avc1.4d401f"
            index-v1-a1.m3u8
        """.trimIndent()
        assertEquals(536, hlsHeight(playlist))
    }

    @Test
    fun `sur plusieurs variantes on retient la plus haute`() {
        // Un flux adaptatif en liste plusieurs ; c'est la meilleure que le
        // lecteur choisira sur une connexion correcte.
        val playlist = """
            #EXT-X-STREAM-INF:RESOLUTION=640x360
            a.m3u8
            #EXT-X-STREAM-INF:RESOLUTION=1920x1080
            b.m3u8
            #EXT-X-STREAM-INF:RESOLUTION=1280x720
            c.m3u8
        """.trimIndent()
        assertEquals(1080, hlsHeight(playlist))
    }

    @Test
    fun `une playlist sans resolution ne rend rien`() {
        assertNull(hlsHeight("#EXTM3U\n#EXTINF:10.0,\nseg0.ts"))
        assertNull(hlsHeight(""))
    }

    @Test
    fun `les hauteurs batardes sont ramenees au palier connu`() {
        // Les hébergeurs préservent le ratio d'origine au lieu de remplir un
        // 16:9 : « 536p » n'apprend rien, « 480p » situe la source.
        assertEquals("480p", qualityLabel(536))
        assertEquals("480p", qualityLabel(480))
        assertEquals("720p", qualityLabel(692))
        assertEquals("1080p", qualityLabel(1080))
        assertEquals("360p", qualityLabel(360))
    }

    @Test
    fun `une hauteur absente ou absurde ne produit pas de badge`() {
        assertNull(qualityLabel(null))
        assertNull(qualityLabel(0))
        assertNull(qualityLabel(120))
    }
}
