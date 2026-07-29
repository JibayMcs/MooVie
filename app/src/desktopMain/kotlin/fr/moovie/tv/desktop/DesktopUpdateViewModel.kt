package fr.moovie.tv.desktop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.update.UpdateRepository
import fr.moovie.tv.ui.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

/**
 * Vérifie une fois par démarrage la dernière release GitHub. Sur desktop,
 * « Installer » ouvre la page de la release dans le navigateur (le paquet natif
 * s'installe hors de l'app, contrairement à l'APK Android).
 */
class DesktopUpdateViewModel : ViewModel() {

    private val repo = UpdateRepository()
    private val currentVersion = System.getProperty("moovie.version") ?: "0.0.0"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state

    init {
        viewModelScope.launch {
            val release = repo.latestRelease() ?: return@launch
            if (release.draft || release.prerelease) return@launch
            if (repo.isNewer(release.tagName, currentVersion)) {
                // apkUrl transporte ici l'URL de la page de release.
                _state.value = UpdateState.Available(release.tagName.removePrefix("v"), release.htmlUrl)
            }
        }
    }

    /** « Plus tard » : masque jusqu'au prochain démarrage de l'app. */
    fun dismiss() {
        _state.value = UpdateState.None
    }

    /** Ouvre la page de la release (téléchargement du paquet natif). */
    fun install() {
        val available = _state.value as? UpdateState.Available ?: return
        if (available.apkUrl.isBlank()) return
        runCatching { Desktop.getDesktop().browse(URI(available.apkUrl)) }
    }
}
