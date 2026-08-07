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
    /**
     * Horodatage de la dernière décision sur chaque clé, **retrait compris**.
     * Absent = donnée d'avant les pierres tombales, lue à 0.
     */
    val watchedAt: Map<String, Long> = emptyMap(),
    val history: List<HistoryEntry> = emptyList(),
    val audioTracks: Map<String, String> = emptyMap(),
    /** Dates de retrait d'une reprise. Une clé ici et dans [resume] est impossible. */
    val resumeRemovedAt: Map<String, Long> = emptyMap(),
    /** Dates de retrait de la liste « à voir ». */
    val watchlistRemovedAt: Map<String, Long> = emptyMap(),
)

/**
 * Tranche entre les deux versions d'une même clé : **la décision la plus récente
 * gagne**, un retrait comptant comme une décision à part entière.
 *
 * À égalité — typiquement deux zéros, c'est-à-dire deux données d'avant les
 * pierres tombales — la présence l'emporte. C'est l'union d'autrefois, et le
 * seul choix tenable quand ni l'un ni l'autre ne sait dater ses suppressions.
 *
 * Écrit une fois pour la reprise et la liste : la règle est la même, seul le
 * type de ce qu'on garde change.
 */
private fun <T> latest(
    mine: T?,
    minePresentAt: Long,
    mineRemovedAt: Long,
    theirs: T?,
    theirsPresentAt: Long,
    theirsRemovedAt: Long,
): T? {
    val myPick = if (mineRemovedAt > minePresentAt) null else mine
    val myWhen = maxOf(minePresentAt, mineRemovedAt)
    val theirPick = if (theirsRemovedAt > theirsPresentAt) null else theirs
    val theirWhen = maxOf(theirsPresentAt, theirsRemovedAt)
    return when {
        myWhen > theirWhen -> myPick
        theirWhen > myWhen -> theirPick
        else -> myPick ?: theirPick
    }
}

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
 * Pour les « vus », la décision **la plus récente** gagne, y compris quand c'est
 * un retrait. C'est un revirement, et il mérite son explication : la règle
 * d'avant faisait l'union, un titre vu ne redevenant jamais non-vu. Ce n'était
 * pas un caprice — sans date sur les suppressions, une absence pouvait tout
 * aussi bien vouloir dire « pas encore synchronisé », et ressusciter était le
 * moindre mal. Les pierres tombales lèvent cette ambiguïté : on sait désormais
 * *quand* quelqu'un a démarqué, donc on peut le respecter.
 *
 * À égalité d'horodatage — deux zéros, c'est-à-dire deux données d'avant — on
 * refait l'union. L'ancien comportement reste donc exact pour les vieux
 * fichiers, qui ne peuvent rien dire de leurs suppressions.
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

    // Chaque clé connue d'un côté ou de l'autre, présente ou enterrée.
    val watchedKeys = current.watched + incoming.watched +
        current.watchedAt.keys + incoming.watchedAt.keys
    val mergedWatched = watchedKeys.filterTo(mutableSetOf()) { key ->
        val mine = current.watchedAt[key] ?: 0L
        val theirs = incoming.watchedAt[key] ?: 0L
        when {
            theirs > mine -> key in incoming.watched
            mine > theirs -> key in current.watched
            // Égalité : aucune des deux parties ne sait dater sa décision.
            else -> key in current.watched || key in incoming.watched
        }
    }
    val mergedWatchedAt = stampUnion(current.watchedAt, incoming.watchedAt)
    val newlyWatched = mergedWatched.filterNot { it in current.watched }

    // Reprises : la plus récente décision par clé, un retrait en étant une.
    val currentResume = current.resume.associateBy { it.key }
    val incomingResume = incoming.resume.associateBy { it.key }
    val resumeKeys = currentResume.keys + incomingResume.keys +
        current.resumeRemovedAt.keys + incoming.resumeRemovedAt.keys
    val mergedResume = resumeKeys.mapNotNull { key ->
        latest(
            mine = currentResume[key],
            minePresentAt = currentResume[key]?.updatedAt ?: 0L,
            mineRemovedAt = current.resumeRemovedAt[key] ?: 0L,
            theirs = incomingResume[key],
            theirsPresentAt = incomingResume[key]?.updatedAt ?: 0L,
            theirsRemovedAt = incoming.resumeRemovedAt[key] ?: 0L,
        )
    }
    val resumeAdded = mergedResume.count { it.key !in currentResume }
    val resumeUpdated = mergedResume.count { kept ->
        currentResume[kept.key]?.let { it.updatedAt < kept.updatedAt } == true
    }
    val mergedResumeRemovedAt = stampUnion(current.resumeRemovedAt, incoming.resumeRemovedAt)

    val currentLater = current.watchlist.associateBy { it.key }
    val incomingLater = incoming.watchlist.associateBy { it.key }
    val laterKeys = currentLater.keys + incomingLater.keys +
        current.watchlistRemovedAt.keys + incoming.watchlistRemovedAt.keys
    val mergedWatchlist = laterKeys.mapNotNull { key ->
        latest(
            mine = currentLater[key],
            minePresentAt = currentLater[key]?.addedAt ?: 0L,
            mineRemovedAt = current.watchlistRemovedAt[key] ?: 0L,
            theirs = incomingLater[key],
            theirsPresentAt = incomingLater[key]?.addedAt ?: 0L,
            theirsRemovedAt = incoming.watchlistRemovedAt[key] ?: 0L,
        )
    }
    val newLater = mergedWatchlist.filterNot { it.key in currentLater }
    val mergedWatchlistRemovedAt = stampUnion(current.watchlistRemovedAt, incoming.watchlistRemovedAt)

    // Historique : dédoublonné sur (clé, moment), deux appareils pouvant avoir
    // vu le même épisode à des instants différents — ce sont deux visionnages.
    val seenHistory = current.history.map { it.key to it.watchedAt }.toSet()
    val newHistory = incoming.history.filterNot { (it.key to it.watchedAt) in seenHistory }

    val merged = WatchState(
        resume = mergedResume.sortedByDescending { it.updatedAt },
        watchlist = mergedWatchlist,
        resumeRemovedAt = mergedResumeRemovedAt,
        watchlistRemovedAt = mergedWatchlistRemovedAt,
        watched = mergedWatched,
        watchedAt = mergedWatchedAt,
        history = (current.history + newHistory).sortedByDescending { it.watchedAt },
        // Le choix de langue de l'appareil courant prime : il vient d'un geste
        // récent de l'utilisateur, là où la sauvegarde reflète un autre poste.
        audioTracks = incoming.audioTracks + current.audioTracks,
    )

    return merged to ImportReport(
        watchedAdded = newlyWatched.size,
        watchedAlreadyThere = incoming.watched.count { it in current.watched },
        resumeAdded = resumeAdded,
        resumeUpdated = resumeUpdated,
        watchlistAdded = newLater.size,
        historyAdded = newHistory.size,
    )
}

/** Union de deux tables d'horodatage : la plus récente date pour chaque clé. */
private fun stampUnion(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> =
    (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0L, b[it] ?: 0L) }
