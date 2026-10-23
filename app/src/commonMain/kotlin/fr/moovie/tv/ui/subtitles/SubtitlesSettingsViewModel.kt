package fr.moovie.tv.ui.subtitles

import fr.moovie.tv.shared.dispatcherEs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.core.subtitles.model.SubtitleBackdrop
import fr.moovie.tv.core.subtitles.model.SubtitleColor
import fr.moovie.tv.core.subtitles.model.SubtitleSize
import fr.moovie.tv.core.subtitles.model.SubtitleStyle
import fr.moovie.tv.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** État de la section « Sous-titres » des réglages. */
data class SubtitlesSettingsState(
    /** Langues recherchées, dans l'ordre de préférence. */
    val languages: List<String> = emptyList(),
    /** Préférence d'*apparence* : comment le texte est rendu à l'écran. */
    val style: SubtitleStyle = SubtitleStyle.Default,
)

class SubtitlesSettingsViewModel : ViewModel() {

    private val settings = SettingsRepository()
    private val _state = MutableStateFlow(SubtitlesSettingsState())
    val state: StateFlow<SubtitlesSettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Relit les langues et l'apparence. */
    fun refresh() = io {
        _state.value = _state.value.copy(
            languages = settings.subtitleLanguages.first(),
            style = settings.subtitleStyle.first(),
        )
    }

    /**
     * Ajoute ou retire une langue, en conservant l'ordre d'ajout — c'est lui qui
     * fait la préférence.
     *
     * La dernière langue ne peut pas être retirée : une liste vide ne
     * chercherait rien, et l'utilisateur n'aurait aucun moyen de comprendre
     * pourquoi plus aucun sous-titre n'apparaît.
     */
    fun toggleLanguage(code: String) = io {
        val current = _state.value.languages
        val next = when {
            code !in current -> current + code
            current.size > 1 -> current - code
            else -> current
        }
        settings.setSubtitleLanguages(next)
        _state.value = _state.value.copy(languages = next)
    }

    // L'état local est mis à jour à la main plutôt que collecté depuis le
    // magasin : l'écriture DataStore est asynchrone, et attendre son aller-retour
    // laisse le bouton visiblement en arrière du geste sur une télécommande.

    fun setSize(value: SubtitleSize) = io {
        settings.setSubtitleSize(value)
        _state.value = _state.value.copy(style = _state.value.style.copy(size = value))
    }

    fun setColor(value: SubtitleColor) = io {
        settings.setSubtitleColor(value)
        _state.value = _state.value.copy(style = _state.value.style.copy(color = value))
    }

    fun setBackdrop(value: SubtitleBackdrop) = io {
        settings.setSubtitleBackdrop(value)
        _state.value = _state.value.copy(style = _state.value.style.copy(backdrop = value))
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch { withContext(dispatcherEs) { block() } }
    }
}
