package fr.moovie.tv.data.remote

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Le téléviseur que ce téléphone pilote.
 *
 * @param name nom affiché, celui que la TV s'est donné.
 * @param host adresse sur le réseau local. Elle change au gré du bail DHCP,
 *   d'où la découverte qui la rafraîchit ([RemoteDiscovery]).
 * @param port port du serveur d'appairage, éphémère : il change à chaque
 *   démarrage de l'application sur la TV, et c'est aussi la découverte qui le
 *   redonne.
 * @param token jeton de session. **Seule pièce qu'on ne peut pas redécouvrir** :
 *   il vient de l'appairage par QR et ne circule pas sur le réseau.
 */
data class RemoteTarget(
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
) {
    /** Racine des requêtes, jeton compris — la forme qu'attend le serveur. */
    fun base(): String = "http://$host:$port/$token"
}

/**
 * Mémoire du dernier téléviseur appairé.
 *
 * Un seul, et non une liste : le cas d'usage est un salon, et proposer un choix
 * là où il n'y a qu'une réponse coûte un écran de plus à traverser. Un second
 * appairage remplace le premier.
 *
 * Magasin **global**, volontairement hors de [fr.moovie.tv.data.store.PROFILE_SCOPED_STORES] :
 * le téléviseur du salon est un fait de l'installation, pas une préférence de
 * la personne qui regarde. Le rattacher à un profil obligerait chacun à
 * réappairer le même appareil.
 */
class RemoteTargetRepository {

    private val store = preferencesStore("moovie_remote_target")

    val target: Flow<RemoteTarget?> = store.data.map { prefs ->
        val host = prefs[HOST]?.takeIf { it.isNotBlank() } ?: return@map null
        val token = prefs[TOKEN]?.takeIf { it.isNotBlank() } ?: return@map null
        RemoteTarget(
            name = prefs[NAME].orEmpty().ifBlank { host },
            host = host,
            port = prefs[PORT] ?: return@map null,
            token = token,
        )
    }

    suspend fun remember(target: RemoteTarget) {
        store.edit {
            it[NAME] = target.name
            it[HOST] = target.host
            it[PORT] = target.port
            it[TOKEN] = target.token
        }
    }

    /**
     * Met à jour l'adresse sans toucher au jeton.
     *
     * C'est ce que rend la découverte : elle sait où est le téléviseur et sur
     * quel port il écoute, jamais avec quel jeton lui parler.
     */
    suspend fun relocate(host: String, port: Int) {
        store.edit {
            if (it[TOKEN].isNullOrBlank()) return@edit
            it[HOST] = host
            it[PORT] = port
        }
    }

    suspend fun forget() {
        store.edit { it.clear() }
    }

    private companion object {
        val NAME = stringPreferencesKey("name")
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val TOKEN = stringPreferencesKey("token")
    }
}
