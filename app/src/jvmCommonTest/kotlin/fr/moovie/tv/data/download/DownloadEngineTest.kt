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

        override suspend fun fetch(
            url: String,
            headers: Map<String, String>,
            target: File,
            onProgress: suspend (recus: Long, total: Long) -> Unit,
        ): Long {
            calls += url
            headersSeen += headers
            if (url in failOn && failuresLeft > 0) {
                failuresLeft--
                throw java.io.IOException("HTTP 403 sur $url")
            }
            val body = bodies[url] ?: "octets-de-$url"
            target.writeText(body)
            // Un vrai transfert rend compte pendant qu'il coule, pas seulement
            // à la fin : c'est ce que le moteur doit relayer.
            onProgress(target.length() / 2, target.length())
            onProgress(target.length(), target.length())
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
        /** Octets libres annoncés par le volume. Par défaut : de la place. */
        freeSpace: (File) -> Long = { Long.MAX_VALUE },
    ) = DownloadEngine(
        fetcher = fetcher,
        progress = { seen += it },
        resolver = resolver,
        dirFor = { File(root, safeName(it)) },
        freeSpace = freeSpace,
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

    /**
     * Un fichier unique doit **avancer pendant qu'il se télécharge**.
     *
     * Il ne le faisait pas : compté en segments, un MP4 en a un seul, donc 0 %
     * du début à la fin puis 100 % à la dernière seconde. Sur un film de
     * plusieurs gigaoctets, l'écran affirmait pendant une heure qu'il ne se
     * passait rien pendant que le forfait fondait — signalé comme un
     * téléchargement bloqué, ce qu'il n'était pas.
     */
    @Test
    fun `un mp4 rend compte de son avancement avant d etre fini`() = runTest {
        val mp4 = stream.copy(url = "https://cdn.example.com/v/film.mp4", format = StreamFormat.MP4)
        val seen = mutableListOf<Download>()
        engineWith(FakeFetcher(emptyMap()), StreamResolver { _, _ -> null }, seen).run(download, mp4)

        val enCours = seen.filter { it.state == DownloadState.RUNNING }
        assertTrue(
            enCours.any { it.progress > 0f && it.progress < 1f },
            "aucune progression intermédiaire : la barre resterait à zéro " +
                "jusqu'à la fin (états vus : ${seen.map { it.state to it.progress }})",
        )
        assertEquals(1f, seen.last().progress)
        assertEquals(DownloadState.DONE, seen.last().state)
    }

    /** Sans taille annoncée, on retombe sur les segments plutôt que sur rien. */
    @Test
    fun `sans taille annoncee l avancement reste celui des segments`() {
        val muet = Download(key = "movie:1", title = "T", totalSegments = 4, doneSegments = 1)
        assertEquals(0.25f, muet.progress)
    }

    // --- Garde d'espace disque ---------------------------------------------

    @Test
    fun `un disque plein arrête avant de télécharger quoi que ce soit`() = runTest {
        val fetcher = FakeFetcher(mapOf(stream.url to playlist))

        val outcome = engineWith(
            fetcher,
            StreamResolver { _, _ -> null },
            mutableListOf(),
            freeSpace = { 10L * 1024 * 1024 },
        ).run(download, stream)

        assertTrue(outcome is DownloadOutcome.Failed)
        // Rien n'a été demandé au réseau : commencer pour s'arrêter aussitôt
        // laisserait des octets inutiles sur un disque qui manque déjà.
        assertTrue(fetcher.calls.isEmpty(), "aucune requête ne doit partir")
    }

    /**
     * La réserve est de 500 Mo. Juste au-dessus, on télécharge : refuser là
     * rendrait la fonctionnalité inutilisable sur un appareil peu rempli.
     */
    @Test
    fun `juste au-dessus de la réserve, le téléchargement se fait`() = runTest {
        val fetcher = FakeFetcher(mapOf(stream.url to playlist))

        val outcome = engineWith(
            fetcher,
            StreamResolver { _, _ -> null },
            mutableListOf(),
            freeSpace = { 501L * 1024 * 1024 },
        ).run(download, stream)

        assertTrue(outcome is DownloadOutcome.Done)
    }

    /**
     * Le disque se remplit **pendant** le téléchargement : on s'arrête, et les
     * segments déjà posés restent — c'est ce qui permet à la reprise de repartir
     * d'où elle en était une fois de la place libérée.
     */
    @Test
    fun `un disque qui se remplit en cours de route garde ce qui est déjà là`() = runTest {
        val fetcher = FakeFetcher(mapOf(stream.url to playlist))
        var probes = 0

        val outcome = engineWith(
            fetcher,
            StreamResolver { _, _ -> null },
            mutableListOf(),
            // Le premier contrôle passe (celui d'avant le départ), les suivants non.
            freeSpace = { if (probes++ == 0) Long.MAX_VALUE else 1L },
        ).run(download, stream)

        assertTrue(outcome is DownloadOutcome.Failed)
        assertTrue(fetcher.calls.isNotEmpty(), "le téléchargement doit avoir commencé")
        val dir = File(root, safeName(download.key))
        assertTrue(File(dir, PLAYLIST_NAME).exists(), "la playlist locale doit rester")
    }
}
