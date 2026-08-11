package fr.moovie.tv.data.trailer

import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verrouille la fabrication du manifeste — sans réseau, donc sans dépendre de
 * l'humeur de YouTube. C'est la moitié testable de la bande-annonce : la sonde
 * dit si YouTube répond encore, ce test dit si on sait recoller ce qu'il rend.
 */
class YoutubeDashTest {

    private fun video(itag: Int, height: Int, codec: String = "avc1.640028", url: String = URL) =
        YtTrack(
            itag = itag,
            url = url,
            mimeType = """video/mp4; codecs="$codec"""",
            bitrate = 1000L * height,
            initRange = 0..740,
            indexRange = 741..1216,
            durationMs = 183975,
            width = height * 16 / 9,
            height = height,
            fps = 24,
        )

    private fun audio(itag: Int = 140, codec: String = "mp4a.40.2") = YtTrack(
        itag = itag,
        url = URL,
        mimeType = """audio/mp4; codecs="$codec"""",
        bitrate = 130529,
        initRange = 0..722,
        indexRange = 723..982,
        durationMs = 184041,
        audioSampleRate = 44100,
        audioChannels = 2,
    )

    @Test
    fun `le manifeste est du XML bien forme`() {
        val mpd = buildYoutubeDashManifest(listOf(video(137, 1080), audio()))
        assertNotNull(mpd)
        // Parser pour de vrai : une chaîne « qui ressemble à du XML » passe tous
        // les assertContains du monde et se fait rejeter par le lecteur.
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(mpd.byteInputStream())
        assertEquals("MPD", doc.documentElement.tagName)
        assertEquals("PT183.975S", doc.documentElement.getAttribute("mediaPresentationDuration"))
    }

    @Test
    fun `les esperluettes des URLs googlevideo sont echappees`() {
        val mpd = buildYoutubeDashManifest(
            listOf(video(137, 1080, url = "https://x.com/v?a=1&b=2&c=3"), audio()),
        )
        assertNotNull(mpd)
        // Sans échappement le document n'est pas parsable du tout : c'est la
        // panne la plus facile à introduire et la plus totale.
        assertTrue(mpd.contains("a=1&amp;b=2&amp;c=3"))
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(mpd.byteInputStream())
    }

    @Test
    fun `sans piste audio il n'y a pas de manifeste`() {
        assertNull(buildYoutubeDashManifest(listOf(video(137, 1080))))
    }

    @Test
    fun `sans piste video il n'y a pas de manifeste`() {
        assertNull(buildYoutubeDashManifest(listOf(audio())))
    }

    @Test
    fun `VP9 et AV1 sont ecartes au profit du H264`() {
        val mpd = buildYoutubeDashManifest(
            listOf(
                video(248, 1080, codec = "vp09.00.40.08").copy(mimeType = """video/webm; codecs="vp09.00.40.08""""),
                video(399, 1080, codec = "av01.0.08M.08"),
                video(137, 1080),
                audio(),
            ),
        )
        assertNotNull(mpd)
        assertTrue(mpd.contains("""id="137""""))
        assertTrue(!mpd.contains("av01") && !mpd.contains("vp09"))
    }

    @Test
    fun `l'echelle est limitee aux deux meilleurs formats`() {
        val mpd = buildYoutubeDashManifest(
            listOf(
                video(137, 1080), video(136, 720), video(135, 480),
                video(134, 360), video(160, 144), audio(),
            ),
        )
        assertNotNull(mpd)
        val ids = Regex("""<Representation id="(\d+)"""").findAll(mpd).map { it.groupValues[1] }.toList()
        // 1080p, 720p, et l'audio. Pas de barreau à 144p sur lequel démarrer.
        assertEquals(listOf("137", "136", "140"), ids)
    }

    @Test
    fun `une seule piste audio est retenue malgre les doublons`() {
        val mpd = buildYoutubeDashManifest(listOf(video(137, 1080), audio(140), audio(139)))
        assertNotNull(mpd)
        assertEquals(1, Regex("""mimeType="audio/mp4"""").findAll(mpd).count())
    }

    private companion object {
        const val URL = "https://rr2.googlevideo.com/videoplayback?expire=1&sig=abc%3D%3D"
    }
}
