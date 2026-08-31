package fr.moovie.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * La vue native qui porte l'image.
 *
 * ## Pourquoi une sous-classe, et non un `UIView` nu
 *
 * Un `AVPlayerLayer` **ne suit pas** la taille de la vue qui l'héberge :
 * `CALayer` n'a pas d'autoresizing sur iOS, et un layer posé une fois garde le
 * cadre qu'il avait — celui d'un `AVPlayerLayer` neuf étant `CGRectZero`. Un
 * layer de taille nulle ne dessine rien, mais la lecture continue : image noire,
 * son présent, position qui avance. C'est exactement le défaut qu'a montré le
 * premier essai sur iPhone.
 *
 * La version d'avant confiait le recadrage à `onResize` de [UIKitView]. Ce
 * paramètre appartient à l'overload **déprécié**, que Compose Multiplatform 1.7
 * réimplémente par-dessus sa nouvelle API — et qui ne le rappelle plus. Le layer
 * n'était donc jamais dimensionné.
 *
 * `layoutSubviews` ne dépend d'aucune de ces deux API : c'est UIKit qui
 * l'appelle, à chaque fois que la vue change de taille, quel que soit ce qui l'a
 * redimensionnée. La vue devient responsable de son propre contenu, ce qui est
 * la façon normale de tenir un `CALayer` en place.
 *
 * `bounds` et non `frame` : le sous-layer se place dans le système de
 * coordonnées **de sa vue**, dont l'origine est toujours (0, 0). Utiliser le
 * `frame` — la position de la vue chez son parent — décalerait l'image de sa
 * propre position.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class VueVideo(
    private val couche: AVPlayerLayer,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(couche)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        couche.setFrame(bounds)
    }
}

/**
 * La surface où l'image apparaît.
 *
 * ## Pourquoi une vue UIKit et non un `Canvas` Compose
 *
 * Compose Multiplatform dessine dans une seule couche Skia. La vidéo, elle, est
 * décodée par le matériel et composée par le système dans un `AVPlayerLayer` :
 * elle ne traverse jamais Skia, et il n'existe pas de moyen de la faire entrer
 * dans le rendu Compose sans recopier chaque image en mémoire — ce qui coûterait
 * le décodage matériel, c'est-à-dire l'autonomie et la fluidité.
 *
 * [UIKitView] découpe donc un trou dans la composition et y place une vraie vue
 * native. Tout ce que Compose dessine par-dessus — la chrome du lecteur, les
 * sous-titres, les menus — reste au-dessus dans l'ordre d'affichage.
 *
 * `AVLayerVideoGravityResizeAspect` conserve les proportions en ajoutant des
 * bandes, plutôt que `ResizeAspectFill` qui rognerait : sur un film en 2.39:1
 * regardé sur un iPhone, le remplissage couperait la moitié de l'image.
 *
 * Le dimensionnement du layer est tenu par [VueVideo] — voir son KDoc, qui
 * explique pourquoi il ne peut pas l'être ici.
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
        factory = { VueVideo(couche) },
        modifier = modifier,
        // La vue ne reçoit aucun geste : tous les appuis — lecture, pause,
        // pincement, double-tape — sont gérés par la chrome Compose au-dessus.
        // Laisser UIKit les intercepter les lui volerait.
        interactive = false,
        accessibilityEnabled = false,
    )

    // **Cette surface ne libère pas le contrôleur : elle ne l'a pas construit.**
    //
    // Elle le faisait, du temps où rien ne l'appelait. Depuis que
    // `MoovieViewController` construit l'`AvPlayerController` — pour que sa vie
    // soit exactement celle de l'entrée de navigation, et qu'un enchaînement
    // d'épisodes en ouvre bien un neuf — la libération lui appartient. La garder
    // ici la faisait arriver deux fois, et à un moment que cette surface ne
    // décide pas : une recomposition qui la démonte arrêterait la lecture
    // derrière un écran resté vivant.
    //
    // Le layer, lui, est bien à elle : elle le retire.
    DisposableEffect(couche) {
        onDispose { couche.removeFromSuperlayer() }
    }
}
