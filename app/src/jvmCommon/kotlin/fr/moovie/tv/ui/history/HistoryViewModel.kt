package fr.moovie.tv.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.watch.HistoryEntry
import fr.moovie.tv.data.watch.WatchProgressRepository
import java.util.Calendar
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
        val today = dayStart(System.currentTimeMillis())
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        return entries
            .groupBy { dayStart(it.watchedAt) }
            .toSortedMap(compareByDescending { it })
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
        val monthStart = dayStart(
            Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis,
        )
        val yearStart = dayStart(
            Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1) }.timeInMillis,
        )
        val seriesSince = dayStart(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -TOP_SERIES_DAYS) }.timeInMillis,
        )

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
    private fun dayStart(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
