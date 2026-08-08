package fr.moovie.tv.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.moovie.tv.data.pairing.PairingFields
import fr.moovie.tv.ui.sync.providerLabel
import fr.moovie.tv.resources.settings_cat_sync
import fr.moovie.tv.resources.settings_cat_subtitles
import fr.moovie.tv.resources.settings_cat_api
import fr.moovie.tv.data.sync.SyncSettingsRepository
import fr.moovie.tv.data.sync.SyncProvider
import androidx.compose.runtime.rememberUpdatedState
import fr.moovie.tv.data.pairing.PairingSession
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.remote_intro
import fr.moovie.tv.resources.remote_send
import fr.moovie.tv.resources.remote_title
import fr.moovie.tv.resources.remote_to_remote
import fr.moovie.tv.resources.remote_to_settings
import fr.moovie.tv.resources.remote_type
import fr.moovie.tv.data.pairing.PairingServer
import fr.moovie.tv.data.pairing.PairingTexts
import fr.moovie.tv.data.sync.providers.B2_APP_KEY
import fr.moovie.tv.data.sync.providers.B2_KEY_ID
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.pairing_close
import fr.moovie.tv.resources.pairing_no_network
import fr.moovie.tv.resources.pairing_no_socket
import fr.moovie.tv.resources.pairing_opened
import fr.moovie.tv.resources.pairing_or_url
import fr.moovie.tv.resources.pairing_page_done
import fr.moovie.tv.resources.pairing_page_done_detail
import fr.moovie.tv.resources.pairing_page_intro
import fr.moovie.tv.resources.pairing_page_submit
import fr.moovie.tv.resources.pairing_saved
import fr.moovie.tv.resources.pairing_scan
import fr.moovie.tv.resources.pairing_title
import fr.moovie.tv.resources.pairing_waiting
import fr.moovie.tv.resources.settings_introdb_key
import fr.moovie.tv.resources.settings_tmdb_key
import fr.moovie.tv.resources.subtitles_password
import fr.moovie.tv.resources.subtitles_username
import fr.moovie.tv.resources.sync_field_b2_app_key
import fr.moovie.tv.resources.sync_field_b2_key_id
import fr.moovie.tv.resources.sync_passphrase_hint
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.stringResource

/**
 * Appairage d'un téléphone pour saisir les clés.
 *
 * Le serveur naît et meurt avec cette modale — [DisposableEffect] ferme la
 * socket en sortant. C'est ce qui rend la fonctionnalité acceptable : il n'y a
 * pas de service qui écoute en permanence sur le réseau, seulement le temps
 * qu'on est devant l'écran, à regarder le code.
 *
 * L'adresse est écrite **en toutes lettres sous le QR**. Un appareil photo qui
 * ne veut rien savoir, un téléphone sans lecteur intégré, un QR flou sur une
 * dalle sale : il faut une porte de secours, et recopier vingt caractères reste
 * infiniment moins pénible que de taper une clé B2 à la télécommande — ce qui
 * est précisément le problème qu'on résout.
 */
@Composable
fun PairingDialog(
    onDismiss: () -> Unit,
    /**
     * Appelé à chaque envoi du téléphone qui a modifié quelque chose.
     *
     * L'écran d'installation s'en sert pour vérifier la clé TMDB et enchaîner
     * tout seul. Les réglages ne le renseignent pas : on y est déjà configuré, et
     * refermer la modale sous les doigts de quelqu'un qui saisit encore ses
     * identifiants de synchro serait une surprise, pas un service.
     */
    onSaved: () -> Unit = {},
    /** Message affiché sous l'état, en rouge. Vérification en cours, clé refusée… */
    notice: String? = null,
) {
    val texts = PairingTexts(
        title = stringResource(Res.string.pairing_title),
        intro = stringResource(Res.string.pairing_page_intro),
        submit = stringResource(Res.string.pairing_page_submit),
        done = stringResource(Res.string.pairing_page_done),
        doneDetail = stringResource(Res.string.pairing_page_done_detail),
        remoteTitle = stringResource(Res.string.remote_title),
        remoteIntro = stringResource(Res.string.remote_intro),
        remoteType = stringResource(Res.string.remote_type),
        remoteSend = stringResource(Res.string.remote_send),
        remoteToSettings = stringResource(Res.string.remote_to_settings),
        remoteToRemote = stringResource(Res.string.remote_to_remote),
        remoteBack = stringResource(Res.string.common_back),
    )

    // Les libellés des champs sont ceux des réglages, résolus ici : la couche
    // données ne connaît pas les ressources, et un texte affiché n'existe qu'une
    // fois dans l'application.
    val labels = mapOf(
        PairingFields.TMDB to stringResource(Res.string.settings_tmdb_key),
        PairingFields.INTRODB to stringResource(Res.string.settings_introdb_key),
        PairingFields.OS_USER to stringResource(Res.string.subtitles_username),
        PairingFields.OS_PASS to stringResource(Res.string.subtitles_password),
        PairingFields.PASSPHRASE to stringResource(Res.string.sync_passphrase_hint),
        PairingFields.SYNC_PREFIX + B2_KEY_ID to stringResource(Res.string.sync_field_b2_key_id),
        PairingFields.SYNC_PREFIX + B2_APP_KEY to stringResource(Res.string.sync_field_b2_app_key),
    )

    // Titres de sections. Le service est nommé dans le titre plutôt que laissé
    // à deviner : « Identifiant » sous « Sous-titres · OpenSubtitles » se
    // comprend seul, isolé il ne veut rien dire. Les marques ne se traduisent
    // pas, d'où la composition avec la catégorie traduite.
    val provider by remember { SyncSettingsRepository().provider }
        .collectAsState(initial = SyncProvider.NONE)
    val groups = mapOf(
        PairingFields.GROUP_API to stringResource(Res.string.settings_cat_api),
        PairingFields.GROUP_SUBTITLES to
            "${stringResource(Res.string.settings_cat_subtitles)} · OpenSubtitles",
        PairingFields.GROUP_SYNC to
            "${stringResource(Res.string.settings_cat_sync)} · ${providerLabel(provider)}",
    )

    // `groups` change quand le fournisseur est connu : la fabrique du serveur
    // doit relire la valeur courante, pas celle capturée à la composition.
    val currentGroups by rememberUpdatedState(groups)
    // Le serveur appartient à la session, pas à la modale : armé en
    // télécommande, il doit survivre à sa fermeture. La session le crée au
    // premier besoin et le rend tel quel ensuite — recréer changerait le jeton
    // et ferait tomber en 404 la page ouverte sur le téléphone.
    val server = remember {
        val source = PairingFields()
        PairingSession.start {
            PairingServer(
                fields = { source.snapshot(labels, currentGroups) },
                apply = { source.apply(it) },
                texts = texts,
            )
        }
    }
    DisposableEffect(server) {
        onDispose { PairingSession.releaseDialog() }
    }

    val url by server.url.collectAsState()
    val opened by server.opened.collectAsState()
    val saved by server.saved.collectAsState()
    val failure by server.failure.collectAsState()
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
    // `saved` ne fait que croître : chaque hausse est un envoi qui a changé
    // quelque chose. Zéro exclu, sinon on préviendrait à l'ouverture.
    LaunchedEffect(saved) { if (saved > 0) onSaved() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(MoovieShape)
                .background(Color(0xF5161616))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(texts.title, style = MaterialTheme.typography.titleMedium)

            when {
                failure != null -> Text(
                    stringResource(
                        if (failure == "no-network") Res.string.pairing_no_network
                        else Res.string.pairing_no_socket,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE0A0A0),
                    textAlign = TextAlign.Center,
                )

                url == null -> Text(
                    stringResource(Res.string.pairing_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9A9A9A),
                )

                else -> {
                    Text(
                        stringResource(Res.string.pairing_scan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9A9A9A),
                        textAlign = TextAlign.Center,
                    )
                    // Le fond blanc est porté par le QR lui-même : un lecteur
                    // attend du sombre sur du clair, thème de l'app ou non.
                    QrCode(content = url!!, size = 200.dp)
                    Text(
                        stringResource(Res.string.pairing_or_url),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF7A7A7A),
                    )
                    Text(
                        url!!,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        when {
                            saved > 0 -> stringResource(Res.string.pairing_saved, saved.toString())
                            opened -> stringResource(Res.string.pairing_opened)
                            else -> stringResource(Res.string.pairing_waiting)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (saved > 0) Color(0xFF7DDC7D) else Color(0xFF9A9A9A),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE0A0A0),
                    textAlign = TextAlign.Center,
                )
            }

            MoovieButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().focusRequester(close),
            ) { Text(stringResource(Res.string.pairing_close)) }
        }
    }
}
