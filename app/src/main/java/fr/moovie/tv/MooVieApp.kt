package fr.moovie.tv

import android.app.Application
import android.content.Context
import fr.moovie.tv.data.net.AppDns
import fr.moovie.tv.data.settings.LocaleManager
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

    // Applique la langue choisie au contexte de l'Application : sinon les chaînes
    // résolues côté ViewModel (getApplication().getString(), ex. titres de rangées)
    // resteraient dans la locale système même après un changement de langue.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }

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
