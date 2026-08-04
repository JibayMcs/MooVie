package fr.moovie.tv.data.watch

import kotlinx.serialization.Serializable

/**
 * Entrée « Reprendre la lecture » : progression + métadonnées d'affichage
 * (titre, image, saison/épisode) pour reconstruire une carte sans requête TMDB.
 * Clé stable : "movie:<id>" ou "tv:<id>:s<S>e<E>".
 */
@Serializable
data class ResumeEntry(
    val key: String,
    val tmdbId: Int,
    val isTv: Boolean,
    val season: Int = 0,
    val episode: Int = 0,
    val title: String = "",
    val imageUrl: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val episodeLabel: String? get() = if (isTv) "S$season · E$episode" else null
}

/**
 * Une seule carte par série, sur l'épisode le plus récemment regardé.
 *
 * Une série suivie sur plusieurs fronts — un épisode entamé le soir, un autre
 * repris le lendemain — occupait autant de places dans « Reprendre la lecture »
 * qu'elle avait d'épisodes en cours. Le rail se remplissait d'une seule œuvre et
 * poussait tout le reste hors écran, alors qu'il n'y a jamais qu'un endroit où
 * l'on veut reprendre : le dernier.
 *
 * Les films sont regroupés par leur clé, donc inchangés — chacun est déjà seul
 * de son espèce. Le tri est refait ici plutôt que supposé de l'appelant : c'est
 * lui qui décide quel épisode survit, il ne doit pas dépendre d'un ordre acquis
 * ailleurs.
 */
fun List<ResumeEntry>.oneCardPerSeries(): List<ResumeEntry> =
    sortedByDescending { it.updatedAt }
        .distinctBy { if (it.isTv) "tv:${it.tmdbId}" else it.key }
