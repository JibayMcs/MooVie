package fr.moovie.tv

import android.app.Application
import fr.moovie.tv.data.net.AppDns
import fr.moovie.tv.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Point d'entrée applicatif. Applique la préférence DoH au client d'extraction
 * dès le démarrage puis à chaque changement dans les réglages (le résolveur est
 * mutable et thread-safe).
 */
class MooVieApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsRepository(this)
        scope.launch {
            combine(settings.dohEnabled, settings.dohProvider) { enabled, provider ->
                enabled to provider
            }.collect { (enabled, provider) ->
                AppDns.configure(enabled, provider)
            }
        }
    }
}
