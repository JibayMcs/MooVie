package fr.moovie.tv.data.sources

import fr.moovie.tv.shared.maintenantMs
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.core.sources.usecase.isMeasureFresh
import fr.moovie.tv.data.store.preferencesStore
import fr.moovie.tv.shared.appVersionName
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ENTRY_PREFIX = "measure:"

/** Voir [SourceCacheRepository] : même raison, même ordre de grandeur. */
private const val MAX_ENTRIES = 400

/**
 * Ce qu'une sonde a appris d'un lien d'embed.
 *
 * @param heights toutes les définitions du flux, la meilleure en tête. Le libellé
 *   affiché s'en déduit, et le classement des sources aussi — on ne stocke donc
 *   pas « 1080p », qui est du texte, mais le nombre qui se compare.
 * @param playable verdict de la sonde. Voir [isMeasureFresh] : il ne se garde pas
 *   aussi longtemps qu'une mesure.
 */
@Serializable
data class StreamMeasure(
    val heights: List<Int> = emptyList(),
    val playable: Boolean = true,
    val savedAt: Long = 0,
    val version: String = "",
)

/**
 * Mémoire des mesures de qualité, indexée par **URL d'embed**.
 *
 * ## Ce que ça règle
 *
 * Aucun catalogue n'annonce la définition en listant ses liens : il faut résoudre
 * l'embed puis lire la master playlist. Ce travail n'était gardé que le temps
 * d'une session, si bien que rouvrir une fiche de quinze sources après un
 * redémarrage relançait **quinze extractions** pour retrouver des hauteurs déjà
 * connues — au moment précis où l'application doit paraître prompte.
 *
 * ## Pourquoi un magasin séparé du cache des sources
 *
 * [SourceCacheRepository] est indexé par **titre** et périme en six heures, parce
 * que la liste des liens d'un catalogue bouge. Une mesure, elle, appartient à
 * **un lien** et vit bien plus longtemps. Les loger ensemble ferait jeter les
 * mesures à chaque péremption de la liste, ce qui est exactement le travail qu'on
 * cherche à ne pas refaire ; et un même hébergeur revu sous un autre titre
 * profite ici de ce qu'on sait déjà de lui.
 *
 * ## Global, et non par profil
 *
 * La définition d'un flux est un fait de l'installation, pas un goût. Deux
 * profils qui ouvrent la même fiche n'ont aucune raison de re-sonder chacun de
 * leur côté — d'où l'absence de ce magasin dans
 * [fr.moovie.tv.data.store.PROFILE_SCOPED_STORES].
 */
class StreamMeasureRepository {

    private val store = preferencesStore("moovie_stream_measures")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Toutes les mesures encore fraîches, par URL d'embed.
     *
     * Tout d'un coup, et non lien par lien : DataStore relit son fichier entier à
     * chaque accès, et l'interroger quinze fois à l'ouverture d'une fiche
     * dépenserait une part de ce qu'on vient d'économiser. L'appelant garde le
     * résultat pour la durée de son écran.
     */
    suspend fun all(now: Long = maintenantMs()): Map<String, StreamMeasure> =
        store.data.first().asMap()
            .mapNotNull { (key, value) ->
                if (!key.name.startsWith(ENTRY_PREFIX)) return@mapNotNull null
                val entry = runCatching { json.decodeFromString<StreamMeasure>(value as String) }
                    .getOrNull() ?: return@mapNotNull null
                if (!isMeasureFresh(entry.savedAt, entry.playable, entry.version, appVersionName, now)) {
                    return@mapNotNull null
                }
                key.name.removePrefix(ENTRY_PREFIX) to entry
            }
            .toMap()

    /** Retient ce qu'une sonde vient d'apprendre. */
    suspend fun put(
        url: String,
        heights: List<Int>,
        playable: Boolean,
        now: Long = maintenantMs(),
    ) {
        if (url.isBlank()) return
        store.edit { prefs ->
            prefs[stringPreferencesKey(ENTRY_PREFIX + url)] = json.encodeToString(
                StreamMeasure(heights, playable, now, appVersionName),
            )
            prune(prefs)
        }
    }

    /**
     * Oublie tout.
     *
     * Appelée par le bouton « Vider » des réglages, avec le cache des sources :
     * les deux répondent à la même question — « je crois que l'application me
     * ressert du vieux » — et n'en vider qu'un laisserait la moitié du symptôme.
     */
    suspend fun clear() {
        store.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(ENTRY_PREFIX) }
                .forEach { prefs.remove(stringPreferencesKey(it.name)) }
        }
    }

    /** Garde les [MAX_ENTRIES] mesures les plus récentes. */
    private fun prune(prefs: MutablePreferences) {
        val entries = prefs.asMap()
            .filterKeys { it.name.startsWith(ENTRY_PREFIX) }
            .map { (k, v) ->
                val saved = runCatching { json.decodeFromString<StreamMeasure>(v as String).savedAt }
                    .getOrDefault(0L)
                k.name to saved
            }
        if (entries.size <= MAX_ENTRIES) return
        entries.sortedBy { it.second }
            .take(entries.size - MAX_ENTRIES)
            .forEach { (name, _) -> prefs.remove(stringPreferencesKey(name)) }
    }
}
