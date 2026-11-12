package fr.moovie.tv.data.download

import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La file des téléchargements, éprouvée sans DataStore ni Android.
 *
 * Le cas qui compte : l'arrêt de l'application. Il ne doit rien écrire sur
 * l'état des titres (ni PAUSED, ni FAILED), attendre la fin des écritures en
 * vol, et laisser le titre en RUNNING pour que la reprise le relance.
 */
class DownloadQueueTest {

    private val root: Path = File(System.getProperty("java.io.tmpdir"), "moovie-dlq-test-${System.nanoTime()}")
        .toOkioPath()

    @AfterTest
    fun clean() {
        File(root.toString()).deleteRecursively()
    }

    private val film = Download(key = "movie:1", title = "Film")
    private val autre = Download(key = "movie:2", title = "Autre")
    private val stream = PlayableStream(
        url = "https://cdn.example.com/video.mp4",
        format = StreamFormat.MP4,
    )

    /** Signale qu'un transfert a commencé : le test sait alors qu'il tient le sérialiseur. */
    private val started = CompletableDeferred<Unit>()

    /** Un magasin en mémoire : le même contrat que [DownloadRepository], sans DataStore. */
    private class MemoryStore : DownloadStore {
        private val entries = MutableStateFlow<Map<String, Download>>(emptyMap())
        override val downloads: Flow<List<Download>> = entries.map { it.values.toList() }
        override suspend fun get(key: String): Download? = entries.value[key]
        override suspend fun put(download: Download) {
            entries.update { it + (download.key to download) }
        }
        override suspend fun remove(key: String) {
            entries.update { it - key }
        }
        fun state(key: String): DownloadState? = entries.value[key]?.state
    }

    /**
     * Un transfert qui ne finit jamais : il attend qu'on l'annule. À l'annulation,
     * il met un moment à rendre la main, comme une écriture en vol : [finished]
     * dit si la file a attendu cette fin.
     */
    private inner class BlockingFetcher : ByteFetcher {
        @Volatile
        var finished = false

        override suspend fun fetch(
            url: String,
            headers: Map<String, String>,
            target: Path,
            onProgress: suspend (recus: Long, total: Long) -> Unit,
        ): Long {
            started.complete(Unit)
            return try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { delay(SLOW_WRITE_MS) }
                finished = true
            }
        }
    }

    /** Un transfert qui réussit : le fichier vidéo est posé, sans attendre. */
    private class FinishingFetcher : ByteFetcher {
        override suspend fun fetch(
            url: String,
            headers: Map<String, String>,
            target: Path,
            onProgress: suspend (recus: Long, total: Long) -> Unit,
        ): Long {
            target.parent?.let(FileSystem.SYSTEM::createDirectories)
            FileSystem.SYSTEM.write(target) { writeUtf8("octets") }
            return 6L
        }
    }

    private fun engine(store: DownloadStore, fetcher: ByteFetcher) = DownloadEngine(
        fetcher = fetcher,
        progress = { store.put(it) },
        resolver = StreamResolver { _, _ -> null },
        dirFor = { root / it.replace(':', '_') },
        freeSpace = { Long.MAX_VALUE },
        // Pas d'affiche en test : la version de production interrogerait TMDB.
        posterFetcher = { _, _, _, _ -> null },
    )

    private fun queue(
        store: DownloadStore,
        fetcher: ByteFetcher,
        resolver: StreamResolver = StreamResolver { _, _ -> stream },
    ) = DownloadQueueCore(
        store = store,
        engine = engine(store, fetcher),
        resolver = resolver,
        onBusy = {},
        onIdle = {},
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private suspend fun awaitState(store: DownloadStore, key: String, state: DownloadState) {
        withTimeout(WAIT_MS) {
            while (store.get(key)?.state != state) delay(10)
        }
    }

    @Test
    fun arretDuProcessusNeProduitPasDEchec() = runBlocking {
        val store = MemoryStore()
        val queue = queue(store, BlockingFetcher())

        queue.enqueue(film, stream)
        withTimeout(WAIT_MS) { started.await() }
        queue.haltForExit()

        // L'annulation remonte du moteur : la file ne doit rien écrire (ni FAILED
        // ni PAUSED). Le titre reste en RUNNING, comme après une coupure.
        assertEquals(DownloadState.RUNNING, store.state(film.key))
    }

    @Test
    fun pauseDUnTelechargementEnCoursAboutitAPausedEtPasAFailed() = runBlocking {
        val store = MemoryStore()
        val queue = queue(store, BlockingFetcher())

        queue.enqueue(film, stream)
        withTimeout(WAIT_MS) { started.await() }
        queue.pause(film.key)

        awaitState(store, film.key, DownloadState.PAUSED)
        // Laisse le temps à un éventuel FAILED tardif d'écraser l'état.
        delay(SLOW_WRITE_MS * 2)
        assertEquals(DownloadState.PAUSED, store.state(film.key))
    }

    @Test
    fun annulationEnAttenteDuSerialiseurNeProduitPasDePause() = runBlocking {
        val store = MemoryStore()
        val queue = queue(store, BlockingFetcher())

        // Le premier titre tient le sérialiseur ; le second attend derrière.
        queue.enqueue(film, stream)
        withTimeout(WAIT_MS) { started.await() }
        queue.enqueue(autre, stream)
        awaitState(store, autre.key, DownloadState.QUEUED)

        // L'annulation tombe pendant l'attente : run() n'a jamais démarré, rien
        // n'a donc à passer en PAUSED.
        queue.pause(autre.key)
        queue.haltForExit()

        assertEquals(DownloadState.QUEUED, store.state(autre.key))
    }

    @Test
    fun haltForExitAttendLaFinDesTravaux() = runBlocking {
        val store = MemoryStore()
        val fetcher = BlockingFetcher()
        val queue = queue(store, fetcher)

        queue.enqueue(film, stream)
        withTimeout(WAIT_MS) { started.await() }
        queue.haltForExit()

        assertTrue(fetcher.finished, "haltForExit a rendu la main avant la fin de l'écriture en vol")
        assertTrue(queue.active.isEmpty(), "des travaux restent après haltForExit")
    }

    @Test
    fun titreInterrompuRepartALaReprise() = runBlocking {
        val store = MemoryStore()
        val premierProcessus = queue(store, BlockingFetcher())
        premierProcessus.enqueue(film, stream)
        withTimeout(WAIT_MS) { started.await() }
        premierProcessus.haltForExit()
        assertEquals(DownloadState.RUNNING, store.state(film.key))

        // Nouveau processus : une autre file, le même magasin, une source fraîche.
        val deuxiemeProcessus = queue(store, FinishingFetcher())
        deuxiemeProcessus.resumePending()

        awaitState(store, film.key, DownloadState.DONE)
    }

    private companion object {
        const val SLOW_WRITE_MS = 200L
        const val WAIT_MS = 5_000L
    }
}
