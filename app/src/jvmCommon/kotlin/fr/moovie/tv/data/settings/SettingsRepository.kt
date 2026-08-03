package fr.moovie.tv.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.net.DohProvider
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Réglages utilisateur persistés. Tout ce qui doit être saisi/choisi par
 * l'utilisateur (clé TMDB, langue de stream, langue d'UI…) passe par ici.
 * À étendre par catégorie au fur et à mesure (sources, lecture, interface).
 */
class SettingsRepository {

    private val store = preferencesStore("moovie_settings")

    val tmdbApiKey: Flow<String> =
        store.data.map { it[TMDB_API_KEY].orEmpty() }

    val streamLanguage: Flow<StreamLanguage> =
        store.data.map {
            runCatching { StreamLanguage.valueOf(it[STREAM_LANGUAGE] ?: "VF") }
                .getOrDefault(StreamLanguage.VF)
        }

    /** Providers désactivés par l'utilisateur (par défaut : aucun). */
    val disabledProviders: Flow<Set<String>> =
        store.data.map { prefs ->
            prefs[DISABLED_PROVIDERS]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }

    /**
     * Ordre de priorité des providers (les premiers sont joués en premier).
     * Les providers absents de la liste passent en fin, dans l'ordre du registre.
     */
    val providerOrder: Flow<List<String>> =
        store.data.map { prefs ->
            prefs[PROVIDER_ORDER]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        }

    /** DoH activé (par défaut oui : nécessaire au contournement du blocage FAI). */
    val dohEnabled: Flow<Boolean> =
        store.data.map { it[DOH_ENABLED] ?: true }

    /** Résolveur DoH choisi (par défaut Cloudflare). */
    val dohProvider: Flow<DohProvider> =
        store.data.map {
            runCatching { DohProvider.valueOf(it[DOH_PROVIDER] ?: "CLOUDFLARE") }
                .getOrDefault(DohProvider.CLOUDFLARE)
        }

    /** Boutons « Passer l'intro / le générique » (TheIntroDB) — activés par défaut. */
    val skipIntroOutro: Flow<Boolean> =
        store.data.map { it[SKIP_INTRO_OUTRO] ?: true }

    suspend fun setSkipIntroOutro(value: Boolean) =
        store.edit { it[SKIP_INTRO_OUTRO] = value }

    /**
     * Masque les cartes de statistiques de l'historique — activé par défaut :
     * la page s'ouvre sur la grille pleine hauteur, les stats ne sont qu'un
     * bonus qu'on va chercher.
     */
    val hideHistoryWidgets: Flow<Boolean> =
        store.data.map { it[HIDE_HISTORY_WIDGETS] ?: true }

    suspend fun setHideHistoryWidgets(value: Boolean) =
        store.edit { it[HIDE_HISTORY_WIDGETS] = value }

    /** Fréquence de vérification des mises à jour (30 min par défaut). */
    val updateInterval: Flow<UpdateInterval> =
        store.data.map {
            runCatching { UpdateInterval.valueOf(it[UPDATE_INTERVAL] ?: "M30") }
                .getOrDefault(UpdateInterval.M30)
        }

    suspend fun setUpdateInterval(value: UpdateInterval) =
        store.edit { it[UPDATE_INTERVAL] = value.name }

    /** Délai avant l'écran de veille en pause (15 min par défaut). */
    val screensaverDelay: Flow<ScreensaverDelay> =
        store.data.map {
            runCatching { ScreensaverDelay.valueOf(it[SCREENSAVER_DELAY] ?: "M15") }
                .getOrDefault(ScreensaverDelay.M15)
        }

    suspend fun setScreensaverDelay(value: ScreensaverDelay) =
        store.edit { it[SCREENSAVER_DELAY] = value.name }

    /** Enchaînement automatique de l'épisode suivant en fin de lecture. */
    val autoPlayNext: Flow<Boolean> =
        store.data.map { it[AUTO_PLAY_NEXT] ?: true }

    suspend fun setAutoPlayNext(value: Boolean) =
        store.edit { it[AUTO_PLAY_NEXT] = value }

    /**
     * Date et heure en haut du lecteur. Activées par défaut : devant un film,
     * savoir l'heure sans sortir de l'app est précisément ce qu'on cherche.
     */
    val playerClock: Flow<Boolean> =
        store.data.map { it[PLAYER_CLOCK] ?: true }

    suspend fun setPlayerClock(value: Boolean) =
        store.edit { it[PLAYER_CLOCK] = value }

    suspend fun setDohEnabled(value: Boolean) =
        store.edit { it[DOH_ENABLED] = value }

    suspend fun setDohProvider(value: DohProvider) =
        store.edit { it[DOH_PROVIDER] = value.name }

    // ─── Compte OpenSubtitles ────────────────────────────────────────────────
    //
    // La *clé* d'API n'est pas ici : elle identifie l'application et vit dans le
    // binaire. Ce qui est propre à l'utilisateur, c'est son compte, et lui seul.
    // Sans compte, le téléchargement reste possible mais très plafonné.

    val osUsername: Flow<String> = store.data.map { it[OS_USERNAME].orEmpty() }

    /**
     * Mot de passe, conservé **uniquement si l'utilisateur l'a demandé**.
     *
     * Il n'existe aucun endpoint de rafraîchissement chez OpenSubtitles : le
     * jeton vaut 24 h et le renouveler impose de renvoyer les identifiants. Se
     * souvenir de la connexion signifie donc mécaniquement garder le mot de
     * passe — on le dit à l'utilisateur plutôt que de le cacher derrière une
     * case à cocher anodine.
     *
     * Il ne part **jamais** dans une sauvegarde : une clé d'API se révoque et ne
     * vaut que pour un service, un mot de passe se réutilise ailleurs.
     */
    val osPassword: Flow<String> = store.data.map { it[OS_PASSWORD].orEmpty() }

    val osRemember: Flow<Boolean> = store.data.map { it[OS_REMEMBER] ?: false }

    /** Jeton de session. Vide = déconnecté. */
    val osToken: Flow<String> = store.data.map { it[OS_TOKEN].orEmpty() }

    /** Instant d'obtention du jeton, pour savoir quand il expire (24 h). */
    val osTokenAt: Flow<Long> = store.data.map { it[OS_TOKEN_AT] ?: 0L }

    suspend fun setOsCredentials(username: String, password: String, remember: Boolean) =
        store.edit {
            it[OS_USERNAME] = username
            it[OS_REMEMBER] = remember
            // Oublier, c'est effacer : garder le mot de passe d'un utilisateur
            // qui vient de décocher la case serait le trahir.
            if (remember) it[OS_PASSWORD] = password else it.remove(OS_PASSWORD)
        }

    suspend fun setOsSession(token: String, atMs: Long) = store.edit {
        it[OS_TOKEN] = token
        it[OS_TOKEN_AT] = atMs
    }

    suspend fun clearOsSession() = store.edit {
        it.remove(OS_TOKEN)
        it.remove(OS_TOKEN_AT)
    }

    /** Déconnexion complète : la session *et* ce qui permettait de la rouvrir. */
    suspend fun forgetOsAccount() = store.edit {
        listOf(OS_TOKEN, OS_TOKEN_AT, OS_USERNAME, OS_PASSWORD, OS_REMEMBER)
            .forEach { key -> it.remove(key) }
    }

    /**
     * Langues de sous-titres recherchées, **dans l'ordre de préférence**.
     *
     * L'ordre n'est pas décoratif : c'est lui que le classement des candidats
     * suit en premier critère. La première langue de la liste passe donc avant
     * toutes les autres, quelle que soit la popularité du sous-titre.
     */
    val subtitleLanguages: Flow<List<String>> = store.data.map { prefs ->
        prefs[SUBTITLE_LANGUAGES]?.split(',')?.filter { it.isNotBlank() }
            ?: listOf("fr", "en")
    }

    suspend fun setSubtitleLanguages(value: List<String>) =
        store.edit { it[SUBTITLE_LANGUAGES] = value.joinToString(",") }

    suspend fun setTmdbApiKey(value: String) =
        store.edit { it[TMDB_API_KEY] = value.trim() }

    suspend fun setStreamLanguage(value: StreamLanguage) =
        store.edit { it[STREAM_LANGUAGE] = value.name }

    suspend fun setProviderEnabled(name: String, enabled: Boolean) =
        store.edit { prefs ->
            val current = prefs[DISABLED_PROVIDERS]?.split(',')?.filter { it.isNotBlank() }?.toMutableSet()
                ?: mutableSetOf()
            if (enabled) current.remove(name) else current.add(name)
            prefs[DISABLED_PROVIDERS] = current.joinToString(",")
        }

    suspend fun setProviderOrder(order: List<String>) =
        store.edit { it[PROVIDER_ORDER] = order.joinToString(",") }

    private companion object {
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val STREAM_LANGUAGE = stringPreferencesKey("stream_language")
        val DISABLED_PROVIDERS = stringPreferencesKey("disabled_providers")
        val PROVIDER_ORDER = stringPreferencesKey("provider_order")
        val DOH_ENABLED = booleanPreferencesKey("doh_enabled")
        val DOH_PROVIDER = stringPreferencesKey("doh_provider")
        val SKIP_INTRO_OUTRO = booleanPreferencesKey("skip_intro_outro")
        val HIDE_HISTORY_WIDGETS = booleanPreferencesKey("hide_history_widgets")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val PLAYER_CLOCK = booleanPreferencesKey("player_clock")
        val UPDATE_INTERVAL = stringPreferencesKey("update_interval")
        val SCREENSAVER_DELAY = stringPreferencesKey("screensaver_delay")
        val OS_USERNAME = stringPreferencesKey("os_username")
        val OS_PASSWORD = stringPreferencesKey("os_password")
        val OS_REMEMBER = booleanPreferencesKey("os_remember")
        val OS_TOKEN = stringPreferencesKey("os_token")
        val OS_TOKEN_AT = longPreferencesKey("os_token_at")
        val SUBTITLE_LANGUAGES = stringPreferencesKey("subtitle_languages")
    }
}
