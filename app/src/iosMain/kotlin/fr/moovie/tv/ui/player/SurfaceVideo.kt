package fr.moovie.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
    /**
     * Comment l'image occupe la surface.
     *
     * Le lecteur garde le défaut, `ResizeAspect` : des bandes plutôt qu'une
     * image rognée — sur un film en 2.39:1 vu sur un iPhone, le remplissage
     * couperait la moitié du cadre.
     *
     * L'aperçu de bande-annonce demande l'inverse, `ResizeAspectFill` : il tient
     * la place de l'affiche de fond, et des bandes noires y trahiraient une
     * vidéo posée là au lieu d'un décor. C'est le même choix que fait le
     * `ContentScale.Crop` de l'aperçu desktop.
     */
    // `String?` et non `String` : Kotlin/Native expose les constantes
    // `AVLayerVideoGravity*` en nullable, le typedef Objective-C étant un
    // `NSString *` sans annotation de nullité. C'est aussi le type que prend
    // `videoGravity`, si bien que la valeur traverse sans déballage.
    gravite: String? = AVLayerVideoGravityResizeAspect,
) {
    // **Un seul layer pour toute la vie de la surface.**
    //
    // La version d'avant le mémorisait sous `remember(controleur, gravite)`,
    // donc en construisait un neuf au moindre changement de cadrage — c'est
    // exactement ce que fait la bande-annonce en passant au premier plan, où
    // `ResizeAspectFill` devient `ResizeAspect`. Or `factory` de [UIKitView]
    // n'est appelée qu'une fois : la [VueVideo] déjà posée continuait de porter
    // l'ancien layer, que le `onDispose` plus bas venait de retirer de sa
    // hiérarchie. Il ne restait qu'une vue sans contenu, dont le fond est noir —
    // avec le son et la position qui avançaient derrière, l'AVPlayer n'ayant
    // rien perdu. Le même symptôme que le layer de taille nulle, pour une autre
    // raison, et c'est ce que le KDoc de [VueVideo] appelle une image noire sur
    // une lecture qui va bien.
    //
    // Le layer est donc posé une fois pour toutes, et ce qui varie est réglé
    // dessus : `player` et `videoGravity` sont des propriétés de `CALayer`, les
    // écrire suffit, et aucune des deux ne demande de reconstruire quoi que ce
    // soit. Plus rien ne peut désigner un layer que la vue n'a pas.
    val couche = remember { AVPlayerLayer() }
    SideEffect {
        couche.player = controleur.player
        couche.videoGravity = gravite
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
