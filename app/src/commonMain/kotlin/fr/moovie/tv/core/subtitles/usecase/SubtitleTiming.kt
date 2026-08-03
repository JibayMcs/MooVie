package fr.moovie.tv.core.subtitles.usecase

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Recalage d'un sous-titre sur le flux joué : une mise à l'échelle, puis un
 * décalage.
 *
 * @param scale rapport de cadences. 1.0 = aucune correction.
 * @param offsetMs décalage constant, réglé à la main par l'utilisateur.
 */
data class SubtitleTiming(
    val scale: Double = 1.0,
    val offsetMs: Long = 0,
) {
    /** Vrai si le sous-titre est joué tel quel. */
    val isIdentity: Boolean get() = offsetMs == 0L && !scaled

    /** Vrai si une correction de cadence s'applique. */
    val scaled: Boolean get() = abs(scale - 1.0) >= SCALE_EPSILON

    /** Horodatage d'origine → horodatage à afficher. */
    fun applyTo(timeMs: Long): Long = (timeMs * scale).roundToLong() + offsetMs

    companion object {
        /**
         * En deçà, la correction ne vaut pas la peine d'être appliquée.
         *
         * Les cadences sont déclarées en flottants (23.976 pour 24000/1001) et
         * mesurées avec du bruit ; sans ce seuil, un écart insignifiant
         * introduirait une dérive là où il n'y en avait aucune. 0,1 % vaut moins
         * de quatre secondes sur un film d'une heure quarante-cinq, alors que le
         * vrai cas — 23,976 contre 25 — en pèse 4,3 %.
         */
        const val SCALE_EPSILON = 0.001

        val None = SubtitleTiming()
    }
}

/**
 * Déduit la correction de cadence entre un sous-titre et le flux joué.
 *
 * **C'est la pièce qui remplace le `moviehash`.** L'appariement exact
 * d'OpenSubtitles repose sur une empreinte du *fichier* vidéo, impossible à
 * calculer sur nos flux : ils sont segmentés (HLS) ou refusent les requêtes par
 * plage. Le décalage est donc le cas normal, pas l'accident.
 *
 * Or la désynchronisation la plus courante n'est pas un décalage constant, c'est
 * un écart de cadence — un sous-titre calé sur 23,976 i/s joué sur un flux à
 * 25 i/s dérive *progressivement*, d'environ quatre minutes trente en fin de
 * film. Aucun curseur de décalage ne rattrape ça, seule une mise à l'échelle le
 * peut.
 *
 * Le raisonnement : un horodatage vaut `image / cadence`. La même image tombe
 * donc à `t × (cadenceSousTitre / cadenceFlux)` sur notre flux.
 *
 * Rend [SubtitleTiming.None] si l'une des deux cadences manque — on ne corrige
 * pas au jugé, une correction erronée est pire que pas de correction.
 */
fun timingFor(
    subtitleFps: Double?,
    streamFps: Double?,
    offsetMs: Long = 0,
): SubtitleTiming {
    val sub = subtitleFps?.takeIf { it > 0 }
    val stream = streamFps?.takeIf { it > 0 }
    if (sub == null || stream == null) return SubtitleTiming(offsetMs = offsetMs)
    return SubtitleTiming(scale = sub / stream, offsetMs = offsetMs)
}

/**
 * Cadences usuelles, pour proposer une correction quand le flux ne déclare pas
 * la sienne : l'utilisateur choisit alors « ce sous-titre est en 25, ma vidéo
 * est en 23,976 » plutôt que de tâtonner au curseur.
 */
val COMMON_FPS = listOf(23.976, 24.0, 25.0, 29.97, 30.0, 50.0, 59.94, 60.0)

/**
 * Ramène une cadence mesurée à la valeur normalisée la plus proche.
 *
 * Un lecteur rend 23.976023976023978 ou 24.999999 selon le conteneur ; les
 * comparer brutalement à la valeur déclarée par le catalogue ferait échouer des
 * correspondances évidentes. Rend la valeur d'origine si rien n'est assez
 * proche — mieux vaut une cadence inhabituelle qu'une cadence inventée.
 */
fun normalizeFps(fps: Double?, tolerance: Double = 0.05): Double? {
    val value = fps?.takeIf { it > 0 } ?: return null
    return COMMON_FPS.minByOrNull { abs(it - value) }
        ?.takeIf { abs(it - value) <= tolerance }
        ?: value
}
