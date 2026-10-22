package fr.moovie.tv.data.watch

import fr.moovie.tv.core.watch.EpisodeRef

/**
 * Épisode en cours le plus récent d'une série.
 *
 * Une série peut avoir plusieurs entrées en cours : un épisode « à suivre »
 * posé à la fin du précédent mais jamais lancé (S1E9, un bonus que personne ne
 * diffuse) et l'épisode réellement regardé ensuite (S2E1). La carte de
 * l'accueil montre le plus récent ; la fiche doit s'ouvrir sur le même. Le tri
 * est fait ici plutôt que supposé de l'appelant, comme pour la carte.
 */
fun latestInProgress(entries: List<ResumeEntry>, tmdbId: Int): EpisodeRef? =
    entries
        .filter { it.isTv && it.tmdbId == tmdbId && it.season > 0 }
        .maxByOrNull { it.updatedAt }
        ?.let { EpisodeRef(it.season, it.episode) }
