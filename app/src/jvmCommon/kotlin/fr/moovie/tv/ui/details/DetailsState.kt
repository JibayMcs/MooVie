package fr.moovie.tv.ui.details

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.data.tmdb.Episode
import fr.moovie.tv.data.tmdb.MovieDetails
import fr.moovie.tv.data.tmdb.TmdbVideo
import fr.moovie.tv.data.tmdb.TvDetails

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Movie(val details: MovieDetails) : DetailsState
    /**
     * @param resumeEpisode épisode à reprendre ou à suivre dans [season]
     *   (0 = aucun). Sert à le repérer d'un coup d'œil dans la liste, sans lui
     *   donner le focus : à l'arrivée sur la fiche, le focus doit rester sur
     *   l'action principale.
     */
    data class Tv(
        val details: TvDetails,
        val season: Int,
        val episodes: List<Episode>,
        val resumeEpisode: Int = 0,
        /**
         * Résumé et année **de la saison affichée**, quand TMDB les donne. Le
         * héros s'en sert et retombe sur ceux de la série sinon : mieux vaut le
         * synopsis de la série qu'un cadre vide.
         */
        val seasonOverview: String = "",
        val seasonYear: String? = null,
        /**
         * Date de première diffusion de la saison, brute. L'année seule ne
         * suffit pas à distinguer une saison sortie d'une saison annoncée.
         */
        val seasonAirDate: String? = null,
    ) : DetailsState
    data class Error(val message: String) : DetailsState
}

/**
 * État de la bande-annonce d'un titre.
 *
 * Deux états seulement, et c'est une décision : **on n'affiche rien tant que le
 * flux n'est pas résolu**. Le bouton et l'aperçu du hero ne doivent pas exister
 * sur la foi de ce que TMDB déclare — TMDB dit qu'une clé YouTube existe, pas
 * qu'elle est jouable, et les deux divergent (vidéo retirée, restreinte par
 * région, ou client InnerTube bloqué ce jour-là).
 *
 * Le prix est un aller-retour réseau à l'ouverture de la fiche, en tâche de
 * fond. Ce qu'on achète, c'est qu'un bouton visible marche toujours — plutôt
 * qu'un bouton qui, une fois sur dix, tourne puis s'excuse.
 */
sealed interface TrailerState {
    /** Pas de bande-annonce, ou pas encore résolue : rien à l'écran. */
    data object None : TrailerState

    /**
     * Flux résolu et jouable. [durationSeconds] sert à l'aperçu du hero, qui
     * doit savoir quand se rendre.
     */
    data class Ready(
        val video: TmdbVideo,
        val stream: PlayableStream,
        val durationSeconds: Int,
    ) : TrailerState
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
        /**
         * Liste de providers vide = chargement tout juste démarré (elle n'arrive
         * qu'après lecture des réglages). On la compte comme « en cours » sinon
         * la lecture rapide conclurait « aucune source » avant même d'avoir
         * cherché — c'est ce qui obligeait à appuyer deux fois sur OK.
         */
        val anyLoading: Boolean
            get() = providers.isEmpty() || providers.any { it.status == ProviderStatus.LOADING }
    }
}

/** Épisode ouvert en fiche détaillée (saison + épisode affichés). */
data class EpisodeSelection(val season: Int, val episode: Episode)

/**
 * État de la « lecture rapide » : résolution automatique de la meilleure source
 * dans la langue préférée, sans passer par le panneau.
 */
sealed interface QuickPlayState {
    data object Idle : QuickPlayState
    /** [label] = descripteur technique ("VF" ou "S1E3 · VF"), formaté par l'UI. */
    /** [hoster] non nul pendant l'essai d'un hébergeur précis (cascade visible). */
    data class Searching(val label: String, val hoster: String? = null) : QuickPlayState
    /** [lang] = langue manquante ("VF"…), formatée par l'UI. */
    data class Unavailable(val lang: String) : QuickPlayState
}
