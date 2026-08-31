package fr.moovie.tv.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import fr.moovie.tv.shared.Verrou
import fr.moovie.tv.shared.avec
import okio.Path.Companion.toPath
import kotlin.concurrent.Volatile

/**
 * Chemin absolu du fichier de persistance d'un magasin nommé.
 *
 * Un chemin et non un `java.io.File` : ce type n'existe pas en Kotlin/Native.
 * Les cibles JVM le dérivent de `moovieDataStoreFile`, qui reste en place et
 * rend le **même** fichier qu'avant — les données existantes des utilisateurs
 * sont donc servies à l'identique.
 */
expect fun moovieDataStoreChemin(name: String): String

/**
 * Profil d'origine : celui de tout le monde avant que les profils existent.
 *
 * Son identifiant ne sert **jamais** de suffixe — ses fichiers gardent leur nom
 * d'origine (`moovie_watch`, `moovie_home`). C'est ce qui fait qu'une
 * installation existante *devient* le profil par défaut sans rien migrer, et que
 * la feature reste invisible tant qu'on n'en crée pas un second. Toute autre
 * approche aurait imposé de réécrire les reprises et l'historique de chaque
 * utilisateur au premier lancement, pour un gain nul.
 */
const val DEFAULT_PROFILE_ID = "default"

/** Magasins dont chaque profil possède sa propre copie. */
const val STORE_WATCH = "moovie_watch"

/** Voir [STORE_WATCH] : la disposition de l'accueil appartient aussi à la personne. */
const val STORE_HOME = "moovie_home"

/**
 * Voir [STORE_WATCH] : les filtres de recherche sont une habitude, et une
 * habitude appartient à qui l'a prise.
 */
const val STORE_SEARCH_FILTERS = "moovie_search_filters"

/**
 * Les filtres du catalogue, dans **leur propre** magasin.
 *
 * Séparés de ceux de la recherche à dessein : ce sont deux gestes différents.
 * « Je cherche un titre précis » et « je regarde ce qui existe en science-
 * fiction » n'appellent pas le même tri, et poser un plancher de note dans l'un
 * pour le retrouver dans l'autre serait une surprise, pas une commodité.
 */
const val STORE_CATALOG_FILTERS = "moovie_catalog_filters"

/**
 * Les magasins à dupliquer par profil, et donc à effacer quand on en supprime un.
 *
 * Liste explicite plutôt que balayage du répertoire : elle rend visible, en un
 * endroit, ce qui est personnel et ce qui ne l'est pas. Les clés d'API, le DNS
 * ou l'ordre des hébergeurs sont des réglages de l'installation, pas de la
 * personne — ils restent globaux.
 */
/**
 * Les réponses au questionnaire de la page Découverte.
 *
 * Personnel par nature : deux personnes devant le même téléviseur n'ont pas la
 * même humeur un mardi soir, et c'est précisément ce que la question demande.
 */
const val STORE_MOOD = "moovie_mood"

/**
 * Ce qui a été écarté de la page Découverte : « déjà vu » et « pas intéressé ».
 *
 * Séparé du suivi de lecture à dessein. Marquer « déjà vu » depuis la
 * découverte écrit bien un vrai « vu » dans [STORE_WATCH] — il doit compter, se
 * synchroniser et porter sa pierre tombale. Ce magasin-ci ne garde que ce qui
 * n'a pas d'équivalent ailleurs : les sagas écartées, qui n'ont pas de clé de
 * visionnage.
 */
const val STORE_DISCOVERY = "moovie_discovery"

val PROFILE_SCOPED_STORES =
    listOf(
        STORE_WATCH,
        STORE_HOME,
        STORE_SEARCH_FILTERS,
        STORE_CATALOG_FILTERS,
        STORE_MOOD,
        STORE_DISCOVERY,
    )

/**
 * Profil dont les données sont servies, lu à la construction de chaque dépôt.
 *
 * Un état global plutôt qu'un paramètre de constructeur : les dépôts se
 * construisent sans argument depuis une quinzaine d'endroits
 * (`remember { WatchProgressRepository() }`), et le projet n'a volontairement pas
 * d'injection de dépendances. Le faire traverser tous ces appels aurait coûté un
 * diff sans rapport avec la feature.
 *
 * Ce qui rend l'astuce correcte est ailleurs : changer de profil **relance la
 * composition** (voir `key(activeProfileId)` au montage de l'app), ce qui
 * reconstruit les dépôts et resouscrit leurs flux. Sans cela, un `Flow` déjà
 * collecté continuerait de servir l'ancien fichier, puisqu'il capture son
 * magasin à l'initialisation.
 */
object ActiveProfile {
    @Volatile
    var id: String = DEFAULT_PROFILE_ID
}

/** Nom de fichier d'un magasin, pour le profil actif. */
fun profileStoreName(base: String, profileId: String = ActiveProfile.id): String =
    if (profileId == DEFAULT_PROFILE_ID) base else "${base}__$profileId"

private val verrou = Verrou()

private val stores = mutableMapOf<String, DataStore<Preferences>>()

/** Fabrique posée par les tests, voir [overrideStores]. Null en production. */
@Volatile
private var override: ((String) -> DataStore<Preferences>)? = null

private val overridden = mutableMapOf<String, DataStore<Preferences>>()

/**
 * DataStore Preferences partagé, par nom ("moovie_settings", "moovie_watch"…).
 * Instance unique par fichier — DataStore interdit deux instances actives sur
 * le même fichier — créée à la demande.
 */
fun preferencesStore(name: String): DataStore<Preferences> = verrou.avec {
    override?.let { create -> return@avec overridden.getOrPut(name) { create(name) } }
    stores.getOrPut(name) {
        // `createWithPath` et non `create` : cette dernière prend un
        // `java.io.File` et n'existe que sur la JVM. Même fichier, même format,
        // seule l'API d'entrée change.
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { moovieDataStoreChemin(name).toPath() },
        )
    }
}

/**
 * Pour les tests : sert des magasins fabriqués ici plutôt que des fichiers.
 *
 * **Pourquoi une couture dans le code de production.** Les dépôts appellent
 * [preferencesStore] à leur construction, sans argument et sans injection — c'est
 * le choix assumé décrit sur [ActiveProfile]. Sans point de substitution, aucun
 * d'entre eux n'est testable : les faire tourner écrirait dans la configuration
 * réelle de la machine, et deux tests successifs se liraient l'un l'autre. Le
 * premier à en avoir eu besoin est `syncFingerprint()`, qui décide si un
 * téléviseur a le droit d'enregistrer ce qu'on lui diffuse.
 *
 * **Deux caches et non un.** Les instances sur fichier ne sont jamais jetées :
 * les vider pour installer la fabrique de test, puis la retirer, en recréerait
 * une seconde sur le même fichier — précisément ce que DataStore refuse
 * (« multiple DataStores active for the same file »). Seul le cache de test est
 * remis à zéro, ce qui donne à chaque test des magasins vierges.
 *
 * Passer `null` rend la main aux fichiers.
 */
internal fun overrideStores(create: ((String) -> DataStore<Preferences>)?) = verrou.avec {
    override = create
    overridden.clear()
}

/**
 * Efface les données d'un profil supprimé.
 *
 * **On vide, on ne supprime pas.** Retirer l'instance du cache puis effacer le
 * fichier semblait plus propre : c'est ce qui faisait planter l'app dès qu'on
 * recréait un profil au même identifiant — l'instance oubliée du cache restait
 * bien vivante, et la suivante tombait sur « multiple DataStores active for the
 * same file ». Le cas se produit à chaque import d'une sauvegarde après une
 * suppression, c'est-à-dire exactement quand on compte sur ses données.
 *
 * Passer par `edit { clear() }` laisse une instance unique par fichier, celle
 * que DataStore exige. Il reste un fichier vide de quelques octets par profil
 * supprimé : le prix est dérisoire à côté d'un crash.
 */
suspend fun clearProfileStores(profileId: String) {
    // Le profil d'origine n'a pas de fichiers à lui : ce sont ceux de
    // l'installation, les vider effacerait l'historique de tout le monde.
    if (profileId == DEFAULT_PROFILE_ID) return
    PROFILE_SCOPED_STORES.forEach { base ->
        preferencesStore(profileStoreName(base, profileId)).edit { it.clear() }
    }
}
