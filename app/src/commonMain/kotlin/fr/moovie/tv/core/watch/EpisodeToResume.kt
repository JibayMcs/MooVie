package fr.moovie.tv.core.watch

/** Repère d'un épisode dans une série. */
data class EpisodeRef(val season: Int, val episode: Int)

/**
 * Épisode sur lequel ouvrir la fiche d'une série.
 *
 * La fiche s'ouvrait toujours sur la première saison, épisode inconnu — sur une
 * série de huit saisons suivie depuis des semaines, c'est le seul endroit où on
 * ne veut *pas* atterrir. Toutes les données nécessaires existaient déjà
 * (reprise en cours, épisodes vus) ; c'est la sélection initiale qui les
 * ignorait.
 *
 * L'ordre des règles compte :
 *
 *  1. un épisode **entamé** l'emporte sur tout — c'est celui qu'on venait finir ;
 *  2. sinon, on prend la **suite du dernier vu**, au sens (saison, épisode) et
 *     non au sens chronologique de visionnage : quelqu'un qui rattrape un
 *     épisode manqué du milieu ne veut pas être renvoyé au milieu de la série ;
 *  3. rien de vu : le premier épisode de la première saison.
 *
 * Le numéro rendu peut dépasser le dernier épisode de la saison (dernier épisode
 * d'une saison terminée) : à l'appelant de le rabattre sur la saison suivante,
 * lui seul connaît la longueur réelle des saisons.
 */
fun episodeToResume(
    inProgress: EpisodeRef?,
    watched: Set<EpisodeRef>,
    firstSeason: Int,
): EpisodeRef {
    inProgress?.let { return it }

    val last = watched.maxWithOrNull(compareBy({ it.season }, { it.episode }))
        ?: return EpisodeRef(firstSeason, 1)

    return EpisodeRef(last.season, last.episode + 1)
}

/**
 * Parse une clé d'épisode (`tv:1399:s2e5`) en [EpisodeRef].
 *
 * Rend null pour tout ce qui n'est pas un épisode de la série visée : clés de
 * film, clés d'une autre série, ou format inattendu venu d'une version
 * antérieure. Un `null` est traité comme « pas d'information », jamais comme un
 * épisode 0.
 */
fun parseEpisodeKey(key: String, tmdbId: Int): EpisodeRef? {
    val prefix = "tv:$tmdbId:s"
    if (!key.startsWith(prefix)) return null
    val rest = key.removePrefix(prefix)
    val season = rest.substringBefore('e', "").toIntOrNull() ?: return null
    val episode = rest.substringAfter('e', "").toIntOrNull() ?: return null
    return EpisodeRef(season, episode)
}
