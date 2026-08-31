package fr.moovie.tv.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoList(val results: List<TmdbVideo> = emptyList())

/**
 * Une vidéo promotionnelle déclarée par TMDB.
 *
 * TMDB ne les héberge pas : il n'en donne qu'une **clé** chez un hébergeur
 * tiers, presque toujours YouTube. C'est cette clé que résout
 * `YoutubeTrailerExtractor` ; sans lui, `site = "Vimeo"` n'est pas jouable.
 */
@Serializable
data class TmdbVideo(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val site: String = "",
    /** "Trailer", "Teaser", "Clip", "Featurette", "Behind the Scenes"… */
    val type: String = "",
    /** Hauteur annoncée : 360, 480, 720, 1080. */
    val size: Int = 0,
    /** Publiée par le studio, par opposition à une reprise de chaîne tierce. */
    val official: Boolean = false,
    @SerialName("iso_639_1") val language: String = "",
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("published_at") val publishedAt: String = "",
) {
    val isYoutube: Boolean get() = site.equals("YouTube", ignoreCase = true)

    /** Vignette YouTube, pour la carte du rail sans résoudre le flux. */
    fun thumbnailUrl(): String? =
        key.takeIf { it.isNotBlank() && isYoutube }?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
}

/**
 * Classe les vidéos d'un titre, la meilleure d'abord.
 *
 * L'ordre encode ce qu'on entend par « la » bande-annonce, du plus au moins
 * décisif :
 *
 * 1. **Jouable** — pas YouTube, pas de clé : écartée, pas rétrogradée. Proposer
 *    un bouton qui ne peut pas aboutir est pire que ne rien proposer.
 * 2. **Langue de l'utilisateur** avant tout le reste. Une bande-annonce
 *    française 720p vaut mieux qu'une anglaise 1080p pour qui regarde en
 *    français — c'est la demande, et c'est aussi ce que fait Canal+.
 * 3. **Type** : une vraie bande-annonce avant un teaser, un teaser avant un
 *    extrait. Les coulisses et les featurettes ne sont pas des bandes-annonces
 *    et ne remontent jamais en tête.
 * 4. **Officielle**, puis **définition**, puis **la plus récente** : à qualité
 *    égale, la dernière publiée est celle du montage final.
 *
 * @param language code TMDB complet (`fr-FR`) ou court (`fr`) — les deux formes
 *        circulent dans l'app selon qu'elles viennent des réglages ou de la
 *        locale système.
 */
fun List<TmdbVideo>.rankedTrailers(language: String): List<TmdbVideo> {
    val short = language.take(2).lowercase()
    return filter { it.isYoutube && it.key.isNotBlank() && it.typeRank < UNRANKED }
        .sortedWith(
            compareBy<TmdbVideo> { if (it.language.equals(short, ignoreCase = true)) 0 else 1 }
                .thenBy { it.typeRank }
                .thenBy { if (it.official) 0 else 1 }
                .thenByDescending { it.size }
                .thenByDescending { it.publishedAt },
        )
}

/** La meilleure bande-annonce, ou null s'il n'y en a aucune de jouable. */
fun List<TmdbVideo>.bestTrailer(language: String): TmdbVideo? =
    rankedTrailers(language).firstOrNull()

/**
 * Rang du type. [UNRANKED] écarte : ce sont des vidéos promotionnelles, pas des
 * bandes-annonces, et les faire remonter derrière un bouton « Bande-annonce »
 * serait mentir sur ce qu'on va montrer.
 */
private val TmdbVideo.typeRank: Int
    get() = when (type.lowercase()) {
        "trailer" -> 0
        "teaser" -> 1
        else -> UNRANKED
    }

private const val UNRANKED = 99
