package fr.moovie.tv.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.net.DohProvider
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.data.sources.ProviderRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ligne d'affichage d'un provider dans les réglages. */
data class ProviderSetting(val name: String, val enabled: Boolean)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    val tmdbApiKey: StateFlow<String> =
        repo.tmdbApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val streamLanguage: StateFlow<StreamLanguage> =
        repo.streamLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamLanguage.VF)

    val uiLanguage: StateFlow<String> =
        repo.uiLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr-FR")

    val dohEnabled: StateFlow<Boolean> =
        repo.dohEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dohProvider: StateFlow<DohProvider> =
        repo.dohProvider.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DohProvider.CLOUDFLARE)

    fun setDohEnabled(value: Boolean) = viewModelScope.launch { repo.setDohEnabled(value) }
    fun setDohProvider(value: DohProvider) = viewModelScope.launch { repo.setDohProvider(value) }

    /** Providers dans l'ordre de priorité effectif, avec leur état on/off. */
    val providers: StateFlow<List<ProviderSetting>> =
        combine(repo.providerOrder, repo.disabledProviders) { order, disabled ->
            orderedNames(order).map { ProviderSetting(it, it !in disabled) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTmdbApiKey(value: String) = viewModelScope.launch { repo.setTmdbApiKey(value) }
    fun setStreamLanguage(value: StreamLanguage) = viewModelScope.launch { repo.setStreamLanguage(value) }
    fun setUiLanguage(value: String) = viewModelScope.launch { repo.setUiLanguage(value) }

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
