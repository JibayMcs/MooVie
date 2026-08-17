package fr.moovie.tv.core.sources.usecase

/**
 * Hauteur de l'image annoncée par une master playlist HLS, ou null.
 *
 * Aucun catalogue n'annonce la qualité au moment de lister ses liens : le
 * `RESOLUTION=` de la playlist est la seule donnée fiable. C'est aussi pour ça
 * qu'elle coûte cher — il faut d'abord résoudre le lien.
 *
 * On retient la **plus haute** variante : un flux adaptatif en liste plusieurs,
 * et c'est celle que le lecteur choisira sur une bonne connexion.
 */
fun hlsHeight(playlist: String): Int? = hlsHeights(playlist).firstOrNull()

/**
 * **Toutes** les définitions annoncées, de la plus haute à la plus basse et sans
 * doublon.
 *
 * C'est la liste que le menu « Qualité » propose. [hlsHeight] n'en garde que la
 * première parce qu'il répond à une autre question — « que vaut cette source ? »
 * — et qu'un flux adaptatif est aussi bon que sa meilleure variante.
 *
 * Dédoublonnée sur la hauteur : une master playlist liste souvent la même
 * définition à plusieurs débits, ce qui donnerait trois lignes « 1080p »
 * impossibles à départager à l'écran.
 */
fun hlsHeights(playlist: String): List<Int> =
    RESOLUTION.findAll(playlist)
        .mapNotNull { m ->
            val largeur = m.groupValues[1].toIntOrNull()
            val hauteur = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            nominalHeight(largeur, hauteur)
        }
        .filter { it > 0 }
        .distinct()
        .sortedDescending()
        .toList()

private val RESOLUTION = Regex("""RESOLUTION=(\d+)x(\d+)""")

/**
 * Définition **de classe**, en hauteur 16:9 équivalente.
 *
 * ## Le défaut que ça corrige
 *
 * Un film large ne remplit pas un 16:9 : *Reacher* est en 2,00:1, donc une copie
 * pleine largeur mesure 1920×**960**. Classer sur la hauteur seule la rangeait
 * en « 720p » — pour une source qui a exactement autant de pixels par ligne
 * qu'un 1080p. Mesuré sur S2E6 : uqload servait du 1920×960 étiqueté 720p, et
 * vidzy du 864×432 étiqueté 360p là où c'est du 480p. **Toute source large était
 * sous-évaluée**, d'autant plus qu'elle était cinémascope.
 *
 * L'ancien contournement — arrondir au palier inférieur — traitait le symptôme
 * (« 536 n'est pas un chiffre rond ») en aggravant la cause : il rabotait vers
 * le bas ce qui était déjà mesuré trop bas.
 *
 * ## Pourquoi la largeur, et pourquoi quand même le maximum
 *
 * La largeur ne dépend pas du format d'image : 1920 est du 1080p qu'on soit en
 * 16:9 ou en 2,40:1. C'est donc elle qui porte la classe. On garde néanmoins le
 * **plus grand** des deux estimateurs, pour le cas inverse : un 1440×1080 en 4:3
 * est bien du 1080p, et sa largeur seule le dirait « 810p ».
 *
 * Largeur inconnue — un `tkhd` illisible, une playlist sans les deux nombres —
 * et on retombe exactement sur l'ancien comportement, c'est-à-dire la hauteur.
 */
fun nominalHeight(width: Int?, height: Int): Int {
    val parLargeur = width?.takeIf { it > 0 }?.let { it * 9 / 16 } ?: 0
    return maxOf(parLargeur, height)
}

/**
 * Libellé lisible d'une hauteur d'image.
 *
 * Arrondi au palier connu le plus proche par le bas : les hébergeurs servent des
 * hauteurs bâtardes (536, 544, 816…) parce qu'ils préservent le ratio d'origine
 * au lieu de remplir un 16:9. Afficher « 536p » n'apprend rien à personne ;
 * « 480p » situe tout de suite la source.
 */
fun qualityLabel(height: Int?): String? {
    val h = height?.takeIf { it > 0 } ?: return null
    val palier = TIERS.lastOrNull { h >= it.first } ?: return null
    return palier.second
}

/**
 * Paliers usuels, du plus bas au plus haut. Les seuils sont volontairement sous
 * la valeur nominale : une copie « 720p » recadrée fait souvent 692 ou 700.
 */
private val TIERS = listOf(
    240 to "240p",
    340 to "360p",
    460 to "480p",
    680 to "720p",
    1000 to "1080p",
    1900 to "4K",
)
