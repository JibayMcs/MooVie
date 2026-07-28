package fr.moovie.tv.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.StreamLanguage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    val tmdbApiKey: StateFlow<String> =
        repo.tmdbApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val streamLanguage: StateFlow<StreamLanguage> =
        repo.streamLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamLanguage.VF)

    val uiLanguage: StateFlow<String> =
        repo.uiLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr-FR")

    fun setTmdbApiKey(value: String) = viewModelScope.launch { repo.setTmdbApiKey(value) }
    fun setStreamLanguage(value: StreamLanguage) = viewModelScope.launch { repo.setStreamLanguage(value) }
    fun setUiLanguage(value: String) = viewModelScope.launch { repo.setUiLanguage(value) }
}
