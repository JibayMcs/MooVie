package fr.moovie.tv.data.home

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Disposition de l'accueil : quelles rangées, dans quel ordre, et lesquelles
 * sont masquées.
 *
 * Le catalogue est rangé par genre, l'accueil ne l'était pas : ses rangées
 * étaient écrites en dur, « tendances » et « mieux notés », qui ne correspondent
 * à rien qu'on puisse rouvrir. Épingler renverse la relation — c'est
 * l'utilisateur qui décide de ce que l'accueil montre, et chaque rangée sait
 * alors exactement où elle mène.
 *
 * Un genre épinglé n'est donc pas une liste à côté des rangées intégrées : c'est
 * une entrée **du même ordre**. Sans ça, « avant / après telle catégorie »
 * n'aurait aucun sens à exprimer.
 *
 * Toute la disposition tient dans **une seule clé**. Une clé par entrée rendrait
 * un déplacement non atomique : l'accueil se recomposerait sur un ordre à
 * moitié écrit. Ici, un déplacement est une écriture, ou rien.
 *
 * Un magasin à part plutôt qu'une clé de plus dans les réglages : c'est du
 * contenu, pas une préférence, et il entre dans la sauvegarde USB au même titre
 * que la liste et l'historique.
 */
class HomeLayoutRepository {

    private val store = preferencesStore("moovie_home")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * La disposition, **complétée** : jamais vide, jamais incomplète. Un flux,
     * pas une lecture ponctuelle — l'accueil doit se redessiner à l'épinglage,
     * pas seulement au démarrage.
     */
    val layout: Flow<List<HomeLayoutEntry>> = store.data.map { prefs ->
        mergeHomeLayout(decode(prefs[LAYOUT]))
    }

    /** Les seuls genres épinglés, pour l'état des boutons du catalogue. */
    val pinnedGenres: Flow<List<PinnedGenre>> =
        layout.map { entries -> entries.mapNotNull { it.genre } }

    /**
     * Épingle un genre à la position demandée.
     *
     * @param anchorId entrée de référence ([HomeLayoutEntry.id]), null pour la fin.
     * @param after après l'ancre plutôt qu'avant.
     */
    suspend fun pin(
        isTv: Boolean,
        genreId: Int,
        name: String,
        anchorId: String? = null,
        after: Boolean = true,
    ) = update { current ->
        insertHomeEntry(current, HomeLayoutEntry.of(PinnedGenre(isTv, genreId, name)), anchorId, after)
    }

    /** Retire un genre de l'accueil. Le catalogue, lui, le propose toujours. */
    suspend fun unpin(isTv: Boolean, genreId: Int) {
        val key = pinnedGenreKey(isTv, genreId)
        update { current -> current.filterNot { it.genre?.key == key } }
    }

    /** Vrai si le genre est déjà sur l'accueil — pour l'état du bouton. */
    suspend fun isPinned(isTv: Boolean, genreId: Int): Boolean {
        val key = pinnedGenreKey(isTv, genreId)
        return layout.first().any { it.genre?.key == key }
    }

    /**
     * Masque ou réaffiche une rangée.
     *
     * Masquer plutôt que supprimer, pour les rangées intégrées : c'est ce qui
     * permet de les faire revenir depuis l'écran de réorganisation. Un genre
     * épinglé, lui, se retire pour de bon — il se réépingle depuis le catalogue.
     */
    suspend fun setVisible(id: String, visible: Boolean) = update { current ->
        current.map { if (it.id == id) it.copy(visible = visible) else it }
    }

    /**
     * Déplace une entrée de [delta] rangs. Le résultat est borné aux extrémités :
     * un appui de trop en haut de liste ne fait rien, il ne renvoie pas en bas.
     */
    suspend fun move(id: String, delta: Int) = update { current ->
        val from = current.indexOfFirst { it.id == id }
        if (from < 0 || delta == 0) return@update current
        val to = (from + delta).coerceIn(0, current.lastIndex)
        if (to == from) return@update current
        current.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Remplace la disposition — import de sauvegarde, ou réorganisation en bloc. */
    suspend fun replaceAll(entries: List<HomeLayoutEntry>) = update { mergeHomeLayout(entries) }

    /** Rend l'accueil d'origine, sans toucher aux genres épinglés du catalogue. */
    suspend fun resetToDefault() = update { current ->
        defaultHomeLayout + current.filter { it.kind == HomeRowKind.GENRE }
    }

    /**
     * Lecture-modification-écriture dans une seule transaction `edit` : deux
     * gestes rapprochés (épingler puis déplacer) ne doivent pas se lire l'un
     * l'autre avant écriture.
     */
    private suspend fun update(block: (List<HomeLayoutEntry>) -> List<HomeLayoutEntry>) {
        store.edit { prefs ->
            val current = mergeHomeLayout(decode(prefs[LAYOUT]))
            prefs[LAYOUT] = json.encodeToString(StoredLayout(block(current)))
        }
    }

    /** Un magasin illisible rend null, donc la disposition par défaut. */
    private fun decode(raw: String?): List<HomeLayoutEntry>? = raw?.let {
        runCatching { json.decodeFromString<StoredLayout>(it).entries }.getOrNull()
    }

    private companion object {
        val LAYOUT = stringPreferencesKey("home_layout")
    }
}
