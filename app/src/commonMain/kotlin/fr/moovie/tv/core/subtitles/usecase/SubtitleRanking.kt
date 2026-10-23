package fr.moovie.tv.core.subtitles.usecase

import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import kotlin.math.abs

/**
 * Classe les sous-titres proposés, du plus probable au moins.
 *
 * **Le classement est la fonctionnalité, pas de la présentation.** À cinq
 * téléchargements par jour, chaque essai raté coûte un cinquième de la journée :
 * il faut que le premier de la liste soit le bon le plus souvent possible, parce
 * que l'utilisateur n'aura pas les moyens d'en essayer quatre.
 *
 * Critères, dans l'ordre :
 *
 * 1. **La langue**, selon la préférence déclarée. Un sous-titre dans la mauvaise
 *    langue n'est pas un mauvais candidat, c'en est un autre.
 * 2. **La cadence**, quand on connaît celle du flux. Un sous-titre calé sur la
 *    bonne cadence évite la dérive ; une cadence différente mais connue se
 *    corrige exactement, voir [timingFor].
 *
 * Le catalogue Stremio ne renseigne ni popularité ni type de sous-titre : seuls
 * la langue et la cadence départagent, l'ordre du catalogue faisant le reste
 * (le tri est stable).
 *
 * @param streamFps cadence du flux joué, si le lecteur la connaît. Null la
 *   neutralise plutôt que de pénaliser tout le monde au hasard.
 */
fun rankSubtitles(
    candidates: List<SubtitleCandidate>,
    preferredLanguages: List<String>,
    streamFps: Double? = null,
): List<SubtitleCandidate> {
    val langOrder = preferredLanguages.map { it.lowercase() }
    val stream = normalizeFps(streamFps)

    return candidates.sortedWith(
        compareBy<SubtitleCandidate> { candidate ->
            // Langue inconnue de la préférence : après toutes celles qui y sont.
            langOrder.indexOf(candidate.language.lowercase())
                .takeIf { it >= 0 } ?: langOrder.size
        }
            .thenBy { fpsRank(it.fps, stream) },
    )
}

/**
 * 0 = cadence identique, 1 = corrigeable, 2 = inconnue.
 *
 * Une cadence différente mais *connue* passe devant une cadence absente : elle
 * se corrige exactement, alors qu'un sous-titre qui ne déclare rien laisse
 * l'utilisateur régler à la main.
 */
private fun fpsRank(subtitleFps: Double?, streamFps: Double?): Int {
    val sub = normalizeFps(subtitleFps) ?: return 2
    if (streamFps == null) return 1
    return if (abs(sub - streamFps) < 0.01) 0 else 1
}
