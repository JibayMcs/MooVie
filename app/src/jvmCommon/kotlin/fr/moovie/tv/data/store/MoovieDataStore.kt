package fr.moovie.tv.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

/** Fichier de persistance d'un DataStore Preferences nommé (chemin par plateforme). */
expect fun moovieDataStoreFile(name: String): File

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
 * Les magasins à dupliquer par profil, et donc à effacer quand on en supprime un.
 *
 * Liste explicite plutôt que balayage du répertoire : elle rend visible, en un
 * endroit, ce qui est personnel et ce qui ne l'est pas. Les clés d'API, le DNS
 * ou l'ordre des hébergeurs sont des réglages de l'installation, pas de la
 * personne — ils restent globaux.
 */
val PROFILE_SCOPED_STORES = listOf(STORE_WATCH, STORE_HOME)

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

private val stores = mutableMapOf<String, DataStore<Preferences>>()

/**
 * DataStore Preferences partagé, par nom ("moovie_settings", "moovie_watch"…).
 * Instance unique par fichier — DataStore interdit deux instances actives sur
 * le même fichier — créée à la demande.
 */
fun preferencesStore(name: String): DataStore<Preferences> = synchronized(stores) {
    stores.getOrPut(name) {
        PreferenceDataStoreFactory.create(produceFile = { moovieDataStoreFile(name) })
    }
}

/**
 * Efface les données d'un profil supprimé, et oublie ses instances.
 *
 * L'oubli n'est pas cosmétique : DataStore interdit deux instances vivantes sur
 * un même fichier, donc recréer plus tard un profil qui retomberait sur le même
 * identifiant heurterait l'instance morte. On ne supprime jamais le profil
 * actif — l'appelant bascule d'abord — de sorte qu'aucun flux ne lit le fichier
 * au moment où il disparaît.
 */
fun dropProfileStores(profileId: String) {
    if (profileId == DEFAULT_PROFILE_ID) return
    synchronized(stores) {
        PROFILE_SCOPED_STORES.forEach { base ->
            val name = profileStoreName(base, profileId)
            stores.remove(name)
            runCatching { moovieDataStoreFile(name).delete() }
        }
    }
}
