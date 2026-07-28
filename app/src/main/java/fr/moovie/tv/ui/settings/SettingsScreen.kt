package fr.moovie.tv.ui.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape as RoundedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import fr.moovie.tv.data.settings.StreamLanguage

/**
 * Écran de réglages, groupé par catégories. V1 : API (clé TMDB), Lecture
 * (langue de stream), Langue d'interface. À étendre (Sources & hébergeurs,
 * Interface, Données) au fil du portage.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val apiKey by viewModel.tmdbApiKey.collectAsStateWithLifecycle()
    val streamLang by viewModel.streamLanguage.collectAsStateWithLifecycle()
    val uiLang by viewModel.uiLanguage.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()

    Column(
        // Scroll pleine page puis marges : les boutons agrandis au focus débordent
        // dans la marge au lieu d'être rognés par le conteneur défilant.
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineMedium)

        SettingsCategory("API & Clés") {
            Text("Clé API TMDB", style = MaterialTheme.typography.titleMedium)
            ApiKeyField(value = apiKey, onValueChange = viewModel::setTmdbApiKey)
            Text(
                "Crée une clé gratuite sur themoviedb.org (API v3).",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SettingsCategory("Lecture & Langue") {
            Text("Langue du stream par défaut", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreamLanguage.entries.forEach { lang ->
                    MoovieButton(
                        onClick = { viewModel.setStreamLanguage(lang) },
                        selected = lang == streamLang,
                    ) { Text(lang.name) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Langue de l'interface (TMDB)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("fr-FR" to "Français", "en-US" to "English").forEach { (code, label) ->
                    MoovieButton(
                        onClick = { viewModel.setUiLanguage(code) },
                        selected = code == uiLang,
                    ) { Text(label) }
                }
            }
        }

        SettingsCategory("Sources & hébergeurs") {
            Text(
                "Ordre = priorité de lecture (le premier est essayé d'abord).",
                style = MaterialTheme.typography.bodySmall,
            )
            // Liste « stripped » : nom à gauche, actions icône à droite
            // (monter / descendre / activer-désactiver).
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
                                contentDescription = "Monter ${p.name}",
                            )
                        }
                        if (index < providers.lastIndex) {
                            MoovieIconButton(
                                onClick = { viewModel.moveProviderDown(p.name) },
                                icon = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Descendre ${p.name}",
                            )
                        }
                        MoovieIconButton(
                            onClick = { viewModel.toggleProvider(p.name, !p.enabled) },
                            icon = Icons.Default.PowerSettingsNew,
                            contentDescription = if (p.enabled) "Désactiver ${p.name}" else "Activer ${p.name}",
                            selected = p.enabled,
                        )
                    }
                }
            }
        }

        MoovieButton(onClick = onBack) { Text("Retour") }
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
            Text("Colle ta clé TMDB ici…", color = Color(0xFF888888))
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
