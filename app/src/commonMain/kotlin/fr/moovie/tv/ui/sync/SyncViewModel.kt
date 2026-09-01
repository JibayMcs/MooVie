package fr.moovie.tv.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.backup.BackupRepository
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.sync.BackupSyncSubject
import fr.moovie.tv.data.sync.SyncEngine
import fr.moovie.tv.data.sync.SyncException
import fr.moovie.tv.data.sync.SyncFailure
import fr.moovie.tv.data.sync.SyncProvider
import fr.moovie.tv.data.sync.SyncProviders
import fr.moovie.tv.data.sync.SyncReport
import fr.moovie.tv.data.sync.SyncSettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Où en est la synchro, du point de vue de l'écran. */
sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Done(val report: SyncReport) : SyncState

    /**
     * [detail] vient du fournisseur et n'est pas traduit : il dit ce que le
     * service a répondu là où [failure] ne donne qu'une catégorie. Montré en
     * second, sous la phrase traduite.
     */
    data class Failed(val failure: SyncFailure, val detail: String?) : SyncState
}

/**
 * L'écran de synchro.
 *
 * Il ne connaît **aucun fournisseur** : il lit le descripteur du fournisseur
 * choisi et en déduit les champs à demander. Ajouter WebDAV demain ne touche
 * donc pas ce fichier — seulement le libellé de ses champs, que la vue résout.
 */
class SyncViewModel : ViewModel() {

    private val settings = SyncSettingsRepository()
    private val backup = BackupRepository(WatchProgressRepository(), SettingsRepository())

    val provider: StateFlow<SyncProvider> =
        settings.provider.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncProvider.NONE)

    val credentials: StateFlow<Map<String, String>> =
        settings.credentials.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val lastSyncAt: StateFlow<Long> =
        settings.lastSyncAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /**
     * Ce qui a fait échouer la dernière synchro **de fond**.
     *
     * Séparé de [state], qui ne parle que de la synchro qu'on vient de demander :
     * une panne de fond n'a pas à disparaître parce qu'on a ouvert l'écran.
     */
    val backgroundFailure: StateFlow<String?> =
        settings.lastFailure.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val passphrase: StateFlow<String> =
        settings.passphrase.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setPassphrase(value: String) {
        viewModelScope.launch { settings.setPassphrase(value) }
    }

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state

    /** Les fournisseurs proposables, [SyncProvider.NONE] en tête pour éteindre. */
    val choices: List<SyncProvider> = listOf(SyncProvider.NONE) + SyncProviders.available()

    fun fieldsFor(provider: SyncProvider) =
        SyncProviders.descriptor(provider)?.fields.orEmpty()

    fun setProvider(provider: SyncProvider) {
        viewModelScope.launch {
            settings.setProvider(provider)
            _state.value = SyncState.Idle
        }
    }

    fun setCredential(id: String, value: String) {
        viewModelScope.launch {
            settings.setCredentials(credentials.value + (id to value))
            // Un identifiant qui change invalide le verdict précédent : laisser
            // « échec » à l'écran pendant qu'on corrige la clé serait décourager
            // quelqu'un qui est en train de bien faire.
            _state.value = SyncState.Idle
        }
    }

    /**
     * Lance une synchro.
     *
     * C'est aussi le bouton « tester » : il n'y en a pas d'autre, parce qu'une
     * synchro *est* le test — elle lit, elle écrit, elle dit combien d'appareils
     * elle a vus. Un bouton de test séparé aurait éprouvé autre chose que ce qui
     * tourne vraiment.
     */
    fun syncNow(now: Long) {
        if (_state.value == SyncState.Running) return
        _state.value = SyncState.Running
        viewModelScope.launch {
            val store = settings.openStore()
            if (store == null) {
                _state.value = SyncState.Failed(SyncFailure.NOT_CONFIGURED, null)
                return@launch
            }
            val engine = SyncEngine(store, settings.deviceId(), BackupSyncSubject(backup))
            _state.value = try {
                val report = engine.sync(now)
                settings.recordSync(at = now, clockOffset = report.clockOffset)
                SyncState.Done(report)
            } catch (e: SyncException) {
                SyncState.Failed(e.failure, e.message)
            }
        }
    }
}
