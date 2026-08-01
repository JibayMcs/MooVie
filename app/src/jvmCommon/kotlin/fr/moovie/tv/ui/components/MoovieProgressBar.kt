package fr.moovie.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fr.moovie.tv.ui.theme.MoovieGradient

/**
 * Barre de progression de lecture.
 *
 * Remplace `LinearProgressIndicator` de Material 3, qui depuis la 1.3 décore la
 * barre tout seul : bouts arrondis, espace entre la partie lue et la piste, et
 * surtout un **point d'arrêt** dessiné à l'extrémité droite. Sur une barre de
 * 4 dp de haut ça se lit comme un artefact — quelques pixels de rouge à droite
 * d'une barre grise, exactement le défaut remonté sur « Reprendre la lecture ».
 *
 * Deux rectangles, aucune décoration : ce qui est lu est plein, le reste est
 * gris jusqu'au bord. Et le composant ne suivra pas les changements de style de
 * Material d'une version à l'autre.
 */
@Composable
fun MoovieProgressBar(
    /** Avancement entre 0 et 1 (les valeurs hors bornes sont ramenées dedans). */
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color(0x66FFFFFF),
) {
    Box(modifier = modifier.background(trackColor)) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                // Dégradé identité plutôt qu'un aplat : c'est le seul endroit de
                // l'app où la bannière se déploie sur une vraie longueur.
                .background(MoovieGradient),
        )
    }
}
