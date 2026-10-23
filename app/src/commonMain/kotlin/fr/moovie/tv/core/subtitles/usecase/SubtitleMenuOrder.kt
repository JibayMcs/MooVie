package fr.moovie.tv.core.subtitles.usecase

import fr.moovie.tv.core.subtitles.model.SubtitleCandidate

/**
 * L'ordre des sous-titres dans le menu : l'actif, puis ceux qu'on n'a pas
 * encore essayés dans l'ordre du classement, puis ceux qu'on a déjà essayés.
 *
 * Essayer des sous-titres l'un après l'autre à la télécommande est pénible
 * quand rien ne dit lesquels on a déjà vus : ici le prochain à essayer est
 * toujours en tête, et les essayés ne se mélangent plus aux autres. Un
 * sous-titre n'est téléchargé que sur une validation explicite : « essayé »
 * se lit donc à « déjà sur disque » (`PlayerSubtitlesState.downloaded`).
 */
fun orderForMenu(
    candidates: List<SubtitleCandidate>,
    activeId: String?,
    tried: Set<String>,
): List<SubtitleCandidate> {
    val actif = candidates.filter { it.fileId == activeId }
    val restants = candidates.filter { it.fileId != activeId }
    return actif + restants.filterNot { it.fileId in tried } + restants.filter { it.fileId in tried }
}
