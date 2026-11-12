package fr.moovie.tv.data.download

import fr.moovie.tv.core.sources.model.PlayableStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import fr.moovie.tv.shared.dispatcherEs
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import fr.moovie.tv.shared.Verrou
import fr.moovie.tv.shared.avec
import kotlin.concurrent.Volatile

/**
 * La file des téléchargements.
 *
 * Un objet global, comme [fr.moovie.tv.data.sync.SyncCoordinator] : les points
 * d'appel sont la fiche d'un titre, l'écran des téléchargements et le démarrage
 * de l'app, trois endroits sans raison de se connaître.
 *
 * La logique vit dans [DownloadQueueCore], injectable, pour être éprouvée sans
 * DataStore ni Android. Cet objet n'en est que la façade.
 */
object DownloadQueue {

    private val core: DownloadQueueCore by lazy {
        val repo = DownloadRepository()
        DownloadQueueCore(
            store = repo,
            engine = DownloadEngine(
                fetcher = ByteFetcherKtor(),
                progress = { repo.put(it) },
                resolver = ExtractorStreamResolver(repo),
            ),
            resolver = ExtractorStreamResolver(repo),
            onBusy = { DownloadForeground.start() },
            onIdle = { DownloadForeground.stop() },
            scope = CoroutineScope(SupervisorJob() + dispatcherEs),
        )
    }

    /** Les titres en cours ou en attente, pour griser un bouton. */
    val active: Set<String> get() = core.active

    /** Met un titre en file. Sans effet s'il y est déjà. Voir [DownloadQueueCore.enqueue]. */
    fun enqueue(download: Download, stream: PlayableStream) = core.enqueue(download, stream)

    /** Interrompt sans effacer. Voir [DownloadQueueCore.pause]. */
    fun pause(key: String) = core.pause(key)

    /** Coupe toute la file sans rien écrire. Voir [DownloadQueueCore.haltForExit]. */
    suspend fun haltForExit() = core.haltForExit()

    /** Interrompt **et** efface. Voir [DownloadQueueCore.remove]. */
    suspend fun remove(key: String) = core.remove(key)

    /** Relance ce qui n'était pas fini. Voir [DownloadQueueCore.resumePending]. */
    suspend fun resumePending() = core.resumePending()
}

/**
 * La file proprement dite, avec ses dépendances passées en paramètre.
 *
 * [onBusy] et [onIdle] encadrent la file pour le premier plan Android (voir
 * [DownloadForeground]) ; [resolver] sert à re-résoudre une source à la reprise.
 */
internal class DownloadQueueCore(
    private val store: DownloadStore,
    private val engine: DownloadEngine,
    private val resolver: StreamResolver,
    private val onBusy: () -> Unit,
    private val onIdle: () -> Unit,
    private val scope: CoroutineScope,
) {

    private val serial = Mutex()
    // `ConcurrentHashMap` n'existe pas en commun. Une carte ordinaire gardée
    // par un verrou donne la même garantie ici : toutes les manipulations de
    // `jobs` passent par les quelques lignes ci-dessous.
    private val verrou = Verrou()
    private val jobs = mutableMapOf<String, Job>()

    /**
     * Vrai une fois [haltForExit] appelée : le processus va mourir, et plus
     * rien ne doit être écrit sur l'état des titres. Voir [haltForExit].
     */
    @Volatile
    private var arretDuProcessus = false

    /** Les titres en cours ou en attente, pour griser un bouton. */
    val active: Set<String> get() = verrou.avec { jobs.keys.toSet() }

    /**
     * Met un titre en file. Sans effet s'il y est déjà — appuyer deux fois sur
     * « Télécharger » ne doit pas lancer deux fois la même chose.
     */
    fun enqueue(download: Download, stream: PlayableStream) {
        if (verrou.avec { jobs.containsKey(download.key) }) return
        // Avant de lancer quoi que ce soit : sans premier plan, Android tue ce
        // processus au premier passage en arrière-plan et le téléchargement
        // meurt en silence.
        onBusy()
        val queued = download.copy(state = DownloadState.QUEUED, error = null)
        // L'enregistrement se fait **sous le verrou et autour du lancement**,
        // comme l'affectation d'origine le faisait implicitement : la coroutine
        // démarre aussitôt, et si elle se terminait avant qu'on ait noté sa
        // référence, le retrait ne trouverait rien à retirer et l'entrée
        // resterait pour toujours — un titre impossible à relancer.
        verrou.avec {
            jobs[download.key] = scope.launch {
                store.put(queued)
                try {
                    serial.withLock { run(queued, stream) }
                } finally {
                    verrou.avec { jobs.remove(download.key) }
                    // Dernier travail terminé : on rend la main, ce qui retire
                    // aussi la notification. Rien ne doit rester dans le volet.
                    if (verrou.avec { jobs.isEmpty() }) onIdle()
                }
            }
        }
    }

    private suspend fun run(download: Download, stream: PlayableStream) {
        try {
            when (val outcome = engine.run(download, stream)) {
                is DownloadOutcome.Done -> Unit // l'état DONE est publié par le moteur
                // Le moteur rattrape toute exception, sauf l'annulation, qui
                // remonte jusqu'ici (voir plus bas). Un échec réel seulement.
                is DownloadOutcome.Failed -> if (!arretDuProcessus) {
                    store.put(download.copy(state = DownloadState.FAILED, error = outcome.reason))
                }
            }
        } catch (e: CancellationException) {
            if (arretDuProcessus) throw e
            // Annulation demandée : on repasse en pause plutôt qu'en échec. Les
            // fichiers déjà là restent, et la reprise les réutilisera.
            store.put(store.get(download.key)?.copy(state = DownloadState.PAUSED) ?: download)
            throw e
        }
    }

    /** Interrompt sans effacer : ce qui est téléchargé reste, la reprise s'en sert. */
    fun pause(key: String) {
        verrou.avec { jobs.remove(key) }?.cancel()
    }

    /**
     * Coupe toute la file parce que l'application se ferme, **sans rien
     * écrire** sur l'état des titres.
     *
     * Ni pause ni échec : un titre en pause attend qu'on le relance à la main,
     * un titre en échec dit qu'il y a un problème. Fermer l'application n'est ni
     * l'un ni l'autre, c'est exactement une coupure, et le titre doit rester
     * en `RUNNING` ou `QUEUED` pour que [resumePending] le reprenne au prochain
     * lancement, comme après une panne de courant. Les segments déjà écrits
     * restent sur le disque, la reprise repart d'eux.
     *
     * Attend la fin des travaux annulés : une écriture de progression en vol
     * doit être terminée avant que le processus ne meure, sans quoi le fichier
     * du magasin pourrait rester à moitié écrit.
     */
    suspend fun haltForExit() {
        arretDuProcessus = true
        val enCours = verrou.avec { jobs.values.toList() }
        enCours.forEach { it.cancel() }
        enCours.joinAll()
    }

    /** Interrompt **et** efface. Le seul chemin qui perd des octets. */
    suspend fun remove(key: String) {
        pause(key)
        store.remove(key)
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
        store.downloads.first()
            .filter { it.state == DownloadState.RUNNING || it.state == DownloadState.QUEUED }
            .forEach { pending ->
                val stream = resolver.resolve(pending.key, pending.language) ?: run {
                    // Sans source, on ne peut rien faire de plus que le dire.
                    store.put(
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
