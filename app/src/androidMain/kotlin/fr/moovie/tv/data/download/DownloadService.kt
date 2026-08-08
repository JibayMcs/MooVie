package fr.moovie.tv.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fr.moovie.tv.MainActivity
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.downloads_notification_channel
import fr.moovie.tv.resources.settings_cat_downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Service de premier plan qui tient la file en vie.
 *
 * Il ne télécharge rien lui-même — [DownloadQueue] s'en charge, et continuera de
 * s'en charger. Son unique rôle est de dire au système que ce processus a un
 * travail visible en cours, sans quoi Android le tue au premier passage en
 * arrière-plan.
 *
 * ### La notification, et ce qu'elle ne fait pas
 *
 * Canal en `IMPORTANCE_LOW` : pas de son, pas de bandeau qui recouvre l'écran.
 * Un téléchargement est un travail de fond, il informe sans interrompre.
 *
 * Elle est **`ongoing` et disparaît avec le service** : rien ne reste dans le
 * volet une fois la file vide, et il n'y a aucune notification de fin. C'est
 * volontaire — la demande était « zéro stockage ». L'écran des téléchargements
 * est le seul endroit qui garde une trace, et c'est sa place.
 *
 * Sur Android TV le volet de notifications n'existe pas : la notification y est
 * invisible. Le service, lui, garde tout son intérêt — c'est ce qui empêche le
 * téléchargement de mourir. On ne le réserve donc pas au téléphone.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo = DownloadRepository()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // **Synchrone, et avant toute autre chose.** Android exige `startForeground`
        // dans les cinq secondes qui suivent `startForegroundService`, sinon il tue
        // le processus — ce qui s'est produit : plus de processus, plus de service,
        // et un téléchargement arrêté sans un mot. La version précédente attendait
        // un `getString` de ressource Compose, qui est suspendu : le premier plan
        // dépendait donc d'une course.
        //
        // Le libellé du lanceur suffit à démarrer, il ne demande aucune ressource.
        // Le titre traduit et le nom de canal arrivent juste après, par la mise à
        // jour ci-dessous : un canal se renomme en le recréant sous le même
        // identifiant.
        val label = applicationInfo.loadLabel(packageManager).toString()
        ensureChannel(label)
        startInForeground(build(label, null, 0, 0))

        scope.launch {
            val channelName = getString(Res.string.downloads_notification_channel)
            val title = getString(Res.string.settings_cat_downloads)
            ensureChannel(channelName)

            repo.downloads
                // Ne redessiner que sur un vrai changement : la file écrit à
                // chaque segment, et repousser une notification identique
                // quarante fois par seconde ne coûte que de la batterie.
                .map { all -> all.firstOrNull { it.state == DownloadState.RUNNING } }
                .distinctUntilChanged { a, b ->
                    a?.key == b?.key && a?.doneSegments == b?.doneSegments
                }
                .collect { running ->
                    if (running == null && DownloadQueue.active.isEmpty()) {
                        // Plus rien à faire : on se retire du premier plan, ce
                        // qui retire aussi la notification.
                        stopSelf()
                        return@collect
                    }
                    notifier().notify(
                        NOTIFICATION_ID,
                        build(
                            title = title,
                            line = running?.let { listOf(it.title, it.subtitle).filter(String::isNotBlank).joinToString(" · ") },
                            done = running?.doneSegments ?: 0,
                            total = running?.totalSegments ?: 0,
                        ),
                    )
                }
        }
        // START_NOT_STICKY : si le système nous tue faute de mémoire, il ne
        // faut pas relancer un service sans file — c'est `enqueue` qui décide
        // qu'il y a du travail, pas le redémarrage.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(title: String, line: String?, done: Int, total: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            // Icône du système : un téléchargement en cours a déjà son symbole,
            // et en dessiner un autre n'apprendrait rien à personne.
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(line)
            .setContentIntent(open)
            .setOngoing(true)
            // Sans quoi chaque mise à jour de progression rejoue l'alerte.
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Indéterminée tant que la playlist n'est pas lue : une barre figée
            // à zéro se lit comme un blocage plutôt que comme un démarrage.
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .build()
    }

    private fun ensureChannel(name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notifier().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14 exige un type déclaré, et refuse le démarrage sans lui.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifier() =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 4201
    }
}
