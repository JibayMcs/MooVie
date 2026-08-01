package fr.moovie.tv.core.sources.usecase

/**
 * En dessous de cette fraction de la durée attendue, un flux n'est pas le média
 * demandé.
 *
 * Volontairement bas : une copie peut légitimement être plus courte que la fiche
 * TMDB (générique coupé, montage différent, arrondi de la durée annoncée). Ce
 * qu'on vise, ce sont les flux de remplacement — logo animé, bande-annonce,
 * message « vidéo indisponible » — qui font quelques secondes là où le film en
 * fait plus de deux heures. Sur Dune (155 min annoncées), trois liens
 * « premium » mesuraient moins d'une minute.
 */
const val MIN_DURATION_RATIO = 0.5

/**
 * La durée mesurée d'un flux est-elle compatible avec celle attendue ?
 *
 * Deux principes de prudence, symétriques de ceux du filtre de type de contenu :
 * on n'écarte que ce qu'on a **mesuré** et qui est **manifestement** trop court.
 * Une durée inconnue (flux non HLS, playlist illisible) ou une durée attendue
 * absente laissent passer — refuser par défaut écarterait des sources valides.
 */
fun isDurationAcceptable(streamSeconds: Double?, expectedMinutes: Int?): Boolean {
    val expected = expectedMinutes?.takeIf { it > 0 } ?: return true
    val measured = streamSeconds ?: return true
    // Mesuré à zéro : la playlist existe mais ne contient aucun segment.
    if (measured <= 0.0) return false
    return measured >= expected * 60.0 * MIN_DURATION_RATIO
}
