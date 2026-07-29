package fr.moovie.tv.ui.details

import fr.moovie.tv.data.sources.EmbedLink
import fr.moovie.tv.data.tmdb.Episode
import fr.moovie.tv.data.tmdb.MovieDetails
import fr.moovie.tv.data.tmdb.TvDetails

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Movie(val details: MovieDetails) : DetailsState
    data class Tv(val details: TvDetails, val season: Int, val episodes: List<Episode>) : DetailsState
    data class Error(val message: String) : DetailsState
}

/** Statut de chargement d'un provider donné. */
enum class ProviderStatus { LOADING, DONE, EMPTY, FAILED }

data class ProviderProgress(val name: String, val status: ProviderStatus)

/**
 * État du panneau de sources. Idle = fermé. Active = panneau ouvert (dès le clic),
 * avec les liens accumulés et la progression par provider, mis à jour en streaming.
 */
sealed interface SourcesState {
    data object Idle : SourcesState
    data class Active(
        val links: List<EmbedLink>,
        val providers: List<ProviderProgress>,
    ) : SourcesState {
        val anyLoading: Boolean get() = providers.any { it.status == ProviderStatus.LOADING }
    }
}

/**
 * État de la « lecture rapide » : résolution automatique de la meilleure source
 * dans la langue préférée, sans passer par le panneau.
 */
sealed interface QuickPlayState {
    data object Idle : QuickPlayState
    /** [label] = descripteur technique ("VF" ou "S1E3 · VF"), formaté par l'UI. */
    data class Searching(val label: String) : QuickPlayState
    /** [lang] = langue manquante ("VF"…), formatée par l'UI. */
    data class Unavailable(val lang: String) : QuickPlayState
}
