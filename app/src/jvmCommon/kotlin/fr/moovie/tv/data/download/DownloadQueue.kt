package fr.moovie.tv.data.download

import fr.moovie.tv.core.sources.model.PlayableStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * La file des téléchargements.
 *
 * **Un titre à la fois**, et ce n'est pas de la prudence mal placée : trois
 * téléchargements parallèles se partagent la même bande passante, finissent
 * tous les trois plus tard, et sur une box modeste saturent la carte réseau au
 * point de gêner la lecture en cours. En série, le premier titre est regardable
 * pendant que le deuxième se charge.
 *
 * Un objet global, comme [fr.moovie.tv.data.sync.SyncCoordinator] : les points
 * d'appel sont la fiche d'un titre, l'écran des téléchargements et le démarrage
 * de l'app, trois endroits sans raison de se connaître.
 */
object DownloadQueue {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serial = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()

    private val repo = DownloadRepository()
    private val engine by lazy {
        DownloadEngine(
            fetcher = OkHttpByteFetcher(),
            progress = { repo.put(it) },
            resolver = ExtractorStreamResolver(repo),
        )
    }

    /** Les titres en cours ou en attente, pour griser un bouton. */
    val active: Set<String> get() = jobs.keys

    /**
     * Met un titre en file. Sans effet s'il y est déjà — appuyer deux fois sur
     * « Télécharger » ne doit pas lancer deux fois la même chose.
     */
    fun enqueue(download: Download, stream: PlayableStream) {
        if (jobs.containsKey(download.key)) return
        val queued = download.copy(state = DownloadState.QUEUED, error = null)
        jobs[download.key] = scope.launch {
            repo.put(queued)
            try {
                serial.withLock { run(queued, stream) }
            } finally {
                jobs.remove(download.key)
            }
        }
    }

    private suspend fun run(download: Download, stream: PlayableStream) {
        try {
            when (val outcome = engine.run(download, stream)) {
                is DownloadOutcome.Done -> Unit // l'état DONE est publié par le moteur
                is DownloadOutcome.Failed ->
                    repo.put(download.copy(state = DownloadState.FAILED, error = outcome.reason))
            }
        } catch (e: CancellationException) {
            // Annulation demandée : on repasse en pause plutôt qu'en échec. Les
            // fichiers déjà là restent, et la reprise les réutilisera.
            repo.put(repo.get(download.key)?.copy(state = DownloadState.PAUSED) ?: download)
            throw e
        }
    }

    /** Interrompt sans effacer : ce qui est téléchargé reste, la reprise s'en sert. */
    fun pause(key: String) {
        jobs.remove(key)?.cancel()
    }

    /** Interrompt **et** efface. Le seul chemin qui perd des octets. */
    suspend fun remove(key: String) {
        pause(key)
        repo.remove(key)
    }

    /**
     * Relance ce qui n'était pas fini, au démarrage.
     *
     * Un téléchargement interrompu par une fermeture d'app ou une coupure reste
     * en `RUNNING` dans le magasin : personne n'était là pour écrire autre
     * chose. On le remet donc en file plutôt que de le laisser afficher une
     * progression qui n'avance plus — l'état le plus déroutant possible.
     *
     * La source est re-résolue à partir du lien d'embed conservé : celle d'il y
     * a une semaine a forcément expiré.
     */
    suspend fun resumePending() {
        val resolver = ExtractorStreamResolver(repo)
        repo.downloads.first()
            .filter { it.state == DownloadState.RUNNING || it.state == DownloadState.QUEUED }
            .forEach { pending ->
                val stream = resolver.resolve(pending.key, pending.language) ?: run {
                    // Sans source, on ne peut rien faire de plus que le dire.
                    repo.put(
                        pending.copy(
                            state = DownloadState.FAILED,
                            error = "Source introuvable à la reprise",
                        ),
                    )
                    return@forEach
                }
                enqueue(pending, stream)
            }
    }
}
