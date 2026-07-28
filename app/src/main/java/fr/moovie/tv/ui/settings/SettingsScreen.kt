package fr.moovie.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
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

    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp).verticalScroll(rememberScrollState()),
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
                    Button(onClick = { viewModel.setStreamLanguage(lang) }) {
                        Text(if (lang == streamLang) "● ${lang.name}" else lang.name)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Langue de l'interface (TMDB)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("fr-FR" to "Français", "en-US" to "English").forEach { (code, label) ->
                    Button(onClick = { viewModel.setUiLanguage(code) }) {
                        Text(if (code == uiLang) "● $label" else label)
                    }
                }
            }
        }

        Button(onClick = onBack) { Text("Retour") }
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
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}
