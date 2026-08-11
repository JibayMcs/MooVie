package fr.moovie.tv.data.tmdb

/**
 * Petites règles de lecture des fiches TMDB, isolées du rendu.
 *
 * Elles ont l'air anodines et ce sont elles qui se trompent : une classification
 * prise dans le mauvais pays affiche « R » à un public français, un budget rendu
 * brut affiche « 150000000 ». Ici elles se testent sans Compose ni réseau.
 */

/**
 * Classification d'âge à afficher, pour un pays donné.
 *
 * Le pays de l'utilisateur d'abord, les États-Unis en repli : TMDB renseigne
 * presque toujours la classification américaine et bien plus rarement les
 * autres. Rendre null plutôt qu'une classification étrangère au hasard — « 15 »
 * ne veut rien dire si l'on ignore qu'il est britannique.
 */
fun ContentRatingResults?.forCountry(country: String): String? {
    val entries = this?.results.orEmpty()
    return entries.pick(country) { it.country to it.rating }
}

/** Pendant films : la classification vit dans les dates de sortie par pays. */
fun ReleaseDateResults?.forCountry(country: String): String? {
    val entries = this?.results.orEmpty()
    return entries.pick(country) { pays ->
        pays.country to pays.dates.firstOrNull { it.certification.isNotBlank() }?.certification
            .orEmpty()
    }
}

private fun <T> List<T>.pick(country: String, extract: (T) -> Pair<String, String>): String? {
    val paires = map(extract).filter { it.second.isNotBlank() }
    return paires.firstOrNull { it.first.equals(country, ignoreCase = true) }?.second
        ?: paires.firstOrNull { it.first.equals("US", ignoreCase = true) }?.second
}

/**
 * Espace fine insécable, en **échappement et non en littéral**.
 *
 * Écrite telle quelle dans le source elle est invisible, et c'est un piège
 * concret : un test l'a comparée à une espace ordinaire, et l'échec affichait
 * deux chaînes rigoureusement identiques à l'œil. En échappement, la différence
 * se voit à la relecture.
 */
private const val THIN_NBSP = '\u202F'

/**
 * Somme en dollars, lisible.
 *
 * TMDB rend un entier brut, et zéro pour « non renseigné » — les deux se lisent
 * mal tels quels. Espace fine entre les groupes, comme le veut la typographie
 * française, et non la virgule anglo-saxonne. Insécable pour que le nombre ne se
 * coupe pas en fin de ligne dans une colonne étroite.
 */
fun formatMoney(amount: Long): String? {
    if (amount <= 0) return null
    val chiffres = amount.toString()
    val groupes = StringBuilder()
    chiffres.forEachIndexed { i, c ->
        if (i > 0 && (chiffres.length - i) % 3 == 0) groupes.append(THIN_NBSP)
        groupes.append(c)
    }
    return "$groupes$THIN_NBSP$"
}

/**
 * Durée en minutes → « 2 h 17 » / « 47 min ».
 *
 * Zéro et null rendent null : une fiche sans durée doit **omettre la ligne**,
 * pas afficher « 0 min ».
 */
fun formatRuntime(minutes: Int?): String? {
    val m = minutes?.takeIf { it > 0 } ?: return null
    return if (m < 60) "$m min" else "${m / 60} h ${(m % 60).toString().padStart(2, '0')}"
}

/**
 * Date ISO `2026-08-11` → `11/08/2026`.
 *
 * Rend la chaîne telle quelle si elle n'a pas la forme attendue : mieux vaut une
 * date au format d'origine qu'une ligne vide, et TMDB rend parfois l'année seule.
 */
fun formatDate(iso: String?): String? {
    val s = iso?.takeIf { it.isNotBlank() } ?: return null
    val p = s.split('-')
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else s
}
