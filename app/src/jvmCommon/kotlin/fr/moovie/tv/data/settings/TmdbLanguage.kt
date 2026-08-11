package fr.moovie.tv.data.settings

/**
 * Code langue TMDB (métadonnées) courant. Android : dérivé de la langue de
 * l'app (LocaleManager) ; desktop : locale système.
 */
expect fun currentTmdbLanguage(): String

/**
 * Pays de l'utilisateur, déduit de sa langue TMDB (`fr-FR` → `FR`).
 *
 * Sert à choisir la classification d'âge à afficher : « -12 » et « PG-13 » ne
 * cohabitent pas, et montrer celle d'un autre pays revient à montrer un chiffre
 * dont personne ne connaît l'échelle. Repli sur la France quand le code de
 * langue n'en porte pas — l'app est francophone d'abord.
 */
fun tmdbCountry(): String =
    currentTmdbLanguage().substringAfter('-', "").takeIf { it.length == 2 }?.uppercase() ?: "FR"
