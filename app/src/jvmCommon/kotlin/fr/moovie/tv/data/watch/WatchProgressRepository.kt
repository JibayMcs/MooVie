package fr.moovie.tv.data.watch

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val RESUME_PREFIX = "resume:"
private const val SEEN_PREFIX = "seen:"
private const val LATER_PREFIX = "later:"

/**
 * Suivi de lecture : reprise (position + métadonnées) et statut vu/non vu.
 * Un contenu terminé (moins de 10 s restantes) quitte la reprise et passe
 * automatiquement en « vu ».
 */
class WatchProgressRepository {

    private val store = preferencesStore("moovie_watch")
    private val json = Json { ignoreUnknownKeys = true }

    /** Contenus en cours, du plus récent au plus ancien. */
    val continueWatching: Flow<List<ResumeEntry>> = store.data.map { prefs ->
        prefs.asMap().mapNotNull { (k, v) ->
            if (!k.name.startsWith(RESUME_PREFIX)) return@mapNotNull null
            runCatching { json.decodeFromString<ResumeEntry>(v as String) }.getOrNull()
        }
            .filter { it.positionMs > 0 }
            .sortedByDescending { it.updatedAt }
    }

    /** Clés marquées comme vues ("movie:<id>", "tv:<id>:s<S>e<E>"). */
    val watched: Flow<Set<String>> = store.data.map { prefs ->
        prefs.asMap().keys
            .filter { it.name.startsWith(SEEN_PREFIX) }
            .map { it.name.removePrefix(SEEN_PREFIX) }
            .toSet()
    }

    /** Titres mis de côté, de l'ajout le plus récent au plus ancien. */
    val watchlist: Flow<List<WatchlistEntry>> = store.data.map { prefs ->
        prefs.asMap().mapNotNull { (k, v) ->
            if (!k.name.startsWith(LATER_PREFIX)) return@mapNotNull null
            runCatching { json.decodeFromString<WatchlistEntry>(v as String) }.getOrNull()
        }.sortedByDescending { it.addedAt }
    }

    /** Position sauvegardée en ms (0 si aucune / terminé). */
    suspend fun position(key: String): Long = entry(key)?.positionMs ?: 0L

    /** Ajoute un titre à « À regarder plus tard » (ou rafraîchit ses métadonnées). */
    suspend fun addToWatchlist(entry: WatchlistEntry) {
        store.edit { prefs ->
            prefs[stringPreferencesKey(LATER_PREFIX + entry.key)] =
                json.encodeToString(entry.copy(addedAt = System.currentTimeMillis()))
        }
    }

    /** Retire un titre de « À regarder plus tard ». */
    suspend fun removeFromWatchlist(key: String) {
        store.edit { it.remove(stringPreferencesKey(LATER_PREFIX + key)) }
    }

    /**
     * Enregistre les métadonnées du contenu qu'on s'apprête à lire (appelé au
     * lancement de la lecture). Conserve la progression déjà sauvegardée.
     */
    suspend fun register(meta: ResumeEntry) {
        store.edit { prefs ->
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
        store.edit { prefs ->
            val k = stringPreferencesKey(RESUME_PREFIX + key)
            val existing = prefs[k]?.let { decode(it) } ?: return@edit
            when {
                durationMs > 0 && positionMs >= durationMs - 10_000 -> {
                    prefs.remove(k)
                    prefs[booleanPreferencesKey(SEEN_PREFIX + key)] = true
                    prefs.pruneWatchlist(key)
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
        store.edit { it.remove(stringPreferencesKey(RESUME_PREFIX + key)) }
    }

    /** Marque vu/non vu. Marquer vu retire aussi le contenu de la reprise. */
    suspend fun setWatched(key: String, watched: Boolean) {
        store.edit { prefs ->
            if (watched) {
                prefs[booleanPreferencesKey(SEEN_PREFIX + key)] = true
                prefs.remove(stringPreferencesKey(RESUME_PREFIX + key))
                prefs.pruneWatchlist(key)
            } else {
                prefs.remove(booleanPreferencesKey(SEEN_PREFIX + key))
            }
        }
    }

    /** Marque vu/non vu en lot (une saison entière, par exemple). */
    suspend fun setAllWatched(keys: List<String>, watched: Boolean) {
        store.edit { prefs ->
            keys.forEach { key ->
                if (watched) {
                    prefs[booleanPreferencesKey(SEEN_PREFIX + key)] = true
                    prefs.remove(stringPreferencesKey(RESUME_PREFIX + key))
                } else {
                    prefs.remove(booleanPreferencesKey(SEEN_PREFIX + key))
                }
            }
            // Après le lot : le total d'épisodes vus n'est atteint qu'une fois
            // toutes les clés posées.
            if (watched) keys.firstOrNull()?.let { prefs.pruneWatchlist(it) }
        }
    }

    /**
     * Sort un titre de « À regarder plus tard » dès qu'il est terminé : un film
     * marqué vu, une série dont tous les épisodes le sont. Sans ça, la liste se
     * remplissait de contenus déjà regardés et demandait un ménage manuel.
     *
     * Appelé depuis les trois endroits qui posent un « vu » — marquage manuel,
     * marquage en lot d'une saison, et fin de lecture détectée par [save] —
     * pour que la règle vaille aussi quand le titre est terminé depuis le
     * lecteur, sans repasser par la fiche.
     */
    private fun MutablePreferences.pruneWatchlist(watchedKey: String) {
        val titleKey = when {
            watchedKey.startsWith("movie:") -> watchedKey
            // "tv:<id>:s1e2" → "tv:<id>" : la liste est au niveau du titre.
            watchedKey.startsWith("tv:") -> watchedKey.split(":").take(2).joinToString(":")
            else -> return
        }
        val laterKey = stringPreferencesKey(LATER_PREFIX + titleKey)
        val entry = this[laterKey]?.let {
            runCatching { json.decodeFromString<WatchlistEntry>(it) }.getOrNull()
        } ?: return

        if (!entry.isTv) {
            if (this[booleanPreferencesKey(SEEN_PREFIX + titleKey)] == true) remove(laterKey)
            return
        }
        // Sans total connu, on ne peut pas conclure : on garde la série plutôt
        // que de la faire disparaître au premier épisode vu.
        if (entry.totalEpisodes <= 0) return
        val seen = asMap().keys.count {
            it.name.startsWith("$SEEN_PREFIX$titleKey:")
        }
        if (seen >= entry.totalEpisodes) remove(laterKey)
    }

    private suspend fun entry(key: String): ResumeEntry? =
        store.data.first()[stringPreferencesKey(RESUME_PREFIX + key)]?.let { decode(it) }

    private fun decode(raw: String): ResumeEntry? =
        runCatching { json.decodeFromString<ResumeEntry>(raw) }.getOrNull()
}
