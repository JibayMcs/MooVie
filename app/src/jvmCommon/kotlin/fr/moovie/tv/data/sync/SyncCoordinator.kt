package fr.moovie.tv.data.sync

import fr.moovie.tv.data.backup.BackupRepository
import fr.moovie.tv.data.net.Connectivity
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ce qui a provoqué une tentative de synchro. */
enum class SyncTrigger {
    /** Ouverture de l'app. */
    LAUNCH,

    /**
     * Retour au premier plan. Le seul qu'on tempère : revenir dix fois dans la
     * minute ne veut pas dire que quelque chose a changé.
     */
    FOREGROUND,

    /**
     * Sortie du lecteur. **Le déclencheur qui compte.**
     *
     * Sans lui, la TV ne publierait qu'à son lancement suivant — donc le PC
     * lirait au bureau un fichier d'avant la soirée, et la reprise arriverait
     * avec un jour de retard. Or c'est précisément à la fin d'un épisode que
     * l'état vient de changer.
     */
    PLAYBACK_ENDED,
}

/**
 * Déclenche les synchros de fond.
 *
 * Un objet global plutôt qu'une dépendance passée de main en main : les points
 * d'appel sont dans les deux points d'entrée de plateforme et dans le lecteur,
 * trois endroits qui n'ont aucune raison de se connaître.
 *
 * **Une synchro à la fois** : le verrou évite que la sortie du lecteur et un
 * retour au premier plan ne publient deux fichiers concurrents. Ils ne se
 * corromperaient pas — chacun n'écrit que le nôtre — mais le second écraserait
 * le premier pour rien.
 *
 * **Les échecs sont silencieux.** Une synchro de fond qui échoue parce que le
 * réseau dort n'a rien à dire ; le bouton des réglages, lui, montre la panne
 * puisqu'on la lui a demandée. La contrepartie est assumée : une clé fausse ne
 * se signale qu'au premier essai manuel.
 */
object SyncCoordinator {

    private val settings = SyncSettingsRepository()
    private val mutex = Mutex()

    @Volatile
    private var lastAttemptAt = 0L

    /** Plancher entre deux retours au premier plan. */
    private const val FOREGROUND_INTERVAL_MS = 5 * 60_000L

    suspend fun sync(trigger: SyncTrigger, now: Long): SyncReport? {
        // Hors ligne, on ne tente rien.
        //
        // L'échec serait silencieux de toute façon, mais il coûterait une
        // ouverture de magasin, une lecture de réglages et un délai réseau à
        // chaque retour au premier plan — trois fois rien, sauf que le retour
        // au premier plan est justement ce qu'on fait sans arrêt en cherchant
        // du réseau. Surtout, `lastAttemptAt` serait posé pour une tentative
        // qui n'a pas eu lieu, ce qui ferait taire la vraie synchro pendant
        // cinq minutes après le retour de la connexion.
        if (!Connectivity.online.value) return null
        if (trigger == SyncTrigger.FOREGROUND && now - lastAttemptAt < FOREGROUND_INTERVAL_MS) {
            return null
        }
        // Posé avant le verrou : deux déclencheurs simultanés ne doivent pas
        // faire la queue pour refaire le même travail.
        lastAttemptAt = now
        return mutex.withLock {
            // La correction de la synchro précédente vaut dès maintenant : les
            // horodatages écrits *pendant* cette synchro doivent déjà en tenir
            // compte, pas seulement ceux d'après.
            MoovieClock.correctBy(settings.clockOffset.first())
            val store = settings.openStore() ?: return@withLock null
            val engine = SyncEngine(
                store = store,
                deviceId = settings.deviceId(),
                subject = BackupSyncSubject(
                    BackupRepository(WatchProgressRepository(), SettingsRepository()),
                ),
            )
            try {
                engine.sync(now).also {
                    settings.recordSync(at = now, clockOffset = it.clockOffset)
                    MoovieClock.correctBy(it.clockOffset)
                }
            } catch (e: SyncException) {
                settings.recordFailure(e.failure, e.message)
                null
            } catch (e: Exception) {
                // Tout le reste est une surprise : on la garde entière, parce
                // qu'une synchro qui ne part jamais sans rien dire est
                // indébogable — y compris pour celui qui l'a écrite.
                settings.recordFailure(SyncFailure.STORE, e.toString())
                null
            }
        }
    }
}
