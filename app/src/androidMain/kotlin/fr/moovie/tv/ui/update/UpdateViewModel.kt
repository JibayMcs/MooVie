package fr.moovie.tv.ui.update

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.BuildConfig
import fr.moovie.tv.R
import fr.moovie.tv.data.update.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** États de la bannière de mise à jour. */
sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val version: String, val apkUrl: String) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Vérifie une fois par démarrage la dernière release GitHub. « Plus tard »
 * masque la bannière pour la session uniquement (état en mémoire process) :
 * elle réapparaît au prochain démarrage de l'app.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = UpdateRepository()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state

    /** Mémorise la mise à jour trouvée (pour Réessayer après une erreur). */
    private var available: UpdateState.Available? = null

    init {
        viewModelScope.launch {
            val release = repo.latestRelease() ?: return@launch
            if (release.draft || release.prerelease) return@launch
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@launch
            if (repo.isNewer(release.tagName, BuildConfig.VERSION_NAME)) {
                val found = UpdateState.Available(release.tagName.removePrefix("v"), apk.downloadUrl)
                available = found
                _state.value = found
            }
        }
    }

    /** « Plus tard » : masque jusqu'au prochain démarrage de l'app. */
    fun dismiss() {
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
                _state.value = UpdateState.Error(getApplication<Application>().getString(R.string.update_error))
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
