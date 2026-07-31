package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ENTRY_PREFIX = "sources:"

/**
 * Durée de validité d'une entrée. On ne cache que les **liens d'embed** (la page
 * de l'hébergeur), pas le flux vidéo final : ces pages restent valables
 * longtemps, là où l'URL de flux extraite expire souvent en moins de deux heures
 * et parfois se lie à l'IP. Quelques heures évitent de resservir des liens d'un
 * catalogue qui a bougé.
 */
private const val TTL_MS = 6L * 60 * 60 * 1000

/** Nombre max d'entrées conservées (purge des plus anciennes au-delà). */
private const val MAX_ENTRIES = 80

@Serializable
private data class CachedSources(val links: List<EmbedLink>, val savedAt: Long)

/**
 * Cache disque des liens d'embed trouvés par les providers, indexé par clé de
 * lecture ("movie:<id>" / "tv:<id>:s<S>e<E>").
 *
 * Objectif : au retour sur une fiche déjà consultée, afficher les sources
 * immédiatement au lieu de réinterroger les trois providers. Le flux jouable,
 * lui, est toujours ré-extrait au moment de lire.
 */
class SourceCacheRepository {

    private val store = preferencesStore("moovie_sources_cache")
    private val json = Json { ignoreUnknownKeys = true }

    /** Liens en cache pour cette clé, ou null si absent / périmé. */
    suspend fun get(key: String): List<EmbedLink>? {
        if (key.isBlank()) return null
        val raw = store.data.first()[stringPreferencesKey(ENTRY_PREFIX + key)] ?: return null
        val entry = runCatching { json.decodeFromString<CachedSources>(raw) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - entry.savedAt > TTL_MS) return null
        return entry.links.ifEmpty { null }
    }

    /** Mémorise les liens d'une fiche (ignoré si la recherche n'a rien donné). */
    suspend fun put(key: String, links: List<EmbedLink>) {
        if (key.isBlank() || links.isEmpty()) return
        store.edit { prefs ->
            prefs[stringPreferencesKey(ENTRY_PREFIX + key)] =
                json.encodeToString(CachedSources(links, System.currentTimeMillis()))
            prune(prefs)
        }
    }

    /**
     * Oublie une entrée. Appelé quand aucun lien issu du cache n'a pu être lu :
     * ils sont probablement tous morts, mieux vaut refaire une vraie recherche.
     */
    suspend fun invalidate(key: String) {
        if (key.isBlank()) return
        store.edit { it.remove(stringPreferencesKey(ENTRY_PREFIX + key)) }
    }

    /** Vide tout le cache des sources. */
    suspend fun clear() {
        store.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(ENTRY_PREFIX) }
                .forEach { prefs.remove(stringPreferencesKey(it.name)) }
        }
    }

    /** Garde les [MAX_ENTRIES] entrées les plus récentes. */
    private fun prune(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        val entries = prefs.asMap()
            .filterKeys { it.name.startsWith(ENTRY_PREFIX) }
            .mapNotNull { (k, v) ->
                val saved = runCatching { json.decodeFromString<CachedSources>(v as String).savedAt }
                    .getOrDefault(0L)
                k.name to saved
            }
        if (entries.size <= MAX_ENTRIES) return
        entries.sortedBy { it.second }
            .take(entries.size - MAX_ENTRIES)
            .forEach { (name, _) -> prefs.remove(stringPreferencesKey(name)) }
    }
}
