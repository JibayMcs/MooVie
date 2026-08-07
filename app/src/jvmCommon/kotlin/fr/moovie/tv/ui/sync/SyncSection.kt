package fr.moovie.tv.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.sync.CredentialField
import fr.moovie.tv.data.sync.SyncFailure
import fr.moovie.tv.data.sync.SyncProvider
import fr.moovie.tv.data.sync.providers.B2_APP_KEY
import fr.moovie.tv.data.sync.providers.B2_KEY_ID
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.sync_devices_seen
import fr.moovie.tv.resources.sync_error_credentials
import fr.moovie.tv.resources.sync_error_network
import fr.moovie.tv.resources.sync_error_not_configured
import fr.moovie.tv.resources.sync_error_store
import fr.moovie.tv.resources.sync_field_b2_app_key
import fr.moovie.tv.resources.sync_field_b2_key_id
import fr.moovie.tv.resources.sync_background_failed
import fr.moovie.tv.resources.sync_help
import fr.moovie.tv.resources.sync_last
import fr.moovie.tv.resources.sync_never
import fr.moovie.tv.resources.sync_none
import fr.moovie.tv.resources.sync_now
import fr.moovie.tv.resources.sync_passphrase
import fr.moovie.tv.resources.sync_passphrase_help
import fr.moovie.tv.resources.sync_passphrase_hint
import fr.moovie.tv.resources.sync_provider
import fr.moovie.tv.resources.sync_running
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.format.formatBackupDate
import fr.moovie.tv.ui.settings.ApiKeyField
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val DIM = Color(0xFF9A9A9A)
private val PANEL = Color(0xFF1A1A1A)

/**
 * Réglages de la synchro en ligne.
 *
 * **Cet écran ne connaît aucun fournisseur.** Il demande au ViewModel les champs
 * du fournisseur choisi et dessine le formulaire à partir de là. Ajouter WebDAV
 * demain ne touchera donc que [credentialLabel] — une branche de plus pour son
 * libellé, parce que c'est la seule chose qu'un descripteur ne peut pas porter
 * sans faire entrer de la traduction dans la couche données.
 */
@Composable
fun SyncSection(viewModel: SyncViewModel = remember { SyncViewModel() }) {
    val provider by viewModel.provider.collectAsState()
    val credentials by viewModel.credentials.collectAsState()
    val state by viewModel.state.collectAsState()
    val lastSync by viewModel.lastSyncAt.collectAsState()
    val backgroundFailure by viewModel.backgroundFailure.collectAsState()
    val passphrase by viewModel.passphrase.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.sync_help), style = MaterialTheme.typography.bodySmall, color = DIM)

        Text(stringResource(Res.string.sync_provider), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            viewModel.choices.forEach { choice ->
                MoovieButton(
                    onClick = { viewModel.setProvider(choice) },
                    selected = choice == provider,
                ) {
                    Text(providerLabel(choice))
                }
            }
        }

        // Le formulaire est engendré : la vue ne sait pas ce qu'elle demande.
        viewModel.fieldsFor(provider).forEach { field ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(credentialLabel(field), style = MaterialTheme.typography.titleMedium)
                ApiKeyField(
                    value = credentials[field.id].orEmpty(),
                    hint = credentialLabel(field),
                    onValueChange = { viewModel.setCredential(field.id, it) },
                )
            }
        }

        if (provider != SyncProvider.NONE) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.sync_passphrase),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(Res.string.sync_passphrase_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = DIM,
                )
                ApiKeyField(
                    value = passphrase,
                    hint = stringResource(Res.string.sync_passphrase_hint),
                    onValueChange = viewModel::setPassphrase,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MoovieButton(
                    onClick = { viewModel.syncNow(System.currentTimeMillis()) },
                    enabled = state != SyncState.Running,
                ) {
                    Text(
                        stringResource(
                            if (state == SyncState.Running) Res.string.sync_running else Res.string.sync_now,
                        ),
                    )
                }
            }
            Status(state = state, lastSync = lastSync, backgroundFailure = backgroundFailure)
        }
    }
}

@Composable
private fun Status(state: SyncState, lastSync: Long, backgroundFailure: String?) {
    val message = when (state) {
        is SyncState.Failed -> stringResource(
            when (state.failure) {
                SyncFailure.NOT_CONFIGURED -> Res.string.sync_error_not_configured
                SyncFailure.CREDENTIALS -> Res.string.sync_error_credentials
                SyncFailure.NETWORK -> Res.string.sync_error_network
                SyncFailure.STORE -> Res.string.sync_error_store
            },
        )

        is SyncState.Done -> pluralStringResource(
            Res.plurals.sync_devices_seen,
            state.report.devicesSeen,
            state.report.devicesSeen,
        )

        // Au repos, la fraîcheur est la seule chose utile — et la seule qui
        // rende visible une synchro automatique, qui par définition ne se
        // montre pas. « Jamais » compris : c'est ce qui distingue « pas encore
        // configuré » de « configuré et silencieux ».
        else -> if (lastSync == 0L) {
            stringResource(Res.string.sync_never)
        } else {
            stringResource(Res.string.sync_last, formatBackupDate(lastSync))
        }
    }

    // Une panne de fond survit à l'ouverture de l'écran : sans elle, une clé
    // fausse laisserait l'app ne rien synchroniser pendant des semaines sans
    // que rien ne le laisse voir.
    val background = backgroundFailure
        ?.takeIf { state is SyncState.Idle }
        ?.substringAfter('|', "")
        ?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PANEL, MoovieShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        // Le détail vient du service et n'est pas traduit. Il est montré quand
        // même : « la clé doit être limitée à un bucket » est exactement ce
        // qu'il faut savoir, et aucune phrase générique ne le remplacerait.
        (state as? SyncState.Failed)?.detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = DIM)
        }
        background?.let {
            Text(
                stringResource(Res.string.sync_background_failed, it),
                style = MaterialTheme.typography.bodySmall,
                color = DIM,
            )
        }
    }
}

@Composable
private fun providerLabel(provider: SyncProvider): String = when (provider) {
    SyncProvider.NONE -> stringResource(Res.string.sync_none)
    // Un nom de marque ne se traduit pas.
    SyncProvider.BACKBLAZE_B2 -> "Backblaze B2"
}

/**
 * Le seul endroit à toucher pour un nouveau fournisseur.
 *
 * Le libellé ne peut pas vivre dans le descripteur sans y faire entrer une
 * ressource de traduction, donc une préoccupation d'affichage dans la couche
 * données. Le prix est une branche par champ, ici.
 */
@Composable
private fun credentialLabel(field: CredentialField): String = when (field.id) {
    B2_KEY_ID -> stringResource(Res.string.sync_field_b2_key_id)
    B2_APP_KEY -> stringResource(Res.string.sync_field_b2_app_key)
    else -> field.id
}
