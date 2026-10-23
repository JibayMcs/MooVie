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
 * 2. **Le titre de l'épisode** dans le nom de la release, voir [episodeTitles] :
 *    un sous-titre bien calé reste le mauvais contenu s'il vient d'un autre
 *    épisode ou d'une autre série.
 * 3. **La cadence**, quand on connaît celle du flux. Un sous-titre calé sur la
 *    bonne cadence évite la dérive ; une cadence différente mais connue se
 *    corrige exactement, voir [timingFor].
 *
 * Le catalogue Stremio ne renseigne ni popularité ni type de sous-titre : seuls
 * la langue, le titre d'épisode et la cadence départagent, l'ordre du catalogue
 * faisant le reste (le tri est stable).
 *
 * @param streamFps cadence du flux joué, si le lecteur la connaît. Null la
 *   neutralise plutôt que de pénaliser tout le monde au hasard.
 * @param episodeTitles titre de l'épisode d'après TMDB (dans la langue de
 *   l'application, et en anglais : les releases sont nommées en anglais), vide
 *   pour un film. Une release qui en contient un passe devant les autres de sa
 *   langue. Mesuré sur *Fullmetal Alchemist* (2003) S01E02 : l'addon renvoie,
 *   sous cette série, un « Fullmetal.Alchemist.Brotherhood.E02 » — le
 *   sous-titre de l'autre série, qui raconte l'épisode 1 de celle-ci. Seul le
 *   nom de la release le trahit, les bons candidats s'appelant « 02 - Body of
 *   the Sanctioned ». Les titres de moins de quatre caractères sont ignorés :
 *   trop courts pour ne pas se trouver dans n'importe quel nom.
 */
fun rankSubtitles(
    candidates: List<SubtitleCandidate>,
    preferredLanguages: List<String>,
    streamFps: Double? = null,
    episodeTitles: List<String> = emptyList(),
): List<SubtitleCandidate> {
    val langOrder = preferredLanguages.map { it.lowercase() }
    val stream = normalizeFps(streamFps)
    val titres = episodeTitles.map(::normalizeTitle).filter { it.length >= 4 }

    return candidates.sortedWith(
        compareBy<SubtitleCandidate> { candidate ->
            // Langue inconnue de la préférence : après toutes celles qui y sont.
            langOrder.indexOf(candidate.language.lowercase())
                .takeIf { it >= 0 } ?: langOrder.size
        }
            .thenBy { candidate ->
                val release = normalizeTitle(candidate.release)
                if (titres.any { release.contains(it) }) 0 else 1
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

/** Minuscules, lettres et chiffres seulement, un espace entre les mots : « Body.of.the.Sanctioned » et « Body of the Sanctioned » se rejoignent. */
private fun normalizeTitle(text: String): String =
    text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").trim().replace(Regex("\\s+"), " ")
