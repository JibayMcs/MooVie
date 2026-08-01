package fr.moovie.tv.core.format

/**
 * Durée lisible par un humain : `1h36`, `2h45`, `45 min`.
 *
 * « 165 min » oblige à faire la division de tête au moment précis où on décide
 * si on a le temps de lancer le film. Au-delà de l'heure on donne donc des
 * heures, en dessous des minutes.
 *
 * `min` n'est pas traduit : l'abréviation est la même en français, en anglais et
 * en espagnol. Ce qui l'entoure, en revanche, passe par les ressources — d'où
 * une fonction qui rend la durée seule et jamais une phrase.
 *
 * @return null si la durée est inconnue ou nulle, pour que l'appelant puisse
 *   simplement ne rien afficher plutôt que d'afficher « 0 min ».
 */
fun formatDuration(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$minutes min"
        // Pile deux heures : « 2h » se lit mieux que « 2h00 ».
        rest == 0 -> "${hours}h"
        else -> "${hours}h${rest.toString().padStart(2, '0')}"
    }
}
