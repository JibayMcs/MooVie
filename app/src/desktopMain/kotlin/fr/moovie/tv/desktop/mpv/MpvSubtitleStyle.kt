package fr.moovie.tv.desktop.mpv

import fr.moovie.tv.core.subtitles.model.SubtitleBackdrop
import fr.moovie.tv.core.subtitles.model.SubtitleStyle
import fr.moovie.tv.core.subtitles.model.toHexColor

/**
 * Traduit un [SubtitleStyle] en propriétés mpv.
 *
 * Fonction pure, et séparée du moteur exprès : c'est la seule partie de
 * l'apparence qui se vérifie sans lecteur, sans fenêtre et sans libmpv chargée.
 * Ce qui reste dans [MpvEngine] n'est plus qu'une boucle qui pose des chaînes.
 *
 * ## Toutes les clés sont écrites à chaque fois, y compris à zéro
 *
 * mpv garde ce qu'on lui a posé. Ne renseigner que les propriétés utiles au
 * style courant laisserait le contour d'un réglage précédent sous le bandeau du
 * suivant : l'utilisateur changerait de fond et en verrait deux. On repart donc
 * d'un état complet — c'est aussi ce qui rend la table lisible d'un coup d'œil.
 *
 * ## Les couleurs sont opaques, sans alpha
 *
 * mpv accepte `#AARRGGBB`, mais les deux conventions d'alpha se rencontrent dans
 * la nature (libass compte l'inverse de tout le monde) et une valeur mal lue
 * donne un bandeau invisible ou un fond noir permanent — deux façons de croire
 * que le réglage n'a pas été enregistré. Six chiffres ne se prêtent à aucune
 * ambiguïté : la seule transparence dont on a besoin est celle du **défaut**,
 * qu'on retrouve en mettant la taille de bordure et l'ombre à zéro.
 */
fun mpvSubtitleProperties(style: SubtitleStyle): Map<String, String> = mapOf(
    // Facteur, pas taille absolue : mpv part d'une référence qui tient déjà
    // compte de la fenêtre.
    "sub-scale" to style.size.scale.toString(),
    "sub-color" to style.color.rgb.toHexColor(),
    "sub-border-size" to if (style.backdrop == SubtitleBackdrop.OUTLINE) "3" else "0",
    "sub-shadow-offset" to if (style.backdrop == SubtitleBackdrop.SHADOW) "2" else "0",
    "sub-border-color" to "#000000",
    "sub-shadow-color" to "#000000",
    // Le bandeau est la seule option qui peint derrière le texte. Ailleurs on
    // laisse mpv à son fond par défaut, qui est transparent.
    "sub-back-color" to if (style.backdrop == SubtitleBackdrop.BOX) "#000000" else "#00000000",
)
