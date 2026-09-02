package fr.moovie.tv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.inter_bold
import fr.moovie.tv.resources.inter_medium
import fr.moovie.tv.resources.inter_regular
import fr.moovie.tv.resources.inter_semibold
import fr.moovie.tv.resources.outfit_bold
import fr.moovie.tv.resources.outfit_semibold
import fr.moovie.tv.ui.adaptive.HeightClass
import fr.moovie.tv.ui.adaptive.LocalHeightClass
import org.jetbrains.compose.resources.Font

/**
 * Les deux familles de Moo-vie.
 *
 * ## Pourquoi deux, et pas une
 *
 * L'app n'en embarquait aucune : elle héritait de celle du système, donc Roboto
 * sur un téléviseur, San Francisco sur un iPhone, et ce que la distribution
 * avait installé sur un bureau. Trois appareils, trois dessins, aucune
 * identité — et sur la fiche de détails, un titre en pleine image dont la forme
 * changeait d'un écran à l'autre.
 *
 * [Outfit] porte les titres. C'est une géométrique aux formes ouvertes, dessinée
 * pour être grande : le titre d'un film est le seul texte de l'app qu'on lit à
 * trois mètres sans le chercher, et il a besoin d'un dessin qui tienne à cette
 * taille sans devenir une bouillie de contreformes.
 *
 * [Inter] porte tout le reste. Elle est faite pour l'écran — hauteur d'x
 * généreuse, chiffres qui ne se confondent pas, terminaisons franches à petite
 * taille. Un synopsis, une durée, un libellé de réglage : ce sont des textes
 * qu'on lit, pas qu'on regarde, et une police d'affichage y fatiguerait.
 *
 * Le contraste entre les deux fait la moitié du travail. Un titre en Outfit
 * posé sur un synopsis en Inter dit lequel des deux est le sujet, sans qu'on
 * ait eu besoin de doubler sa taille.
 *
 * ## Statiques, pas variables
 *
 * Google ne publie plus que les versions variables de ces deux familles, et une
 * police variable demande de choisir son axe à l'exécution — ce que les
 * ressources Compose n'exposent pas de façon portable. Les instances statiques
 * viennent donc des dépôts d'origine (rsms/inter, Outfitio/Outfit-Fonts), sous
 * licence OFL, dont le texte est embarqué à côté des fichiers. Elles pèsent
 * plus lourd et ne se discutent pas à l'exécution : sur une box de 2018, c'est
 * le bon échange.
 */
val Outfit: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.outfit_semibold, FontWeight.SemiBold),
        Font(Res.font.outfit_bold, FontWeight.Bold),
    )

val Inter: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )

/**
 * L'échelle typographique, **et elle se resserre sur les petits écrans**.
 *
 * ## Le facteur
 *
 * Un téléviseur 1080p ne fait que 540 dp de haut, une fenêtre de bureau plus du
 * double. Une échelle unique donne donc, sur la télé, des titres qui prennent le
 * quart de l'écran et des paragraphes qui débordent — c'est le défaut qu'on a
 * corrigé à la main sur la fiche de détails, un `if` à la fois. Ici il est
 * corrigé une fois, à la racine, et toutes les pages en héritent.
 *
 * Le facteur est doux — 0,88 — et ne s'applique qu'aux grandes tailles. Les
 * petites ne se réduisent pas : sous 14 sp, un texte lu à trois mètres cesse
 * d'être un texte.
 *
 * ## Les interlignes
 *
 * Exprimés en `em` et non en `sp` : ils suivent alors la taille au lieu d'être
 * recalculés à chaque palier. Un titre serré (1,1) et un paragraphe aéré (1,5),
 * c'est tout ce qu'il y a à retenir.
 */
@Composable
fun moovieTypography(): Typography {
    val titre = Outfit
    val texte = Inter
    // La classe de hauteur dit ce que l'appareil offre en vertical. Voir
    // `HeightClass` : le téléviseur et le téléphone en paysage y tombent
    // ensemble, et ce sont les deux écrans où la place manque.
    val compact = LocalHeightClass.current != HeightClass.EXPANDED
    fun taille(grande: Float): Float = if (compact) grande * 0.88f else grande

    return Typography(
        // ── Affichage : le titre d'une fiche, rien d'autre ────────────────
        displayLarge = TextStyle(
            fontFamily = titre,
            fontWeight = FontWeight.Bold,
            fontSize = taille(57f).sp,
            lineHeight = 1.08.em,
            letterSpacing = (-0.02).em,
        ),
        displayMedium = TextStyle(
            fontFamily = titre,
            fontWeight = FontWeight.Bold,
            fontSize = taille(45f).sp,
            lineHeight = 1.1.em,
            // Le resserrement est ce qui distingue un titre d'un gros texte :
            // aux grandes tailles, l'espacement par défaut fait flotter les
            // lettres. Négatif ici, nul plus bas, positif pour les capitales.
            letterSpacing = (-0.02).em,
        ),
        displaySmall = TextStyle(
            fontFamily = titre,
            fontWeight = FontWeight.Bold,
            fontSize = taille(36f).sp,
            lineHeight = 1.12.em,
            letterSpacing = (-0.015).em,
        ),

        // ── En-têtes : titres de page et de section ───────────────────────
        headlineLarge = TextStyle(
            fontFamily = titre,
            fontWeight = FontWeight.Bold,
            fontSize = taille(32f).sp,
            lineHeight = 1.15.em,
            letterSpacing = (-0.015).em,
        ),
        headlineMedium = TextStyle(
            fontFamily = titre,
            fontWeight = FontWeight.SemiBold,
            fontSize = taille(28f).sp,
            lineHeight = 1.18.em,
            letterSpacing = (-0.01).em,
        ),
        headlineSmall = TextStyle(
            fontFamily = titre,
            fontWeight = FontWeight.SemiBold,
            fontSize = taille(24f).sp,
            lineHeight = 1.2.em,
            letterSpacing = (-0.01).em,
        ),

        // ── Titres : rangées, cartes, blocs ───────────────────────────────
        //
        // Outfit encore, mais à ces tailles elle ne crie plus : c'est ce qui
        // fait qu'un titre de rangée se lit comme un titre et non comme un
        // paragraphe en gras.
        titleLarge = TextStyle(
            fontFamily = titre,
            fontWeight = FontWeight.SemiBold,
            fontSize = taille(22f).sp,
            lineHeight = 1.25.em,
        ),
        titleMedium = TextStyle(
            fontFamily = titre,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17f.sp,
            lineHeight = 1.3.em,
        ),
        titleSmall = TextStyle(
            fontFamily = titre,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15f.sp,
            lineHeight = 1.3.em,
        ),

        // ── Corps : tout ce qui se lit vraiment ───────────────────────────
        bodyLarge = TextStyle(
            fontFamily = texte,
            fontWeight = FontWeight.Normal,
            fontSize = 17f.sp,
            lineHeight = 1.5.em,
        ),
        bodyMedium = TextStyle(
            fontFamily = texte,
            fontWeight = FontWeight.Normal,
            fontSize = 15f.sp,
            lineHeight = 1.5.em,
        ),
        bodySmall = TextStyle(
            fontFamily = texte,
            fontWeight = FontWeight.Normal,
            fontSize = 13f.sp,
            lineHeight = 1.45.em,
        ),

        // ── Étiquettes : boutons, onglets, méta ───────────────────────────
        //
        // L'espacement est **positif** ici. Ces libellés sont courts, souvent
        // en capitales, et des capitales serrées se lisent comme un bloc :
        // c'est l'écart entre les lettres qui les redécoupe en mots.
        labelLarge = TextStyle(
            fontFamily = texte,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14f.sp,
            lineHeight = 1.3.em,
            letterSpacing = 0.02.em,
        ),
        labelMedium = TextStyle(
            fontFamily = texte,
            fontWeight = FontWeight.Medium,
            fontSize = 12f.sp,
            lineHeight = 1.3.em,
            letterSpacing = 0.03.em,
        ),
        labelSmall = TextStyle(
            fontFamily = texte,
            fontWeight = FontWeight.Medium,
            fontSize = 11f.sp,
            lineHeight = 1.3.em,
            letterSpacing = 0.04.em,
        ),
    )
}
