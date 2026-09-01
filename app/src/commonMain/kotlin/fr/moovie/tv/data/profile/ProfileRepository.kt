package fr.moovie.tv.data.profile

import fr.moovie.tv.shared.maintenantMs
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.ActiveProfile
import fr.moovie.tv.data.store.DEFAULT_PROFILE_ID
import fr.moovie.tv.data.store.clearProfileStores
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Liste des profils et profil actif.
 *
 * Ce dépôt est le seul du domaine à vivre dans un magasin **global** : la liste
 * des profils ne peut évidemment pas être rangée dans l'un d'eux. Il est aussi
 * le seul à pouvoir écrire [ActiveProfile], que tous les autres dépôts lisent à
 * leur construction pour savoir quel fichier ouvrir.
 *
 * Toute la liste tient dans une clé, pour la même raison que la disposition de
 * l'accueil : renommer ou supprimer est alors une écriture, ou rien.
 */
class ProfileRepository {

    private val store = preferencesStore("moovie_profiles")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Les profils, **complétés** : la liste contient toujours celui d'origine,
     * même quand rien n'a jamais été écrit. Une installation qui n'a jamais
     * touché à la feature se lit donc comme un profil unique, sans cas
     * particulier à traiter partout ailleurs.
     */
    val profiles: Flow<List<Profile>> = store.data.map { prefs -> complete(decode(prefs[PROFILES])) }

    /**
     * Suppressions datées : identifiant de profil vers l'instant du retrait.
     *
     * **Une suppression sans date ne peut pas se fusionner.** Sans elle, un
     * profil supprimé n'est qu'une absence, et une absence est indistinguable de
     * « pas encore synchronisé » : la fusion doit alors faire l'union, et le
     * profil ressuscite au premier import. C'est exactement ce que `seenat:` et
     * `resumeat:` règlent pour les épisodes démarqués ; les profils avaient été
     * oubliés.
     *
     * On garde l'instant, pas un simple drapeau, parce que la règle est « la
     * décision la plus récente gagne » : un profil recréé après coup doit
     * pouvoir l'emporter sur son propre retrait.
     */
    val deletedAt: Flow<Map<String, Long>> = store.data.map { prefs -> decodeTombstones(prefs[DELETED]) }

    /**
     * L'identifiant actif, ramené au profil d'origine si celui qu'on avait
     * retenu n'existe plus — un profil supprimé depuis un autre appareil, ou une
     * donnée abîmée, ne doit pas laisser l'app pointer dans le vide.
     */
    val activeId: Flow<String> = store.data.map { prefs ->
        val known = complete(decode(prefs[PROFILES])).map { it.id }
        prefs[ACTIVE]?.takeIf { it in known } ?: DEFAULT_PROFILE_ID
    }

    /** Le profil actif en entier, pour l'afficher. */
    val active: Flow<Profile> = store.data.map { prefs ->
        val all = complete(decode(prefs[PROFILES]))
        all.firstOrNull { it.id == prefs[ACTIVE] } ?: Profile.Default
    }

    /**
     * Pose [ActiveProfile] avant que quoi que ce soit ne lise des données.
     *
     * Appelé au démarrage, depuis l'aiguillage d'écran racine : rien ne doit se
     * composer tant que la réponse n'est pas connue, sinon un dépôt construit
     * entre-temps ouvrirait le fichier du mauvais profil et le servirait jusqu'à
     * la prochaine recomposition.
     */
    suspend fun restoreActive(): String {
        val id = activeId.first()
        ActiveProfile.id = id
        return id
    }

    /**
     * Bascule de profil.
     *
     * [ActiveProfile] est posé **avant** l'écriture, et non en réaction au flux :
     * l'app se recompose sur la nouvelle valeur, et tout dépôt reconstruit dans
     * la foulée doit déjà ouvrir le bon fichier.
     */
    suspend fun setActive(id: String) {
        ActiveProfile.id = id
        store.edit { it[ACTIVE] = id }
    }

    /** Crée un profil et rend son identifiant. Ne bascule pas dessus. */
    suspend fun create(name: String, now: Long): Profile {
        val current = profiles.first()
        val profile = Profile(
            id = newId(now, current.map { it.id }.toSet()),
            name = name.trim(),
            colorIndex = current.size % Profile.COLOR_COUNT,
            createdAt = now,
        )
        write(current + profile)
        return profile
    }

    /**
     * Ajoute un profil **à son identifiant d'origine**, ou met à jour le nom et
     * la couleur s'il existe déjà.
     *
     * C'est ce dont l'import a besoin, et ce que [create] ne peut pas faire :
     * il forge un identifiant neuf, si bien que restaurer deux fois la même
     * sauvegarde fabriquerait deux « Enfants » côte à côte, chacun avec la
     * moitié de l'historique.
     */
    suspend fun upsert(profile: Profile) {
        if (profile.isDefault) {
            // Le profil d'origine n'a pas à être créé : il existe partout. Seuls
            // son nom et sa couleur voyagent.
            write(profiles.first().map { if (it.isDefault) profile else it })
            return
        }
        val current = profiles.first()
        write(
            if (current.any { it.id == profile.id }) {
                current.map { if (it.id == profile.id) it.copy(name = profile.name, colorIndex = profile.colorIndex) else it }
            } else {
                current + profile
            },
        )
    }

    /** Renomme. Un nom vide sur le profil d'origine le renvoie à son libellé traduit. */
    suspend fun rename(id: String, name: String) {
        write(profiles.first().map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    suspend fun setColor(id: String, colorIndex: Int) {
        write(profiles.first().map { if (it.id == id) it.copy(colorIndex = colorIndex) else it })
    }

    /**
     * Supprime un profil **et ses données**.
     *
     * Le profil d'origine ne se supprime pas : il n'a pas de fichiers à lui, ce
     * sont ceux de l'installation. Le supprimer effacerait l'historique de tout
     * le monde en croyant n'effacer qu'un profil.
     *
     * On bascule d'abord si c'était l'actif, pour qu'aucun flux ne lise les
     * données au moment où elles sont vidées.
     */
    suspend fun delete(id: String, now: Long = maintenantMs()) {
        if (id == DEFAULT_PROFILE_ID) return
        if (activeId.first() == id) setActive(DEFAULT_PROFILE_ID)
        write(profiles.first().filterNot { it.id == id })
        // La pierre tombale part avec la synchro : c'est elle qui apprend le
        // retrait aux autres appareils, et qui empêche le fichier distant — qui
        // contient encore le profil — de le réécrire ici au prochain import.
        rememberDeletion(id, now)
        clearProfileStores(id)
    }

    /** Date un retrait, en gardant la décision la plus récente. */
    suspend fun rememberDeletion(id: String, now: Long) {
        if (id == DEFAULT_PROFILE_ID) return
        val merged = deletedAt.first().toMutableMap()
        if ((merged[id] ?: 0L) < now) merged[id] = now
        store.edit { it[DELETED] = json.encodeToString(merged) }
    }

    /**
     * Fusionne des retraits venus d'ailleurs, et applique ceux qui gagnent.
     *
     * Un profil dont le retrait est **postérieur** à sa création disparaît : la
     * suppression faite sur un autre appareil se propage ici. À égalité, ou sur
     * des données d'avant l'horodatage, on garde le profil — un vieux fichier ne
     * doit rien effacer.
     */
    suspend fun mergeDeletions(incoming: Map<String, Long>) {
        if (incoming.isEmpty()) return
        val merged = deletedAt.first().toMutableMap()
        incoming.forEach { (id, at) ->
            if (id != DEFAULT_PROFILE_ID && (merged[id] ?: 0L) < at) merged[id] = at
        }
        store.edit { it[DELETED] = json.encodeToString(merged) }

        val doomed = profiles.first().filter { !it.isDefault && isDeleted(it, merged) }
        doomed.forEach { delete(it.id, merged.getValue(it.id)) }
    }

    /** Vrai si le retrait de ce profil est plus récent que sa création. */
    fun isDeleted(profile: Profile, tombstones: Map<String, Long>): Boolean =
        !profile.isDefault && (tombstones[profile.id] ?: 0L) > profile.createdAt

    private suspend fun write(list: List<Profile>) {
        // Le profil d'origine est déduit, pas stocké : ne persister que ce qui
        // s'en écarte évite qu'une liste vide et une liste « juste le défaut »
        // soient deux états distincts à distinguer.
        val stored = list.filterNot { it == Profile.Default }
        store.edit { it[PROFILES] = json.encodeToString(stored) }
    }

    private fun decodeTombstones(raw: String?): Map<String, Long> =
        raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<Map<String, Long>>(it) }.getOrNull() }
            .orEmpty()

    private fun decode(raw: String?): List<Profile> =
        raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() }
            .orEmpty()

    private fun complete(stored: List<Profile>): List<Profile> =
        listOf(stored.firstOrNull { it.isDefault } ?: Profile.Default) +
            stored.filterNot { it.isDefault }

    /**
     * Identifiant dérivé de l'horodatage, et non un compteur : un profil
     * supprimé puis recréé ne doit pas hériter des fichiers de l'ancien.
     */
    private fun newId(now: Long, taken: Set<String>): String {
        var candidate = "p$now"
        var suffix = 1
        while (candidate in taken) candidate = "p$now-${suffix++}"
        return candidate
    }

    private companion object {
        val PROFILES = stringPreferencesKey("profiles")
        val DELETED = stringPreferencesKey("profiles_deleted_at")
        val ACTIVE = stringPreferencesKey("active_profile")
    }
}
