package fr.moovie.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.net.DohProvider
import fr.moovie.tv.data.settings.ScreensaverDelay
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.data.settings.UpdateInterval
import fr.moovie.tv.data.sources.ProviderRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val repo = SettingsRepository()

    val tmdbApiKey: StateFlow<String> =
        repo.tmdbApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val streamLanguage: StateFlow<StreamLanguage> =
        repo.streamLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamLanguage.VF)

    val dohEnabled: StateFlow<Boolean> =
        repo.dohEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dohProvider: StateFlow<DohProvider> =
        repo.dohProvider.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DohProvider.CLOUDFLARE)

    fun setDohEnabled(value: Boolean) = viewModelScope.launch { repo.setDohEnabled(value) }
    fun setDohProvider(value: DohProvider) = viewModelScope.launch { repo.setDohProvider(value) }

    val skipIntroOutro: StateFlow<Boolean> =
        repo.skipIntroOutro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoPlayNext: StateFlow<Boolean> =
        repo.autoPlayNext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAutoPlayNext(value: Boolean) {
        viewModelScope.launch { repo.setAutoPlayNext(value) }
    }

    val hideHistoryWidgets: StateFlow<Boolean> =
        repo.hideHistoryWidgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setHideHistoryWidgets(value: Boolean) {
        viewModelScope.launch { repo.setHideHistoryWidgets(value) }
    }

    val updateInterval: StateFlow<UpdateInterval> =
        repo.updateInterval.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdateInterval.M30)

    fun setUpdateInterval(value: UpdateInterval) {
        viewModelScope.launch { repo.setUpdateInterval(value) }
    }

    val screensaverDelay: StateFlow<ScreensaverDelay> =
        repo.screensaverDelay.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreensaverDelay.M15)

    fun setScreensaverDelay(value: ScreensaverDelay) {
        viewModelScope.launch { repo.setScreensaverDelay(value) }
    }

    fun setSkipIntroOutro(value: Boolean) = viewModelScope.launch { repo.setSkipIntroOutro(value) }

    /** Providers dans l'ordre de priorité effectif, avec leur état on/off. */
    val providers: StateFlow<List<ProviderSetting>> =
        combine(repo.providerOrder, repo.disabledProviders) { order, disabled ->
            orderedNames(order).map { ProviderSetting(it, it !in disabled) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTmdbApiKey(value: String) = viewModelScope.launch { repo.setTmdbApiKey(value) }
    fun setStreamLanguage(value: StreamLanguage) = viewModelScope.launch { repo.setStreamLanguage(value) }

    fun toggleProvider(name: String, enabled: Boolean) =
        viewModelScope.launch { repo.setProviderEnabled(name, enabled) }

    /** Monte un provider d'un cran dans l'ordre de priorité. */
    fun moveProviderUp(name: String) = moveProvider(name, delta = -1)

    /** Descend un provider d'un cran dans l'ordre de priorité. */
    fun moveProviderDown(name: String) = moveProvider(name, delta = +1)

    private fun moveProvider(name: String, delta: Int) {
        viewModelScope.launch {
            val current = orderedNames(repo.providerOrder.first()).toMutableList()
            val index = current.indexOf(name)
            val target = index + delta
            if (index != -1 && target in current.indices) {
                current.removeAt(index)
                current.add(target, name)
                repo.setProviderOrder(current)
            }
        }
    }

    /** Ordre effectif : ordre sauvegardé, puis le reste du registre. */
    private fun orderedNames(saved: List<String>): List<String> {
        val all = ProviderRegistry.all.map { it.name }
        return saved.filter { it in all } + all.filter { it !in saved }
    }
}
