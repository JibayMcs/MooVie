package fr.moovie.tv.data.watch

import kotlinx.serialization.Serializable

/**
 * Entrée « À regarder plus tard ».
 *
 * Comme [ResumeEntry], elle embarque titre et affiche pour que le rail se
 * dessine dès le premier frame, sans attendre une requête TMDB.
 *
 * La clé est au **niveau du titre** — `"movie:<id>"` ou `"tv:<id>"`, sans
 * saison ni épisode : on met une série de côté, pas un épisode précis.
 */
@Serializable
data class WatchlistEntry(
    val key: String,
    val tmdbId: Int,
    val isTv: Boolean,
    val title: String = "",
    val imageUrl: String? = null,
    /**
     * Nombre total d'épisodes de la série au moment de l'ajout, qui sert à
     * décider quand elle est terminée et sort d'elle-même de la liste. C'est un
     * instantané : une saison diffusée après coup ne le met pas à jour, la
     * série sortira donc de la liste un peu tôt. Le corriger imposerait une
     * requête TMDB à chaque marquage « vu ».
     */
    val totalEpisodes: Int = 0,
    val addedAt: Long = 0,
) {
    companion object {
        fun movieKey(tmdbId: Int) = "movie:$tmdbId"
        fun tvKey(tmdbId: Int) = "tv:$tmdbId"
    }
}
