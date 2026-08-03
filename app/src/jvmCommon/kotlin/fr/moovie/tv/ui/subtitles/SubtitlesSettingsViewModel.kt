package fr.moovie.tv.ui.subtitles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.core.subtitles.model.SubtitleQuota
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.subtitles.OpenSubtitlesApi
import fr.moovie.tv.data.subtitles.OpenSubtitlesCatalog
import fr.moovie.tv.data.subtitles.OpenSubtitlesSession
import fr.moovie.tv.data.subtitles.OsAccountState
import fr.moovie.tv.data.subtitles.OsLoginError
import fr.moovie.tv.shared.openSubtitlesApiKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * État de la section « Sous-titres » des réglages.
 *
 * @param busy une connexion est en cours — les champs restent lisibles mais le
 *   bouton ne doit pas pouvoir être pressé deux fois : `/login` est plafonné à
 *   une requête par seconde et l'API demande d'arrêter d'insister après un échec.
 */
data class SubtitlesSettingsState(
    val keyPresent: Boolean = true,
    val account: OsAccountState = OsAccountState(),
    val quota: SubtitleQuota = SubtitleQuota.Unknown,
    /** Langues recherchées, dans l'ordre de préférence. */
    val languages: List<String> = emptyList(),
    val busy: Boolean = false,
    val error: OsLoginError? = null,
)

class SubtitlesSettingsViewModel : ViewModel() {

    private val settings = SettingsRepository()
    private val api = OpenSubtitlesApi(openSubtitlesApiKey)
    private val session = OpenSubtitlesSession(api, settings)
    private val catalog = OpenSubtitlesCatalog(api)

    private val _state = MutableStateFlow(
        SubtitlesSettingsState(keyPresent = openSubtitlesApiKey.isNotBlank()),
    )
    val state: StateFlow<SubtitlesSettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Relit le compte et, s'il est ouvert, le quota. L'appel réseau ne part que
     * pour un utilisateur connecté : sans compte, `/infos/user` répond 401 et
     * l'interroger ne ferait que consommer du rythme pour rien.
     */
    fun refresh() = io {
        session.restore()
        val account = session.state()
        _state.value = _state.value.copy(
            account = account,
            languages = settings.subtitleLanguages.first(),
        )
        if (account.connected) {
            _state.value = _state.value.copy(quota = catalog.quota())
        }
    }

    fun login(username: String, password: String, remember: Boolean) = io {
        if (username.isBlank() || password.isBlank()) return@io
        _state.value = _state.value.copy(busy = true, error = null)

        session.login(username, password, remember, System.currentTimeMillis())
            .onSuccess { account ->
                _state.value = _state.value.copy(
                    account = account,
                    quota = account.quota,
                    busy = false,
                )
                // Le quota renvoyé par /login est parfois incomplet : on le
                // complète avec /infos/user, qui ne coûte pas de téléchargement.
                _state.value = _state.value.copy(quota = catalog.quota())
            }
            .onFailure { failure ->
                _state.value = _state.value.copy(
                    busy = false,
                    error = (failure as? OpenSubtitlesSession.LoginFailure)?.reason
                        ?: OsLoginError.NETWORK,
                )
            }
    }

    fun logout() = io {
        session.logout()
        _state.value = _state.value.copy(
            account = OsAccountState(),
            quota = SubtitleQuota.Unknown,
            error = null,
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

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch { withContext(Dispatchers.IO) { block() } }
    }
}
