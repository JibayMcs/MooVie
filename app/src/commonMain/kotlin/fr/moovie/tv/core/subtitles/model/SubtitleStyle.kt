package fr.moovie.tv.core.subtitles.model

/**
 * Apparence des sous-titres, telle que l'utilisateur la règle.
 *
 * ## Pourquoi ce réglage existe, et pourquoi il pèse plus ici qu'ailleurs
 *
 * Un sous-titre se lit à trois mètres, sur un écran qu'on ne choisit pas, dans
 * une pièce qu'on n'éclaire pas pour lire. Netflix, Plex et Jellyfin proposent
 * tous ce réglage ; ici il compte davantage, parce que **le fichier OpenSubtitles
 * est souvent le seul texte lisible du flux** — nos hébergeurs servent du
 * mono-piste sans sous-titre incrusté, la piste externe n'a donc pas de repli.
 *
 * ## Ce que le modèle décrit, et ce qu'il ne décrit pas
 *
 * Trois axes, pas davantage. Chacun se règle avec quatre appuis sur une
 * télécommande, et chacun répond à une gêne réelle : le texte est trop petit,
 * il se perd sur une scène claire, il n'accroche pas l'œil.
 *
 * Ce qui est **volontairement absent** :
 *
 * - **la police**, parce qu'aucune n'est garantie des deux côtés — Android
 *   n'embarque pas les mêmes familles qu'une machine de bureau, et mpv ne rend
 *   que ce que le système lui donne. Un réglage dont la valeur change de sens
 *   selon l'appareil n'est pas un réglage ;
 * - **l'opacité fine et la palette complète**, qui demandent un sélecteur de
 *   couleur. Sur une télécommande, c'est un formulaire à faire au pavé
 *   directionnel pour un gain que deux couleurs bien choisies couvrent déjà.
 *
 * Le modèle est **pur et partagé** : Android l'applique à la `SubtitleView`
 * d'ExoPlayer, le desktop à des propriétés mpv. Les deux traductions se testent
 * sans lecteur, ce qui est tout l'intérêt de ne pas décrire l'apparence en
 * termes de l'une ou l'autre.
 */
data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.NORMAL,
    val color: SubtitleColor = SubtitleColor.WHITE,
    val backdrop: SubtitleBackdrop = SubtitleBackdrop.OUTLINE,
) {
    companion object {
        /** Ce que voit quelqu'un qui n'a jamais ouvert ces réglages. */
        val Default = SubtitleStyle()
    }
}

/**
 * Taille du texte, en **facteur** de la taille par défaut du lecteur.
 *
 * Un facteur et non une taille en points : les deux lecteurs partent d'une
 * référence qui tient déjà compte de la définition de l'écran, et la remplacer
 * par une valeur absolue reviendrait à recalculer nous-mêmes ce qu'ils font
 * mieux. Une taille fixe calibrée sur un 1080p serait minuscule en 4K.
 */
enum class SubtitleSize(val scale: Float) {
    SMALL(0.8f),
    NORMAL(1.0f),
    LARGE(1.3f),

    /**
     * Pensé pour une vraie gêne de vue, pas pour l'effet : à ce facteur une
     * réplique longue prend deux lignes de plus, ce qui est le compromis
     * accepté.
     */
    HUGE(1.6f),
}

/**
 * Couleur du texte, en RGB sans alpha (`0xRRGGBB`).
 *
 * Deux valeurs seulement. Le blanc est la convention ; le jaune est le vieux
 * réglage de sous-titrage télévisé, et il n'est pas là par nostalgie : il se
 * détache d'un décor clair là où le blanc s'y dissout, ce qui est précisément
 * le cas que le fond ne règle pas toujours.
 */
enum class SubtitleColor(val rgb: Int) {
    WHITE(0xFFFFFF),
    YELLOW(0xF2C200),
}

/**
 * Ce qui sépare le texte de l'image.
 *
 * [OUTLINE] par défaut, et non [NONE] : sans séparation le texte disparaît dès
 * qu'une scène claire passe derrière, et l'utilisateur n'a aucune raison de
 * soupçonner qu'un réglage existe pour ça. [BOX] est le recours pour les
 * sources très contrastées — il gêne l'image, mais on le choisit en connaissance
 * de cause.
 */
enum class SubtitleBackdrop {
    /** Rien. Pour qui trouve le reste salissant, sur une source sombre. */
    NONE,

    /** Liseré noir autour des lettres. Le défaut. */
    OUTLINE,

    /** Ombre portée : plus discrète qu'un contour, moins sûre sur du clair. */
    SHADOW,

    /** Bandeau noir derrière le texte. Le plus lisible, le plus intrusif. */
    BOX,
}

/**
 * `0xRRGGBB` → `"#RRGGBB"`, la forme qu'attend mpv.
 *
 * Le remplissage à six chiffres n'est pas cosmétique : mpv lit `#F2C200` mais
 * refuse `#f2c2` sans dire pourquoi, et une couleur silencieusement ignorée
 * ressemble à un réglage qui n'a pas été enregistré.
 */
fun Int.toHexColor(): String = "#" + toString(16).uppercase().padStart(6, '0')

/** `0xRRGGBB` → ARGB opaque, la forme qu'attend Android. */
fun Int.toOpaqueArgb(): Int = this or (0xFF shl 24)
