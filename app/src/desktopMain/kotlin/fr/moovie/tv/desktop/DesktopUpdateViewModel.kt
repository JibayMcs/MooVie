package fr.moovie.tv.desktop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.UpdateInterval
import fr.moovie.tv.data.update.UpdateRepository
import fr.moovie.tv.ui.update.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

/**
 * Vérifie la dernière release GitHub au démarrage puis à l'intervalle choisi
 * dans les réglages. Sur desktop, « Installer » ouvre la page de la release dans
 * le navigateur (le paquet natif s'installe hors de l'app, contrairement à
 * l'APK Android).
 */
class DesktopUpdateViewModel : ViewModel() {

    private val repo = UpdateRepository()
    private val settings = SettingsRepository()
    private val currentVersion = System.getProperty("moovie.version") ?: "0.0.0"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state

    /** Version écartée par « Plus tard », à ne plus proposer d'ici la fin de session. */
    private var dismissedVersion: String? = null

    init {
        viewModelScope.launch {
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

    private suspend fun check() {
        val release = repo.latestRelease() ?: return
        if (release.draft || release.prerelease) return
        if (!repo.isNewer(release.tagName, currentVersion)) return
        val version = release.tagName.removePrefix("v")
        if (version == dismissedVersion) return
        // apkUrl transporte ici l'URL de la page de release.
        _state.value = UpdateState.Available(version, release.htmlUrl)
    }

    /** « Plus tard » : masque cette version jusqu'au prochain démarrage. */
    fun dismiss() {
        dismissedVersion = (_state.value as? UpdateState.Available)?.version
        _state.value = UpdateState.None
    }

    /** Ouvre la page de la release (téléchargement du paquet natif). */
    fun install() {
        val available = _state.value as? UpdateState.Available ?: return
        if (available.apkUrl.isBlank()) return
        runCatching { Desktop.getDesktop().browse(URI(available.apkUrl)) }
    }
}
