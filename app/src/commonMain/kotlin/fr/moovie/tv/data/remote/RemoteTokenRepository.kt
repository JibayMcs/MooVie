package fr.moovie.tv.data.remote

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/**
 * Le jeton que le téléviseur attend de ses télécommandes.
 *
 * ### Pourquoi il ne se retire plus au hasard à chaque démarrage
 *
 * Tant que le serveur ne servait qu'à saisir des clés, un jeton de session
 * suffisait : on regardait le QR pendant qu'on tapait, et sa péremption était une
 * qualité. Une télécommande, elle, doit répondre **le lendemain**. Redonner un
 * jeton neuf à chaque démarrage de l'application sur la TV rendait muet, sans
 * rien dire, le téléphone appairé la veille — et le seul remède était de
 * reprendre la télécommande physique pour rescanner, c'est-à-dire exactement le
 * geste que la fonctionnalité existe pour éviter.
 *
 * Il reste **distinct du jeton d'adresse de l'appairage des clés** dans son rôle,
 * même si c'est le même : ce qui change est qu'il survit. L'adresse et le port,
 * eux, continuent de bouger et se retrouvent par la découverte ([RemoteBeacons]).
 *
 * Magasin **global**, hors de [fr.moovie.tv.data.store.PROFILE_SCOPED_STORES] :
 * un téléviseur n'appartient à personne en particulier, et changer de profil ne
 * doit pas couper la télécommande de celui d'à côté.
 *
 * ### La porte de sortie
 *
 * Un secret durable a besoin d'être révocable, sinon un téléphone prêté garde la
 * main pour toujours. [regenerate] est ce que fait « Oublier les télécommandes »
 * dans les réglages : un jeton neuf invalide d'un coup tout ce qui était appairé.
 */
class RemoteTokenRepository {

    private val store = preferencesStore("moovie_remote_token")

    /** Le jeton courant, créé et enregistré au premier appel. */
    suspend fun token(): String =
        store.data.first()[TOKEN]?.takeIf { it.isNotBlank() } ?: create()

    /**
     * Un jeton neuf, qui remplace l'ancien.
     *
     * Tout ce qui était appairé tombe en 404 au premier appui : c'est le but.
     */
    suspend fun regenerate(): String {
        val fresh = randomToken()
        store.edit { it[TOKEN] = fresh }
        return fresh
    }

    /**
     * Création atomique.
     *
     * Le `edit` relit la valeur avant d'écrire, parce que deux appels concurrents
     * y arrivent pour de bon — la modale d'appairage et l'écoute permanente du
     * téléviseur demandent le jeton en même temps au démarrage. DataStore
     * sérialise les transformations d'un même fichier ; le second appel voit donc
     * ce que le premier a écrit, au lieu d'en tirer un autre et de faire tomber
     * l'un des deux serveurs à côté.
     */
    private suspend fun create(): String {
        var result = ""
        store.edit { prefs ->
            result = prefs[TOKEN]?.takeIf { it.isNotBlank() }
                ?: randomToken().also { prefs[TOKEN] = it }
        }
        return result
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
    }
}

/**
 * Jeton d'adresse. Huit caractères sans ambiguïté visuelle — ni `0`/`O`, ni
 * `1`/`l` — parce qu'il arrive qu'on le recopie depuis l'écran.
 */
internal fun randomToken(): String {
    val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
    return (1..8).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
}
