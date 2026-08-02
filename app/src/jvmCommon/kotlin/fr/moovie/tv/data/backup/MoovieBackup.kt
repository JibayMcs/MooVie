package fr.moovie.tv.data.backup

import fr.moovie.tv.data.watch.HistoryEntry
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.WatchlistEntry
import kotlinx.serialization.Serializable

/**
 * Contenu d'un fichier de sauvegarde Moo-vie.
 *
 * On exporte le **sens** (des reprises, une liste, un historique) et non le
 * contenu brut du magasin DataStore : ce dernier changera de forme au premier
 * refactor, alors qu'un fichier de sauvegarde doit rester lisible par les
 * versions suivantes. C'est aussi ce qui le rend inspectable à l'œil dans un
 * éditeur de texte.
 *
 * [version] est là pour ça : une 2.0 qui déplacerait un champ saura toujours
 * lire un export de la 1.11, et refusera proprement un format qu'elle ne
 * connaît pas plutôt que d'importer n'importe quoi.
 *
 * Le **cache des sources n'est pas exporté** : il périme en six heures et ne
 * décrit rien de l'utilisateur.
 */
@Serializable
data class MoovieBackup(
    val version: Int = FORMAT_VERSION,
    /** Millisecondes epoch, pour l'afficher à l'aperçu avant import. */
    val exportedAt: Long = 0,
    /** Version de l'app qui a produit le fichier — utile en diagnostic. */
    val appVersion: String = "",
    /** « Android TV » / « Desktop » : dit d'où vient la sauvegarde. */
    val platform: String = "",

    val resume: List<ResumeEntry> = emptyList(),
    val watchlist: List<WatchlistEntry> = emptyList(),
    /** Clés vues (`movie:123`, `tv:1396:s2e5`). */
    val watched: List<String> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    /** Piste audio retenue par titre (`tv:1396` → « French »). */
    val audioTracks: Map<String, String> = emptyMap(),

    /**
     * Clé TMDB. **C'est un secret** : sans elle l'import ne dispenserait pas de
     * la saisir au clavier virtuel, ce qui vide de son sens le parcours de
     * premier lancement. L'utilisateur est prévenu au moment de l'export.
     */
    val tmdbApiKey: String? = null,
    val settings: BackupSettings? = null,
) {
    companion object {
        /** À incrémenter dès qu'un champ change de sens, jamais pour un ajout. */
        const val FORMAT_VERSION = 1
    }
}

/** Réglages transportés. Nommés, pas sérialisés en vrac, pour rester lisibles. */
@Serializable
data class BackupSettings(
    val streamLanguage: String? = null,
    val appLanguage: String? = null,
    val disabledProviders: List<String> = emptyList(),
    val providerOrder: List<String> = emptyList(),
    val dohEnabled: Boolean? = null,
    val dohProvider: String? = null,
    val skipIntroOutro: Boolean? = null,
    val autoPlayNext: Boolean? = null,
    val playerClock: Boolean? = null,
    val hideHistoryWidgets: Boolean? = null,
    val updateInterval: String? = null,
    val screensaverDelay: String? = null,
)

/** Ce qu'un fichier contient, pour l'aperçu **avant** d'agir. */
data class BackupSummary(
    val watched: Int,
    val resume: Int,
    val watchlist: Int,
    val history: Int,
    val exportedAt: Long,
    val appVersion: String,
    val platform: String,
    val hasApiKey: Boolean,
    val hasSettings: Boolean,
)

fun MoovieBackup.summary() = BackupSummary(
    watched = watched.size,
    resume = resume.size,
    watchlist = watchlist.size,
    history = history.size,
    exportedAt = exportedAt,
    appVersion = appVersion,
    platform = platform,
    hasApiKey = !tmdbApiKey.isNullOrBlank(),
    hasSettings = settings != null,
)
