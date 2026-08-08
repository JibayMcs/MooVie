package fr.moovie.tv.data.download

import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import fr.moovie.tv.data.store.appContext

/**
 * Démarre et arrête [DownloadService].
 *
 * `startForegroundService` à partir d'Android 8 : le service doit alors appeler
 * `startForeground` dans les secondes qui suivent, sous peine d'ANR. C'est fait
 * dès la première ligne de `onStartCommand`.
 *
 * Les deux opérations sont enveloppées : démarrer un service de premier plan
 * depuis l'arrière-plan est refusé sur les Android récents, et ce refus ne doit
 * pas faire échouer le téléchargement lui-même — il continuera simplement sans
 * la protection, comme avant.
 */
actual object DownloadForeground {

    actual fun start() {
        runCatching {
            val intent = Intent(appContext, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    actual fun stop() {
        runCatching { appContext.stopService(Intent(appContext, DownloadService::class.java)) }
    }
}
