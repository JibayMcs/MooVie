package fr.moovie.tv.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Les implémentations JVM sont **exactement** le code d'avant le portage :
 * mêmes formateurs, même cache, même forçage de l'année sur quatre chiffres.
 * Android et desktop affichent donc les dates au caractère près comme
 * auparavant.
 */

/**
 * Un formateur par locale — leur construction passe par une recherche de motif
 * localisé, inutile de la refaire à chaque date d'une liste d'épisodes.
 */
private val formatters = mutableMapOf<Locale, DateTimeFormatter>()

@Synchronized
private fun formatterFor(locale: Locale): DateTimeFormatter = formatters.getOrPut(locale) {
    val pattern = DateTimeFormatterBuilder
        .getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
        .replace(Regex("y+"), "yyyy")
    DateTimeFormatter.ofPattern(pattern, locale)
}

internal actual fun formaterDateCourte(annee: Int, mois: Int, jour: Int): String =
    formatterFor(Locale.getDefault()).format(LocalDate.of(annee, mois, jour))

internal actual fun formaterHeureCourte(epochMs: Long): String {
    val moment = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(moment)
}

internal actual fun formaterJourMoisAbrege(epochMs: Long): String {
    val moment = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(moment)
}

internal actual fun formaterAuMotif(epochMs: Long, motif: String): String =
    java.text.SimpleDateFormat(motif, Locale.getDefault())
        .format(java.util.Date(epochMs))
