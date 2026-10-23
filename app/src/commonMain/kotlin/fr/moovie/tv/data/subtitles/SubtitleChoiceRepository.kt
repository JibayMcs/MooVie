package fr.moovie.tv.data.subtitles

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Le sous-titre que l'utilisateur a choisi, par média (clé de lecture).
 *
 * Un choix fait dans le menu se perdait à la sortie du lecteur : à la reprise
 * il fallait rouvrir le menu et retrouver le même sous-titre. La reprise rejoue
 * maintenant ce choix, sans rien chercher ni télécharger — voir
 * `PlayerSubtitlesViewModel.restore`.
 *
 * Ce qui est gardé suffit à retrouver le fichier déjà sur disque (identifiant
 * du catalogue) et à restituer la même ligne dans le menu.
 */
class SubtitleChoiceRepository {
    private val store = preferencesStore(STORE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Choix(
        val fileId: String = "",
        val language: String = "",
        val release: String = "",
        val fps: Double? = null,
    )

    /** Le choix retenu pour ce média, ou null si rien n'a été choisi. */
    suspend fun get(key: String): SubtitleCandidate? {
        if (key.isBlank()) return null
        val raw = store.data.first()[stringPreferencesKey(key)] ?: return null
        val c = runCatching { json.decodeFromString<Choix>(raw) }.getOrNull() ?: return null
        if (c.fileId.isBlank()) return null
        return SubtitleCandidate(fileId = c.fileId, language = c.language, release = c.release, fps = c.fps)
    }

    suspend fun remember(key: String, candidate: SubtitleCandidate) {
        if (key.isBlank()) return
        val c = Choix(candidate.fileId, candidate.language, candidate.release, candidate.fps)
        store.edit { it[stringPreferencesKey(key)] = json.encodeToString(c) }
    }

    /** L'utilisateur a quitté ce sous-titre pour une piste du fichier : on n'y revient pas. */
    suspend fun forget(key: String) {
        if (key.isBlank()) return
        store.edit { it.remove(stringPreferencesKey(key)) }
    }

    private companion object {
        const val STORE = "moovie_subtitle_choice"
    }
}
