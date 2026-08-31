package fr.moovie.tv.data.download

import fr.moovie.tv.shared.systemeFichiers
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * La liste des téléchargements et leur état.
 *
 * Magasin **global**, non scindé par profil : voir [Download]. Une entrée par
 * clé média, et non une liste sérialisée d'un bloc — deux téléchargements
 * avancent en parallèle du point de vue de l'écran, et réécrire toute la liste
 * à chaque segment ferait de la barre de progression un point de contention.
 */
class DownloadRepository {

    private val store = preferencesStore("moovie_downloads")
    private val json = Json { ignoreUnknownKeys = true }

    val downloads: Flow<List<Download>> = store.data.map { prefs ->
        prefs.asMap().mapNotNull { (k, v) ->
            if (!k.name.startsWith(PREFIX)) return@mapNotNull null
            runCatching { json.decodeFromString<Download>(v as String) }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    suspend fun get(key: String): Download? = store.data.first()[keyOf(key)]
        ?.let { runCatching { json.decodeFromString<Download>(it) }.getOrNull() }

    suspend fun put(download: Download) {
        store.edit { it[keyOf(download.key)] = json.encodeToString(download) }
    }

    /**
     * Retire l'entrée **et les fichiers**.
     *
     * Les deux ensemble, jamais l'un sans l'autre : une entrée sans fichiers
     * annonce un titre illisible, des fichiers sans entrée occupent le disque
     * sans que rien ne les nomme.
     */
    suspend fun remove(key: String) {
        store.edit { it.remove(keyOf(key)) }
        runCatching { systemeFichiers.deleteRecursively(downloadDir(key)) }
    }

    /**
     * Ce que les téléchargements occupent réellement, mesuré sur le disque.
     *
     * On somme les fichiers plutôt que les valeurs enregistrées : un
     * téléchargement interrompu par une coupure de courant laisse des octets que
     * personne n'a comptés, et c'est précisément quand le disque se remplit que
     * le chiffre doit être juste.
     */
    fun bytesOnDisk(): Long = moovieDownloadsDir()
        // `listRecursively` remplace `walkBottomUp` : l'ordre n'importe pas
        // pour une somme, seule la couverture compte.
        .let { racine ->
            if (systemeFichiers.metadataOrNull(racine)?.isDirectory != true) return 0L
            systemeFichiers.listRecursively(racine)
                .mapNotNull { systemeFichiers.metadataOrNull(it) }
                .filter { it.isRegularFile }
                .sumOf { it.size ?: 0L }
        }

    private fun keyOf(mediaKey: String) = stringPreferencesKey(PREFIX + mediaKey)

    private companion object {
        const val PREFIX = "dl:"
    }
}
