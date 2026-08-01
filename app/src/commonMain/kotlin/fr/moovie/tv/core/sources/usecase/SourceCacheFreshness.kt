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
