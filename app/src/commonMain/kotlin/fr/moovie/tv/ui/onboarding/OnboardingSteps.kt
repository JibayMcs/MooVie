package fr.moovie.tv.ui.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.onboarding_key_valid
import fr.moovie.tv.resources.onboarding_lang_help
import fr.moovie.tv.resources.onboarding_no
import fr.moovie.tv.resources.onboarding_pair_instead
import fr.moovie.tv.resources.onboarding_playback_title
import fr.moovie.tv.resources.onboarding_profile_help
import fr.moovie.tv.resources.onboarding_profile_title
import fr.moovie.tv.resources.onboarding_skip_help
import fr.moovie.tv.resources.onboarding_yes
import fr.moovie.tv.resources.profile_name_hint
import fr.moovie.tv.resources.settings_autoplay
import fr.moovie.tv.resources.settings_autoplay_help
import fr.moovie.tv.resources.settings_skip_intro
import fr.moovie.tv.resources.settings_stream_lang
import fr.moovie.tv.resources.settings_tmdb_help
import fr.moovie.tv.resources.settings_tmdb_hint
import fr.moovie.tv.resources.settings_tmdb_key
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.remote.remoteTypable
import fr.moovie.tv.ui.settings.ApiKeyField
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.stringResource

/**
 * La mise en page commune à toutes les questions : un titre, une explication,
 * puis la commande qui répond.
 *
 * Les trois dans cet ordre et jamais autrement. L'explication est **au-dessus**
 * de la commande parce qu'elle sert à décider : posée en dessous, elle se lit
 * après avoir répondu, c'est-à-dire trop tard.
 */
@Composable
private fun Question(
    titre: String,
    aide: String?,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
    ) {
        Text(titre, style = MaterialTheme.typography.headlineSmall)
        if (aide != null) {
            Text(
                aide,
                style = MaterialTheme.typography.bodyMedium,
                color = DIM,
                // Bornée : en pleine largeur d'un 1080p la ligne devient illisible.
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }
        contenu()
    }
}

/**
 * La clé TMDB.
 *
 * Le champ est celui des réglages, et c'est délibéré : il sait déjà masquer la
 * clé, se réaligner sur une valeur arrivée par un autre chemin, et surtout
 * placer l'œil **avant** lui au D-pad, sans quoi le clavier virtuel d'Android TV
 * capte la navigation et rend inatteignable tout ce qui suit. En réécrire un ici
 * aurait été réapprendre ces trois leçons.
 */
@Composable
internal fun EtapeCle(
    cle: String,
    onCle: (String) -> Unit,
    validee: Boolean,
    verdict: String?,
    onAppairer: (() -> Unit)?,
    focus: FocusRequester,
) {
    Question(
        titre = stringResource(Res.string.settings_tmdb_key),
        aide = stringResource(Res.string.settings_tmdb_help),
    ) {
        Box(modifier = Modifier.focusRequester(focus)) {
            ApiKeyField(
                value = cle,
                hint = stringResource(Res.string.settings_tmdb_hint),
                onValueChange = onCle,
            )
        }
        // Un seul emplacement pour les deux nouvelles possibles, parce qu'elles
        // s'excluent : une clé validée n'a pas de verdict à afficher, et un
        // verdict veut dire qu'aucune ne l'est.
        when {
            verdict != null -> Text(
                verdict,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE0B057),
            )
            validee -> Text(
                stringResource(Res.string.onboarding_key_valid),
                style = MaterialTheme.typography.bodyMedium,
                color = MOOVIE_ACCENT,
            )
        }
        // Le renvoi vers le téléphone reste offert **dans** l'étape, et pas
        // seulement sur l'écran d'accueil : c'est ici qu'on découvre ce qu'il
        // faut taper, donc ici qu'on renonce à le taper à la télécommande.
        if (onAppairer != null) {
            MoovieButton(onClick = onAppairer) {
                Text(stringResource(Res.string.onboarding_pair_instead))
            }
        }
    }
}

/**
 * La langue des flux.
 *
 * Les trois valeurs de [StreamLanguage] portent leur propre nom — VF, VOSTFR,
 * VO — et c'est ce que les réglages affichent aussi. Les traduire en phrases ici
 * donnerait deux vocabulaires pour un seul réglage, et la personne qui reviendra
 * le changer ne reconnaîtrait pas ce qu'elle a choisi.
 */
@Composable
internal fun EtapeLangue(
    choix: StreamLanguage?,
    onChoix: (StreamLanguage) -> Unit,
    focus: FocusRequester,
) {
    Question(
        titre = stringResource(Res.string.settings_stream_lang),
        aide = stringResource(Res.string.onboarding_lang_help),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StreamLanguage.entries.forEachIndexed { index, langue ->
                MoovieButton(
                    onClick = { onChoix(langue) },
                    selected = langue == choix,
                    modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                ) {
                    Text(langue.name)
                }
            }
        }
    }
}

/**
 * Les deux automatismes de lecture.
 *
 * Deux questions sur un même écran, contrairement à la règle d'une par page :
 * elles se répondent d'un mot, et les séparer aurait allongé le parcours sans
 * rien clarifier. Ce sont aussi les deux seules qui décrivent la même chose —
 * ce que le lecteur fait tout seul quand on ne lui demande rien.
 */
@Composable
internal fun EtapeLecture(
    enchainer: Boolean?,
    onEnchainer: (Boolean) -> Unit,
    passerGeneriques: Boolean?,
    onPasserGeneriques: (Boolean) -> Unit,
    focus: FocusRequester,
) {
    Question(titre = stringResource(Res.string.onboarding_playback_title), aide = null) {
        SousQuestion(
            label = stringResource(Res.string.settings_autoplay),
            aide = stringResource(Res.string.settings_autoplay_help),
            valeur = enchainer,
            onChoix = onEnchainer,
            focus = focus,
        )
        SousQuestion(
            label = stringResource(Res.string.settings_skip_intro),
            aide = stringResource(Res.string.onboarding_skip_help),
            valeur = passerGeneriques,
            onChoix = onPasserGeneriques,
            focus = null,
        )
    }
}

/**
 * Le nom du profil.
 *
 * Le profil d'origine existe dans toute installation : la question ne le crée
 * pas, elle le nomme. C'est ce qui la rend légitime en dernière position — on
 * vient de configurer un appareil, on signe.
 */
@Composable
internal fun EtapeProfil(
    nom: String,
    onNom: (String) -> Unit,
    focus: FocusRequester,
) {
    Question(
        titre = stringResource(Res.string.onboarding_profile_title),
        aide = stringResource(Res.string.onboarding_profile_help),
    ) {
        ChampTexte(
            valeur = nom,
            indication = stringResource(Res.string.profile_name_hint),
            onValeur = onNom,
            modifier = Modifier.focusRequester(focus),
        )
    }
}

/** Une question fermée : son libellé, son explication, et Oui / Non. */
@Composable
private fun SousQuestion(
    label: String,
    aide: String,
    valeur: Boolean?,
    onChoix: (Boolean) -> Unit,
    focus: FocusRequester?,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(
            aide,
            style = MaterialTheme.typography.bodySmall,
            color = DIM,
            modifier = Modifier.widthIn(max = 760.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Deux boutons plutôt qu'un interrupteur, et c'est tout l'enjeu du
            // parcours : un interrupteur est toujours dans une position, donc
            // toujours en train de répondre quelque chose. Ces deux-là ne
            // répondent rien tant qu'on n'a pas appuyé, ce qui est la seule
            // façon de distinguer « non » de « pas encore lu ».
            MoovieButton(
                onClick = { onChoix(true) },
                selected = valeur == true,
                modifier = focus?.let { Modifier.focusRequester(it) } ?: Modifier,
            ) {
                Text(stringResource(Res.string.onboarding_yes))
            }
            MoovieButton(onClick = { onChoix(false) }, selected = valeur == false) {
                Text(stringResource(Res.string.onboarding_no))
            }
        }
    }
}

/**
 * Champ de saisie libre, celui de la porte des profils.
 *
 * `remoteTypable` porte la saisie à la télécommande, et l'échappement au D-pad
 * est repris tel quel : le champ avale les flèches, si bien que sans lui on
 * n'en ressort plus — une télécommande n'a pas de touche Tab.
 */
@Composable
private fun ChampTexte(
    valeur: String,
    indication: String,
    onValeur: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .border(1.dp, Color(0xFF555555), MoovieShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (valeur.isEmpty()) Text(indication, color = Color(0xFF888888))
        BasicTextField(
            value = valeur,
            onValueChange = onValeur,
            singleLine = true,
            textStyle = TextStyle(color = Color.White),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .remoteTypable(
                    label = indication,
                    value = valeur,
                    onValueChange = onValeur,
                )
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val direction = when (event.key) {
                        Key.DirectionDown -> FocusDirection.Down
                        Key.DirectionUp -> FocusDirection.Up
                        else -> return@onPreviewKeyEvent false
                    }
                    focusManager.moveFocus(direction)
                    true
                },
        )
    }
}
