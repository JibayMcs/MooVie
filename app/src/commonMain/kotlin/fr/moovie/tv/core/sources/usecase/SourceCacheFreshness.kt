package fr.moovie.tv.core.sources.usecase

/**
 * Une entrée de cache de sources est-elle encore représentative ?
 *
 * Le critère n'est pas l'âge — ça, c'est le TTL — mais la **couverture** : une
 * entrée écrite alors que trois catalogues existaient ne dit rien de ce qu'un
 * quatrième aurait rendu. La resservir revient à figer la redondance d'une
 * version antérieure sur toutes les fiches déjà visitées, exactement là où
 * l'utilisateur revient le plus.
 *
 * Un catalogue **retiré** des attentes (désactivé dans les réglages) ne périme
 * rien : l'entrée en sait alors plus que nécessaire, on la garde et la cascade
 * filtrera. Seul un catalogue attendu mais jamais interrogé invalide.
 *
 * Une entrée sans liste de catalogues vient d'une version qui ne l'écrivait pas :
 * on la considère incomplète dès qu'on attend quoi que ce soit, plutôt que de
 * parier qu'elle couvrait déjà tout.
 */
fun isCacheComplete(consulted: Collection<String>, expected: Collection<String>): Boolean {
    if (expected.isEmpty()) return true
    return consulted.toSet().containsAll(expected.toSet())
}

/**
 * Une **mesure jouable** se garde longtemps : la définition d'un fichier est une
 * propriété du fichier, elle ne bouge pas tant que le lien vit.
 */
const val MEASURE_OK_TTL_MS = 24L * 60 * 60 * 1000

/**
 * Un **verdict d'échec** se garde peu. Voir [isMeasureFresh] : ce n'est pas de la
 * prudence, c'est la seule chose qui empêche un faux négatif de devenir définitif.
 */
const val MEASURE_DEAD_TTL_MS = 20L * 60 * 1000

/**
 * Peut-on resservir une mesure de qualité déjà faite, au lieu de re-résoudre ?
 *
 * ## Pourquoi la question se pose
 *
 * Mesurer la définition oblige à résoudre l'embed puis à lire la master playlist.
 * Rien n'était gardé d'un lancement à l'autre : rouvrir une fiche de quinze
 * sources relançait quinze extractions pour retrouver des hauteurs déjà connues,
 * au moment précis où l'application doit paraître prompte.
 *
 * ## Deux péremptions, et surtout pas une
 *
 * C'est le seul choix de conception ici, et le faire symétrique serait un défaut.
 *
 * Une mesure **réussie** décrit le fichier : `1080` restera `1080`. On la garde
 * un jour ([MEASURE_OK_TTL_MS]), et c'est [version] qui la périme vraiment —
 * changer de build, c'est changer le code d'extraction, donc possiblement la
 * variante retenue.
 *
 * Un **échec** ne décrit que l'instant : le Wi-Fi, le DoH, un hébergeur qui a une
 * mauvaise minute, un `HEAD` refusé venu d'un contexte inhabituel. La sonde a des
 * faux négatifs connus et assumés — c'est pour ça qu'une source morte est grisée
 * et non masquée. Garder ce verdict un jour le rendrait **définitif à l'écran** :
 * la source resterait grise au lancement suivant, et le seul recours serait de
 * vider tout le cache des sources depuis les réglages, en supposant qu'on ait
 * deviné le rapport. D'où [MEASURE_DEAD_TTL_MS], vingt minutes : assez pour ne
 * pas re-sonder un lien mort à chaque ouverture du panneau, assez peu pour qu'un
 * incident de réseau s'oublie de lui-même.
 *
 * @param playable ce que la mesure a conclu.
 * @param version version de l'app qui a écrit la mesure. Vide = d'avant ce champ.
 */
fun isMeasureFresh(
    savedAt: Long,
    playable: Boolean,
    version: String,
    runningVersion: String,
    now: Long,
): Boolean {
    if (version.isBlank() || version != runningVersion) return false
    val age = now - savedAt
    // Une mesure datée du futur vient d'une horloge qui a reculé depuis : on ne
    // la croit pas plutôt que de la garder pour l'éternité.
    if (age < 0) return false
    return age <= if (playable) MEASURE_OK_TTL_MS else MEASURE_DEAD_TTL_MS
}
