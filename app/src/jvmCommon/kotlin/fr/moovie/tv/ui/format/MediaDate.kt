package fr.moovie.tv.ui.format

import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Date TMDB (`2013-04-07`) rendue dans la langue de l'app : `07/04/2013` en
 * français, `4/7/2013` en anglais américain.
 *
 * TMDB rend toujours de l'ISO ; l'afficher tel quel donnait une date qu'aucun
 * francophone ne lit dans ce sens.
 *
 * La locale n'a pas à être passée : sur Android, `LocaleManager` appelle
 * `Locale.setDefault()` avec la langue choisie dans les réglages avant que la
 * moindre UI soit construite ; sur desktop c'est la locale système. Les deux
 * plateformes convergent donc sur `Locale.getDefault()`.
 *
 * Le motif vient de la locale (ordre jour/mois propre à chaque langue) mais
 * l'année est forcée sur quatre chiffres : le format court américain donne
 * sinon `4/7/13`, illisible pour une date de sortie où l'année est justement
 * l'information qu'on cherche.
 */
fun formatMediaDate(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    // TMDB rend parfois l'année seule (année de sortie d'un film) : rien à
    // reformater, et surtout rien à inventer comme jour et mois.
    if (YEAR_ONLY.matches(value)) return value

    val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return value
    return date.format(formatterFor(Locale.getDefault()))
}

/** Année seule d'une date TMDB, ou la valeur telle quelle si illisible. */
fun mediaYear(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (YEAR_ONLY.matches(value)) return value
    return runCatching { LocalDate.parse(value).year.toString() }.getOrDefault(value)
}

private val YEAR_ONLY = Regex("""\d{4}""")

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

/**
 * Instant daté dans la langue de l'app : « 02/08/2026 · 14:30 ».
 *
 * Sert à l'aperçu d'une sauvegarde, où la date répond à la seule question qui
 * compte avant d'importer : est-ce plus récent que ce que j'ai ici ? D'où
 * l'heure, deux exports du même jour étant le cas courant.
 */
fun formatBackupDate(ms: Long): String {
    if (ms <= 0) return "—"
    val locale = Locale.getDefault()
    val moment = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(moment)
    return "${formatterFor(locale).format(moment)} · $time"
}

/**
 * Date et heure courantes, dans la langue de l'app : « sam. 1 août · 21:42 ».
 *
 * Format long-mais-abrégé pour la date (jour de semaine + jour + mois) et
 * numérique pour l'heure : sur un bandeau de lecteur, la date sert de repère et
 * l'heure de mesure. La minute suffit — afficher les secondes obligerait à
 * recomposer le lecteur une fois par seconde pendant tout un film.
 */
fun formatNowDateTime(nowMs: Long): String {
    val locale = Locale.getDefault()
    val moment = java.time.Instant.ofEpochMilli(nowMs)
        .atZone(java.time.ZoneId.systemDefault())
    val date = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(moment)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .format(moment)
    return "$date · $time"
}
