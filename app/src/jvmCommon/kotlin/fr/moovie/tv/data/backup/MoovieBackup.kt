package fr.moovie.tv.data.backup

import fr.moovie.tv.data.home.HomeLayoutEntry
import fr.moovie.tv.data.watch.HistoryEntry
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.TitleMeta
import fr.moovie.tv.data.watch.WatchlistEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    /**
     * Dates de retrait de l'historique.
     *
     * Ajoutée après coup, donc par défaut vide : un fichier écrit par une
     * version antérieure ne sait rien de ses suppressions, et la fusion doit
     * continuer de faire l'union pour lui — un vieux fichier ne doit rien
     * effacer.
     */
    val historyRemovedAt: Map<String, Long> = emptyMap(),
    /** Piste audio retenue par titre (`tv:1396` → « French »). */
    val audioTracks: Map<String, String> = emptyMap(),
    /**
     * Fiches relevées à l'ouverture d'un titre (nom, affiche, genres). Sans
     * elles, l'historique importé s'afficherait en vignettes vides tant que
     * l'utilisateur n'a pas rouvert chaque fiche.
     */
    val titles: Map<String, TitleMeta> = emptyMap(),

    /**
     * Disposition de l'accueil : rangées, ordre, genres épinglés.
     *
     * C'est du contenu, pas un réglage — d'où sa place ici et non dans
     * [BackupSettings]. Restaurer une sauvegarde sur une machine neuve doit
     * rendre *son* accueil, pas celui d'origine avec les bons films dedans.
     * Vide = le fichier n'en parle pas (sauvegarde d'avant cette version), et
     * l'accueil de l'appareil reste alors intact.
     */
    val homeLayout: List<HomeLayoutEntry> = emptyList(),

    /**
     * Clé TMDB. **C'est un secret** : sans elle l'import ne dispenserait pas de
     * la saisir au clavier virtuel, ce qui vide de son sens le parcours de
     * premier lancement. L'utilisateur est prévenu au moment de l'export.
     */
    val tmdbApiKey: String? = null,

    /**
     * Clé TheIntroDB. Secret elle aussi, et **la plus coûteuse à perdre** : elle
     * s'obtient sur un compte, pas en deux clics comme celle de TMDB. Elle suit
     * donc le même interrupteur à l'export — l'utilisateur choisit une fois si
     * ses clés partent, pas une fois par service.
     */
    val introDbApiKey: String? = null,
    val settings: BackupSettings? = null,

    /**
     * Les profils et, pour chacun, ses données (format 2).
     *
     * **Vide = sauvegarde d'avant les profils** : tout ce qui précède part alors
     * dans le profil actif, exactement comme avant. C'est le seul comportement
     * qu'on puisse tenir sans inventer une intention — un fichier v1 ne dit pas
     * de qui il vient.
     *
     * Quand elle est remplie, **elle est la seule source** : les champs de
     * premier niveau restent vides. On avait envisagé d'y recopier le profil
     * actif pour qu'une version antérieure retrouve quelque chose — inutile :
     * [BackupJson.decode] refuse tout fichier dont le format dépasse celui qu'il
     * connaît, donc une 1.15 rejette un v2 en bloc sans jamais regarder dedans.
     * Dupliquer n'aurait acheté que deux états à garder d'accord.
     */
    val profiles: List<BackupProfile> = emptyList(),

    /**
     * Profils supprimés, et quand : identifiant vers l'instant du retrait.
     *
     * Sans ces dates, supprimer un profil ne tenait pas une synchro. Le fichier
     * distant contenait encore le profil, la fusion faisait l'union, et il
     * réapparaissait au relancement suivant. Une absence ne dit pas si l'on a
     * supprimé ou si l'on n'a pas encore reçu — seule une date le dit.
     *
     * Même règle que `resumeRemovedAt` et `watchlistRemovedAt` : la décision la
     * plus récente gagne, et un profil recréé après son retrait l'emporte sur
     * lui.
     */
    val profilesRemovedAt: Map<String, Long> = emptyMap(),
) {
    companion object {
        /**
         * À incrémenter dès qu'un champ change de sens, jamais pour un ajout.
         *
         * Passé à 2 avec les profils : un fichier v2 rangerait ses données là où
         * une v1 ne les cherche pas, et lui en laisser lire la moitié serait
         * pire que de refuser. C'est [BackupJson.decode] qui refuse.
         */
        const val FORMAT_VERSION = 2
    }
}

/**
 * Un profil et ce qu'il a regardé, tel qu'il voyage dans le fichier.
 *
 * L'identifiant est transporté pour qu'un aller-retour entre deux appareils
 * retombe sur le même profil au lieu d'en créer un jumeau à chaque import.
 */
@Serializable
data class BackupProfile(
    val id: String,
    /** Vide pour le profil d'origine, qui porte le libellé traduit. */
    val name: String = "",
    val colorIndex: Int = 0,
    /**
     * Instant de création, qui voyage avec le profil.
     *
     * Sans lui, un profil restauré vaudrait zéro ici, et **n'importe quelle**
     * pierre tombale serait postérieure : le profil disparaîtrait pour de bon au
     * lieu de revenir. C'est ce qui rend comparable « quand il a été créé » et
     * « quand il a été supprimé », donc ce qui permet de trancher.
     */
    val createdAt: Long = 0,

    val resume: List<ResumeEntry> = emptyList(),
    val watchlist: List<WatchlistEntry> = emptyList(),
    val watched: List<String> = emptyList(),
    /**
     * Date de la dernière décision sur chaque clé, **retrait compris**.
     *
     * C'est ce qui permet à la fusion de respecter un « démarqué » au lieu de le
     * ressusciter. Une clé datée mais absente de [watched] est une pierre
     * tombale. Vide sur un fichier d'avant : tout se lit alors à 0, et la fusion
     * retombe sur l'union d'autrefois, ce qui est exactement ce qu'on peut dire
     * d'un fichier incapable de décrire ses suppressions.
     */
    val watchedAt: Map<String, Long> = emptyMap(),
    /**
     * Dates de retrait d'une reprise et de la liste « à voir ».
     *
     * La reprise et la liste portent déjà leur date de présence
     * (`updatedAt`, `addedAt`) : seul le retrait manquait. Vides sur un fichier
     * d'avant, et la fusion retombe alors sur l'union.
     */
    val resumeRemovedAt: Map<String, Long> = emptyMap(),
    val watchlistRemovedAt: Map<String, Long> = emptyMap(),
    val history: List<HistoryEntry> = emptyList(),
    /**
     * Dates de retrait de l'historique.
     *
     * Ajoutée après coup, donc par défaut vide : un fichier écrit par une
     * version antérieure ne sait rien de ses suppressions, et la fusion doit
     * continuer de faire l'union pour lui — un vieux fichier ne doit rien
     * effacer.
     */
    val historyRemovedAt: Map<String, Long> = emptyMap(),
    val audioTracks: Map<String, String> = emptyMap(),
    val titles: Map<String, TitleMeta> = emptyMap(),
    val homeLayout: List<HomeLayoutEntry> = emptyList(),
)

/** Réglages transportés. Nommés, pas sérialisés en vrac, pour rester lisibles. */
@Serializable
data class BackupSettings(
    val streamLanguage: String? = null,
    val disabledProviders: List<String> = emptyList(),
    val providerOrder: List<String> = emptyList(),
    val dohEnabled: Boolean? = null,
    val dohProvider: String? = null,
    val skipIntroOutro: Boolean? = null,
    val autoPlayNext: Boolean? = null,
    val playerClock: Boolean? = null,
    /** Nul dans les sauvegardes d'avant la bande-annonce : le réglage garde alors sa valeur. */
    val trailerAutoplay: Boolean? = null,
    val trailerSound: Boolean? = null,
    val updatePrereleases: Boolean? = null,
    val hideHistoryWidgets: Boolean? = null,
    val updateInterval: String? = null,
    val screensaverDelay: String? = null,
    val splashAnimation: Boolean? = null,

    /**
     * Langues de sous-titres, **l'ordre compte** : c'est le premier critère de
     * classement des candidats. Une liste vide veut dire « le fichier n'en
     * parle pas », et laisse alors celles de l'appareil tranquilles.
     */
    val subtitleLanguages: List<String> = emptyList(),

    /**
     * Compte OpenSubtitles, **sans le mot de passe**.
     *
     * Une clé d'API se révoque et ne vaut que pour un service ; un mot de passe
     * se réessaie ailleurs. Le nom d'utilisateur épargne la saisie au clavier
     * virtuel, et [osRemember] est une préférence — la case retrouvée cochée
     * quand l'utilisateur ressaisit son mot de passe.
     */
    val osUsername: String? = null,
    val osRemember: Boolean? = null,
)

/**
 * Lecture et écriture du fichier.
 *
 * Séparé du dépôt pour rester éprouvable sans magasin DataStore : c'est la
 * frontière entre l'app et un fichier venu d'ailleurs, donc l'endroit où une
 * régression se paie par un import raté.
 */
object BackupJson {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: MoovieBackup): String = json.encodeToString(backup)

    /**
     * Rend null si ce n'est pas du JSON Moo-vie, ou si le format est plus
     * récent que ce que cette version sait lire — mieux vaut refuser
     * franchement que d'importer une moitié de sauvegarde.
     */
    fun decode(raw: String): MoovieBackup? =
        runCatching { json.decodeFromString<MoovieBackup>(raw) }
            .getOrNull()
            ?.takeIf { it.version <= MoovieBackup.FORMAT_VERSION }
}

/** Ce qu'un fichier contient, pour l'aperçu **avant** d'agir. */
data class BackupSummary(
    val watched: Int,
    val resume: Int,
    val watchlist: Int,
    val history: Int,
    val exportedAt: Long,
    val appVersion: String,
    val platform: String,
    /** Vrai dès qu'**une** clé est du voyage : elles partent ou restent ensemble. */
    val hasApiKey: Boolean,
    val hasSettings: Boolean,
    /** Nombre de profils portés. 0 = fichier d'avant la v2. */
    val profiles: Int = 0,
)

/**
 * L'aperçu compte ce que le fichier contient **vraiment** : sur un format 2 les
 * données sont dans les profils, et lire le premier niveau annoncerait « 0 vu »
 * juste avant un import qui en restaure des centaines.
 */
fun MoovieBackup.summary() = BackupSummary(
    watched = watched.size + profiles.sumOf { it.watched.size },
    resume = resume.size + profiles.sumOf { it.resume.size },
    watchlist = watchlist.size + profiles.sumOf { it.watchlist.size },
    history = history.size + profiles.sumOf { it.history.size },
    exportedAt = exportedAt,
    appVersion = appVersion,
    platform = platform,
    hasApiKey = !tmdbApiKey.isNullOrBlank() || !introDbApiKey.isNullOrBlank(),
    hasSettings = settings != null,
    profiles = profiles.size,
)
