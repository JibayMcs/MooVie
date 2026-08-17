package fr.moovie.tv.ui.subtitles

import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fr.moovie.tv.ui.remote.remoteTypable
import fr.moovie.tv.core.subtitles.model.SubtitleQuota
import fr.moovie.tv.data.subtitles.OsLoginError
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_disabled
import fr.moovie.tv.resources.common_enabled
import fr.moovie.tv.resources.subtitles_account
import fr.moovie.tv.resources.subtitles_account_help
import fr.moovie.tv.resources.subtitles_connected_as
import fr.moovie.tv.resources.subtitles_connecting
import fr.moovie.tv.resources.subtitles_error_credentials
import fr.moovie.tv.resources.subtitles_error_network
import fr.moovie.tv.resources.subtitles_error_rate
import fr.moovie.tv.resources.subtitles_intro
import fr.moovie.tv.resources.subtitles_login
import fr.moovie.tv.resources.subtitles_logout
import fr.moovie.tv.resources.subtitles_no_key
import fr.moovie.tv.resources.subtitles_password
import fr.moovie.tv.resources.subtitles_quota_known
import fr.moovie.tv.resources.subtitles_quota_unknown
import fr.moovie.tv.resources.subtitles_languages
import fr.moovie.tv.resources.subtitles_languages_help
import fr.moovie.tv.resources.subtitles_remember
import fr.moovie.tv.resources.subtitles_remember_help
import fr.moovie.tv.resources.subtitles_username
import fr.moovie.tv.resources.subtitles_appearance
import fr.moovie.tv.resources.subtitles_appearance_help
import fr.moovie.tv.resources.subtitles_backdrop
import fr.moovie.tv.resources.subtitles_backdrop_box
import fr.moovie.tv.resources.subtitles_backdrop_none
import fr.moovie.tv.resources.subtitles_backdrop_outline
import fr.moovie.tv.resources.subtitles_backdrop_shadow
import fr.moovie.tv.resources.subtitles_color
import fr.moovie.tv.resources.subtitles_color_white
import fr.moovie.tv.resources.subtitles_color_yellow
import fr.moovie.tv.resources.subtitles_content
import fr.moovie.tv.resources.subtitles_content_help
import fr.moovie.tv.resources.subtitles_prefer_forced
import fr.moovie.tv.resources.subtitles_prefer_forced_help
import fr.moovie.tv.resources.subtitles_prefer_sdh
import fr.moovie.tv.resources.subtitles_prefer_sdh_help
import fr.moovie.tv.resources.subtitles_preview
import fr.moovie.tv.resources.subtitles_size
import fr.moovie.tv.resources.subtitles_size_huge
import fr.moovie.tv.resources.subtitles_size_large
import fr.moovie.tv.resources.subtitles_size_normal
import fr.moovie.tv.resources.subtitles_size_small
import fr.moovie.tv.core.subtitles.model.SubtitleBackdrop
import fr.moovie.tv.core.subtitles.model.SubtitleColor
import fr.moovie.tv.core.subtitles.model.SubtitleSize
import fr.moovie.tv.core.subtitles.model.SubtitleStyle
import fr.moovie.tv.core.subtitles.model.toOpaqueArgb
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.theme.MoovieShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.stringResource

private val DIM = Color(0xFF9A9A9A)

/**
 * Section « Sous-titres » des réglages : compte OpenSubtitles et quota.
 *
 * Elle porte son état comme la section Sauvegarde, et pour la même raison — se
 * connecter est un parcours, pas un réglage.
 *
 * Le compte est présenté comme **facultatif** de bout en bout : les sous-titres
 * fonctionnent sans, la connexion ne fait que relever la limite et rendre le
 * quota visible. C'est ce qui garde l'esprit « pas de compte » de Moo-vie tout
 * en offrant la porte de sortie à qui la veut.
 */
@Composable
fun SubtitlesSection(
    viewModel: SubtitlesSettingsViewModel = remember { SubtitlesSettingsViewModel() },
) {
    val state by viewModel.state.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!state.keyPresent) {
            Warning(stringResource(Res.string.subtitles_no_key))
            return@Column
        }

        Help(stringResource(Res.string.subtitles_intro))
        QuotaLine(state.quota)

        Text(
            stringResource(Res.string.subtitles_account),
            style = MaterialTheme.typography.titleMedium,
        )
        Help(stringResource(Res.string.subtitles_account_help))

        if (state.account.connected) {
            Text(
                stringResource(Res.string.subtitles_connected_as, state.account.username),
                style = MaterialTheme.typography.bodyMedium,
            )
            MoovieButton(onClick = viewModel::logout) {
                Text(stringResource(Res.string.subtitles_logout))
            }
        } else {
            LabelledField(
                label = stringResource(Res.string.subtitles_username),
                value = username,
                onValueChange = { username = it },
            )
            LabelledField(
                label = stringResource(Res.string.subtitles_password),
                value = password,
                onValueChange = { password = it },
                mask = true,
            )

            Text(
                stringResource(Res.string.subtitles_remember),
                style = MaterialTheme.typography.titleMedium,
            )
            // L'explication est longue et c'est voulu : on demande à quelqu'un
            // de laisser son mot de passe sur un appareil, il doit savoir
            // pourquoi c'est nécessaire et jusqu'où ça va.
            Help(stringResource(Res.string.subtitles_remember_help))
            OnOff(value = remember, onChange = { remember = it })

            state.error?.let { Warning(it.message()) }

            MoovieButton(
                onClick = { viewModel.login(username.trim(), password, remember) },
                enabled = !state.busy && username.isNotBlank() && password.isNotBlank(),
            ) {
                Text(
                    if (state.busy) {
                        stringResource(Res.string.subtitles_connecting)
                    } else {
                        stringResource(Res.string.subtitles_login)
                    },
                )
            }
        }

        Text(
            stringResource(Res.string.subtitles_languages),
            style = MaterialTheme.typography.titleMedium,
        )
        Help(stringResource(Res.string.subtitles_languages_help))
        LanguagePicker(selected = state.languages, onToggle = viewModel::toggleLanguage)

        Text(
            stringResource(Res.string.subtitles_content),
            style = MaterialTheme.typography.titleMedium,
        )
        Help(stringResource(Res.string.subtitles_content_help))

        Text(stringResource(Res.string.subtitles_prefer_forced), style = MaterialTheme.typography.bodyMedium)
        Help(stringResource(Res.string.subtitles_prefer_forced_help))
        OnOff(value = state.preferForced, onChange = viewModel::setPreferForced)

        Text(stringResource(Res.string.subtitles_prefer_sdh), style = MaterialTheme.typography.bodyMedium)
        Help(stringResource(Res.string.subtitles_prefer_sdh_help))
        OnOff(value = state.preferHearingImpaired, onChange = viewModel::setPreferHearingImpaired)

        Text(
            stringResource(Res.string.subtitles_appearance),
            style = MaterialTheme.typography.titleMedium,
        )
        Help(stringResource(Res.string.subtitles_appearance_help))
        // L'aperçu **avant** les choix : c'est lui qu'on regarde en réglant, et
        // il ne doit pas sortir de l'écran quand on descend d'un cran.
        SubtitlePreview(state.style)

        Choice(
            label = stringResource(Res.string.subtitles_size),
            values = SubtitleSize.entries,
            selected = state.style.size,
            name = { it.label() },
            onPick = viewModel::setSize,
        )
        Choice(
            label = stringResource(Res.string.subtitles_color),
            values = SubtitleColor.entries,
            selected = state.style.color,
            name = { it.label() },
            onPick = viewModel::setColor,
        )
        Choice(
            label = stringResource(Res.string.subtitles_backdrop),
            values = SubtitleBackdrop.entries,
            selected = state.style.backdrop,
            name = { it.label() },
            onPick = viewModel::setBackdrop,
        )
    }
}

/**
 * Le réglage tel qu'il se verra, sur un fond qui ressemble à une image.
 *
 * Sans aperçu, chaque essai demande de lancer une vidéo, d'attendre un
 * sous-titre et de revenir : personne ne règle une taille dans ces conditions,
 * et le réglage resterait sur son défaut. Le dégradé imite une scène — un aplat
 * uni rendrait le choix du fond incompréhensible, puisque c'est justement sur
 * les zones claires que contour et bandeau se justifient.
 *
 * Ce n'est **pas** le rendu du lecteur : ExoPlayer et mpv dessinent leur propre
 * texte. L'aperçu vise la ressemblance, pas l'identité — il répond à « est-ce
 * assez gros », qui est la question posée.
 */
@Composable
private fun SubtitlePreview(style: SubtitleStyle) {
    val text = stringResource(Res.string.subtitles_preview)
    val color = Color(style.color.rgb.toOpaqueArgb())
    val size = MaterialTheme.typography.bodyLarge.fontSize * style.size.scale

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(MoovieShape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF1A1A1A), Color(0xFF8A8A8A), Color(0xFF2A2A2A)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = color,
            fontSize = size,
            textAlign = TextAlign.Center,
            style = when (style.backdrop) {
                SubtitleBackdrop.OUTLINE -> TextStyle(
                    shadow = Shadow(Color.Black, Offset.Zero, blurRadius = 6f),
                )
                SubtitleBackdrop.SHADOW -> TextStyle(
                    shadow = Shadow(Color.Black, Offset(3f, 3f), blurRadius = 2f),
                )
                SubtitleBackdrop.NONE, SubtitleBackdrop.BOX -> TextStyle.Default
            },
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .then(
                    if (style.backdrop == SubtitleBackdrop.BOX) {
                        Modifier
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/** Rangée « libellé + choix exclusifs », comme ailleurs dans les réglages. */
@Composable
private fun <T> Choice(
    label: String,
    values: List<T>,
    selected: T,
    name: @Composable (T) -> String,
    onPick: (T) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = DIM)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            MoovieButton(onClick = { onPick(value) }, selected = value == selected) {
                Text(name(value))
            }
        }
    }
}

@Composable
private fun SubtitleSize.label(): String = stringResource(
    when (this) {
        SubtitleSize.SMALL -> Res.string.subtitles_size_small
        SubtitleSize.NORMAL -> Res.string.subtitles_size_normal
        SubtitleSize.LARGE -> Res.string.subtitles_size_large
        SubtitleSize.HUGE -> Res.string.subtitles_size_huge
    },
)

@Composable
private fun SubtitleColor.label(): String = stringResource(
    when (this) {
        SubtitleColor.WHITE -> Res.string.subtitles_color_white
        SubtitleColor.YELLOW -> Res.string.subtitles_color_yellow
    },
)

@Composable
private fun SubtitleBackdrop.label(): String = stringResource(
    when (this) {
        SubtitleBackdrop.NONE -> Res.string.subtitles_backdrop_none
        SubtitleBackdrop.OUTLINE -> Res.string.subtitles_backdrop_outline
        SubtitleBackdrop.SHADOW -> Res.string.subtitles_backdrop_shadow
        SubtitleBackdrop.BOX -> Res.string.subtitles_backdrop_box
    },
)

/**
 * Le quota, ou l'aveu qu'on ne le connaît pas.
 *
 * Afficher zéro faute de mieux serait un mensonge : sans compte connecté, la
 * valeur n'existe qu'après un téléchargement.
 */
@Composable
private fun QuotaLine(quota: SubtitleQuota) {
    val text = if (quota.known) {
        stringResource(
            Res.string.subtitles_quota_known,
            quota.remaining ?: 0,
            quota.allowed ?: (quota.remaining ?: 0),
        )
    } else {
        stringResource(Res.string.subtitles_quota_unknown)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (quota.known) Color.White else DIM,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF333333), MoovieShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun OsLoginError.message(): String = when (this) {
    OsLoginError.BAD_CREDENTIALS -> stringResource(Res.string.subtitles_error_credentials)
    OsLoginError.RATE_LIMITED -> stringResource(Res.string.subtitles_error_rate)
    OsLoginError.NETWORK -> stringResource(Res.string.subtitles_error_network)
}

@Composable
private fun Help(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = DIM,
    modifier = Modifier.widthIn(max = 760.dp),
)

@Composable
private fun Warning(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = Color(0xFFE0B057),
    modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFF7A5E22), MoovieShape)
        .padding(horizontal = 16.dp, vertical = 12.dp),
)

@Composable
private fun OnOff(value: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MoovieButton(onClick = { onChange(true) }, selected = value) {
            Text(stringResource(Res.string.common_enabled))
        }
        MoovieButton(onClick = { onChange(false) }, selected = !value) {
            Text(stringResource(Res.string.common_disabled))
        }
    }
}

@Composable
private fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    mask: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DIM)
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .border(1.dp, Color(0xFF555555), MoovieShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = if (mask) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier
                    .fillMaxWidth()
                    // Le mot de passe est masqué à l'écran : il ne serait pas
                    // cohérent de l'envoyer en clair au téléphone. Le champ
                    // s'annonce, son contenu non.
                    .remoteTypable(
                        label = label,
                        value = value,
                        onValueChange = onValueChange,
                        secret = mask,
                    )
                    // Un champ texte avale les flèches : sans ça le D-pad ne peut
                    // plus en sortir, faute de touche Tab sur une télécommande.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val direction = when (event.key) {
                            Key.DirectionDown -> FocusDirection.Down
                            Key.DirectionUp -> FocusDirection.Up
                            Key.DirectionLeft -> FocusDirection.Left
                            else -> return@onPreviewKeyEvent false
                        }
                        focusManager.moveFocus(direction)
                        true
                    },
            )
        }
    }
}

/**
 * Choix des langues recherchées.
 *
 * Les langues actives portent leur **rang**, parce que l'ordre décide : c'est le
 * premier critère de classement des sous-titres proposés, avant la cadence et
 * avant la popularité. Sans ce numéro, rien ne dirait à l'utilisateur que
 * « français puis anglais » n'est pas la même chose que « anglais puis
 * français », et l'information serait invisible alors qu'elle est structurante.
 *
 * Les noms sont donnés dans **leur propre langue** plutôt que traduits : c'est
 * l'usage des sélecteurs de langue, et ça évite trois traductions par entrée
 * pour un gain nul — personne ne cherche « Deutsch » sous « Allemand ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagePicker(selected: List<String>, onToggle: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.widthIn(max = 760.dp),
    ) {
        SUBTITLE_LANGUAGES.forEach { (code, name) ->
            val rank = selected.indexOf(code)
            MoovieButton(onClick = { onToggle(code) }, selected = rank >= 0) {
                Text(if (rank >= 0) "${rank + 1}. $name" else name)
            }
        }
    }
}

/**
 * Langues proposées, par code ISO 639-1 — celui qu'attend OpenSubtitles.
 *
 * Liste volontairement courte : un catalogue exhaustif ferait des dizaines de
 * cibles à traverser au D-pad pour un usage qui, en pratique, tourne autour de
 * deux ou trois langues.
 */
private val SUBTITLE_LANGUAGES = listOf(
    "fr" to "Français",
    "en" to "English",
    "es" to "Español",
    "de" to "Deutsch",
    "it" to "Italiano",
    "pt" to "Português",
    "nl" to "Nederlands",
    "pl" to "Polski",
    "ru" to "Русский",
    "ar" to "العربية",
    "tr" to "Türkçe",
    "ja" to "日本語",
    "ko" to "한국어",
    "zh" to "中文",
)
