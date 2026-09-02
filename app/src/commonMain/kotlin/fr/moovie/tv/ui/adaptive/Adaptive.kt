package fr.moovie.tv.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

/**
 * La largeur de la fenêtre, en points, telle qu'elle est.
 *
 * Les classes disent dans quel régime on est ; elles ne disent pas de combien
 * on dispose. Une marge de page qui vaut un dixième de la largeur a besoin du
 * nombre, pas du régime — et sans lui, chaque écran remesurait la fenêtre avec
 * son propre `BoxWithConstraints`, ce qui donnait quatre marges différentes
 * pour une même application.
 *
 * `compositionLocalOf` et non `static` : celle-ci change au redimensionnement.
 */
val LocalWindowWidth = compositionLocalOf { 960.dp }

/** La hauteur de la fenêtre, même raison : un héros plein écran a besoin du nombre. */
val LocalWindowHeight = compositionLocalOf { 540.dp }

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
 *
 * ## Elle fournit aussi la densité
 *
 * Sur téléviseur, et là seulement : la densité qu'une box déclare est un
 * réglage d'usine sans rapport avec sa dalle, et c'est ce qui faisait paraître
 * l'application grossie sur certains appareils. Voir [echelleTv]. Les classes
 * et les dimensions publiées ci-dessous sont celles de l'arbre **après**
 * correction — c'est-à-dire toujours 960 points de large sur un téléviseur.
 *
 * Poser la densité ici plutôt que dans le thème n'est pas un détail : c'est
 * l'unique endroit où l'on connaît à la fois la nature de l'appareil et la
 * place qu'il offre.
 */
@Composable
fun AdaptiveRoot(
    flavor: UiFlavor,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val densiteReelle = LocalDensity.current
        val echelle = echelleTv(flavor, maxWidth)
        // Les dimensions **logiques** : ce que l'arbre croira mesurer une fois
        // la densité corrigée. Sur tout ce qui n'est pas un téléviseur,
        // `echelle` vaut 1 et ce sont les dimensions réelles.
        val largeur = maxWidth / echelle
        val hauteur = maxHeight / echelle
        CompositionLocalProvider(
            LocalDensity provides Density(densiteReelle.density * echelle, densiteReelle.fontScale),
            LocalUiFlavor provides flavor,
            LocalWidthClass provides widthClassOf(largeur),
            LocalHeightClass provides heightClassOf(hauteur),
            LocalWindowWidth provides largeur,
            LocalWindowHeight provides hauteur,
            content = content,
        )
    }
}

/**
 * Largeur logique du téléviseur de référence.
 *
 * La Xiaomi Mi Box 4 du projet : 1920 × 1080 pixels à 320 dpi, soit 960 × 540
 * points. C'est sur elle que toutes les dimensions fixes de l'application ont
 * été relevées — la largeur d'une affiche, la hauteur de la barre, le bloc
 * d'une rangée.
 */
private val TV_REFERENCE: Dp = 960.dp

/**
 * ## Pourquoi un téléviseur a besoin d'être remis à l'échelle
 *
 * Sur un téléphone, le point est une unité **physique** : le constructeur
 * déclare une densité qui correspond à peu près à la réalité, et 48 points font
 * à peu près la même trace du pouce sur tous les appareils. C'est ce qui rend
 * les dimensions en dur légitimes.
 *
 * Sur un téléviseur, rien de tout cela n'est vrai. La densité déclarée est un
 * réglage du fabricant, sans rapport avec la taille de la dalle ni avec la
 * distance de lecture : deux téléviseurs de 55 pouces posés côte à côte
 * annoncent 960 points de large chez l'un et 640 chez l'autre — le second
 * rend simplement son interface en 1280 pixels au lieu de 1920. Une affiche de
 * 138 points y occupe alors **une fois et demie** la place prévue, et une
 * rangée qui montrait cinq titres n'en montre plus que trois.
 *
 * C'est exactement ce qu'on observait : l'accueil, calibré sur la box de
 * référence, paraissait grossi sur un téléviseur qui déclare moins de points
 * pour la même dalle.
 *
 * ## Ce que corrige cette fonction
 *
 * Plutôt que de rendre adaptative chacune des dimensions de l'application —
 * il y en a des centaines, et il en naîtra d'autres — on corrige **l'unité**.
 * La densité fournie à l'arbre est multipliée par le rapport entre la largeur
 * annoncée et celle de la référence, si bien que l'arbre mesure toujours
 * [TV_REFERENCE] points de large. Tout ce qui est exprimé en points ou en `sp`
 * suit sans le savoir, y compris le code qui n'existe pas encore.
 *
 * Sur la box de référence le rapport vaut exactement 1 : la correction est
 * alors l'identité, et ne peut rien changer à ce qui a été réglé dessus.
 *
 * ## Les bornes
 *
 * Elles ne servent pas la mise en page mais la survie : un téléviseur qui
 * annoncerait une largeur aberrante — un mode d'affichage exotique, une
 * surcharge `wm density` posée à la main — donnerait une échelle absurde, et
 * mieux vaut alors une interface un peu trop grande qu'une interface
 * illisible. Elles couvrent de 480 à 2 400 points annoncés, ce qui déborde
 * largement tout ce qui existe.
 *
 * Hors téléviseur la fonction rend 1 sans rien mesurer : sur un téléphone, un
 * point est un point, et le nombre annoncé veut dire quelque chose.
 */
internal fun echelleTv(flavor: UiFlavor, largeurReelle: Dp): Float =
    if (flavor != UiFlavor.TV || largeurReelle <= 0.dp) {
        1f
    } else {
        (largeurReelle / TV_REFERENCE).coerceIn(0.5f, 2.5f)
    }
