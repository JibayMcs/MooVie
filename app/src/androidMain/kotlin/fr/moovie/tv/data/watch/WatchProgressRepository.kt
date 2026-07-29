package fr.moovie.tv.data.watch

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.watchStore: DataStore<Preferences> by preferencesDataStore(name = "moovie_watch")

private const val RESUME_PREFIX = "resume:"
private const val SEEN_PREFIX = "seen:"

// ResumeEntry vit désormais dans jvmCommon (data/watch/ResumeEntry.kt),
// partagé avec l'écran d'accueil commun.

/**
 * Suivi de lecture : reprise (position + métadonnées) et statut vu/non vu.
 * Un contenu terminé (moins de 10 s restantes) quitte la reprise et passe
 * automatiquement en « vu ».
 */
class WatchProgressRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Contenus en cours, du plus récent au plus ancien. */
    val continueWatching: Flow<List<ResumeEntry>> = context.watchStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (k, v) ->
            if (!k.name.startsWith(RESUME_PREFIX)) return@mapNotNull null
            runCatching { json.decodeFromString<ResumeEntry>(v as String) }.getOrNull()
        }
            .filter { it.positionMs > 0 }
            .sortedByDescending { it.updatedAt }
    }

    /** Clés marquées comme vues ("movie:<id>", "tv:<id>:s<S>e<E>"). */
    val watched: Flow<Set<String>> = context.watchStore.data.map { prefs ->
        prefs.asMap().keys
            .filter { it.name.startsWith(SEEN_PREFIX) }
            .map { it.name.removePrefix(SEEN_PREFIX) }
            .toSet()
    }

    /** Position sauvegardée en ms (0 si aucune / terminé). */
    suspend fun position(key: String): Long = entry(key)?.positionMs ?: 0L

    /**
     * Enregistre les métadonnées du contenu qu'on s'apprête à lire (appelé au
     * lancement de la lecture). Conserve la progression déjà sauvegardée.
     */
    suspend fun register(meta: ResumeEntry) {
        context.watchStore.edit { prefs ->
            val k = stringPreferencesKey(RESUME_PREFIX + meta.key)
            val existing = prefs[k]?.let { decode(it) }
            prefs[k] = json.encodeToString(
                meta.copy(
                    positionMs = existing?.positionMs ?: 0,
                    durationMs = existing?.durationMs ?: 0,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Met à jour la position de lecture. Dans les 10 dernières secondes, le
     * contenu est considéré terminé : sortie de la reprise + marqué vu.
     * Les positions < 5 s sont ignorées (générique, zapping).
     */
    suspend fun save(key: String, positionMs: Long, durationMs: Long) {
        context.watchStore.edit { prefs ->
            val k = stringPreferencesKey(RESUME_PREFIX + key)
            val existing = prefs[k]?.let { decode(it) } ?: return@edit
            when {
                durationMs > 0 && positionMs >= durationMs - 10_000 -> {
                    prefs.remove(k)
                    prefs[booleanPreferencesKey(SEEN_PREFIX + key)] = true
                }
                positionMs > 5_000 -> prefs[k] = json.encodeToString(
                    existing.copy(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /** Retire un contenu du rail « Reprendre » (sans toucher au statut vu). */
    suspend fun remove(key: String) {
        context.watchStore.edit { it.remove(stringPreferencesKey(RESUME_PREFIX + key)) }
    }

    /** Marque vu/non vu. Marquer vu retire aussi le contenu de la reprise. */
    suspend fun setWatched(key: String, watched: Boolean) {
        context.watchStore.edit { prefs ->
            if (watched) {
                prefs[booleanPreferencesKey(SEEN_PREFIX + key)] = true
                prefs.remove(stringPreferencesKey(RESUME_PREFIX + key))
            } else {
                prefs.remove(booleanPreferencesKey(SEEN_PREFIX + key))
            }
        }
    }

    /** Marque vu/non vu en lot (une saison entière, par exemple). */
    suspend fun setAllWatched(keys: List<String>, watched: Boolean) {
        context.watchStore.edit { prefs ->
            keys.forEach { key ->
                if (watched) {
                    prefs[booleanPreferencesKey(SEEN_PREFIX + key)] = true
                    prefs.remove(stringPreferencesKey(RESUME_PREFIX + key))
                } else {
                    prefs.remove(booleanPreferencesKey(SEEN_PREFIX + key))
                }
            }
        }
    }

    private suspend fun entry(key: String): ResumeEntry? =
        context.watchStore.data.first()[stringPreferencesKey(RESUME_PREFIX + key)]?.let { decode(it) }

    private fun decode(raw: String): ResumeEntry? =
        runCatching { json.decodeFromString<ResumeEntry>(raw) }.getOrNull()
}
