package fr.moovie.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import fr.moovie.tv.shared.appVersionName
import fr.moovie.tv.shared.deviceName
import fr.moovie.tv.shared.platformName
import platform.UIKit.UIViewController

/**
 * Point d'entrée iOS, appelé depuis Swift.
 *
 * Le nom est celui qu'attend `ContentView.swift` : Kotlin/Native exporte les
 * fonctions de premier niveau du framework sous le nom du fichier
 * (`MoovieViewControllerKt`), c'est ce que le côté Swift importe.
 */
fun MoovieViewController(): UIViewController = ComposeUIViewController { EcranAmorce() }

/**
 * Écran d'amorçage — **provisoire, et volontairement honnête**.
 *
 * L'application réelle vit dans `jvmCommon` : navigation, catalogues,
 * extraction, lecteur. Ce source set dépend de Retrofit, OkHttp, jsoup et JNA,
 * qui n'existent pas en Kotlin/Native ; iOS ne peut donc pas encore l'afficher.
 * Ce que cet écran prouve, et c'est tout ce qu'il prétend prouver : le framework
 * se lie, Compose rend, et les `actual` de `Platform.kt` répondent.
 *
 * Il disparaît dès que `ui/` remonte de `jvmCommon` vers `commonMain`, ce qui
 * suppose que la couche `data/` soit portée d'abord.
 */
@Composable
private fun EcranAmorce() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Moo-vie", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Portage iOS en cours — le socle partagé est en place, " +
                "les écrans arrivent avec le portage de la couche data.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text("$platformName · $deviceName", style = MaterialTheme.typography.bodySmall)
        Text("Version $appVersionName", style = MaterialTheme.typography.bodySmall)
    }
}
