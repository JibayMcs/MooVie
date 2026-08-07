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
import fr.moovie.tv.resources.pairing_page_filled
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
fun PairingDialog(onDismiss: () -> Unit) {
    val texts = PairingTexts(
        title = stringResource(Res.string.pairing_title),
        intro = stringResource(Res.string.pairing_page_intro),
        filled = stringResource(Res.string.pairing_page_filled),
        submit = stringResource(Res.string.pairing_page_submit),
        done = stringResource(Res.string.pairing_page_done),
        doneDetail = stringResource(Res.string.pairing_page_done_detail),
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

    val server = remember {
        val source = PairingFields()
        PairingServer(
            fields = { source.snapshot(labels) },
            apply = { source.apply(it) },
            texts = texts,
        )
    }
    DisposableEffect(server) {
        server.start()
        onDispose { server.close() }
    }

    val url by server.url.collectAsState()
    val opened by server.opened.collectAsState()
    val saved by server.saved.collectAsState()
    val failure by server.failure.collectAsState()
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }

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

            MoovieButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().focusRequester(close),
            ) { Text(stringResource(Res.string.pairing_close)) }
        }
    }
}
