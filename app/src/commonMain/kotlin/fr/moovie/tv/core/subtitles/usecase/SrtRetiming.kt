package fr.moovie.tv.core.subtitles.usecase

/**
 * Réécrit les horodatages d'un fichier SRT en y appliquant [timing].
 *
 * **Pourquoi réécrire le fichier plutôt que régler le lecteur.** Le décalage de
 * sous-titres existe côté libVLC (`setSpuDelay`) mais pas côté Media3, et
 * *aucun* des deux ne sait étirer la cadence. Or c'est l'étirement qui répare la
 * dérive dominante (23,976 contre 25). Posséder le fichier est donc le seul
 * moyen d'obtenir le même comportement sur les deux lecteurs, avec un seul code.
 *
 * OpenSubtitles sait aussi convertir à la livraison (`in_fps` / `out_fps` /
 * `timeshift` sur `/download`) — **volontairement inutilisé** : chaque
 * ajustement repasserait alors par un téléchargement, donc par le quota, alors
 * que régler un décalage se fait par petites touches successives. Ici on
 * retravaille le fichier déjà payé, autant de fois qu'on veut, hors ligne.
 *
 * Tout ce qui n'est pas un horodatage est rendu **intact** : numérotation,
 * texte, balises, coordonnées de position en fin de ligne, fins de ligne
 * Windows, BOM. On ne reformate pas un fichier qu'on ne fait que recaler.
 */
fun retimeSrt(srt: String, timing: SubtitleTiming): String {
    // Un fichier de 46 Ko n'a aucune raison d'être réécrit pour ne rien changer.
    if (timing.isIdentity) return srt

    return CUE.replace(srt) { match ->
        val (start, arrow, end) = match.destructured.toList().let {
            Triple(it[0], it[1], it[2])
        }
        shift(start, timing) + arrow + shift(end, timing)
    }
}

/**
 * Un horodatage : `HH:MM:SS,mmm`, ou `.` en séparateur comme le fait WebVTT.
 * Les heures peuvent dépasser deux chiffres sur les très longs métrages.
 */
private val TIME = """\d{1,3}:\d{2}:\d{2}[,.]\d{1,3}"""

/** `début --> fin`, la flèche et ses espaces conservés tels quels. */
private val CUE = Regex("""($TIME)(\s*-->\s*)($TIME)""")

private fun shift(stamp: String, timing: SubtitleTiming): String {
    val separator = if (stamp.contains(',')) ',' else '.'
    val shifted = timing.applyTo(parseStamp(stamp))
    // Un décalage négatif peut rejeter une réplique avant le début du média :
    // on la ramène à zéro plutôt que de produire un horodatage négatif, qu'aucun
    // lecteur ne sait lire.
    return formatStamp(shifted.coerceAtLeast(0), separator)
}

private fun parseStamp(stamp: String): Long {
    val (time, millis) = stamp.split(',', '.')
    val (h, m, s) = time.split(':').map { it.toLong() }
    // « ,5 » vaut 500 ms, pas 5 : on complète à droite comme une fraction.
    val ms = millis.padEnd(3, '0').take(3).toLong()
    return ((h * 60 + m) * 60 + s) * 1000 + ms
}

private fun formatStamp(totalMs: Long, separator: Char): String {
    val ms = totalMs % 1000
    val totalSeconds = totalMs / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return "${pad(h)}:${pad(m)}:${pad(s)}$separator${pad(ms, 3)}"
}

private fun pad(value: Long, width: Int = 2): String =
    value.toString().padStart(width, '0')
