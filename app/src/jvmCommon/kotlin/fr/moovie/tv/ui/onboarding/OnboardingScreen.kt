package fr.moovie.tv.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.onboarding_fresh
import fr.moovie.tv.resources.onboarding_fresh_help
import fr.moovie.tv.resources.onboarding_intro
import fr.moovie.tv.resources.onboarding_no_key
import fr.moovie.tv.resources.onboarding_restore
import fr.moovie.tv.resources.onboarding_restore_help
import fr.moovie.tv.resources.onboarding_title
import fr.moovie.tv.ui.backup.BackupSection
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.navigation.Screen
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

private val DIM = Color(0xFF9A9A9A)

/**
 * Écran de première installation.
 *
 * Il remplace l'accueil vide qu'on obtenait sans clé TMDB : celui-ci disait
 * « saisis ta clé dans les réglages » sans mentionner qu'une sauvegarde pouvait
 * tout restaurer d'un coup, clé comprise.
 *
 * Il se referme tout seul dès qu'une clé existe — qu'elle vienne d'un import ou
 * d'une saisie dans les réglages — d'où [onReady] déclenché par le flux plutôt
 * que par le bouton qui l'a provoqué.
 */
@Composable
fun OnboardingScreen(
    onOpenSettings: () -> Unit,
    onReady: () -> Unit,
) {
    val repo = remember { SettingsRepository() }
    val hasKey by produceState(initialValue = false) {
        repo.tmdbApiKey.collect { value = it.isNotBlank() }
    }
    LaunchedEffect(hasKey) { if (hasKey) onReady() }

    var restoring by remember { mutableStateOf(false) }
    // Un import qui n'apportait pas de clé laisse l'installation à moitié faite :
    // on le dit, plutôt que de renvoyer sur le même choix sans explication.
    var importedWithoutKey by remember { mutableStateOf(false) }

    val firstChoice = remember { FocusRequester() }
    LaunchedEffect(restoring) { if (!restoring) runCatching { firstChoice.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(Res.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(Res.string.onboarding_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = DIM,
            // Bornée : en pleine largeur d'un 1080p la ligne devient illisible.
            modifier = Modifier.widthIn(max = 760.dp),
        )

        if (restoring) {
            BackupSection(
                importOnly = true,
                onLeave = {
                    restoring = false
                    importedWithoutKey = true
                },
            )
        } else {
            if (importedWithoutKey) {
                Text(
                    stringResource(Res.string.onboarding_no_key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE0B057),
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
            Choice(
                label = stringResource(Res.string.onboarding_restore),
                help = stringResource(Res.string.onboarding_restore_help),
                onClick = { restoring = true },
                modifier = Modifier.focusRequester(firstChoice),
            )
            Choice(
                label = stringResource(Res.string.onboarding_fresh),
                help = stringResource(Res.string.onboarding_fresh_help),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun Choice(
    label: String,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoovieButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().widthIn(max = 900.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 16.dp,
        ),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(help, style = MaterialTheme.typography.bodySmall, color = DIM)
        }
    }
}

/**
 * Écran racine de la pile, une fois su s'il existe une clé TMDB.
 *
 * Rend null tant que la réponse n'est pas lue : construire la pile sur l'accueil
 * puis la remplacer aurait laissé passer une image d'accueil vide, exactement ce
 * que l'écran d'installation existe pour éviter. La lecture est un accès
 * DataStore, de l'ordre de la dizaine de millisecondes.
 *
 * [override] court-circuite tout : c'est le crochet de dev qui ouvre directement
 * le lecteur sur un flux de test.
 */
@Composable
fun rememberStartScreen(override: Screen? = null): Screen? {
    if (override != null) return override
    val repo = remember { SettingsRepository() }
    val hasKey by produceState<Boolean?>(initialValue = null) {
        value = repo.tmdbApiKey.first().isNotBlank()
    }
    return hasKey?.let { if (it) Screen.Home else Screen.Onboarding }
}
