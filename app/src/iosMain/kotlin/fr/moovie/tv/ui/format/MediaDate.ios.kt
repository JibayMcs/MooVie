package fr.moovie.tv.ui.format

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * `NSDateFormatter` est l'exact analogue de `DateTimeFormatter` : il porte les
 * motifs de date de chaque langue et suit les réglages de l'appareil, sans
 * qu'on ait à lui dire quoi que ce soit de la locale.
 *
 * Les formateurs sont conservés : `NSDateFormatter` est notoirement coûteux à
 * construire — c'est le premier conseil de perfomance d'Apple à son sujet — et
 * une liste d'épisodes en demande un par ligne.
 */
private val dateCourte: NSDateFormatter by lazy {
    NSDateFormatter().apply {
        // Le motif localisé de la locale courante, puis l'année forcée sur
        // quatre chiffres — même correction que côté JVM, et pour la même
        // raison : le format court américain donne sinon « 4/7/13 ».
        val motif = NSDateFormatter.dateFormatFromTemplate(
            tmplate = "yMd",
            options = 0uL,
            locale = null,
        ) ?: "yyyy-MM-dd"
        dateFormat = Regex("y+").replace(motif, "yyyy")
    }
}

private val heureCourte: NSDateFormatter by lazy {
    NSDateFormatter().apply {
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }
}

private val jourMoisAbrege: NSDateFormatter by lazy {
    NSDateFormatter().apply {
        // « sam. 1 août » : l'ordre des composants vient de la locale, d'où le
        // gabarit plutôt qu'un motif écrit en dur.
        dateFormat = NSDateFormatter.dateFormatFromTemplate(
            tmplate = "EEEdMMM",
            options = 0uL,
            locale = null,
        ) ?: "EEE d MMM"
    }
}

private fun dateDepuisMs(epochMs: Long): NSDate =
    NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)

internal actual fun formaterDateCourte(annee: Int, mois: Int, jour: Int): String {
    // Passer par une NSDate demanderait un NSCalendar et un fuseau ; ces trois
    // nombres sont déjà une date civile, on les rend directement au motif.
    val brut = "$annee-${mois.toString().padStart(2, '0')}-${jour.toString().padStart(2, '0')}"
    val analyseur = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }
    val date = analyseur.dateFromString(brut) ?: return brut
    return dateCourte.stringFromDate(date)
}

internal actual fun formaterHeureCourte(epochMs: Long): String =
    heureCourte.stringFromDate(dateDepuisMs(epochMs))

internal actual fun formaterJourMoisAbrege(epochMs: Long): String =
    jourMoisAbrege.stringFromDate(dateDepuisMs(epochMs))

/**
 * Le motif vient d'une traduction et change donc avec la langue : le formateur
 * ne peut pas être conservé comme les trois autres. Il reste bon marché à
 * l'échelle d'un en-tête de section par jour.
 */
internal actual fun formaterAuMotif(epochMs: Long, motif: String): String =
    NSDateFormatter().apply { dateFormat = motif }.stringFromDate(dateDepuisMs(epochMs))
