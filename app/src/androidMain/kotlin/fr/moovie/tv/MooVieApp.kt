package fr.moovie.tv

import android.app.Application
import android.content.Context
import fr.moovie.tv.data.net.AppDns
import fr.moovie.tv.data.net.Connectivity
import fr.moovie.tv.data.settings.LocaleManager
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.store.appContext
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
        // Contexte pour les repos jvmCommon (chemin des fichiers DataStore) :
        // posé ici, avant toute création de ViewModel.
        appContext = base
        super.attachBaseContext(LocaleManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Sonde réseau : posée au démarrage du processus, pas d'un écran. Le
        // premier relevé décide de ce que l'accueil affiche, et un service de
        // fond — la synchro, la file de téléchargement — a besoin de la réponse
        // même si aucune Activity n'est vivante.
        Connectivity.start()
        val settings = SettingsRepository()
        scope.launch {
            combine(settings.dohEnabled, settings.dohProvider) { enabled, provider ->
                enabled to provider
            }.collect { (enabled, provider) ->
                AppDns.configure(enabled, provider)
            }
        }
    }
}
