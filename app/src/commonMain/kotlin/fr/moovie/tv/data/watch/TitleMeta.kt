package fr.moovie.tv.data.watch

import kotlinx.serialization.Serializable

/**
 * Carte d'identité d'un titre, relevée à l'ouverture de sa fiche et conservée
 * à part (une entrée par titre, pas par épisode).
 *
 * Elle sert de filet à l'historique : un contenu marqué vu sans avoir jamais
 * été lu n'a pas d'entrée de reprise d'où tirer son nom et son image, et un
 * appel TMDB à chaque fin d'épisode serait absurde. Les genres suivent la même
 * logique, pour les statistiques.
 */
@Serializable
data class TitleMeta(
    val title: String = "",
    val imageUrl: String? = null,
    val genres: List<String> = emptyList(),
)
