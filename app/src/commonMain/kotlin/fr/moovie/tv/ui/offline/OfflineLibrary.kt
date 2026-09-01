package fr.moovie.tv.ui.offline

import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.download.DownloadState
import fr.moovie.tv.data.download.titleKey
import fr.moovie.tv.data.download.titleKeyOf

/**
 * Le téléchargement à lire pour ce titre, ou null s'il n'y en a aucun.
 *
 * ### Ce que ça répare
 *
 * L'historique reste consultable hors ligne — c'est une donnée locale — mais
 * ses vignettes menaient à la fiche du titre, qui a besoin de TMDB. Sans
 * réseau, chaque appui ouvrait donc un écran qui ne chargerait jamais : un
 * cul-de-sac que rien n'annonçait, sur le seul écran qui continuait de
 * fonctionner.
 *
 * Hors ligne, la même vignette lit le fichier quand il est là. Rien ne se passe
 * quand il n'y est pas, ce qui est décevant mais honnête — et incomparablement
 * mieux qu'une fiche vide dont il faut ressortir.
 *
 * `DONE` uniquement : un téléchargement à moitié écrit n'est pas lisible, et le
 * lancer donnerait une erreur de lecture là où la vraie réponse est « pas
 * encore ». Le plus petit épisode par clé, pour qu'une série ouvre son premier
 * épisode disponible plutôt qu'un au hasard.
 */
fun List<Download>.playableFor(tmdbId: Int, isTv: Boolean): Download? {
    val cible = titleKeyOf(tmdbId, isTv)
    return filter { it.state == DownloadState.DONE && it.titleKey() == cible }
        .minByOrNull { it.key }
}
