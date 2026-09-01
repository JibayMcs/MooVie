package fr.moovie.tv.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.HistoryEntry
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.shared.maintenantMs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Fenêtre de la carte « série du moment ». */
private const val TOP_SERIES_DAYS = 30

/**
 * Historique de visionnage : groupement par jour et statistiques.
 *
 * Tout se calcule sur les entrées locales, sans la moindre requête TMDB — les
 * métadonnées (titre, affiche, genres) ont été consignées au moment du
 * visionnage par [WatchProgressRepository].
 */
class HistoryViewModel : ViewModel() {

    private val watchRepo = WatchProgressRepository()
    private val settings = SettingsRepository()

    /** Jours d'historique, du plus récent au plus ancien. */
    val days: StateFlow<List<HistoryDay>> = watchRepo.history
        .map { groupByDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Statistiques du bandeau, ou `null` quand l'utilisateur a demandé à masquer
     * les widgets (réglage actif par défaut) : la page s'ouvre alors sur la
     * grille pleine hauteur.
     */
    val stats: StateFlow<HistoryStats?> =
        combine(watchRepo.history, settings.hideHistoryWidgets) { entries, hidden ->
            if (hidden) null else computeStats(entries).takeUnless { it.isEmpty }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Retire la ligne de l'historique, sans toucher au statut « vu ». */
    fun remove(key: String) {
        viewModelScope.launch { watchRepo.removeFromHistory(key) }
    }

    /**
     * Remet le contenu en non-vu. La ligne d'historique part avec : garder une
     * date de visionnage pour un épisode redevenu « à voir » n'aurait pas de sens.
     */
    fun markUnwatched(key: String) {
        viewModelScope.launch {
            watchRepo.setWatched(key, false)
            watchRepo.removeFromHistory(key)
        }
    }

    /**
     * Regroupe sur la date **locale** et non sur l'epoch brut : un épisode
     * terminé à 1 h du matin appartient au jour civil de l'utilisateur, pas à
     * celui d'UTC.
     */
    private fun groupByDay(entries: List<HistoryEntry>): List<HistoryDay> {
        val today = dayStart(maintenantMs())
        // Un jour civil en arrière, et non 24 h : le passage à l'heure d'été en
        // compte 23. `Calendar.add(DAY_OF_YEAR, -1)` faisait déjà ce calcul-là ;
        // kotlinx-datetime le refait en passant par la date, pas par la durée.
        val yesterday = Instant.fromEpochMilliseconds(today)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
            .minus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        return entries
            .groupBy { dayStart(it.watchedAt) }
            // `toSortedMap` est une extension de `java.util.SortedMap` : elle
            // n'existe pas dans le commun. Trier les entrées donne la même
            // chose — l'ordre décroissant des jours — sans passer par une carte
            // triée dont on ne se sert que pour la parcourir une fois.
            .entries.sortedByDescending { it.key }
            .map { (start, dayEntries) ->
                HistoryDay(
                    dayStart = start,
                    relative = when (start) {
                        today -> RelativeDay.TODAY
                        yesterday -> RelativeDay.YESTERDAY
                        else -> RelativeDay.OLDER
                    },
                    entries = dayEntries,
                )
            }
    }

    private fun computeStats(entries: List<HistoryEntry>): HistoryStats {
        // Premier jour du mois et de l'année en cours, à minuit. `Calendar`
        // partait de maintenant et remettait le champ à 1 ; kotlinx-datetime
        // reconstruit la date civile, ce qui dit la même chose plus directement.
        val zone = TimeZone.currentSystemDefault()
        val aujourdHui = Instant.fromEpochMilliseconds(maintenantMs()).toLocalDateTime(zone).date
        val monthStart = LocalDate(aujourdHui.year, aujourdHui.month, 1)
            .atStartOfDayIn(zone).toEpochMilliseconds()
        val yearStart = LocalDate(aujourdHui.year, 1, 1)
            .atStartOfDayIn(zone).toEpochMilliseconds()
        // Trente jours civils en arrière, minuit. En jours et non en heures :
        // c'est ce que faisait `Calendar.add(DAY_OF_YEAR, …)`, et les deux
        // changements d'heure de l'année valent chacun une heure d'écart.
        val seriesSince = aujourdHui.minus(TOP_SERIES_DAYS, DateTimeUnit.DAY)
            .atStartOfDayIn(zone).toEpochMilliseconds()

        val ofMonth = entries.filter { it.watchedAt >= monthStart }
        val ofYear = entries.filter { it.watchedAt >= yearStart }
        val recentSeries = entries.filter { it.isTv && it.watchedAt >= seriesSince }

        val monthGenre = dominantGenre(ofMonth)
        // La série est identifiée par son titleKey, mais affichée avec le titre
        // et l'image de son épisode le plus récent : c'est la vignette la plus
        // parlante, et un ancien épisode peut avoir été consigné sans image.
        val topSeries = recentSeries.groupBy { it.titleKey }.maxByOrNull { it.value.size }

        return HistoryStats(
            monthGenre = monthGenre?.first,
            monthGenreCount = monthGenre?.second ?: 0,
            yearEpisodes = ofYear.count { it.isTv },
            yearMovies = ofYear.count { !it.isTv },
            yearGenre = dominantGenre(ofYear)?.first,
            topSeriesTitle = topSeries?.value?.firstOrNull { it.title.isNotBlank() }?.title,
            topSeriesImageUrl = topSeries?.value?.firstNotNullOfOrNull { it.imageUrl },
            topSeriesEpisodes = topSeries?.value?.size ?: 0,
        )
    }

    /**
     * Genre le plus représenté, avec son nombre de visionnages. Un même titre
     * pèse autant de fois qu'on l'a regardé : dix épisodes d'une série policière
     * pèsent plus qu'un film d'animation, ce qui est bien le but.
     */
    private fun dominantGenre(entries: List<HistoryEntry>): Pair<String, Int>? =
        entries.flatMap { it.genres }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.toPair()

    /** Minuit local du jour contenant [millis]. */
    private fun dayStart(millis: Long): Long {
        val zone = TimeZone.currentSystemDefault()
        return Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(zone).date
            .atStartOfDayIn(zone)
            .toEpochMilliseconds()
    }
}
