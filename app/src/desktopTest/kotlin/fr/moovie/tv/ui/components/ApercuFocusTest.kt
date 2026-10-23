package fr.moovie.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MOOVIE_BG
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Rendu de contrôle des états d'un bouton, écrit dans MOOVIE_APERCU si posé :
 *
 * ```
 * MOOVIE_APERCU=/tmp/apercu ./gradlew :app:desktopTest --tests '*ApercuFocusTest*'
 * ```
 *
 * Le survol produit le même état « actif » que le focus télécommande. C'est le
 * survol qu'on simule, et non un `FocusRequester` : dans une `ImageComposeScene`
 * la demande de focus reste lettre morte, la scène n'ayant pas de fenêtre qui
 * le porte, et les deux rendus sortaient identiques.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ApercuFocusTest {

    @Test
    fun apercu() {
        val dossier = System.getenv("MOOVIE_APERCU") ?: return
        rendre(File(dossier, "focus_lire.png"), survol = Offset(324f, 124f))
        rendre(File(dossier, "focus_onglet.png"), survol = Offset(170f, 295f))
        rendre(File(dossier, "focus_sources.png"), survol = Offset(716f, 124f))
    }

    private fun rendre(fichier: File, survol: Offset) {
        val scene = ImageComposeScene(width = 1600, height = 400, density = Density(2f))
        scene.setContent {
            Column(
                Modifier.fillMaxSize().background(MOOVIE_BG).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoovieButton(
                        onClick = {},
                        modifier = Modifier.width(260.dp),
                        remplissage = MOOVIE_ACCENT,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                    ) { Text("LIRE") }
                    MoovieButton(onClick = {}) { Text("Sources") }
                    MoovieButton(onClick = {}, selected = true) { Text("Option retenue") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoovieButton(onClick = {}, selected = true) { Text("ÉPISODES") }
                    MoovieButton(onClick = {}) { Text("À VOIR AUSSI") }
                    MoovieButton(onClick = {}) { Text("EN SAVOIR PLUS") }
                }
            }
        }
        scene.render(0L)
        scene.sendPointerEvent(PointerEventType.Enter, survol)
        scene.sendPointerEvent(PointerEventType.Move, survol)
        scene.render(500_000_000L)
        val image = scene.render(1_500_000_000L)
        val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("png")
        fichier.writeBytes(data.bytes)
        scene.close()
    }
}
