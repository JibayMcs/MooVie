package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.core.sources.usecase.isCacheComplete
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import fr.moovie.tv.shared.appVersionName
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
private data class CachedSources(
    val links: List<EmbedLink>,
    val savedAt: Long,
    /**
     * Catalogues **interrogés** pour produire cette entrée — pas seulement ceux
     * qui ont rendu quelque chose. Sans ça, une entrée écrite avant l'ajout d'un
     * provider (ou pendant qu'il était désactivé) resservait indéfiniment sa
     * liste amputée : le catalogue neuf n'était jamais consulté sur les fiches
     * déjà visitées, et l'utilisateur gardait la redondance de l'ancienne
     * version. Vide = entrée d'une version antérieure, à refaire.
     */
    val providers: List<String> = emptyList(),
    /**
     * Version de l'application qui a écrit l'entrée.
     *
     * Le champ [providers] couvre le catalogue **ajouté** ; celui-ci couvre le
     * catalogue **corrigé**, qui est le cas le plus fréquent et que rien ne
     * rattrapait. Une entrée reste « complète » quand un provider se met à
     * rendre autre chose : mêmes catalogues interrogés, donc resservie telle
     * quelle pendant six heures.
     *
     * Constaté sur anime-sama : la VF venait d'être débloquée, la sonde la
     * voyait, et l'application affichait encore « Aucune source en VF » sur les
     * fiches déjà visitées. Le correctif était bon, le cache le masquait — et
     * rien ne permettait de forcer la relecture.
     *
     * Vide = entrée d'avant ce champ, à refaire. Une version qui change est un
     * signal bien plus sûr qu'une durée : c'est exactement le moment où le code
     * d'extraction a pu changer.
     */
    val version: String = "",
)

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

    /**
     * Liens en cache pour cette clé, ou null si absent, périmé, ou **incomplet**.
     *
     * Incomplet = [expectedProviders] contient un catalogue que l'entrée n'a pas
     * interrogé. C'est le cas juste après une mise à jour qui ajoute un provider,
     * ou quand l'utilisateur en réactive un : resservir l'entrée telle quelle
     * masquerait le nouveau catalogue pendant toute la durée de vie du cache.
     */
    suspend fun get(key: String, expectedProviders: Set<String> = emptySet()): List<EmbedLink>? {
        if (key.isBlank()) return null
        val raw = store.data.first()[stringPreferencesKey(ENTRY_PREFIX + key)] ?: return null
        val entry = runCatching { json.decodeFromString<CachedSources>(raw) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - entry.savedAt > TTL_MS) return null
        // Écrite par une autre version : le code qui a produit ces liens n'est
        // plus celui qui tourne.
        if (entry.version != appVersionName) return null
        if (!isCacheComplete(entry.providers, expectedProviders)) return null
        return entry.links.ifEmpty { null }
    }

    /**
     * Mémorise les liens d'une fiche (ignoré si la recherche n'a rien donné).
     *
     * [providers] est l'ensemble **interrogé**, catalogues muets compris : un
     * provider qui n'a rien pour ce titre a quand même fait son travail, et
     * l'exclure ferait rejeter l'entrée à chaque relecture.
     */
    suspend fun put(key: String, links: List<EmbedLink>, providers: Set<String> = emptySet()) {
        if (key.isBlank() || links.isEmpty()) return
        store.edit { prefs ->
            prefs[stringPreferencesKey(ENTRY_PREFIX + key)] = json.encodeToString(
                CachedSources(links, System.currentTimeMillis(), providers.sorted(), appVersionName),
            )
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
