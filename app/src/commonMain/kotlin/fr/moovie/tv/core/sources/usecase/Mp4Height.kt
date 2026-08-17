package fr.moovie.tv.core.sources.usecase

/**
 * Hauteur d'image annoncée par l'en-tête d'un MP4, ou null.
 *
 * ## Pourquoi lire un conteneur à la main
 *
 * Aucun catalogue n'annonce la définition, et `RESOLUTION=` n'existe que dans une
 * master playlist HLS. Les sources **MP4** — swiftflow, uqload, doodstream —
 * n'avaient donc jamais de qualité : ni affichée, ni utilisable pour trier. Elles
 * tombaient au pivot des 720, ce qui rangeait un 575p au-dessus d'un vrai 720p et
 * un 1080p en dessous. C'est la moitié du classement qui reposait sur rien.
 *
 * Mesuré avant d'écrire ceci (`Mp4HeightProbeTest`) : sur les sources MP4
 * rencontrées, **toutes** portaient leur `moov` dans les premiers kilo-octets et
 * **aucune** n'a refusé une requête par plage. La lecture d'en-tête est donc un
 * aller-retour de quelques centaines de kilo-octets, pas un téléchargement.
 *
 * ## Ce qu'on lit, et pourquoi à rebours
 *
 * Un MP4 est un empilement de boîtes `[taille:4][type:4][charge]`. La piste vidéo
 * porte un `tkhd` dont les **huit derniers octets** sont largeur et hauteur, en
 * virgule fixe 16.16 — donc la partie entière est le premier demi-mot.
 *
 * On lit ces huit octets — largeur **et** hauteur — depuis la **fin** de la boîte
 * plutôt que depuis son début : `tkhd` existe en version 0 et 1, dont les champs
 * de tête n'ont pas la même taille (dates sur 32 ou 64 bits). Compter depuis la
 * fin rend les deux versions identiques, et évite une branche qui ne se
 * testerait qu'à moitié.
 *
 * La valeur rendue est une **classe** de définition, pas la hauteur brute : voir
 * [nominalHeight]. Une copie en 2,40:1 mesure 1920×800, et sa hauteur seule la
 * ferait passer pour du 720p.
 *
 * ## Ce qui est délibérément ignoré
 *
 * - **La matrice de transformation**, qui peut porter une rotation de 90° et
 *   donc échanger largeur et hauteur. Un fichier tourné rendrait ici sa largeur.
 *   Le cas existe sur des captures de téléphone, pas sur des copies de films —
 *   et le prix d'une erreur est un libellé faux, pas une lecture cassée.
 * - **Les pistes multiples** : on retient la plus grande hauteur non nulle
 *   rencontrée. Une piste audio porte un `tkhd` à zéro, une piste de sous-titres
 *   aussi ; prendre le maximum les écarte sans avoir à identifier les types.
 */
fun mp4Height(header: ByteArray): Int? {
    var best: Int? = null
    var i = 4 // il faut 4 octets de taille avant le type

    while (i <= header.size - 8) {
        if (!matchesAt(header, i, TKHD)) {
            i++
            continue
        }
        val boxStart = i - 4
        val size = readInt32(header, boxStart)
        val end = boxStart + size
        // Une taille aberrante — 0, négative, ou qui sort du fragment lu —
        // signale qu'on est tombé sur les quatre lettres « tkhd » dans des
        // données, pas sur une boîte. On passe.
        if (size in MIN_TKHD..header.size && end <= header.size) {
            // Les **huit** derniers octets, pas les quatre : largeur puis
            // hauteur. Sans la largeur, une copie en 2,40:1 se déclare à sa
            // hauteur rognée et perd une classe entière — voir [nominalHeight].
            val width = readInt32(header, end - 8) ushr 16
            val height = readInt32(header, end - 4) ushr 16
            if (height in 1..MAX_HEIGHT) {
                val nominal = nominalHeight(width.takeIf { it in 1..MAX_WIDTH }, height)
                if (best == null || nominal > best!!) best = nominal
            }
        }
        i++
    }
    return best
}

/**
 * Vrai si l'en-tête lu contient de quoi conclure — c'est-à-dire un `moov`.
 *
 * Sert à distinguer « ce fichier est en 480p » de « le `moov` est à la fin du
 * fichier et on n'a lu que le début ». Sans cette nuance, un MP4 non
 * « faststart » passerait pour une source sans définition, exactement comme
 * avant, sans qu'on sache que la mesure n'a simplement pas eu lieu.
 */
fun mp4HeaderComplete(header: ByteArray): Boolean {
    var i = 0
    while (i <= header.size - 4) {
        if (matchesAt(header, i, MOOV)) return true
        i++
    }
    return false
}

/** `tkhd` version 0 fait 92 octets ; rien de plus petit n'est une vraie boîte. */
private const val MIN_TKHD = 84

/** 8K. Au-delà, c'est qu'on lit autre chose qu'une définition. */
private const val MAX_HEIGHT = 4320

/** Idem en largeur : 8K fait 7680 de large. */
private const val MAX_WIDTH = 7680

private val TKHD = byteArrayOf(0x74, 0x6B, 0x68, 0x64) // "tkhd"
private val MOOV = byteArrayOf(0x6D, 0x6F, 0x6F, 0x76) // "moov"

private fun matchesAt(data: ByteArray, at: Int, pattern: ByteArray): Boolean {
    if (at + pattern.size > data.size) return false
    for (k in pattern.indices) if (data[at + k] != pattern[k]) return false
    return true
}

/** Entier 32 bits gros-boutiste, la seule convention des boîtes MP4. */
private fun readInt32(data: ByteArray, at: Int): Int =
    ((data[at].toInt() and 0xFF) shl 24) or
        ((data[at + 1].toInt() and 0xFF) shl 16) or
        ((data[at + 2].toInt() and 0xFF) shl 8) or
        (data[at + 3].toInt() and 0xFF)
