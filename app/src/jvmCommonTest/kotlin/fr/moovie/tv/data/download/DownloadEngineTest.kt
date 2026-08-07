package fr.moovie.tv.data.download

import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le moteur, éprouvé **sans réseau ni hébergeur**.
 *
 * Deux mécaniques ne peuvent pas se vérifier à la main : la reprise après
 * coupure, et la re-résolution quand l'URL expire en cours de route. Toutes deux
 * ne se déclenchent qu'au bout de plusieurs gigaoctets ou de deux heures.
 */
class DownloadEngineTest {

    private val root = File(System.getProperty("java.io.tmpdir"), "moovie-dl-test-${System.nanoTime()}")

    @AfterTest
    fun clean() {
        root.deleteRecursively()
    }

    private val playlist = """
        #EXTM3U
        #EXTINF:10.0,
        seg0.ts
        #EXTINF:10.0,
        seg1.ts
        #EXT-X-ENDLIST
    """.trimIndent()

    /** Écrit un contenu fabriqué, et retient ce qu'on lui a demandé. */
    private class FakeFetcher(
        private val bodies: Map<String, String>,
        private val failOn: Set<String> = emptySet(),
    ) : ByteFetcher {
        val calls = mutableListOf<String>()
        val headersSeen = mutableListOf<Map<String, String>>()
        var failuresLeft = failOn.size

        override suspend fun fetch(url: String, headers: Map<String, String>, target: File): Long {
            calls += url
            headersSeen += headers
            if (url in failOn && failuresLeft > 0) {
                failuresLeft--
                throw java.io.IOException("HTTP 403 sur $url")
            }
            val body = bodies[url] ?: "octets-de-$url"
            target.writeText(body)
            return target.length()
        }
    }

    private fun engine(
        fetcher: ByteFetcher,
        resolver: StreamResolver = StreamResolver { _, _ -> null },
        seen: MutableList<Download> = mutableListOf(),
    ) = engineWith(fetcher, resolver, seen)

    private fun engineWith(
        fetcher: ByteFetcher,
        resolver: StreamResolver,
        seen: MutableList<Download>,
    ) = DownloadEngine(
        fetcher = fetcher,
        progress = { seen += it },
        resolver = resolver,
        dirFor = { File(root, safeName(it)) },
    )

    private val stream = PlayableStream(
        url = "https://cdn.example.com/v/stream.m3u8",
        format = StreamFormat.HLS,
        headers = mapOf("Referer" to "https://hote.example.com/"),
    )

    private val download = Download(key = "tv:1:s1e1", title = "Série", language = "VF")

    @Test
    fun `un flux hls produit un dossier lisible hors ligne`() = runTest {
        val fetcher = FakeFetcher(mapOf(stream.url to playlist))

        val outcome = engine(fetcher).run(download, stream)

        assertTrue(outcome is DownloadOutcome.Done)
        val dir = File(root, safeName(download.key))
        assertTrue(File(dir, "stream.m3u8").exists())
        assertTrue(File(dir, "seg00000.ts").exists())
        assertTrue(File(dir, "seg00001.ts").exists())
        // La playlist locale ne doit plus désigner l'extérieur, sinon le dossier
        // ne se lit qu'en ligne — c'est-à-dire pas du tout.
        assertFalse("cdn.example.com" in File(dir, "stream.m3u8").readText())
    }

    /** Les en-têtes de la source suivent chaque segment, sinon l'hébergeur refuse. */
    @Test
    fun `les en-tetes accompagnent chaque segment`() = runTest {
        val fetcher = FakeFetcher(mapOf(stream.url to playlist))

        engine(fetcher).run(download, stream)

        assertTrue(fetcher.headersSeen.all { it["Referer"] == "https://hote.example.com/" })
    }

    /**
     * La reprise : ce qui est déjà sur le disque ne se retélécharge pas. C'est
     * ce qui fait qu'une coupure ne coûte que le segment en cours.
     */
    @Test
    fun `un segment deja present n est pas retelecharge`() = runTest {
        val dir = File(root, safeName(download.key)).also { it.mkdirs() }
        File(dir, "seg00000.ts").writeText("déjà là")
        val fetcher = FakeFetcher(mapOf(stream.url to playlist))

        engine(fetcher).run(download, stream)

        assertFalse("https://cdn.example.com/v/seg0.ts" in fetcher.calls)
        assertTrue("https://cdn.example.com/v/seg1.ts" in fetcher.calls)
        assertEquals("déjà là", File(dir, "seg00000.ts").readText())
    }

    /**
     * L'expiration en cours de route. Un refus veut presque toujours dire que le
     * jeton a expiré : redemander une source coûte une requête, ne pas le faire
     * coûte tout ce qui a déjà été téléchargé.
     */
    @Test
    fun `un refus declenche une re-resolution`() = runTest {
        val segment = "https://cdn.example.com/v/seg1.ts"
        val fetcher = FakeFetcher(mapOf(stream.url to playlist), failOn = setOf(segment))
        var resolved = 0
        val resolver = StreamResolver { _, _ ->
            resolved++
            stream.copy(headers = mapOf("Referer" to "https://frais.example.com/"))
        }

        val outcome = engine(fetcher, resolver).run(download, stream)

        assertTrue(outcome is DownloadOutcome.Done)
        assertEquals(1, resolved)
        assertTrue(fetcher.headersSeen.last()["Referer"] == "https://frais.example.com/")
    }

    /** Sans source de repli, l'échec est rendu au lieu d'être avalé. */
    @Test
    fun `un refus sans re-resolution possible echoue proprement`() = runTest {
        val fetcher = FakeFetcher(
            mapOf(stream.url to playlist),
            failOn = setOf("https://cdn.example.com/v/seg0.ts"),
        )

        val outcome = engine(fetcher).run(download, stream)

        assertTrue(outcome is DownloadOutcome.Failed)
    }

    @Test
    fun `une master playlist redescend sur sa meilleure variante`() = runTest {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=500000
            bas/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000
            haut/index.m3u8
        """.trimIndent()
        val fetcher = FakeFetcher(
            mapOf(
                stream.url to master,
                "https://cdn.example.com/v/haut/index.m3u8" to playlist,
            ),
        )

        engine(fetcher).run(download, stream)

        assertTrue("https://cdn.example.com/v/haut/index.m3u8" in fetcher.calls)
        assertTrue("https://cdn.example.com/v/haut/seg0.ts" in fetcher.calls)
    }

    /** Les trois sources MP4 : un fichier, pas de playlist à réécrire. */
    @Test
    fun `un mp4 direct donne un seul fichier`() = runTest {
        val mp4 = stream.copy(url = "https://cdn.example.com/v/film.mp4", format = StreamFormat.MP4)
        val fetcher = FakeFetcher(emptyMap())

        val outcome = engine(fetcher).run(download, mp4)

        assertTrue(outcome is DownloadOutcome.Done)
        assertTrue(File(File(root, safeName(download.key)), "video.mp4").exists())
    }

    /** L'avancement se compte en segments : un flux HLS n'annonce pas sa taille. */
    @Test
    fun `l avancement va jusqu au bout`() = runTest {
        val seen = mutableListOf<Download>()
        engineWith(FakeFetcher(mapOf(stream.url to playlist)), StreamResolver { _, _ -> null }, seen)
            .run(download, stream)

        val last = seen.last()
        assertEquals(DownloadState.DONE, last.state)
        assertEquals(2, last.totalSegments)
        assertEquals(2, last.doneSegments)
        assertEquals(1f, last.progress)
    }
}
