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
fun hlsHeight(playlist: String): Int? =
    RESOLUTION.findAll(playlist)
        .mapNotNull { it.groupValues[2].toIntOrNull() }
        .maxOrNull()

private val RESOLUTION = Regex("""RESOLUTION=(\d+)x(\d+)""")

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
