package fr.moovie.tv.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Nature de l'appareil qui pilote l'UI.
 *
 * **Pourquoi ce n'est pas un `expect`/`actual` comme `isPointerUi`** : téléphone
 * et Android TV partagent le **même APK**. Un `actual val` est figé à la
 * compilation, il ne peut donc pas les distinguer — il faut interroger le
 * système au démarrage et injecter le résultat dans l'arbre Compose.
 *
 * L'ancien `expect val isPointerUi` a été retiré : il valait faux sur Android,
 * donc pour la TV **comme** pour le téléphone. Juste tant que seule la TV
 * existait, faux dès qu'un second appareil est arrivé.
 */
enum class UiFlavor {
    /** Android TV : navigation à la télécommande, focus visible, recul de 3 m. */
    TV,

    /** Téléphone ou tablette : doigt, pas de focus, cibles d'au moins 48 dp. */
    TOUCH,

    /** Desktop : souris et clavier, survol disponible. */
    POINTER,
    ;

    /** Vrai quand la navigation se fait au D-pad — le focus est alors le curseur. */
    val isDpad: Boolean get() = this == TV

    /** Vrai quand l'utilisateur désigne directement (doigt ou souris). */
    val isDirect: Boolean get() = this != TV
}

/**
 * Classe de largeur disponible, au sens Material 3.
 *
 * Les seuils ne sont pas arbitraires : ils correspondent aux ruptures où une
 * mise en page à deux volets cesse de tenir. Mesures relevées sur les appareils
 * du projet :
 *
 * | Appareil                  | Largeur | Classe     |
 * |---------------------------|---------|------------|
 * | Pixel 8 Pro, portrait     | 448 dp  | [COMPACT]  |
 * | Pixel 8 Pro, paysage      | 997 dp  | [EXPANDED] |
 * | Xiaomi Mi Box 4 (1080p)   | 960 dp  | [EXPANDED] |
 *
 * Un téléphone en paysage est donc **plus large que la TV**. Ce n'est pas la
 * largeur qui casse l'app sur téléphone, c'est le portrait — et la hauteur, voir
 * [HeightClass].
 */
enum class WidthClass {
    /** < 600 dp : un seul volet, navigation par empilement. */
    COMPACT,

    /** 600–839 dp : deux volets serrés, ou un volet large. */
    MEDIUM,

    /** ≥ 840 dp : deux volets confortables — la mise en page TV actuelle. */
    EXPANDED,
    ;

    val isCompact: Boolean get() = this == COMPACT
}

/**
 * Classe de hauteur disponible.
 *
 * C'est la contrainte oubliée. Le téléphone en paysage n'offre que **448 dp** de
 * haut contre **540** sur la TV : 92 dp de moins, soit à peu près une rangée
 * d'affiches. Les écrans calibrés au dp près sur le budget vertical d'un 1080p
 * débordent donc en paysage alors que leur largeur, elle, est suffisante.
 */
enum class HeightClass {
    /** < 480 dp : téléphone en paysage. Le vertical est la ressource rare. */
    COMPACT,

    /** 480–899 dp : la TV (540 dp) et les tablettes en paysage. */
    MEDIUM,

    /** ≥ 900 dp : téléphone en portrait, tablette en portrait. */
    EXPANDED,
    ;

    val isCompact: Boolean get() = this == COMPACT
}

/**
 * Nature de l'appareil courant. Fournie une fois à la racine par chaque
 * plateforme : `MainActivity` sur Android (qui interroge `PackageManager`),
 * `Screens.kt` sur desktop (toujours [UiFlavor.POINTER]).
 *
 * `staticCompositionLocalOf` et non `compositionLocalOf` : la valeur ne change
 * jamais pendant la vie du processus, autant éviter de suivre ses lecteurs.
 */
val LocalUiFlavor = staticCompositionLocalOf { UiFlavor.POINTER }

/** Classe de largeur de la fenêtre. Recalculée à chaque rotation ou redimensionnement. */
val LocalWidthClass = staticCompositionLocalOf { WidthClass.EXPANDED }

/** Classe de hauteur de la fenêtre. Voir [HeightClass] : c'est elle qui manque en paysage. */
val LocalHeightClass = staticCompositionLocalOf { HeightClass.MEDIUM }

/** Vrai sur un appareil piloté au doigt — téléphone ou tablette. */
val isTouchUi: Boolean
    @Composable get() = LocalUiFlavor.current == UiFlavor.TOUCH

/**
 * Vrai au pointeur — desktop. Remplace l'ancien `expect val isPointerUi`, qui
 * disait la même chose mais à la compilation, donc sans pouvoir distinguer un
 * téléphone d'une TV.
 */
val isPointerUi: Boolean
    @Composable get() = LocalUiFlavor.current == UiFlavor.POINTER

/**
 * Vrai quand la navigation doit se faire au pouce, en bas de l'écran.
 *
 * Sur un téléphone tenu à une main, le haut de l'écran est hors de portée : les
 * icônes de l'en-tête, parfaites en face d'une télécommande ou d'une souris, y
 * deviennent un étirement du poignet. La barre basse est la réponse habituelle,
 * et elle vaut pour toutes les tailles tactiles — une tablette se tient aussi
 * par le bas.
 */
val useBottomNav: Boolean
    @Composable get() = isTouchUi

internal fun widthClassOf(width: Dp): WidthClass = when {
    width < 600.dp -> WidthClass.COMPACT
    width < 840.dp -> WidthClass.MEDIUM
    else -> WidthClass.EXPANDED
}

internal fun heightClassOf(height: Dp): HeightClass = when {
    height < 480.dp -> HeightClass.COMPACT
    height < 900.dp -> HeightClass.MEDIUM
    else -> HeightClass.EXPANDED
}

/**
 * Racine adaptative : mesure la fenêtre et publie [LocalWidthClass] /
 * [LocalHeightClass] pour tout l'arbre. À poser une seule fois, juste sous le
 * thème.
 *
 * Volontairement bâtie sur `BoxWithConstraints` plutôt que sur
 * `calculateWindowSizeClass` : ce dernier est une API Android qui demande une
 * `Activity`, et cet arbre est partagé avec le desktop. `BoxWithConstraints`
 * mesure la place réellement offerte aux enfants, ce qui est de toute façon la
 * bonne question — en écran partagé ou en fenêtre desktop redimensionnée, la
 * taille de l'écran ne dit rien.
 */
@Composable
fun AdaptiveRoot(
    flavor: UiFlavor,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        CompositionLocalProvider(
            LocalUiFlavor provides flavor,
            LocalWidthClass provides widthClassOf(maxWidth),
            LocalHeightClass provides heightClassOf(maxHeight),
            content = content,
        )
    }
}
