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
private const val HIST_PREFIX = "hist:"
private const val META_PREFIX = "meta:"
private const val AUDIO_PREFIX = "audio:"

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

    /** Historique de visionnage, du plus récent au plus ancien. */
    val history: Flow<List<HistoryEntry>> = store.data.map { prefs ->
        prefs.asMap().mapNotNull { (k, v) ->
            if (!k.name.startsWith(HIST_PREFIX)) return@mapNotNull null
            runCatching { json.decodeFromString<HistoryEntry>(v as String) }.getOrNull()
        }.sortedByDescending { it.watchedAt }
    }

    /**
     * Mémorise le nom, l'image et les genres d'un titre, relevés à l'ouverture
     * de sa fiche. Voir [TitleMeta] pour le pourquoi de cette table à part.
     */
    suspend fun rememberTitle(titleKey: String, meta: TitleMeta) {
        store.edit { it[stringPreferencesKey(META_PREFIX + titleKey)] = json.encodeToString(meta) }
    }

    /** Retire une ligne de l'historique (sans toucher au statut vu). */
    suspend fun removeFromHistory(key: String) {
        store.edit { it.remove(stringPreferencesKey(HIST_PREFIX + key)) }
    }

    /** Fiches mémorisées, pour l'export. */
    suspend fun titles(): Map<String, TitleMeta> = store.data.first().asMap()
        .mapNotNull { (k, v) ->
            if (!k.name.startsWith(META_PREFIX)) return@mapNotNull null
            val meta = runCatching { json.decodeFromString<TitleMeta>(v as String) }.getOrNull()
            meta?.let { k.name.removePrefix(META_PREFIX) to it }
        }
        .toMap()

    /** Pistes audio retenues, par titre, pour l'export. */
    suspend fun audioTracks(): Map<String, String> = store.data.first().asMap()
        .mapNotNull { (k, v) ->
            if (!k.name.startsWith(AUDIO_PREFIX)) return@mapNotNull null
            (v as? String)?.takeIf { it.isNotBlank() }?.let { k.name.removePrefix(AUDIO_PREFIX) to it }
        }
        .toMap()

    /**
     * Réécrit tout le suivi d'un coup, à l'import.
     *
     * On efface avant d'écrire : `mergeBackup` rend l'**état final voulu**, que
     * l'utilisateur ait choisi de fusionner ou de remplacer. Repartir de zéro
     * évite qu'une entrée que la fusion a écartée survive faute d'être écrasée.
     * Les fiches ([TitleMeta]) échappent au ménage : elles ne coûtent rien et
     * portent les affiches de l'historique.
     */
    suspend fun replaceAll(
        resume: List<ResumeEntry>,
        watchlist: List<WatchlistEntry>,
        watched: Set<String>,
        history: List<HistoryEntry>,
        audioTracks: Map<String, String>,
        titles: Map<String, TitleMeta> = emptyMap(),
    ) {
        store.edit { prefs ->
            prefs.asMap().keys
                .filter { key ->
                    listOf(RESUME_PREFIX, SEEN_PREFIX, LATER_PREFIX, HIST_PREFIX, AUDIO_PREFIX)
                        .any { key.name.startsWith(it) }
                }
                .forEach { prefs.remove(it) }

            resume.forEach { prefs[stringPreferencesKey(RESUME_PREFIX + it.key)] = json.encodeToString(it) }
            watchlist.forEach { prefs[stringPreferencesKey(LATER_PREFIX + it.key)] = json.encodeToString(it) }
            watched.forEach { prefs[booleanPreferencesKey(SEEN_PREFIX + it)] = true }
            history.forEach { prefs[stringPreferencesKey(HIST_PREFIX + it.key)] = json.encodeToString(it) }
            audioTracks.forEach { (k, v) -> prefs[stringPreferencesKey(AUDIO_PREFIX + k)] = v }
            titles.forEach { (k, v) -> prefs[stringPreferencesKey(META_PREFIX + k)] = json.encodeToString(v) }
        }
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
                    // Avant le remove : c'est l'entrée de reprise qui porte le
                    // titre et l'affiche de la ligne d'historique.
                    prefs.recordHistory(key, System.currentTimeMillis())
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
    /**
     * Piste audio retenue pour un titre, sous forme de **libellé** et non
     * d'identifiant : celui-ci est propre au flux, et le rejouer d'un épisode à
     * l'autre désignerait une piste au hasard (voir `matchAudioTrack`).
     *
     * Clé au niveau du titre (`tv:<id>`) : on choisit une langue pour une série,
     * pas pour un épisode.
     */
    suspend fun audioTrack(titleKey: String): String? =
        store.data.first()[stringPreferencesKey(AUDIO_PREFIX + titleKey)]?.takeIf { it.isNotBlank() }

    suspend fun setAudioTrack(titleKey: String, label: String) {
        if (titleKey.isBlank() || label.isBlank()) return
        store.edit { it[stringPreferencesKey(AUDIO_PREFIX + titleKey)] = label }
    }

    suspend fun remove(key: String) {
        store.edit { it.remove(stringPreferencesKey(RESUME_PREFIX + key)) }
    }

    /** Marque vu/non vu. Marquer vu retire aussi le contenu de la reprise. */
    suspend fun setWatched(key: String, watched: Boolean) {
        store.edit { prefs ->
            if (watched) {
                prefs[booleanPreferencesKey(SEEN_PREFIX + key)] = true
                prefs.recordHistory(key, System.currentTimeMillis())
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
            val now = System.currentTimeMillis()
            keys.forEach { key ->
                if (watched) {
                    prefs[booleanPreferencesKey(SEEN_PREFIX + key)] = true
                    prefs.recordHistory(key, now)
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
     * Consigne un visionnage.
     *
     * Les métadonnées viennent de l'entrée de reprise, présente dès que la
     * lecture a démarré. Un contenu marqué vu à la main sans avoir jamais été lu
     * n'en a pas : on retombe alors sur la [TitleMeta] posée par sa fiche —
     * sans quoi la ligne d'historique serait une vignette vide et sans nom.
     */
    private fun MutablePreferences.recordHistory(key: String, now: Long) {
        val histKey = stringPreferencesKey(HIST_PREFIX + key)
        // Déjà présent : on ne réécrit pas, la première date de visionnage fait foi.
        if (this[histKey] != null) return
        val resume = this[stringPreferencesKey(RESUME_PREFIX + key)]
            ?.let { runCatching { json.decodeFromString<ResumeEntry>(it) }.getOrNull() }
        val parts = key.split(":")
        val isTv = parts.firstOrNull() == "tv"
        val tmdbId = parts.getOrNull(1)?.toIntOrNull() ?: resume?.tmdbId ?: return
        val titleKey = if (isTv) "tv:$tmdbId" else "movie:$tmdbId"
        val meta = this[stringPreferencesKey(META_PREFIX + titleKey)]
            ?.let { runCatching { json.decodeFromString<TitleMeta>(it) }.getOrNull() }
        // "tv:<id>:s1e2" → saison 1, épisode 2 quand la reprise fait défaut.
        val (season, episode) = resume?.let { it.season to it.episode }
            ?: parseEpisode(parts.getOrNull(2))
        val entry = HistoryEntry(
            key = key,
            tmdbId = tmdbId,
            isTv = isTv,
            season = season,
            episode = episode,
            title = resume?.title?.takeIf { it.isNotBlank() } ?: meta?.title.orEmpty(),
            imageUrl = resume?.imageUrl ?: meta?.imageUrl,
            genres = meta?.genres.orEmpty(),
            watchedAt = now,
        )
        this[histKey] = json.encodeToString(entry)
    }

    /** "s1e2" → 1 à 2. (0, 0) si le suffixe manque ou ne suit pas la forme. */
    private fun parseEpisode(suffix: String?): Pair<Int, Int> {
        val match = suffix?.let { Regex("""s(\d+)e(\d+)""").matchEntire(it) } ?: return 0 to 0
        return (match.groupValues[1].toIntOrNull() ?: 0) to (match.groupValues[2].toIntOrNull() ?: 0)
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
