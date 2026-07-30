package fr.moovie.tv.data.watch

import kotlinx.serialization.Serializable

/**
 * Une ligne d'historique : ce qui a été regardé, et quand.
 *
 * Le statut « vu » était jusqu'ici un simple booléen `seen:<clé>`, sans date ni
 * métadonnées — impossible d'en tirer une page d'historique. Cette entrée les
 * porte, ce qui permet de grouper par jour et de calculer des statistiques sans
 * la moindre requête TMDB.
 *
 * Conséquence assumée : l'historique démarre à la version qui l'introduit. Tout
 * ce qui était marqué vu avant n'a pas de date récupérable.
 */
@Serializable
data class HistoryEntry(
    /** Clé du contenu : "movie:<id>" ou "tv:<id>:s<S>e<E>". */
    val key: String,
    val tmdbId: Int,
    val isTv: Boolean,
    val season: Int = 0,
    val episode: Int = 0,
    val title: String = "",
    val imageUrl: String? = null,
    /** Genres TMDB du titre, relevés à l'ouverture de sa fiche. */
    val genres: List<String> = emptyList(),
    val watchedAt: Long = 0,
) {
    val episodeLabel: String? get() = if (isTv) "S$season · E$episode" else null

    /** Clé du titre, pour regrouper les épisodes d'une même série. */
    val titleKey: String get() = if (isTv) "tv:$tmdbId" else "movie:$tmdbId"
}
