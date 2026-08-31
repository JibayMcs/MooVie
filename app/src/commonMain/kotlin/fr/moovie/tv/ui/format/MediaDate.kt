package fr.moovie.tv.ui.format

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * Date TMDB (`2013-04-07`) rendue dans la langue de l'app : `07/04/2013` en
 * français, `4/7/2013` en anglais américain.
 *
 * TMDB rend toujours de l'ISO ; l'afficher tel quel donnait une date qu'aucun
 * francophone ne lit dans ce sens.
 *
 * La locale n'a pas à être passée : chaque plateforme sait la sienne. Sur
 * Android, `LocaleManager` appelle `Locale.setDefault()` avec la langue choisie
 * dans les réglages avant que la moindre UI soit construite ; sur desktop c'est
 * la locale système ; sur iOS, `NSDateFormatter` suit les réglages de
 * l'appareil. Voir les `actual` de [formaterDateCourte].
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
    return formaterDateCourte(date.year, date.monthNumber, date.dayOfMonth)
}

/** Année seule d'une date TMDB, ou la valeur telle quelle si illisible. */
fun mediaYear(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (YEAR_ONLY.matches(value)) return value
    return runCatching { LocalDate.parse(value).year.toString() }.getOrDefault(value)
}

private val YEAR_ONLY = Regex("""\d{4}""")

/**
 * Instant daté dans la langue de l'app : « 02/08/2026 · 14:30 ».
 *
 * Sert à l'aperçu d'une sauvegarde, où la date répond à la seule question qui
 * compte avant d'importer : est-ce plus récent que ce que j'ai ici ? D'où
 * l'heure, deux exports du même jour étant le cas courant.
 */
fun formatBackupDate(ms: Long): String {
    if (ms <= 0) return "—"
    val moment = Instant.fromEpochMilliseconds(ms)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val date = formaterDateCourte(moment.year, moment.monthNumber, moment.dayOfMonth)
    return "$date · ${formaterHeureCourte(ms)}"
}

/**
 * Date et heure courantes, dans la langue de l'app : « sam. 1 août · 21:42 ».
 *
 * Format long-mais-abrégé pour la date (jour de semaine + jour + mois) et
 * numérique pour l'heure : sur un bandeau de lecteur, la date sert de repère et
 * l'heure de mesure. La minute suffit — afficher les secondes obligerait à
 * recomposer le lecteur une fois par seconde pendant tout un film.
 */
fun formatNowDateTime(nowMs: Long): String =
    "${formaterJourMoisAbrege(nowMs)} · ${formaterHeureCourte(nowMs)}"

/**
 * La date d'un épisode **à venir**, formatée, ou null s'il est déjà sorti.
 *
 * Répond à ce que la vignette vide ne pouvait pas dire : une saison non diffusée
 * n'a chez TMDB ni image ni titre d'épisode, et l'application n'affichait qu'un
 * cadre gris. La date, elle, est presque toujours là — c'est la seule
 * information utile à ce stade.
 *
 * Le jour même compte comme sorti : un épisode diffusé ce soir n'est pas
 * « prévu », il arrive. La comparaison est donc stricte.
 *
 * Le fuseau est celui du système, volontairement : c'est la date du calendrier
 * de l'utilisateur qui décide de ce qui est « à venir » pour lui, pas celle du
 * diffuseur.
 */
fun upcomingDate(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return null
    val aujourdHui = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return if (date > aujourdHui) formatMediaDate(value) else null
}

/**
 * Date courte au motif de la locale, année sur quatre chiffres.
 *
 * `expect` parce que le motif lui-même — l'ordre jour/mois, les séparateurs —
 * est une donnée de localisation que porte le système et qu'aucune
 * bibliothèque multiplateforme n'embarque. kotlinx-datetime sait analyser et
 * calculer des dates, il ne sait pas les présenter à un francophone.
 */
internal expect fun formaterDateCourte(annee: Int, mois: Int, jour: Int): String

/** Heure au format court de la locale : « 14:30 », « 2:30 PM ». */
internal expect fun formaterHeureCourte(epochMs: Long): String

/** Jour de semaine, jour et mois abrégés : « sam. 1 août ». */
internal expect fun formaterJourMoisAbrege(epochMs: Long): String
