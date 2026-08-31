package fr.moovie.tv.ui.backup

import fr.moovie.tv.shared.dispatcherEs
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import fr.moovie.tv.shared.maintenantMs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.backup.BACKUP_EXTENSION
import fr.moovie.tv.data.backup.BackupFile
import fr.moovie.tv.data.backup.BackupRepository
import fr.moovie.tv.data.backup.BackupSummary
import fr.moovie.tv.data.backup.BackupTarget
import fr.moovie.tv.data.backup.ImportMode
import fr.moovie.tv.data.backup.ImportReport
import fr.moovie.tv.data.backup.MoovieBackup
import fr.moovie.tv.data.backup.backupTargets
import fr.moovie.tv.data.backup.findBackups
import fr.moovie.tv.data.backup.summary
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Étapes du parcours de sauvegarde.
 *
 * Un état explicite plutôt qu'une poignée de booléens : l'export comme l'import
 * demandent de **montrer avant d'agir** (le support choisi, le contenu du
 * fichier), et c'est cet aperçu qui distingue la fonction d'un simple sélecteur
 * de fichier.
 */
sealed interface BackupStep {

    /** Point de départ : exporter ou importer. */
    data object Menu : BackupStep

    data class ChooseTarget(val targets: List<BackupTarget>) : BackupStep

    /** Dernier écran avant écriture : ce qui part, et l'avertissement sur la clé. */
    data class ConfirmExport(
        val target: BackupTarget,
        val contents: BackupSummary,
        val includeApiKey: Boolean,
        val hasApiKey: Boolean,
    ) : BackupStep

    /** Écriture ou lecture en cours. [importing] change le message affiché. */
    data class Working(val importing: Boolean) : BackupStep

    data class Exported(val path: String) : BackupStep

    data class Scanned(val files: List<BackupFile>) : BackupStep

    /** Aperçu du fichier choisi, avant de trancher entre fusion et remplacement. */
    data class Preview(val file: BackupFile, val contents: BackupSummary) : BackupStep

    data class Imported(val report: ImportReport) : BackupStep

    data class Failed(val reason: Reason) : BackupStep {
        enum class Reason { WRITE, UNREADABLE }
    }
}

class BackupViewModel : ViewModel() {

    private val repo = BackupRepository(WatchProgressRepository(), SettingsRepository())

    private val _step = MutableStateFlow<BackupStep>(BackupStep.Menu)
    val step: StateFlow<BackupStep> = _step.asStateFlow()

    /** Sauvegarde retenue à l'aperçu, gardée pour l'appliquer au clic suivant. */
    private var pending: MoovieBackup? = null

    fun reset() {
        pending = null
        _step.value = BackupStep.Menu
    }

    fun startExport() = io {
        _step.value = BackupStep.ChooseTarget(backupTargets())
    }

    fun chooseTarget(target: BackupTarget) = io {
        // Assemblé dès maintenant pour montrer ce qui partira ; l'écriture
        // n'aura lieu qu'à la confirmation.
        val backup = repo.export(includeApiKey = true, now = maintenantMs())
        pending = backup
        val contents = backup.summary()
        _step.value = BackupStep.ConfirmExport(
            target = target,
            contents = contents,
            includeApiKey = true,
            hasApiKey = contents.hasApiKey,
        )
    }

    fun setIncludeApiKey(include: Boolean) {
        val current = _step.value as? BackupStep.ConfirmExport ?: return
        _step.value = current.copy(includeApiKey = include)
    }

    fun confirmExport(now: Long) = io {
        val step = _step.value as? BackupStep.ConfirmExport ?: return@io
        val backup = pending ?: return@io
        _step.value = BackupStep.Working(importing = false)
        val written = repo.write(
            target = step.target,
            backup = if (step.includeApiKey) {
                backup
            } else {
                backup.copy(tmdbApiKey = null, introDbApiKey = null)
            },
            fileName = fileName(now),
        )
        _step.value = written?.let { BackupStep.Exported(it) }
            ?: BackupStep.Failed(BackupStep.Failed.Reason.WRITE)
    }

    fun startImport() = io {
        _step.value = BackupStep.Working(importing = true)
        _step.value = BackupStep.Scanned(findBackups())
    }

    fun chooseFile(file: BackupFile) = io {
        val backup = repo.read(file.path)
        if (backup == null) {
            _step.value = BackupStep.Failed(BackupStep.Failed.Reason.UNREADABLE)
            return@io
        }
        pending = backup
        _step.value = BackupStep.Preview(file, backup.summary())
    }

    fun applyImport(mode: ImportMode) = io {
        val backup = pending ?: return@io
        _step.value = BackupStep.Working(importing = true)
        _step.value = BackupStep.Imported(repo.import(backup, mode))
    }

    /**
     * Horodaté : deux sauvegardes sur la même clé ne s'écrasent pas, et la liste
     * d'import se lit dans l'ordre sans avoir à ouvrir les fichiers.
     */
    private fun fileName(now: Long) = "moovie-${stamp(now)}$BACKUP_EXTENSION"

    /**
     * `2026-08-02_1430` — trié à l'alphabétique comme à la chronologie.
     *
     * Composé à la main plutôt que par un motif : `java.time` n'existe pas en
     * Kotlin/Native, et kotlinx-datetime — qui, lui, est commun — ne porte pas
     * de formateur à motif. Quatre champs zéro-remplis donnent exactement la
     * même chaîne que `yyyy-MM-dd_HHmm`, et le nom de fichier est un format
     * qu'on lit soi-même, pas un affichage localisé : il ne doit surtout **pas**
     * suivre la langue de l'appareil.
     */
    private fun stamp(now: Long): String {
        val t = Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.currentSystemDefault())
        fun deuxChiffres(v: Int) = v.toString().padStart(2, '0')
        return "${t.year}-${deuxChiffres(t.monthNumber)}-${deuxChiffres(t.dayOfMonth)}" +
            "_${deuxChiffres(t.hour)}${deuxChiffres(t.minute)}"
    }

    /** Les supports se lisent et s'écrivent sur disque : jamais sur le fil UI. */
    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch { withContext(dispatcherEs) { block() } }
    }
}
