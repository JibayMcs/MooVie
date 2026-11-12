package fr.moovie.tv.exit

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import fr.moovie.tv.data.cast.CastNow
import fr.moovie.tv.data.download.DownloadQueue
import fr.moovie.tv.data.download.DownloadService
import fr.moovie.tv.data.pairing.PairingSession
import fr.moovie.tv.shared.maintenantMs
import fr.moovie.tv.ui.adaptive.deviceUiFlavor
import fr.moovie.tv.ui.remote.CastSessionService
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fermer **réellement** l'application, sur téléviseur.
 *
 * Android ne ferme jamais rien de lui-même : quitter l'accueil ou passer à une
 * autre application met Moo-vie en arrière-plan, processus vivant, et tout ce
 * qu'il tient avec lui : un téléchargement qui continue de prendre la bande
 * passante de la box, une diffusion relayée, le serveur d'appairage, le
 * lecteur. Sur une Mi Box à 2 Go, c'est de la mémoire que l'application
 * suivante n'a pas, et un Moo-vie qu'on retrouve au retour dans un état qu'on
 * avait cru quitter.
 *
 * Deux chemins y mènent, et une seule fonction, [quit] :
 *
 * - le double Retour à la racine de la pile (voir `MainActivity`) ;
 * - l'application entière qui passe en arrière-plan ([BackgroundExitPolicy]).
 *
 * Le téléphone n'est concerné par aucun des deux : il garde l'application en
 * mémoire et la lecture part en vignette, comme on l'attend d'un téléphone.
 */
object AppExit {

    /** Tous les rappels d'Activity arrivent sur le fil principal : voir la politique. */
    private val policy = BackgroundExitPolicy()

    /**
     * Ce qui doit être écrit avant de mourir, la position du lecteur, pour
     * l'heure. Appelées sur le fil principal : un lecteur ne se lit que là.
     */
    private val sauvegardes = CopyOnWriteArrayList<suspend () -> Unit>()

    /** Sur le fil principal, et indépendant de toute Activity : celle-ci s'en va. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var enCours = false

    /** Branché une fois, au démarrage du processus. */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) = policy.activityStarted()

                override fun onActivityResumed(activity: Activity) = policy.activityResumed()

                override fun onActivityStopped(activity: Activity) {
                    // La saveur est relue ici plutôt que retenue : c'est la
                    // même détection que celle de la composition, au même
                    // instant, et elle ne coûte qu'un appel système.
                    val fermer = policy.activityStopped(
                        changingConfigurations = activity.isChangingConfigurations,
                        flavor = activity.deviceUiFlavor(),
                        nowMs = maintenantMs(),
                    )
                    if (fermer) quit(activity)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    /**
     * À appeler **juste avant** de lancer un écran système dont on attend le
     * retour : installeur de mise à jour, recherche vocale, demande
     * d'autorisation, réglage système. Sans quoi le téléviseur prendrait ce
     * départ pour un passage à une autre application, et fermerait.
     */
    fun expectReturn() = policy.expectReturn(maintenantMs())

    /** Le lancement annoncé par [expectReturn] a échoué. */
    fun cancelReturn() = policy.cancelReturn()

    /**
     * Inscrit une écriture à faire avant la fermeture. Rend la fonction qui la
     * retire, à appeler quand celui qui l'a posée s'en va.
     */
    fun beforeExit(save: suspend () -> Unit): () -> Unit {
        sauvegardes += save
        return { sauvegardes -= save }
    }

    /**
     * Ferme l'application : tout s'arrête, puis le processus.
     *
     * Dans cet ordre, et chaque étape bornée dans le temps : une socket qui ne
     * répond plus ne doit pas tenir une application qu'on a demandé de quitter.
     */
    fun quit(context: Context) {
        if (enCours) return
        enCours = true
        val app = context.applicationContext
        scope.launch {
            // 1. Ce qui doit survivre : la position de reprise d'abord. Le
            //    lecteur l'écrit déjà en quittant la composition, mais de façon
            //    asynchrone : rien ne garantirait qu'elle soit sur le disque
            //    avant la mort du processus.
            withTimeoutOrNull(SAUVEGARDE_MS) {
                sauvegardes.forEach { runCatching { it() } }
            }

            // 2. Le téléchargement est figé, ni en pause ni en échec : il
            //    reprendra au prochain lancement comme après une coupure.
            withTimeoutOrNull(TELECHARGEMENT_MS) { runCatching { DownloadQueue.haltForExit() } }

            // 3. La diffusion : on rend l'écran au récepteur, puis on ferme le
            //    relais. Laisser tourner un film que plus rien ne pilote serait
            //    le contraire de « tout s'arrête ».
            CastNow.session?.let { session ->
                withTimeoutOrNull(CAST_MS) { runCatching { session.stopPlayback() } }
            }
            CastNow.clear()

            // 4. Les services et le serveur d'appairage.
            runCatching { app.stopService(Intent(app, CastSessionService::class.java)) }
            runCatching { app.stopService(Intent(app, DownloadService::class.java)) }
            runCatching { PairingSession.stop() }

            // 5. Les tâches, puis le processus.
            //
            // `finishAndRemoveTask` retire Moo-vie des applications récentes et
            // détruit ses Activity, ce qui libère les lecteurs par le chemin
            // ordinaire de la composition. Mais le processus, lui, resterait :
            // Android le garde en cache tant que la mémoire le permet, avec
            // tout ce que les objets globaux tiennent encore (la file, les
            // clients réseau, les caches d'images). C'est précisément ce que
            // la demande refusait : « fermée » devait vouloir dire qu'au
            // prochain appui sur l'icône l'application repart de zéro, et non
            // qu'elle ressort d'un tiroir où elle attendait. On l'arrête donc
            // nous-mêmes, une fois les tâches retirées : tué avant, le système
            // aurait pu recréer l'Activity du dessus en croyant à un plantage.
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.appTasks?.forEach { task -> runCatching { task.finishAndRemoveTask() } }
            // Le temps que les `onDestroy` passent : le lecteur relâche son
            // décodeur, la composition ses ressources.
            delay(DESTRUCTION_MS)
            Process.killProcess(Process.myPid())
        }
    }

    private const val SAUVEGARDE_MS = 2_000L
    private const val TELECHARGEMENT_MS = 3_000L
    private const val CAST_MS = 1_500L
    private const val DESTRUCTION_MS = 400L
}
