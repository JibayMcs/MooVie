package fr.moovie.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView

/**
 * La surface où l'image apparaît.
 *
 * ## Pourquoi une vue UIKit et non un `Canvas` Compose
 *
 * Compose Multiplatform dessine dans une seule couche Skia. La vidéo, elle,
 * est décodée par le matériel et composée par le système dans un
 * `AVPlayerLayer` : elle ne traverse jamais Skia, et il n'existe pas de moyen
 * de la faire entrer dans le rendu Compose sans recopier chaque image en
 * mémoire — ce qui coûterait le décodage matériel, c'est-à-dire l'autonomie et
 * la fluidité.
 *
 * `UIKitView` découpe donc un trou dans la composition et y place une vraie
 * vue native. Tout ce que Compose dessine par-dessus — la chrome du lecteur,
 * les sous-titres, les menus — reste au-dessus dans l'ordre d'affichage.
 *
 * ## Le piège du redimensionnement
 *
 * Un `AVPlayerLayer` **ne suit pas** la taille de sa vue parente : `CALayer`
 * n'a pas d'autoresizing, et un layer posé une fois garde ses dimensions
 * initiales — souvent zéro, d'où l'écran noir classique. `onResize` est donc
 * obligatoire, pas une optimisation.
 *
 * `AVLayerVideoGravityResizeAspect` conserve les proportions en ajoutant des
 * bandes, plutôt que `ResizeAspectFill` qui rognerait : sur un film en 2.39:1
 * regardé sur un iPhone, le remplissage couperait la moitié de l'image.
 *
 * **Jamais exécuté.** À vérifier en priorité sur appareil : c'est ici que se
 * jouent le carré noir et les problèmes d'ordre d'affichage.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun SurfaceVideo(
    controleur: AvPlayerController,
    modifier: Modifier = Modifier,
) {
    val couche = remember(controleur) {
        AVPlayerLayer().apply {
            player = controleur.player
            videoGravity = AVLayerVideoGravityResizeAspect
        }
    }

    UIKitView(
        factory = {
            UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
                backgroundColor = platform.UIKit.UIColor.blackColor
                layer.addSublayer(couche)
            }
        },
        modifier = modifier,
        onResize = { vue, rect ->
            // Le layer suit la vue à la main : voir la documentation ci-dessus.
            vue.layer.setFrame(rect)
            couche.setFrame(rect)
        },
        // La vue ne reçoit aucun geste : tous les appuis — lecture, pause,
        // pincement, double-tape — sont gérés par la chrome Compose au-dessus.
        // Laisser UIKit les intercepter les lui volerait.
        interactive = false,
        accessibilityEnabled = false,
    )

    DisposableEffect(controleur) {
        onDispose {
            couche.removeFromSuperlayer()
            controleur.liberer()
        }
    }
}
