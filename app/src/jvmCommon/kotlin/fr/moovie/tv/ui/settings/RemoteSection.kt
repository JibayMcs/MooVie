package fr.moovie.tv.ui.settings

/**
 * Les sections des réglages qui parlent à un autre appareil.
 *
 * ## Pourquoi elles restent ici quand le reste de l'écran est parti en commun
 *
 * Le portage iOS a fait passer `SettingsScreenContent` dans `commonMain` : c'est
 * la même page, le même style, les mêmes lignes sur les quatre plateformes, et
 * la dupliquer aurait été le contraire de ce qu'on cherche. Ces trois sections
 * n'ont pas pu suivre, et pas par paresse — elles s'adossent à `data.cast`,
 * `data.pairing` et `data.remote`, qui reposent sur des sockets d'écoute et un
 * serveur HTTP local. C'est du `jvmCommon` par nature.
 *
 * iOS n'a de toute façon rien à y afficher : le portage a écarté le rôle de
 * cible Cast comme celui de télécommande. La section entière disparaît chez lui
 * plutôt que de s'afficher sans effet — voir le paramètre `remoteSection` de
 * [SettingsScreenContent], qui vaut null là-bas et porte ces fonctions ici.
 */

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.cast.CastPresence
import fr.moovie.tv.data.cast.CastScan
import fr.moovie.tv.data.cast.CastScanVerdict
import fr.moovie.tv.data.pairing.PairingSession
import fr.moovie.tv.data.remote.RemotePresence
import fr.moovie.tv.data.remote.RemoteTargetRepository
import fr.moovie.tv.data.remote.parseRemoteLink
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.cast_scan_action
import fr.moovie.tv.resources.cast_scan_label
import fr.moovie.tv.resources.cast_scan_never
import fr.moovie.tv.resources.cast_scan_refused
import fr.moovie.tv.resources.cast_scan_running
import fr.moovie.tv.resources.cast_scan_silent
import fr.moovie.tv.resources.cast_scan_unreachable
import fr.moovie.tv.resources.cast_scan_unsupported
import fr.moovie.tv.resources.pairing_action
import fr.moovie.tv.resources.pairing_scan
import fr.moovie.tv.resources.pairing_title
import fr.moovie.tv.resources.remote_forget
import fr.moovie.tv.resources.remote_forget_action
import fr.moovie.tv.resources.remote_forget_done
import fr.moovie.tv.resources.remote_forget_help
import fr.moovie.tv.resources.remote_forget_target
import fr.moovie.tv.resources.remote_link_action
import fr.moovie.tv.resources.remote_link_help
import fr.moovie.tv.resources.remote_link_hint
import fr.moovie.tv.resources.remote_link_invalid
import fr.moovie.tv.resources.remote_link_title
import fr.moovie.tv.resources.remote_none
import fr.moovie.tv.resources.remote_none_help
import fr.moovie.tv.resources.remote_paired_help
import fr.moovie.tv.resources.remote_reconnect
import fr.moovie.tv.resources.remote_reconnect_action
import fr.moovie.tv.resources.remote_reconnect_failed
import fr.moovie.tv.resources.remote_reconnect_help
import fr.moovie.tv.resources.remote_reconnect_ok
import fr.moovie.tv.resources.remote_reconnect_running
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.pairing.PairingDialog
import fr.moovie.tv.ui.pairing.pairingOffered
import fr.moovie.tv.ui.theme.MoovieShape
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Section « Télécommande ».
 *
 * ### Elle a deux faces, et l'appareil décide laquelle
 *
 * - **Le téléviseur se laisse piloter** : il affiche le QR qui donne l'adresse
 *   et le jeton, et il peut tout révoquer d'un coup.
 * - **Le téléphone pilote** : il se souvient d'un téléviseur, et il faut pouvoir
 *   l'oublier depuis un endroit stable. Le bandeau « ne répond pas » de l'écran
 *   de télécommande le propose aussi, mais il suppose d'y arriver — or une cible
 *   fausse est précisément ce qui empêche d'y arriver.
 *
 * Les deux tiennent dans la même section parce que c'est le même sujet vu des
 * deux bouts, et parce qu'un appareil ne voit jamais que sa moitié.
 *
 * ### Pourquoi elle n'est plus dans « API & Clés »
 *
 * Elle y était née, du temps où l'appairage ne servait qu'à taper une clé B2 sans
 * clavier. Depuis qu'il porte la télécommande, ranger « Oublier les
 * télécommandes » sous les clés d'API ne dit plus rien de ce que fait le
 * réglage — et surtout, sur un téléphone, la section des clés n'avait aucune
 * raison de parler du téléviseur du salon.
 */
@Composable
internal fun RemoteSection(onPair: () -> Unit) {
    val scope = rememberCoroutineScope()

    // --- Ce téléviseur, piloté depuis un téléphone -------------------------
    if (pairingOffered()) {
        SettingRow(
            label = stringResource(Res.string.pairing_title),
            help = stringResource(Res.string.pairing_scan),
        ) {
            MoovieButton(onClick = onPair) {
                Text(stringResource(Res.string.pairing_action))
            }
        }
        // La contrepartie d'un jeton qui dure : sans elle, un téléphone appairé
        // une fois garde la main sur le téléviseur pour toujours. C'est ce que
        // l'ancien jeton de session faisait tout seul en périmant — et c'est
        // aussi ce qui rendait la télécommande inutilisable le lendemain.
        var renewed by remember { mutableStateOf(false) }
        SettingRow(
            label = stringResource(Res.string.remote_forget),
            help = stringResource(
                if (renewed) Res.string.remote_forget_done else Res.string.remote_forget_help,
            ),
        ) {
            MoovieButton(
                onClick = {
                    scope.launch {
                        PairingSession.renewToken()
                        renewed = true
                    }
                },
            ) { Text(stringResource(Res.string.remote_forget_action)) }
        }
    }

    // --- Le téléviseur que cet appareil pilote -----------------------------
    val targets = remember { RemoteTargetRepository() }
    val target by targets.target.collectAsState(initial = null)
    target?.let {
        // Reconnexion à la main. Le contrôle périodique répare tout seul, mais
        // il attend jusqu'à vingt-cinq secondes : quelqu'un qui vient de
        // rallumer son téléviseur veut pouvoir forcer la vérification plutôt
        // que de regarder un bouton absent en se demandant si c'est cassé.
        //
        // C'est aussi le seul recours si le contrôle s'arrête — il vit dans le
        // bouton flottant, donc pas sur le lecteur ni pendant l'installation.
        var probing by remember { mutableStateOf(false) }
        var reached by remember { mutableStateOf<Boolean?>(null) }
        SettingRow(
            label = stringResource(Res.string.remote_reconnect),
            help = stringResource(
                when {
                    probing -> Res.string.remote_reconnect_running
                    reached == true -> Res.string.remote_reconnect_ok
                    reached == false -> Res.string.remote_reconnect_failed
                    else -> Res.string.remote_reconnect_help
                },
            ),
        ) {
            MoovieButton(
                enabled = !probing,
                onClick = {
                    scope.launch {
                        probing = true
                        reached = RemotePresence.refresh()
                        probing = false
                    }
                },
            ) { Text(stringResource(Res.string.remote_reconnect_action)) }
        }

        SettingRow(
            label = it.name,
            help = stringResource(Res.string.remote_paired_help),
        ) {
            MoovieButton(
                onClick = {
                    scope.launch {
                        targets.forget()
                        // La présence est ce qui fait exister le bouton flottant :
                        // la laisser à vrai après un oubli le maintiendrait à
                        // l'écran, menant à un écran sans téléviseur.
                        RemotePresence.lost()
                    }
                },
            ) { Text(stringResource(Res.string.remote_forget_target)) }
        }
    }

    // Rien à piloter, et pas de QR à montrer : il ne reste qu'à dire par où on
    // commence. Un téléviseur, lui, n'a pas à s'entendre dire qu'aucun
    // téléviseur n'est appairé.
    if (target == null && !pairingOffered()) {
        Text(
            stringResource(Res.string.remote_none),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(Res.string.remote_none_help),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9A9A9A),
        )
        RemoteLinkRow()
    }

    CastDiagnosticRow()
}

/**
 * Ce que la recherche de Chromecast a donné, en toutes lettres.
 *
 * ## Pourquoi cette ligne existe
 *
 * Un utilisateur a signalé que le bouton de diffusion n'apparaissait **jamais**
 * chez lui, sur le même Wi-Fi que son Chromecast. Rien dans l'application ne
 * permettait de dire pourquoi : le bouton est absent quand la liste est vide, et
 * la liste est vide aussi bien parce qu'il n'y a rien que parce que la recherche
 * a échoué. Trois causes très différentes, un seul symptôme.
 *
 * [CastScanReport] les distingue ; cette ligne les affiche. Elle transforme un
 * « ça ne marche pas » en une phrase exploitable — et le bouton force un
 * balayage sans attendre la cadence de veille.
 *
 * C'est la même réponse que les sondes de couverture apportent aux catalogues :
 * un échec silencieux est indistinguable d'un succès sans résultat, tant que
 * personne ne compte.
 */
@Composable
private fun CastDiagnosticRow() {
    val scope = rememberCoroutineScope()
    val rapport by CastScan.dernier.collectAsState()
    val appareils by CastPresence.devices.collectAsState()
    var encours by remember { mutableStateOf(false) }

    val aide = when (rapport.verdict) {
        CastScanVerdict.NON_SUPPORTE -> stringResource(Res.string.cast_scan_unsupported)
        CastScanVerdict.JAMAIS -> stringResource(Res.string.cast_scan_never)
        CastScanVerdict.TROUVE -> appareils.joinToString(", ") { it.name }
        CastScanVerdict.RESEAU_MUET -> stringResource(Res.string.cast_scan_silent)
        CastScanVerdict.RESOLUTION -> stringResource(Res.string.cast_scan_unreachable)
        CastScanVerdict.PILE_REFUSE -> stringResource(Res.string.cast_scan_refused)
    }

    SettingRow(
        label = stringResource(Res.string.cast_scan_label),
        help = if (encours) stringResource(Res.string.cast_scan_running) else aide,
    ) {
        MoovieButton(
            enabled = !encours,
            onClick = {
                scope.launch {
                    encours = true
                    runCatching { CastPresence.refresh() }
                    encours = false
                }
            },
        ) { Text(stringResource(Res.string.cast_scan_action)) }
    }
}

/**
 * Appairage en collant le lien, pour qui ne peut pas scanner le QR.
 *
 * ## Ce que ça débloque
 *
 * **Un ordinateur n'a pas de caméra**, ou pas celle qu'il faut, et c'est le seul
 * chemin qu'il ait vers un téléviseur — le QR suppose un appareil qu'on lève
 * devant l'écran. Sans cette ligne, le desktop verrait « Aucun téléviseur
 * appairé » sans le moindre moyen d'y remédier.
 *
 * Le téléphone y gagne aussi un recours : c'est exactement ce que
 * [fr.moovie.tv.ui.pairing.PairingDialog] prévoyait en écrivant l'adresse en
 * toutes lettres sous le QR — « un appareil photo qui ne veut rien savoir, un QR
 * flou ». L'issue de secours existait, il manquait la porte.
 *
 * ## Le lien ne se valide pas tout seul
 *
 * [parseRemoteLink] refuse ce qui est incomplet, et on le dit. Enregistrer une
 * cible amputée de son jeton donnerait un téléviseur qui répond 404 à chaque
 * appel, sans que rien à l'écran n'explique pourquoi — le pire des deux mondes,
 * puisque l'appairage aurait *l'air* d'avoir réussi.
 */
@Composable
private fun RemoteLinkRow() {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    SettingRow(
        label = stringResource(Res.string.remote_link_title),
        help = stringResource(
            if (invalid) Res.string.remote_link_invalid else Res.string.remote_link_help,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF555555), MoovieShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (draft.isEmpty()) {
                    Text(stringResource(Res.string.remote_link_hint), color = Color(0xFF888888))
                }
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        // L'erreur s'efface dès qu'on retouche : la laisser
                        // affichée pendant qu'on corrige donne l'impression que
                        // la correction ne sert à rien.
                        invalid = false
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MoovieButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    val cible = parseRemoteLink(draft)
                    if (cible == null) {
                        invalid = true
                        return@MoovieButton
                    }
                    scope.launch {
                        RemoteTargetRepository().remember(cible)
                        // Sonder tout de suite : le dépôt se remplit, mais c'est
                        // la présence qui fait exister le bouton de diffusion.
                        // Sans ça, on vient d'appairer et rien ne change à
                        // l'écran jusqu'au prochain contrôle périodique.
                        RemotePresence.refresh()
                        draft = ""
                    }
                },
            ) { Text(stringResource(Res.string.remote_link_action)) }
        }
    }
}
