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
 * Pourquoi le panneau des sources est vide.
 *
 * « Aucune source disponible » recouvrait quatre situations que rien ne
 * distinguait à l'écran, alors que le chargement les sépare déjà : un catalogue
 * qui répond sans avoir le titre ([ProviderStatus.EMPTY]) et un catalogue
 * injoignable ([ProviderStatus.FAILED]) ne veulent pas dire la même chose du
 * tout. Le premier est une réponse — le titre n'existe pas chez nous ; le second
 * est une panne, souvent le réseau, et l'utilisateur peut y faire quelque chose.
 *
 * C'est la faiblesse structurelle d'une application qui extrait ses sources sur
 * l'appareil : elle *sait* pourquoi elle échoue, et jusqu'ici elle n'en disait
 * rien. Le diagnostic est une fonction pure des statuts déjà collectés — aucun
 * appel réseau supplémentaire, aucune heuristique.
 */
enum class SourceDiagnosis {
    /** Tous les catalogues sont désactivés dans les réglages. */
    NONE_ENABLED,

    /** Aucun catalogue n'a répondu : réseau, DNS, ou domaines tous morts. */
    UNREACHABLE,

    /** Une partie a répondu sans résultat, le reste est injoignable. */
    PARTIAL,

    /** Tous ont répondu, aucun n'a ce titre. C'est une réponse, pas une panne. */
    ABSENT,
}

/**
 * Diagnostic d'un panneau vide, ou null tant que la recherche n'est pas finie —
 * auquel cas il n'y a rien à conclure et l'appelant garde son indicateur de
 * chargement.
 */
fun diagnoseEmptySources(state: SourcesState.Active): SourceDiagnosis? {
    if (state.noProviderEnabled) return SourceDiagnosis.NONE_ENABLED
    if (state.anyLoading) return null

    val failed = state.providers.count { it.status == ProviderStatus.FAILED }
    val answered = state.providers.size - failed
    return when {
        answered == 0 -> SourceDiagnosis.UNREACHABLE
        failed > 0 -> SourceDiagnosis.PARTIAL
        else -> SourceDiagnosis.ABSENT
    }
}

/**
 * État du panneau de sources. Idle = fermé. Active = panneau ouvert (dès le clic),
 * avec les liens accumulés et la progression par provider, mis à jour en streaming.
 */
sealed interface SourcesState {
    data object Idle : SourcesState
    data class Active(
        val links: List<EmbedLink>,
        val providers: List<ProviderProgress>,
        /**
         * L'utilisateur a désactivé **tous** les catalogues dans les réglages.
         *
         * Sans ce drapeau la liste de providers reste vide, donc [anyLoading]
         * reste vrai, et le panneau tourne indéfiniment sur une recherche qui
         * n'a jamais été lancée. Une liste vide ne peut pas porter les deux sens
         * à la fois : « pas encore publiée » et « il n'y en a aucun ».
         */
        val noProviderEnabled: Boolean = false,
    ) : SourcesState {
        /**
         * Liste de providers vide = chargement tout juste démarré (elle n'arrive
         * qu'après lecture des réglages). On la compte comme « en cours » sinon
         * la lecture rapide conclurait « aucune source » avant même d'avoir
         * cherché — c'est ce qui obligeait à appuyer deux fois sur OK.
         */
        val anyLoading: Boolean
            get() = !noProviderEnabled &&
                (providers.isEmpty() || providers.any { it.status == ProviderStatus.LOADING })
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
