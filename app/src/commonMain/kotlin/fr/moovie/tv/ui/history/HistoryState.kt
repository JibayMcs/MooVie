package fr.moovie.tv.ui.history

import fr.moovie.tv.data.watch.HistoryEntry

/**
 * Un jour d'historique : les visionnages consignés entre minuit et minuit,
 * heure locale. [dayStart] sert de clé de liste stable ; [relative] décide du
 * libellé (« Aujourd'hui », « Hier », ou la date écrite en toutes lettres).
 */
data class HistoryDay(
    val dayStart: Long,
    val relative: RelativeDay,
    val entries: List<HistoryEntry>,
)

/** Position d'un jour par rapport à aujourd'hui, pour son en-tête. */
enum class RelativeDay { TODAY, YESTERDAY, OLDER }

/**
 * Les trois cartes de statistiques de la page. Chaque champ peut rester vide :
 * un historique tout neuf n'a ni genre dominant ni série du moment, et une
 * carte sans donnée ne s'affiche pas plutôt que d'afficher un zéro.
 */
data class HistoryStats(
    /** Genre le plus vu depuis le 1er du mois. */
    val monthGenre: String? = null,
    val monthGenreCount: Int = 0,
    /** Bilan depuis le 1er janvier. */
    val yearEpisodes: Int = 0,
    val yearMovies: Int = 0,
    val yearGenre: String? = null,
    /** Série la plus regardée sur les 30 derniers jours. */
    val topSeriesTitle: String? = null,
    val topSeriesImageUrl: String? = null,
    val topSeriesEpisodes: Int = 0,
) {
    /** Rien à montrer : on masque le bandeau plutôt que d'afficher des vides. */
    val isEmpty: Boolean
        get() = monthGenre == null && yearEpisodes == 0 && yearMovies == 0 && topSeriesTitle == null
}
