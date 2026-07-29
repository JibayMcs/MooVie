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
