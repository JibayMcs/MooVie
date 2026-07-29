package fr.moovie.tv.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.net.DohProvider
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.common_disabled
import fr.moovie.tv.resources.common_enabled
import fr.moovie.tv.resources.settings_cat_api
import fr.moovie.tv.resources.settings_cat_dns
import fr.moovie.tv.data.settings.ScreensaverDelay
import fr.moovie.tv.data.settings.UpdateInterval
import fr.moovie.tv.resources.settings_autoplay
import fr.moovie.tv.resources.settings_autoplay_help
import fr.moovie.tv.resources.settings_cat_intro
import fr.moovie.tv.resources.settings_cat_screensaver
import fr.moovie.tv.resources.settings_cat_update
import fr.moovie.tv.resources.settings_screensaver_delay
import fr.moovie.tv.resources.settings_screensaver_help
import fr.moovie.tv.resources.screensaver_after_hours
import fr.moovie.tv.resources.screensaver_after_minutes
import fr.moovie.tv.resources.screensaver_never
import fr.moovie.tv.resources.settings_update_help
import fr.moovie.tv.resources.settings_update_interval
import fr.moovie.tv.resources.update_every_hours
import fr.moovie.tv.resources.update_every_minutes
import fr.moovie.tv.resources.update_never
import fr.moovie.tv.resources.settings_cat_playback
import fr.moovie.tv.resources.settings_cat_sources
import fr.moovie.tv.resources.settings_disable
import fr.moovie.tv.resources.settings_dns_help
import fr.moovie.tv.resources.settings_doh_off
import fr.moovie.tv.resources.settings_doh_on
import fr.moovie.tv.resources.settings_doh_resolver
import fr.moovie.tv.resources.settings_enable
import fr.moovie.tv.resources.settings_intro_help
import fr.moovie.tv.resources.settings_language
import fr.moovie.tv.resources.settings_move_down
import fr.moovie.tv.resources.settings_move_up
import fr.moovie.tv.resources.settings_sources_help
import fr.moovie.tv.resources.settings_stream_lang
import fr.moovie.tv.resources.settings_title
import fr.moovie.tv.resources.settings_tmdb_help
import fr.moovie.tv.resources.settings_tmdb_hint
import fr.moovie.tv.resources.settings_tmdb_key
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieSelect
import fr.moovie.tv.ui.components.MoovieIconButton
import org.jetbrains.compose.resources.stringResource

/**
 * Écran de réglages partagé TV + desktop, groupé par catégories : API (clé
 * TMDB), Lecture & Langue, Intro/générique, Réseau (DNS), Sources. État hoisté ;
 * [languageSelector] est un slot plateforme (changer la langue de l'app passe
 * par LocaleManager + redémarrage côté Android).
 */
@Composable
fun SettingsScreenContent(
    apiKey: String,
    streamLang: StreamLanguage,
    skipIntroOutro: Boolean,
    autoPlayNext: Boolean,
    updateInterval: UpdateInterval,
    screensaverDelay: ScreensaverDelay,
    dohEnabled: Boolean,
    dohProvider: DohProvider,
    providers: List<ProviderSetting>,
    onSetApiKey: (String) -> Unit,
    onSetStreamLanguage: (StreamLanguage) -> Unit,
    onSetSkipIntroOutro: (Boolean) -> Unit,
    onSetAutoPlayNext: (Boolean) -> Unit,
    onSetUpdateInterval: (UpdateInterval) -> Unit,
    onSetScreensaverDelay: (ScreensaverDelay) -> Unit,
    onSetDohEnabled: (Boolean) -> Unit,
    onSetDohProvider: (DohProvider) -> Unit,
    onToggleProvider: (name: String, enabled: Boolean) -> Unit,
    onMoveProviderUp: (String) -> Unit,
    onMoveProviderDown: (String) -> Unit,
    onBack: () -> Unit,
    languageSelector: @Composable () -> Unit,
) {
    // Focus initial sur le 1er bouton (pas le champ clé) : sinon le champ texte
    // s'auto-focalise à l'entrée et ouvre le clavier — mauvaise UX sur TV.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        // Scroll pleine page puis marges : les boutons agrandis au focus débordent
        // dans la marge au lieu d'être rognés par le conteneur défilant.
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        SettingsCategory(stringResource(Res.string.settings_cat_api)) {
            Text(stringResource(Res.string.settings_tmdb_key), style = MaterialTheme.typography.titleMedium)
            ApiKeyField(value = apiKey, onValueChange = onSetApiKey)
            Text(stringResource(Res.string.settings_tmdb_help), style = MaterialTheme.typography.bodySmall)
        }

        SettingsCategory(stringResource(Res.string.settings_cat_playback)) {
            Text(stringResource(Res.string.settings_stream_lang), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreamLanguage.entries.forEachIndexed { index, lang ->
                    MoovieButton(
                        onClick = { onSetStreamLanguage(lang) },
                        selected = lang == streamLang,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) { Text(lang.name) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.settings_language), style = MaterialTheme.typography.titleMedium)
            languageSelector()
        }

        SettingsCategory(stringResource(Res.string.settings_cat_intro)) {
            Text(stringResource(Res.string.settings_intro_help), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoovieButton(
                    onClick = { onSetSkipIntroOutro(true) },
                    selected = skipIntroOutro,
                ) { Text(stringResource(Res.string.common_enabled)) }
                MoovieButton(
                    onClick = { onSetSkipIntroOutro(false) },
                    selected = !skipIntroOutro,
                ) { Text(stringResource(Res.string.common_disabled)) }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.settings_autoplay), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.settings_autoplay_help), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoovieButton(
                    onClick = { onSetAutoPlayNext(true) },
                    selected = autoPlayNext,
                ) { Text(stringResource(Res.string.common_enabled)) }
                MoovieButton(
                    onClick = { onSetAutoPlayNext(false) },
                    selected = !autoPlayNext,
                ) { Text(stringResource(Res.string.common_disabled)) }
            }
        }

        SettingsCategory(stringResource(Res.string.settings_cat_screensaver)) {
            Text(stringResource(Res.string.settings_screensaver_help), style = MaterialTheme.typography.bodySmall)
            MoovieSelect(
                title = stringResource(Res.string.settings_screensaver_delay),
                options = ScreensaverDelay.entries.toList(),
                selected = screensaverDelay,
                label = { screensaverDelayLabel(it) },
                onSelect = onSetScreensaverDelay,
            )
        }

        SettingsCategory(stringResource(Res.string.settings_cat_update)) {
            Text(stringResource(Res.string.settings_update_help), style = MaterialTheme.typography.bodySmall)
            MoovieSelect(
                title = stringResource(Res.string.settings_update_interval),
                options = UpdateInterval.entries.toList(),
                selected = updateInterval,
                label = { updateIntervalLabel(it) },
                onSelect = onSetUpdateInterval,
            )
        }

        SettingsCategory(stringResource(Res.string.settings_cat_dns)) {
            Text(stringResource(Res.string.settings_dns_help), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoovieButton(
                    onClick = { onSetDohEnabled(true) },
                    selected = dohEnabled,
                ) { Text(stringResource(Res.string.settings_doh_on)) }
                MoovieButton(
                    onClick = { onSetDohEnabled(false) },
                    selected = !dohEnabled,
                ) { Text(stringResource(Res.string.settings_doh_off)) }
            }
            if (dohEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.settings_doh_resolver), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DohProvider.entries.forEach { provider ->
                        MoovieButton(
                            onClick = { onSetDohProvider(provider) },
                            selected = provider == dohProvider,
                        ) { Text(provider.label) }
                    }
                }
            }
        }

        SettingsCategory(stringResource(Res.string.settings_cat_sources)) {
            Text(stringResource(Res.string.settings_sources_help), style = MaterialTheme.typography.bodySmall)
            // Liste « stripped » : nom à gauche, actions icône à droite.
            providers.forEachIndexed { index, p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
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
                                onClick = { onMoveProviderUp(p.name) },
                                icon = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(Res.string.settings_move_up, p.name),
                            )
                        }
                        if (index < providers.lastIndex) {
                            MoovieIconButton(
                                onClick = { onMoveProviderDown(p.name) },
                                icon = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(Res.string.settings_move_down, p.name),
                            )
                        }
                        MoovieIconButton(
                            onClick = { onToggleProvider(p.name, !p.enabled) },
                            icon = Icons.Default.PowerSettingsNew,
                            contentDescription = if (p.enabled)
                                stringResource(Res.string.settings_disable, p.name)
                            else stringResource(Res.string.settings_enable, p.name),
                            selected = p.enabled,
                        )
                    }
                }
            }
        }

        MoovieButton(onClick = onBack) { Text(stringResource(Res.string.common_back)) }
    }
}

/** Libellé lisible d'un délai de mise en veille. */
@Composable
private fun screensaverDelayLabel(delay: ScreensaverDelay): String = when {
    delay == ScreensaverDelay.NEVER -> stringResource(Res.string.screensaver_never)
    delay.minutes < 60 -> stringResource(Res.string.screensaver_after_minutes, delay.minutes)
    else -> stringResource(Res.string.screensaver_after_hours, delay.minutes / 60)
}

/** Libellé lisible d'une fréquence de vérification. */
@Composable
private fun updateIntervalLabel(interval: UpdateInterval): String = when {
    interval == UpdateInterval.NEVER -> stringResource(Res.string.update_never)
    interval.minutes < 60 -> stringResource(Res.string.update_every_minutes, interval.minutes)
    else -> stringResource(Res.string.update_every_hours, interval.minutes / 60)
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
            Text(stringResource(Res.string.settings_tmdb_hint), color = Color(0xFF888888))
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
