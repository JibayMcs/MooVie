package fr.moovie.tv.data.download

/**
 * Ce qu'un **titre** a de téléchargé, tous épisodes confondus.
 *
 * Une affiche ne montre ni saison ni épisode : elle a besoin d'un résumé, pas
 * de la liste. D'où ce condensé, calculé une fois par écran plutôt que
 * recalculé par chaque carte.
 */
data class TitleDownloads(
    /** Épisodes (ou le film) prêts hors ligne. */
    val ready: Int,
    /** En cours ou en attente. */
    val active: Int,
    /** Avancement moyen de ce qui est en cours, 0 s'il n'y a rien. */
    val progress: Float,
) {
    val any: Boolean get() = ready > 0 || active > 0
}

/**
 * Clé de titre d'un téléchargement : `movie:550`, `tv:1396`.
 *
 * Lue dans la clé et non dans `isTv` — le champ est facultatif et un chemin
 * d'écriture l'a déjà oublié, ce qui avait éclaté une série en autant de
 * groupes que d'épisodes. La clé, elle, est l'identité.
 */
fun Download.titleKey(): String =
    if (key.startsWith("tv:")) key.split(':').take(2).joinToString(":") else key

/** Résumé par titre, prêt à être consulté par une grille d'affiches. */
fun List<Download>.byTitle(): Map<String, TitleDownloads> =
    groupBy { it.titleKey() }.mapValues { (_, items) ->
        val active = items.filter {
            it.state == DownloadState.RUNNING || it.state == DownloadState.QUEUED
        }
        TitleDownloads(
            ready = items.count { it.state == DownloadState.DONE },
            active = active.size,
            // Moyenne des seuls téléchargements en cours : y mêler ceux qui sont
            // finis ferait remonter la barre à mesure qu'on télécharge, ce qui
            // se lirait comme un avancement de la saison et non du transfert.
            progress = if (active.isEmpty()) 0f else active.map { it.progress }.average().toFloat(),
        )
    }

/**
 * Épisodes d'une saison déjà prêts hors ligne.
 *
 * Compté sur les clés (`tv:<id>:s<N>e<M>`) plutôt que sur un champ de saison,
 * que le modèle ne porte pas — et qu'il n'a pas besoin de porter, la clé le
 * disant déjà.
 */
fun List<Download>.readyInSeason(tmdbId: Int, season: Int): Int {
    val prefix = "tv:$tmdbId:s${season}e"
    return count { it.key.startsWith(prefix) && it.state == DownloadState.DONE }
}
