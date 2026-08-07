package fr.moovie.tv.data.backup

import fr.moovie.tv.data.watch.HistoryEntry
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.WatchlistEntry

/** Ce que l'utilisateur choisit sur l'écran d'aperçu. */
enum class ImportMode {
    /** Complète ce qui est là. Rien n'est perdu. */
    MERGE,

    /** Restaure l'état sauvegardé et écarte le reste. */
    REPLACE,
}

/** État de suivi d'un appareil, tel qu'il est avant ou après un import. */
data class WatchState(
    val resume: List<ResumeEntry> = emptyList(),
    val watchlist: List<WatchlistEntry> = emptyList(),
    val watched: Set<String> = emptySet(),
    val history: List<HistoryEntry> = emptyList(),
    val audioTracks: Map<String, String> = emptyMap(),
)

/** Bilan affiché après l'import : ce qui a bougé, et ce qui était déjà là. */
data class ImportReport(
    val watchedAdded: Int,
    val watchedAlreadyThere: Int,
    val resumeAdded: Int,
    val resumeUpdated: Int,
    val watchlistAdded: Int,
    val historyAdded: Int,
) {
    /** Cumul sur plusieurs profils : l'écran d'après parle de l'appareil entier. */
    operator fun plus(other: ImportReport) = ImportReport(
        watchedAdded = watchedAdded + other.watchedAdded,
        watchedAlreadyThere = watchedAlreadyThere + other.watchedAlreadyThere,
        resumeAdded = resumeAdded + other.resumeAdded,
        resumeUpdated = resumeUpdated + other.resumeUpdated,
        watchlistAdded = watchlistAdded + other.watchlistAdded,
        historyAdded = historyAdded + other.historyAdded,
    )
}

/**
 * Applique une sauvegarde à l'état d'un appareil.
 *
 * Pure et testable : c'est la seule partie du dispositif qui puisse détruire
 * des données, et c'est aussi celle qu'on ne peut pas éprouver à la main sans
 * fabriquer deux appareils.
 *
 * En [ImportMode.MERGE], la règle est **la plus récente gagne**, pas « le
 * fichier gagne » : on fusionne deux appareils utilisés en parallèle, et
 * l'épisode regardé hier sur la TV du salon ne doit pas être écrasé par une
 * sauvegarde d'avant-hier. D'où la comparaison sur `updatedAt` / `watchedAt`.
 *
 * Un titre **vu** ne redevient jamais non-vu par fusion : l'information « j'ai
 * fini ça » est plus forte que son absence, qui peut simplement vouloir dire
 * « pas encore synchronisé ».
 */
fun mergeBackup(
    current: WatchState,
    backup: MoovieBackup,
    mode: ImportMode,
): Pair<WatchState, ImportReport> = mergeWatchState(
    current = current,
    incoming = WatchState(
        resume = backup.resume,
        watchlist = backup.watchlist,
        watched = backup.watched.toSet(),
        history = backup.history,
        audioTracks = backup.audioTracks,
    ),
    mode = mode,
)

/**
 * Le cœur de la fusion, sur deux états plutôt que sur un fichier.
 *
 * Un fichier v2 porte un état **par profil** : la règle est la même pour chacun,
 * seul l'état d'en face change. Faire dépendre le calcul du format aurait obligé
 * à le dupliquer, alors que c'est la partie qu'on ne veut écrire qu'une fois —
 * c'est la seule qui puisse détruire des données.
 */
fun mergeWatchState(
    current: WatchState,
    incoming: WatchState,
    mode: ImportMode,
): Pair<WatchState, ImportReport> {
    if (mode == ImportMode.REPLACE) {
        return incoming to ImportReport(
            watchedAdded = incoming.watched.size,
            watchedAlreadyThere = 0,
            resumeAdded = incoming.resume.size,
            resumeUpdated = 0,
            watchlistAdded = incoming.watchlist.size,
            historyAdded = incoming.history.size,
        )
    }

    val newlyWatched = incoming.watched.filterNot { it in current.watched }

    // Reprises : une entrée par clé, la plus récemment mise à jour l'emporte.
    val currentResume = current.resume.associateBy { it.key }
    val mergedResume = currentResume.toMutableMap()
    var resumeAdded = 0
    var resumeUpdated = 0
    for (entry in incoming.resume) {
        val existing = mergedResume[entry.key]
        when {
            existing == null -> {
                mergedResume[entry.key] = entry
                resumeAdded++
            }
            entry.updatedAt > existing.updatedAt -> {
                mergedResume[entry.key] = entry
                resumeUpdated++
            }
        }
    }

    val currentLater = current.watchlist.associateBy { it.key }
    val newLater = incoming.watchlist.filterNot { it.key in currentLater }

    // Historique : dédoublonné sur (clé, moment), deux appareils pouvant avoir
    // vu le même épisode à des instants différents — ce sont deux visionnages.
    val seenHistory = current.history.map { it.key to it.watchedAt }.toSet()
    val newHistory = incoming.history.filterNot { (it.key to it.watchedAt) in seenHistory }

    val merged = WatchState(
        resume = mergedResume.values.sortedByDescending { it.updatedAt },
        watchlist = current.watchlist + newLater,
        watched = current.watched + newlyWatched,
        history = (current.history + newHistory).sortedByDescending { it.watchedAt },
        // Le choix de langue de l'appareil courant prime : il vient d'un geste
        // récent de l'utilisateur, là où la sauvegarde reflète un autre poste.
        audioTracks = incoming.audioTracks + current.audioTracks,
    )

    return merged to ImportReport(
        watchedAdded = newlyWatched.size,
        watchedAlreadyThere = incoming.watched.size - newlyWatched.size,
        resumeAdded = resumeAdded,
        resumeUpdated = resumeUpdated,
        watchlistAdded = newLater.size,
        historyAdded = newHistory.size,
    )
}
