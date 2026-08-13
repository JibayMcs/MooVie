package fr.moovie.tv.ui.update

import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.update_error
import org.jetbrains.compose.resources.getString
import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.BuildConfig
import fr.moovie.tv.R
import fr.moovie.tv.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import fr.moovie.tv.data.settings.UpdateInterval
import fr.moovie.tv.data.update.UpdateRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

// UpdateState vit désormais dans jvmCommon (ui/update/UpdateState.kt), partagé
// avec la bannière commune.

/**
 * Vérifie la dernière release GitHub au démarrage, puis à l'intervalle choisi
 * dans les réglages. « Plus tard » écarte **cette version** pour la session
 * (état en mémoire process) : sans ça la vérification périodique la
 * reproposerait quelques minutes plus tard.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = UpdateRepository()
    private val settings = SettingsRepository()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state

    /** Mémorise la mise à jour trouvée (pour Réessayer après une erreur). */
    private var available: UpdateState.Available? = null

    /** Version écartée par « Plus tard », à ne plus proposer d'ici la fin de session. */
    private var dismissedVersion: String? = null

    /** Issue de la dernière vérification lancée depuis les réglages. */
    private val _checkStatus = MutableStateFlow(UpdateCheck.IDLE)
    val checkStatus: StateFlow<UpdateCheck> = _checkStatus

    init {
        viewModelScope.launch {
            // collectLatest : changer l'intervalle dans les réglages relance
            // aussitôt la boucle avec la nouvelle valeur.
            settings.updateInterval.collectLatest { interval ->
                if (interval == UpdateInterval.NEVER) {
                    if (_state.value is UpdateState.Available) _state.value = UpdateState.None
                    return@collectLatest
                }
                while (true) {
                    check()
                    delay(interval.minutes * 60_000L)
                }
            }
        }
    }

    /**
     * Interroge GitHub une fois. Rend [UpdateCheck.IDLE] quand une version a été
     * trouvée : c'est la bannière qui le dit, les réglages n'ont rien à ajouter.
     */
    private suspend fun check(): UpdateCheck {
        // Ne pas écraser un téléchargement en cours ni une erreur affichée.
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Error) {
            return UpdateCheck.IDLE
        }
        // Le canal est relu à chaque vérification, pas mémorisé : basculer le
        // réglage doit prendre effet sans relancer l'application.
        val canal = settings.updatePrereleases.first()
        val release = repo.latestRelease(prereleases = canal) ?: return UpdateCheck.FAILED
        // L'éligibilité est décidée par le dépôt, partagé avec le desktop :
        // écrite ici, elle jetait les préversions que le canal venait de
        // demander — et il a fallu la corriger des deux côtés.
        if (!repo.isEligible(release, canal)) return UpdateCheck.UP_TO_DATE
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: return UpdateCheck.UP_TO_DATE
        if (!repo.isNewer(release.tagName, BuildConfig.VERSION_NAME)) return UpdateCheck.UP_TO_DATE
        val version = release.tagName.removePrefix("v")
        if (version == dismissedVersion) return UpdateCheck.UP_TO_DATE
        val found = UpdateState.Available(version, apk.downloadUrl)
        available = found
        _state.value = found
        return UpdateCheck.IDLE
    }

    /**
     * Vérification immédiate, déclenchée depuis les réglages.
     *
     * Efface le « Plus tard » au passage : aller chercher soi-même une mise à
     * jour qu'on avait repoussée, c'est avoir changé d'avis. Sans ça le bouton
     * répondrait « à jour » sur une version qu'on vient d'écarter.
     */
    fun checkNow() {
        if (_checkStatus.value == UpdateCheck.CHECKING) return
        viewModelScope.launch {
            _checkStatus.value = UpdateCheck.CHECKING
            dismissedVersion = null
            _checkStatus.value = runCatching { check() }.getOrDefault(UpdateCheck.FAILED)
        }
    }

    /** « Plus tard » : masque cette version jusqu'au prochain démarrage. */
    fun dismiss() {
        dismissedVersion = available?.version
        _state.value = UpdateState.None
    }

    /** Télécharge l'APK puis ouvre l'installateur système. */
    fun install() {
        val target = available ?: return
        if (_state.value is UpdateState.Downloading) return
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(target.version, 0f)
            val context = getApplication<Application>()
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val dest = File(dir, "moovie-${target.version}.apk")
            val ok = repo.downloadApk(target.apkUrl, dest) { p ->
                _state.value = UpdateState.Downloading(target.version, p)
            }
            if (ok) {
                launchInstaller(dest)
                // L'utilisateur peut annuler l'installation système : on
                // retombe sur « disponible » plutôt que de masquer.
                _state.value = target
            } else {
                // Ressource **partagée**, pas `R.string` : le bandeau lit déjà
                // toutes ses autres chaînes dans `Res.string`, et le doublon
                // Android historique avait silencieusement divergé — le message
                // affiché n'était plus celui qu'on croyait modifier.
                _state.value = UpdateState.Error(getString(Res.string.update_error))
            }
        }
    }

    private fun launchInstaller(file: File) {
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
