package fr.moovie.tv.data.sync

import fr.moovie.tv.data.backup.BackupJson
import fr.moovie.tv.data.backup.BackupRepository
import fr.moovie.tv.data.backup.ImportMode
import fr.moovie.tv.data.backup.ImportReport
import fr.moovie.tv.data.backup.MoovieBackup

/**
 * Second port : **ce qu'on synchronise**, vu par le moteur.
 *
 * L'hexagone a deux frontières, pas une. Sans celle-ci le moteur dépendrait de
 * [BackupRepository], donc de DataStore, donc d'un appareil — et la seule façon
 * de l'éprouver serait de fabriquer deux téléphones. Avec, il s'exécute en
 * mémoire, et le test peut décrire deux appareils qui ne se voient jamais.
 */
interface SyncSubject {
    /** L'état local, prêt à publier. */
    suspend fun snapshot(now: Long): MoovieBackup

    /** Fusionne l'état d'un autre appareil et rend ce qui a bougé. */
    suspend fun merge(incoming: MoovieBackup): ImportReport
}

/**
 * L'adaptateur qui branche le moteur sur les données réelles.
 *
 * Il porte aussi la seule règle métier de la frontière : **les réglages et les
 * clés d'API ne voyagent pas.** Une clé TMDB dans un fichier de synchro serait
 * un secret de plus sur un disque tiers, pour rien ; et le résolveur DoH ou
 * l'ordre des hébergeurs sont propres à la machine. Ce qui voyage, c'est ce
 * qu'on regarde.
 *
 * Les mettre à null suffit : l'import les ignore quand ils sont absents, c'est
 * le chemin déjà emprunté par une sauvegarde exportée sans ses clés.
 */
class BackupSyncSubject(
    private val backup: BackupRepository,
) : SyncSubject {

    override suspend fun snapshot(now: Long): MoovieBackup =
        backup.export(includeApiKey = false, now = now).stripped()

    override suspend fun merge(incoming: MoovieBackup): ImportReport {
        // Avant de fusionner : notre horloge prend acte de ce qu'on vient de
        // lire. Sans ça, une décision prise juste après pourrait porter un
        // horodatage *inférieur* à celui d'en face — et se faire écraser par ce
        // qu'elle était censée corriger.
        incoming.profiles.forEach { profile ->
            profile.watchedAt.values.forEach(MoovieClock::observe)
            profile.resumeRemovedAt.values.forEach(MoovieClock::observe)
            profile.watchlistRemovedAt.values.forEach(MoovieClock::observe)
            profile.resume.forEach { MoovieClock.observe(it.updatedAt) }
            profile.watchlist.forEach { MoovieClock.observe(it.addedAt) }
        }
        return backup.import(incoming.stripped(), ImportMode.MERGE)
    }

    private fun MoovieBackup.stripped() = copy(
        settings = null,
        tmdbApiKey = null,
        introDbApiKey = null,
    )
}

/** Ce qu'une synchro a fait, pour l'écran et pour le journal. */
data class SyncReport(
    /** Fichiers d'autres appareils lus et fusionnés. */
    val devicesSeen: Int,
    val merged: ImportReport,
    /**
     * Écart mesuré entre l'horloge du serveur et la nôtre, en ms.
     *
     * Positif = notre horloge retarde. Ce n'est pas une statistique : c'est la
     * correction à appliquer aux horodatages qu'on écrira ensuite, et le seul
     * moyen qu'on ait de rendre la dérive d'horloge inoffensive sans exiger que
     * tout le monde soit à l'heure.
     */
    val clockOffset: Long,
)

/**
 * Le moteur de synchro. **Il ne connaît que [SyncStore]** — ni B2, ni HTTP, ni
 * identifiants. C'est ce qui permet de l'éprouver avec un dépôt en mémoire.
 *
 * Le protocole est celui de l'anti-entropie : on lit ce que les autres ont
 * publié, on fusionne, puis on republie l'état fusionné. Pas de maître, pas
 * d'ordre global, et deux appareils qui ne se voient jamais en même temps
 * convergent quand même — c'est tout l'intérêt d'un dépôt qui garde le fichier
 * entre les deux.
 *
 * **Publier après avoir fusionné**, et pas avant : notre fichier porte alors la
 * vue complète, si bien qu'un troisième appareil converge en le lisant seul au
 * lieu de devoir tous les collecter.
 */
class SyncEngine(
    private val store: SyncStore,
    private val deviceId: String,
    private val subject: SyncSubject,
) {

    suspend fun sync(now: Long): SyncReport {
        val mine = fileNameFor(deviceId)
        val others = store.list().filterNot { it.name == mine }

        var merged = ImportReport(0, 0, 0, 0, 0, 0)
        var seen = 0
        for (file in others) {
            val raw = store.read(file.name) ?: continue
            val remote = BackupJson.decode(raw) ?: continue
            merged += subject.merge(remote)
            seen++
        }

        val snapshot = subject.snapshot(now)
        val serverTime = store.write(mine, BackupJson.encode(snapshot))

        return SyncReport(devicesSeen = seen, merged = merged, clockOffset = serverTime - now)
    }

    companion object {
        /**
         * Un fichier par appareil : personne n'écrit sur le fichier d'un autre,
         * donc aucun conflit d'écriture, donc aucun verrou à demander à un
         * stockage qui ne sait pas en offrir.
         */
        fun fileNameFor(deviceId: String) = "moovie-sync-$deviceId.json"
    }
}
