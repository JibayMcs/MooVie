package fr.moovie.tv.core.intro

/**
 * Type de segment qu'on sait signaler.
 *
 * TheIntroDB en accepte quatre — intro, recap, credits, preview — mais on ne
 * propose que ceux que le lecteur sait **sauter**. Faire marquer à l'utilisateur
 * un segment dont l'app ne fera rien serait lui demander un effort sans
 * contrepartie visible.
 */
enum class SegmentKind { INTRO, CREDITS }

/**
 * Un signalement de segment, tel qu'il partira vers TheIntroDB.
 *
 * L'API tolère qu'une borne manque : une intro sans début commence au tout
 * début du média, un générique sans fin va jusqu'au bout. C'est ce qui permet
 * de signaler un générique **d'un seul appui**. L'intro, elle, se marque
 * toujours des deux bouts : un cold open peut la décaler de plusieurs minutes,
 * et aucune heuristique ne le devine depuis le lecteur.
 *
 * @param videoDurationMs durée totale du média. Facultatif mais recommandé par
 *   TheIntroDB : c'est ce qui rattache le signalement à **la bonne version** du
 *   film (théâtrale, longue, non censurée…). Sans elle, un horodatage relevé sur
 *   une version peut être servi à quelqu'un qui en regarde une autre.
 */
data class SegmentSubmission(
    val tmdbId: Int,
    val isTv: Boolean,
    val kind: SegmentKind,
    val season: Int? = null,
    val episode: Int? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val videoDurationMs: Long? = null,
)

/** Ce qui empêche d'envoyer, en termes qu'on peut montrer à l'utilisateur. */
enum class SubmissionProblem {
    /** Intro sans fin marquée, ou générique sans début. */
    MISSING_MARK,

    /** Moins de 5 s : l'API refuse, et ce n'est de toute façon pas un segment. */
    TOO_SHORT,

    /** Au-delà du plafond du type : 3 min 20 pour une intro, 30 min pour un générique. */
    TOO_LONG,

    /** Au-delà de six heures, ou horodatage négatif. */
    OUT_OF_RANGE,

    /** Une série se signale par épisode : sans saison ni épisode, rien à faire. */
    MISSING_EPISODE,

    /** Fin avant début. */
    REVERSED,
}

/** Durée minimale d'un segment, alignée sur `MinDuration` de l'API. */
const val MIN_SEGMENT_MS = 5_000L

/** Plafond d'une intro (`MaxIntroDuration`). La plupart tiennent en 15–90 s. */
const val MAX_INTRO_MS = 200_000L

/** Plafond d'un générique (`MaxCreditsDuration`). */
const val MAX_CREDITS_MS = 1_800_000L

/** Horodatage maximal accepté (`MaxTimestampMs`) : six heures. */
const val MAX_TIMESTAMP_MS = 21_600_000L

/**
 * Vérifie un signalement **avant** de l'envoyer.
 *
 * Ce n'est pas de la défiance envers l'utilisateur, c'est du respect pour la
 * base : elle est communautaire, un horodatage faux y reste et dessert tout le
 * monde. Autant refuser localement avec une phrase compréhensible plutôt que de
 * laisser l'API répondre 400 après coup.
 *
 * Rend null quand tout va bien.
 */
fun validateSubmission(submission: SegmentSubmission): SubmissionProblem? {
    if (submission.isTv && (submission.season == null || submission.episode == null)) {
        return SubmissionProblem.MISSING_EPISODE
    }

    val start = submission.startMs
    val end = submission.endMs
    if (start != null && start < 0) return SubmissionProblem.OUT_OF_RANGE
    if (end != null && end < 0) return SubmissionProblem.OUT_OF_RANGE
    if (start != null && start > MAX_TIMESTAMP_MS) return SubmissionProblem.OUT_OF_RANGE
    if (end != null && end > MAX_TIMESTAMP_MS) return SubmissionProblem.OUT_OF_RANGE

    return when (submission.kind) {
        // Intro : la fin fait foi, le début est facultatif (null = depuis zéro).
        SegmentKind.INTRO -> {
            if (end == null) return SubmissionProblem.MISSING_MARK
            val duration = end - (start ?: 0L)
            checkDuration(duration, MAX_INTRO_MS)
        }
        // Générique : le début fait foi, la fin est facultative (null = fin du
        // média). Sans fin, il n'y a donc aucune durée à contrôler.
        SegmentKind.CREDITS -> {
            if (start == null) return SubmissionProblem.MISSING_MARK
            if (end == null) null else checkDuration(end - start, MAX_CREDITS_MS)
        }
    }
}

private fun checkDuration(durationMs: Long, maxMs: Long): SubmissionProblem? = when {
    durationMs < 0 -> SubmissionProblem.REVERSED
    // Une durée nulle n'est pas un segment trop court : c'est la façon dont
    // l'API permet de déclarer « cet épisode n'a pas d'intro ». Information
    // utile, et qui évite à tout le monde de la chercher.
    durationMs == 0L -> null
    durationMs < MIN_SEGMENT_MS -> SubmissionProblem.TOO_SHORT
    durationMs > maxMs -> SubmissionProblem.TOO_LONG
    else -> null
}

