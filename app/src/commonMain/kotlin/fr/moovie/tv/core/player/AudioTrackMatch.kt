package fr.moovie.tv.core.player

/**
 * Retrouve, dans les pistes d'un nouvel épisode, celle qui correspond au choix
 * mémorisé sur l'épisode précédent.
 *
 * On ne peut pas mémoriser l'identifiant de piste : il est **propre au flux**
 * — « groupe:index » côté Media3, numéro interne côté libVLC — et le rejouer
 * d'un épisode à l'autre désignerait une piste au hasard. C'est donc le
 * *libellé* qu'on retient, et qu'il faut réapparier.
 *
 * D'où un appariement tolérant : un même doublage s'annonce « French », « fr »,
 * « VF » ou « Français » selon l'encodeur, et parfois « French AC3 5.1 » sur un
 * épisode et « French » sur le suivant. Trois passes, de la plus stricte à la
 * plus permissive :
 *
 *  1. égalité une fois normalisé (casse, accents, ponctuation) ;
 *  2. l'un contient l'autre — couvre les suffixes techniques ;
 *  3. même préfixe de deux lettres — rattrape « fr » contre « français ».
 *
 * @return le libellé retenu parmi [available], ou null si rien ne correspond —
 *   auquel cas l'appelant ne touche à rien plutôt que d'imposer un choix faux.
 */
fun matchAudioTrack(remembered: String, available: List<String>): String? {
    val target = normalize(remembered)
    if (target.isEmpty()) return null

    val pairs = available.map { it to normalize(it) }.filter { it.second.isNotEmpty() }

    pairs.firstOrNull { it.second == target }?.let { return it.first }
    pairs.firstOrNull { it.second.contains(target) || target.contains(it.second) }?.let { return it.first }
    return pairs.firstOrNull { it.second.take(2) == target.take(2) && target.length >= 2 }?.first
}

/** Minuscules, sans accents ni ponctuation : « Français (VF) » et « francais » se rejoignent. */
private fun normalize(label: String): String = label
    .lowercase()
    .map { ACCENTS[it] ?: it }
    .filter { it.isLetterOrDigit() }
    .joinToString("")

private val ACCENTS = mapOf(
    'à' to 'a', 'â' to 'a', 'ä' to 'a',
    'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
    'î' to 'i', 'ï' to 'i',
    'ô' to 'o', 'ö' to 'o',
    'ù' to 'u', 'û' to 'u', 'ü' to 'u',
    'ç' to 'c',
)
