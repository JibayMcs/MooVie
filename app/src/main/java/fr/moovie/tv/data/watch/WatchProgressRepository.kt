package fr.moovie.tv.data.watch

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.watchStore: DataStore<Preferences> by preferencesDataStore(name = "moovie_watch")

/**
 * Progression de lecture par contenu (film ou épisode), pour la reprise.
 * Clé stable fournie par l'appelant : "movie:<id>" ou "tv:<id>:s<S>e<E>".
 */
class WatchProgressRepository(private val context: Context) {

    /** Position sauvegardée en ms (0 si aucune / terminé). */
    suspend fun position(key: String): Long =
        context.watchStore.data.first()[longPreferencesKey(key)] ?: 0L

    /**
     * Sauvegarde la position. Efface l'entrée si on est dans les 10 dernières
     * secondes (considéré comme terminé) ; ignore les positions < 5 s (générique).
     */
    suspend fun save(key: String, positionMs: Long, durationMs: Long) {
        context.watchStore.edit { prefs ->
            val k = longPreferencesKey(key)
            when {
                durationMs > 0 && positionMs >= durationMs - 10_000 -> prefs.remove(k)
                positionMs > 5_000 -> prefs[k] = positionMs
            }
        }
    }
}
