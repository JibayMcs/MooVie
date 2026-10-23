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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_disabled
import fr.moovie.tv.resources.common_enabled
import fr.moovie.tv.resources.subtitles_intro
import fr.moovie.tv.resources.subtitles_languages
import fr.moovie.tv.resources.subtitles_languages_help
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
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE_HIGH
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM

private val DIM = MOOVIE_TEXT_DIM

/**
 * Section « Sous-titres » des réglages : langues recherchées et apparence.
 * Les sous-titres viennent d'un addon public, sans clé ni
 * compte : rien à configurer côté service.
 */
@Composable
fun SubtitlesSection(
    viewModel: SubtitlesSettingsViewModel = remember { SubtitlesSettingsViewModel() },
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Help(stringResource(Res.string.subtitles_intro))

        Text(
            stringResource(Res.string.subtitles_languages),
            style = MaterialTheme.typography.titleMedium,
        )
        Help(stringResource(Res.string.subtitles_languages_help))
        LanguagePicker(selected = state.languages, onToggle = viewModel::toggleLanguage)

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
                    listOf(MOOVIE_SURFACE, MOOVIE_TEXT_DIM, MOOVIE_SURFACE_HIGH),
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

@Composable
private fun Help(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = DIM,
    modifier = Modifier.widthIn(max = 760.dp),
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
 * Langues proposées, par code ISO 639-1, celui que comprend l'addon.
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
