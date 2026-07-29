package fr.moovie.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fr.moovie.tv.shared.platformName

// Squelette de l'app desktop : fenêtre vide en attendant le portage des écrans
// (home/search/details/player) depuis androidMain vers commonMain.
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Moo-vie",
        state = rememberWindowState(width = 1280.dp, height = 720.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF101014)),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Moo-vie", color = Color(0xFF4CAF50), style = MaterialTheme.typography.headlineLarge)
            Text("Port desktop en cours — $platformName", color = Color.White)
        }
    }
}
