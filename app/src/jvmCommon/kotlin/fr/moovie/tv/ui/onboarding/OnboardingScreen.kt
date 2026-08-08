package fr.moovie.tv.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
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
import fr.moovie.tv.ui.theme.MoovieShape
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
import fr.moovie.tv.ui.pairing.PairingDialog
import fr.moovie.tv.ui.pairing.pairingOffered
import fr.moovie.tv.resources.pairing_action
import fr.moovie.tv.resources.onboarding_phone_help
import androidx.compose.runtime.rememberCoroutineScope
import fr.moovie.tv.data.tmdb.KeyCheck
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.resources.pairing_key_checking
import fr.moovie.tv.resources.pairing_key_missing
import fr.moovie.tv.resources.pairing_key_rejected
import fr.moovie.tv.resources.pairing_key_unreachable
import kotlinx.coroutines.launch
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
 * Il se referme tout seul dès qu'une clé existe — qu'elle vienne d'un import, du
 * téléphone ou d'une saisie dans les réglages — d'où [onReady] déclenché par le
 * flux plutôt que par le bouton qui l'a provoqué. Seul l'appairage en cours
 * suspend cette fermeture, voir plus bas.
 *
 * Sur TV il propose aussi la saisie depuis un téléphone, et c'est là qu'elle vaut
 * le plus : la toute première chose que demande l'application est une clé de 32
 * caractères hexadécimaux, à composer à la télécommande sur un clavier en grille.
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
    var pairing by remember { mutableStateOf(false) }
    // L'écran se referme dès qu'une clé existe — **sauf** pendant l'appairage.
    //
    // Sans cette réserve, la clé collée depuis le téléphone ferait disparaître
    // l'écran, donc la modale, donc le serveur, au moment précis où il doit
    // encore répondre au téléphone : celui-ci afficherait une erreur de
    // connexion pour un envoi qui a pourtant réussi. On laisse fermer par
    // « Terminer », après avoir vu le compte des réglages enregistrés.
    LaunchedEffect(hasKey, pairing) { if (hasKey && !pairing) onReady() }

    var restoring by remember { mutableStateOf(false) }
    // Un import qui n'apportait pas de clé laisse l'installation à moitié faite :
    // on le dit, plutôt que de renvoyer sur le même choix sans explication.
    var importedWithoutKey by remember { mutableStateOf(false) }

    val firstChoice = remember { FocusRequester() }
    LaunchedEffect(restoring) { if (!restoring) runCatching { firstChoice.requestFocus() } }

    // Verdict de la vérification, affiché dans la modale d'appairage. Les
    // chaînes sont résolues ici : `stringResource` est un composable, il ne peut
    // pas être appelé depuis la coroutine qui les choisit.
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    val checkingText = stringResource(Res.string.pairing_key_checking)
    val rejectedText = stringResource(Res.string.pairing_key_rejected)
    val unreachableText = stringResource(Res.string.pairing_key_unreachable)
    val missingText = stringResource(Res.string.pairing_key_missing)

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
            // Avant la saisie manuelle, parce que c'est la même situation par un
            // meilleur chemin : sur une TV, coller la clé au clavier tactile bat
            // toujours 32 caractères hexadécimaux à la télécommande. La saisie
            // manuelle reste dessous, comme repli.
            if (pairingOffered()) {
                Choice(
                    label = stringResource(Res.string.pairing_action),
                    help = stringResource(Res.string.onboarding_phone_help),
                    onClick = { pairing = true },
                )
            }
            Choice(
                label = stringResource(Res.string.onboarding_fresh),
                help = stringResource(Res.string.onboarding_fresh_help),
                onClick = onOpenSettings,
            )
        }
    }

    if (pairing) {
        PairingDialog(
            onDismiss = { pairing = false },
            notice = notice,
            // Enchaîner ne se décide pas sur « la clé n'est pas vide » : une clé
            // fausse laisserait l'utilisateur sur un accueil sans catalogue, sans
            // rien pour comprendre. On la fait valider par TMDB avant de passer.
            onSaved = {
                scope.launch {
                    notice = checkingText
                    val key = repo.tmdbApiKey.first()
                    if (key.isBlank()) {
                        // Un envoi sans clé TMDB : le téléphone a renseigné autre
                        // chose. Rien à reprocher, il reste juste l'essentiel.
                        notice = missingText
                        return@launch
                    }
                    notice = when (TmdbRepository().validateKey(key)) {
                        KeyCheck.VALID -> {
                            pairing = false
                            onReady()
                            null
                        }
                        KeyCheck.REJECTED -> rejectedText
                        KeyCheck.UNREACHABLE -> unreachableText
                    }
                }
            },
        )
    }
}

@Composable
private fun Choice(
    label: String,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ces deux-là portent leur propre surface, contrairement au reste de l'app.
    // Un MoovieButton au repos n'est que son libellé : son habillage vient du
    // focus, ce qui va en face d'une télécommande mais ne donne rien au doigt.
    // Ailleurs le contexte suffit à dire qu'on peut toucher — une affiche, une
    // ligne de réglage. Ici il n'y a que deux paragraphes sur du noir, et c'est
    // le tout premier écran de l'app : il ne peut pas se permettre d'être
    // ambigu.
    MoovieButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 900.dp)
            .border(1.dp, Color(0x33FFFFFF), MoovieShape),
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
