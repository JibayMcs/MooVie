package fr.moovie.tv.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape as RoundedShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import fr.moovie.tv.R
import fr.moovie.tv.data.net.DohProvider
import fr.moovie.tv.data.settings.AppLanguage
import fr.moovie.tv.data.settings.LocaleManager
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton

/**
 * Écran de réglages, groupé par catégories : API (clé TMDB), Lecture & Langue
 * (langue de stream + langue de l'app), Intro/générique, Réseau (DNS), Sources.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val apiKey by viewModel.tmdbApiKey.collectAsStateWithLifecycle()
    val streamLang by viewModel.streamLanguage.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val dohEnabled by viewModel.dohEnabled.collectAsStateWithLifecycle()
    val dohProvider by viewModel.dohProvider.collectAsStateWithLifecycle()
    val skipIntroOutro by viewModel.skipIntroOutro.collectAsStateWithLifecycle()

    Column(
        // Scroll pleine page puis marges : les boutons agrandis au focus débordent
        // dans la marge au lieu d'être rognés par le conteneur défilant.
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        SettingsCategory(stringResource(R.string.settings_cat_api)) {
            Text(stringResource(R.string.settings_tmdb_key), style = MaterialTheme.typography.titleMedium)
            ApiKeyField(value = apiKey, onValueChange = viewModel::setTmdbApiKey)
            Text(stringResource(R.string.settings_tmdb_help), style = MaterialTheme.typography.bodySmall)
        }

        SettingsCategory(stringResource(R.string.settings_cat_playback)) {
            Text(stringResource(R.string.settings_stream_lang), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fr.moovie.tv.data.settings.StreamLanguage.entries.forEach { lang ->
                    MoovieButton(
                        onClick = { viewModel.setStreamLanguage(lang) },
                        selected = lang == streamLang,
                    ) { Text(lang.name) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            LanguageSelector()
        }

        SettingsCategory(stringResource(R.string.settings_cat_intro)) {
            Text(stringResource(R.string.settings_intro_help), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoovieButton(
                    onClick = { viewModel.setSkipIntroOutro(true) },
                    selected = skipIntroOutro,
                ) { Text(stringResource(R.string.common_enabled)) }
                MoovieButton(
                    onClick = { viewModel.setSkipIntroOutro(false) },
                    selected = !skipIntroOutro,
                ) { Text(stringResource(R.string.common_disabled)) }
            }
        }

        SettingsCategory(stringResource(R.string.settings_cat_dns)) {
            Text(stringResource(R.string.settings_dns_help), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoovieButton(
                    onClick = { viewModel.setDohEnabled(true) },
                    selected = dohEnabled,
                ) { Text(stringResource(R.string.settings_doh_on)) }
                MoovieButton(
                    onClick = { viewModel.setDohEnabled(false) },
                    selected = !dohEnabled,
                ) { Text(stringResource(R.string.settings_doh_off)) }
            }
            if (dohEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_doh_resolver), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DohProvider.entries.forEach { provider ->
                        MoovieButton(
                            onClick = { viewModel.setDohProvider(provider) },
                            selected = provider == dohProvider,
                        ) { Text(provider.label) }
                    }
                }
            }
        }

        SettingsCategory(stringResource(R.string.settings_cat_sources)) {
            Text(stringResource(R.string.settings_sources_help), style = MaterialTheme.typography.bodySmall)
            // Liste « stripped » : nom à gauche, actions icône à droite.
            providers.forEachIndexed { index, p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedShape(10.dp))
                        .background(if (index % 2 == 0) Color(0xFF161616) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${index + 1}. ${p.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (p.enabled) Color.White else Color(0xFF777777),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (index > 0) {
                            MoovieIconButton(
                                onClick = { viewModel.moveProviderUp(p.name) },
                                icon = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.settings_move_up, p.name),
                            )
                        }
                        if (index < providers.lastIndex) {
                            MoovieIconButton(
                                onClick = { viewModel.moveProviderDown(p.name) },
                                icon = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.settings_move_down, p.name),
                            )
                        }
                        MoovieIconButton(
                            onClick = { viewModel.toggleProvider(p.name, !p.enabled) },
                            icon = Icons.Default.PowerSettingsNew,
                            contentDescription = if (p.enabled)
                                stringResource(R.string.settings_disable, p.name)
                            else stringResource(R.string.settings_enable, p.name),
                            selected = p.enabled,
                        )
                    }
                }
            }
        }

        MoovieButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
    }
}

/** Libellé traduit d'une langue de l'app. */
@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.FRENCH -> R.string.language_fr
        AppLanguage.ENGLISH -> R.string.language_en
        AppLanguage.SPANISH -> R.string.language_es
    },
)

/**
 * Sélecteur de langue « select » : un bouton affichant la langue courante ouvre
 * une liste modale des options. Choisir applique la locale et recrée l'activité.
 */
@Composable
private fun LanguageSelector() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = remember { LocaleManager.current(context) }

    MoovieButton(onClick = { open = true }) {
        Text(languageLabel(current))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
    }

    if (open) {
        val firstFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedShape(14.dp))
                    .background(Color(0xF5161616))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                AppLanguage.entries.forEachIndexed { index, language ->
                    val selected = language == current
                    MoovieButton(
                        onClick = {
                            LocaleManager.set(context, language)
                            open = false
                            (context as? Activity)?.recreate()
                        },
                        selected = selected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    ) {
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(languageLabel(language))
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    }
}

@Composable
private fun SettingsCategory(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun ApiKeyField(value: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, Color(0xFF555555), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(stringResource(R.string.settings_tmdb_hint), color = Color(0xFF888888))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                // Le champ texte avale les flèches par défaut : sans ça, le D-pad
                // ne peut plus quitter le champ (pas de touche Tab en télécommande).
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                        else -> false
                    }
                },
        )
    }
}
