package fr.moovie.tv.data.settings

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/**
 * La langue de l'appareil, au format que TMDB attend (`fr-FR`).
 *
 * `NSLocale.currentLocale` suit les réglages du système, ce qui est le pendant
 * exact du `Locale.getDefault()` des cibles JVM : sur iOS, la langue d'une app
 * se change dans Réglages, et l'app n'a rien à faire pour l'apprendre.
 *
 * Le code pays est recollé parce que TMDB s'en sert pour la classification
 * d'âge — voir `tmdbCountry`. Sans lui, repli sur la France, l'app étant
 * francophone d'abord.
 */
actual fun currentTmdbLanguage(): String {
    val locale = NSLocale.currentLocale
    val langue = locale.languageCode.takeIf { it.isNotBlank() } ?: "fr"
    val pays = locale.countryCode?.takeIf { it.length == 2 } ?: "FR"
    return "$langue-$pays"
}
