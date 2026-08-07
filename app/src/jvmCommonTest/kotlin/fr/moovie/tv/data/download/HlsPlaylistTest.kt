package fr.moovie.tv.data.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La localisation d'une playlist.
 *
 * C'est la partie où une erreur ne se voit qu'après deux gigaoctets téléchargés
 * pour rien — donc celle qui doit s'éprouver sans rien télécharger.
 */
class HlsPlaylistTest {

    private val base = "https://cdn.example.com/v/abc/stream.m3u8"

    private val media = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:10
        #EXTINF:10.0,
        seg0.ts
        #EXTINF:10.0,
        seg1.ts
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test
    fun `une master playlist se reconnait`() {
        assertTrue(HlsPlaylist.isMaster("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\na.m3u8"))
        assertFalse(HlsPlaylist.isMaster(media))
    }

    /**
     * La meilleure qualité, pas la plus légère : on regardera plus tard, souvent
     * sur un plus grand écran, et il sera trop tard pour revenir la chercher.
     */
    @Test
    fun `la variante retenue est la plus haute qualite`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4200000,RESOLUTION=1920x1080
            high/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720
            mid/index.m3u8
        """.trimIndent()

        assertEquals(
            "https://cdn.example.com/v/abc/high/index.m3u8",
            HlsPlaylist.pickVariant(master, base),
        )
    }

    @Test
    fun `une playlist media n a pas de variante`() {
        assertNull(HlsPlaylist.pickVariant(media, base))
    }

    @Test
    fun `les segments deviennent des voisins numerotes`() {
        val local = HlsPlaylist.localize(media, base)

        assertEquals(listOf("seg00000.ts", "seg00001.ts"), local.resources.map { it.localName })
        assertEquals(
            listOf(
                "https://cdn.example.com/v/abc/seg0.ts",
                "https://cdn.example.com/v/abc/seg1.ts",
            ),
            local.resources.map { it.url },
        )
        assertTrue("seg00000.ts" in local.text)
        assertFalse("cdn.example.com" in local.text)
    }

    /** Les directives qui ne référencent rien doivent traverser intactes. */
    @Test
    fun `les directives sont preservees`() {
        val local = HlsPlaylist.localize(media, base)

        assertTrue("#EXT-X-TARGETDURATION:10" in local.text)
        assertTrue("#EXTINF:10.0," in local.text)
        assertTrue("#EXT-X-ENDLIST" in local.text)
    }

    /**
     * Sans la clé, les segments d'un flux AES-128 ne sont que du bruit. C'est
     * l'oubli le plus coûteux : le téléchargement paraît réussi et ne se lit
     * jamais.
     */
    @Test
    fun `la cle de chiffrement est rapatriee`() {
        val encrypted = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/k?id=7",IV=0x0
            #EXTINF:10.0,
            seg0.ts
        """.trimIndent()

        val local = HlsPlaylist.localize(encrypted, base)

        assertTrue("""URI="key0.bin"""" in local.text)
        assertTrue("METHOD=AES-128" in local.text)
        assertTrue("IV=0x0" in local.text)
        assertEquals(
            "https://keys.example.com/k?id=7",
            local.resources.first { it.localName == "key0.bin" }.url,
        )
    }

    /** Sans le segment d'initialisation, un flux fMP4 ne se décode pas. */
    @Test
    fun `le segment d initialisation fmp4 est rapatrie`() {
        val fmp4 = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg0.m4s
        """.trimIndent()

        val local = HlsPlaylist.localize(fmp4, base)

        assertTrue("""URI="init0.mp4"""" in local.text)
        assertEquals(
            listOf("init0.mp4", "seg00000.m4s"),
            local.resources.map { it.localName },
        )
    }

    /** Une référence absolue ne doit pas être recollée derrière la base. */
    @Test
    fun `les segments absolus restent absolus`() {
        val absolute = """
            #EXTM3U
            #EXTINF:10.0,
            https://autre.example.net/x/seg0.ts
        """.trimIndent()

        assertEquals(
            "https://autre.example.net/x/seg0.ts",
            HlsPlaylist.localize(absolute, base).resources.single().url,
        )
    }

    /**
     * Un nom local dérivé de l'URL ferait collision dès que deux segments
     * partagent un nom dans des dossiers différents — ou produirait des chemins
     * invalides sur une URL à paramètres.
     */
    @Test
    fun `des segments homonymes ne se marchent pas dessus`() {
        val colliding = """
            #EXTM3U
            #EXTINF:10.0,
            a/index.ts?token=1
            #EXTINF:10.0,
            b/index.ts?token=2
        """.trimIndent()

        val names = HlsPlaylist.localize(colliding, base).resources.map { it.localName }

        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { '?' !in it && '/' !in it })
    }

    /** Une extension inconnue retombe sur `.ts`, que les deux lecteurs acceptent. */
    @Test
    fun `une extension absente retombe sur ts`() {
        val odd = """
            #EXTM3U
            #EXTINF:10.0,
            chunk-42
        """.trimIndent()

        assertEquals("seg00000.ts", HlsPlaylist.localize(odd, base).resources.single().localName)
    }
}
