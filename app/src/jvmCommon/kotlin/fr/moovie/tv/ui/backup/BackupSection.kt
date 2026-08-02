package fr.moovie.tv.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.backup.BackupFile
import fr.moovie.tv.data.backup.BackupSummary
import fr.moovie.tv.data.backup.BackupTarget
import fr.moovie.tv.data.backup.ImportMode
import fr.moovie.tv.data.backup.ImportReport
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.backup_cancel
import fr.moovie.tv.resources.backup_choose_file
import fr.moovie.tv.resources.backup_choose_target
import fr.moovie.tv.resources.backup_contents
import fr.moovie.tv.resources.backup_done
import fr.moovie.tv.resources.backup_export
import fr.moovie.tv.resources.backup_finish
import fr.moovie.tv.resources.backup_from
import fr.moovie.tv.resources.backup_has_key
import fr.moovie.tv.resources.backup_has_settings
import fr.moovie.tv.resources.backup_import
import fr.moovie.tv.resources.backup_importing
import fr.moovie.tv.resources.backup_include_key
import fr.moovie.tv.resources.backup_intro
import fr.moovie.tv.resources.backup_key_warning
import fr.moovie.tv.resources.backup_merge
import fr.moovie.tv.resources.backup_merge_help
import fr.moovie.tv.resources.backup_mode_question
import fr.moovie.tv.resources.backup_no_target
import fr.moovie.tv.resources.backup_none_found
import fr.moovie.tv.resources.backup_replace
import fr.moovie.tv.resources.backup_replace_help
import fr.moovie.tv.resources.backup_report_history
import fr.moovie.tv.resources.backup_report_resume
import fr.moovie.tv.resources.backup_report_watched
import fr.moovie.tv.resources.backup_report_watchlist
import fr.moovie.tv.resources.backup_rescan
import fr.moovie.tv.resources.backup_stat_history
import fr.moovie.tv.resources.backup_stat_resume
import fr.moovie.tv.resources.backup_stat_watched
import fr.moovie.tv.resources.backup_stat_watchlist
import fr.moovie.tv.resources.backup_target_app_folder
import fr.moovie.tv.resources.backup_target_internal
import fr.moovie.tv.resources.backup_target_removable
import fr.moovie.tv.resources.backup_unreadable
import fr.moovie.tv.resources.backup_write_failed
import fr.moovie.tv.resources.backup_writing
import fr.moovie.tv.resources.backup_written
import fr.moovie.tv.resources.common_disabled
import fr.moovie.tv.resources.common_enabled
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.format.formatBackupDate
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.stringResource

private val DIM = Color(0xFF9A9A9A)
private val PANEL = Color(0xFF161616)

/**
 * Section « Sauvegarde » des réglages : export vers un support, import depuis un
 * fichier trouvé sur les supports branchés.
 *
 * Elle se pilote elle-même — un état, pas quinze paramètres hoistés — parce que
 * c'est un **parcours** et non un réglage : chaque étape découle de la
 * précédente et n'intéresse personne au-dessus. Le reste de l'écran de réglages
 * garde son style sans état.
 */
@Composable
fun BackupSection(
    viewModel: BackupViewModel = remember { BackupViewModel() },
    /**
     * Parcours de première installation : on entre directement dans l'import, et
     * il n'y a rien à exporter d'un appareil encore vide.
     */
    importOnly: Boolean = false,
    /** Sortie du parcours en mode [importOnly] — le retour au menu n'a pas de sens. */
    onLeave: (() -> Unit)? = null,
) {
    val step by viewModel.step.collectAsState()
    LaunchedEffect(importOnly) { if (importOnly) viewModel.startImport() }
    val leave: () -> Unit = if (importOnly && onLeave != null) onLeave else viewModel::reset

    // Chaque changement d'étape reprend le focus : au D-pad, un écran qui change
    // sous les yeux sans que le curseur suive laisse l'utilisateur appuyer dans
    // le vide. Sauf à l'arrivée dans la section : le volet de navigation garde
    // alors le focus, sans quoi parcourir les catégories vers le bas se termine
    // par un saut dans le volet droit dont on ne ressort plus.
    val firstAction = remember { FocusRequester() }
    var entering by remember { mutableStateOf(true) }
    LaunchedEffect(step::class) {
        if (entering) entering = false else runCatching { firstAction.requestFocus() }
    }
    val focusFirst = Modifier.focusRequester(firstAction)

    // Groupe de focus : chaque étape remplace ses boutons, et sans conteneur
    // capable de retenir le focus, celui-ci retombait sur le volet de
    // navigation — dont le `onFocusChanged` rebasculait aussitôt la section.
    Column(
        modifier = Modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val current = step) {
            BackupStep.Menu -> {
                Help(stringResource(Res.string.backup_intro))
                MoovieButton(onClick = viewModel::startExport, modifier = focusFirst) {
                    Text(stringResource(Res.string.backup_export))
                }
                MoovieButton(onClick = viewModel::startImport) {
                    Text(stringResource(Res.string.backup_import))
                }
            }

            is BackupStep.ChooseTarget -> {
                Step(stringResource(Res.string.backup_choose_target))
                if (current.targets.isEmpty()) {
                    Help(stringResource(Res.string.backup_no_target))
                } else {
                    current.targets.forEachIndexed { index, target ->
                        TargetRow(
                            target = target,
                            onClick = { viewModel.chooseTarget(target) },
                            modifier = if (index == 0) focusFirst else Modifier,
                        )
                    }
                }
                Back(leave)
            }

            is BackupStep.ConfirmExport -> {
                Step(current.target.label)
                Summary(stringResource(Res.string.backup_contents), current.contents)
                if (current.hasApiKey) {
                    Text(
                        stringResource(Res.string.backup_include_key),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MoovieButton(
                            onClick = { viewModel.setIncludeApiKey(true) },
                            selected = current.includeApiKey,
                        ) { Text(stringResource(Res.string.common_enabled)) }
                        MoovieButton(
                            onClick = { viewModel.setIncludeApiKey(false) },
                            selected = !current.includeApiKey,
                        ) { Text(stringResource(Res.string.common_disabled)) }
                    }
                    // L'avertissement n'apparaît que quand il s'applique : affiché
                    // en permanence, il devient un décor qu'on ne lit plus.
                    if (current.includeApiKey) Warning(stringResource(Res.string.backup_key_warning))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoovieButton(
                        onClick = { viewModel.confirmExport(System.currentTimeMillis()) },
                        modifier = focusFirst,
                    ) { Text(stringResource(Res.string.backup_export)) }
                    MoovieButton(onClick = leave) {
                        Text(stringResource(Res.string.backup_cancel))
                    }
                }
            }

            is BackupStep.Working -> Help(
                if (current.importing) {
                    stringResource(Res.string.backup_importing)
                } else {
                    stringResource(Res.string.backup_writing)
                },
            )

            is BackupStep.Exported -> {
                Step(stringResource(Res.string.backup_written))
                // Le chemin complet : c'est ce qu'on cherchera depuis un PC pour
                // copier le fichier ailleurs.
                Text(current.path, style = MaterialTheme.typography.bodySmall, color = DIM)
                MoovieButton(onClick = leave, modifier = focusFirst) {
                    Text(stringResource(Res.string.backup_finish))
                }
            }

            is BackupStep.Scanned -> {
                Step(stringResource(Res.string.backup_choose_file))
                if (current.files.isEmpty()) {
                    Help(stringResource(Res.string.backup_none_found, "*.moovie.json"))
                    MoovieButton(onClick = viewModel::startImport, modifier = focusFirst) {
                        Text(stringResource(Res.string.backup_rescan))
                    }
                } else {
                    current.files.forEachIndexed { index, file ->
                        FileRow(
                            file = file,
                            onClick = { viewModel.chooseFile(file) },
                            modifier = if (index == 0) focusFirst else Modifier,
                        )
                    }
                }
                Back(leave)
            }

            is BackupStep.Preview -> {
                Step(current.file.name)
                Summary(
                    stringResource(
                        Res.string.backup_from,
                        formatBackupDate(current.contents.exportedAt),
                        current.contents.platform.ifBlank { "?" },
                    ),
                    current.contents,
                )
                Text(
                    stringResource(Res.string.backup_mode_question),
                    style = MaterialTheme.typography.titleMedium,
                )
                ModeRow(
                    label = stringResource(Res.string.backup_merge),
                    help = stringResource(Res.string.backup_merge_help),
                    onClick = { viewModel.applyImport(ImportMode.MERGE) },
                    modifier = focusFirst,
                )
                ModeRow(
                    label = stringResource(Res.string.backup_replace),
                    help = stringResource(Res.string.backup_replace_help),
                    onClick = { viewModel.applyImport(ImportMode.REPLACE) },
                )
                Back(leave)
            }

            is BackupStep.Imported -> {
                Step(stringResource(Res.string.backup_done))
                Report(current.report)
                MoovieButton(onClick = leave, modifier = focusFirst) {
                    Text(stringResource(Res.string.backup_finish))
                }
            }

            is BackupStep.Failed -> {
                Warning(
                    when (current.reason) {
                        BackupStep.Failed.Reason.WRITE -> stringResource(Res.string.backup_write_failed)
                        BackupStep.Failed.Reason.UNREADABLE -> stringResource(Res.string.backup_unreadable)
                    },
                )
                MoovieButton(onClick = leave, modifier = focusFirst) {
                    Text(stringResource(Res.string.backup_finish))
                }
            }
        }
    }
}

@Composable
private fun Step(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun Help(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = DIM)

/** Message qui doit être lu avant d'agir : encadré, pas seulement grisé. */
@Composable
private fun Warning(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFFE0B057),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF7A5E22), MoovieShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun Back(onLeave: () -> Unit) {
    MoovieButton(onClick = onLeave) { Text(stringResource(Res.string.backup_cancel)) }
}

@Composable
private fun TargetRow(target: BackupTarget, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MoovieButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(target.label)
            Text(
                if (target.removable) {
                    stringResource(Res.string.backup_target_removable)
                } else {
                    stringResource(Res.string.backup_target_internal)
                },
                style = MaterialTheme.typography.bodySmall,
                color = DIM,
            )
            // Dire où le fichier atterrit vraiment évite de le chercher à la
            // racine de la clé depuis un PC et de croire l'export raté.
            if (!target.writableAtRoot) {
                Text(
                    stringResource(Res.string.backup_target_app_folder),
                    style = MaterialTheme.typography.bodySmall,
                    color = DIM,
                )
            }
        }
    }
}

@Composable
private fun FileRow(file: BackupFile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MoovieButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name)
            Text(
                listOf(formatBackupDate(file.modifiedAt), file.targetLabel)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = DIM,
            )
        }
    }
}

/** Choix d'un mode d'import : le libellé, et surtout ce qu'il fait. */
@Composable
private fun ModeRow(
    label: String,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoovieButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text(help, style = MaterialTheme.typography.bodySmall, color = DIM)
        }
    }
}

/** Ce que contient une sauvegarde, en une ligne par nature de donnée. */
@Composable
private fun Summary(title: String, summary: BackupSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PANEL, MoovieShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(
            listOf(
                stringResource(Res.string.backup_stat_watched, summary.watched),
                stringResource(Res.string.backup_stat_resume, summary.resume),
                stringResource(Res.string.backup_stat_watchlist, summary.watchlist),
                stringResource(Res.string.backup_stat_history, summary.history),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = DIM,
        )
        val extras = listOfNotNull(
            stringResource(Res.string.backup_has_key).takeIf { summary.hasApiKey },
            stringResource(Res.string.backup_has_settings).takeIf { summary.hasSettings },
        )
        if (extras.isNotEmpty()) {
            Text(
                extras.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = DIM,
            )
        }
    }
}

/** Bilan d'un import : ce qui a bougé, et ce qui était déjà là. */
@Composable
private fun Report(report: ImportReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PANEL, MoovieShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            stringResource(
                Res.string.backup_report_watched,
                report.watchedAdded,
                report.watchedAlreadyThere,
            ),
            stringResource(
                Res.string.backup_report_resume,
                report.resumeAdded,
                report.resumeUpdated,
            ),
            stringResource(Res.string.backup_report_watchlist, report.watchlistAdded),
            stringResource(Res.string.backup_report_history, report.historyAdded),
        ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = DIM) }
    }
}
