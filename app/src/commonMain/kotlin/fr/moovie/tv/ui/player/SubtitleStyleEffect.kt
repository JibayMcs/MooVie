package fr.moovie.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import fr.moovie.tv.core.subtitles.model.SubtitleStyle
import fr.moovie.tv.data.settings.SettingsRepository

/**
 * Tient l'apparence des sous-titres du lecteur alignée sur les réglages.
 *
 * Partagé parce que les deux écrans de lecture en ont exactement le même besoin
 * et que le contrôleur est déjà un port : ce qui diffère entre Media3 et mpv est
 * dans les adaptateurs, pas ici.
 *
 * ## Pourquoi un flux et pas une lecture unique
 *
 * Les réglages peuvent changer **pendant** que le lecteur vit. Sur téléphone
 * c'est le cas courant — on ouvre les réglages, on grossit le texte, on revient
 * ; l'activité de lecture n'a pas été détruite entre-temps. Une lecture unique
 * au montage ne rendrait le nouveau réglage qu'à la lecture suivante, ce qui se
 * lit comme un réglage qui ne marche pas.
 *
 * L'effet republie aussi le style quand le **contrôleur** change, sans quoi une
 * source rejouée par la cascade repartirait sur l'apparence par défaut.
 */
@Composable
fun ApplySubtitleStyle(controller: MooviePlayerController?) {
    val settings = remember { SettingsRepository() }
    val style by settings.subtitleStyle.collectAsState(SubtitleStyle.Default)

    LaunchedEffect(controller, style) {
        controller?.applySubtitleStyle(style)
    }
}
