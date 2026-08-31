package fr.moovie.tv.data.subtitles

import fr.moovie.tv.data.net.clientRest
import fr.moovie.tv.shared.appVersionName
import fr.moovie.tv.shared.dispatcherEs
import fr.moovie.tv.shared.maintenantMs
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

private const val BASE_URL = "https://api.opensubtitles.com/api/v1"

// ─── Réponses ────────────────────────────────────────────────────────────────

@Serializable
data class OsSearchResponse(val data: List<OsSubtitle> = emptyList())

@Serializable
data class OsSubtitle(val attributes: OsAttributes = OsAttributes())

@Serializable
data class OsAttributes(
    val language: String? = null,
    val release: String = "",
    val fps: Double? = null,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("from_trusted") val fromTrusted: Boolean = false,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerialName("foreign_parts_only") val foreignPartsOnly: Boolean = false,
    @SerialName("ai_translated") val aiTranslated: Boolean = false,
    @SerialName("machine_translated") val machineTranslated: Boolean = false,
    val files: List<OsFile> = emptyList(),
)

@Serializable
data class OsFile(
    @SerialName("file_id") val fileId: Long = 0,
    @SerialName("file_name") val fileName: String = "",
)

@Serializable
data class OsDownloadResponse(
    val link: String = "",
    @SerialName("file_name") val fileName: String = "",
    val requests: Int? = null,
    val remaining: Int? = null,
    val message: String = "",
    @SerialName("reset_time_utc") val resetTimeUtc: String? = null,
)

@Serializable
data class OsUserInfoResponse(val data: OsUserInfo = OsUserInfo())

@Serializable
data class OsUserInfo(
    @SerialName("allowed_downloads") val allowedDownloads: Int? = null,
    @SerialName("remaining_downloads") val remainingDownloads: Int? = null,
    @SerialName("downloads_count") val downloadsCount: Int? = null,
    val level: String = "",
    val vip: Boolean = false,
)

@Serializable
data class OsLoginResponse(
    val token: String = "",
    @SerialName("base_url") val baseUrl: String = "",
    val user: OsUserInfo = OsUserInfo(),
)

/** Ce qui a échoué, pour que l'appelant puisse en dire quelque chose d'utile. */
sealed interface OsFailure {
    /** Quota du jour épuisé. Le cas le plus fréquent, donc pas une erreur générique. */
    data class QuotaExhausted(val resetTimeUtc: String?) : OsFailure
    data object Unauthorized : OsFailure
    data object RateLimited : OsFailure
    data class Http(val code: Int) : OsFailure
    data object Network : OsFailure
}

/**
 * Client bas niveau de l'API OpenSubtitles (`api.opensubtitles.com/api/v1`).
 *
 * **OkHttp à la main plutôt que Retrofit**, contrairement à TMDB, pour deux
 * raisons. La surface est de quatre endpoints, et surtout la documentation
 * impose des paramètres GET *triés alphabétiquement, en minuscules et sans les
 * valeurs par défaut* — un `@Query` Retrofit les émet dans l'ordre de
 * déclaration et sérialise les défauts, ce qui déclenche des redirections que la
 * doc demande justement d'éviter. Construire l'URL soi-même est ici plus simple
 * que de contraindre l'outil.
 *
 * **DNS système, pas de DoH** : comme TMDB et TheIntroDB, ce domaine n'est pas
 * bloqué par les FAI. Le DoH est réservé à l'extraction de sources.
 *
 * La clé identifie l'application et non l'utilisateur — voir
 * [fr.moovie.tv.shared.openSubtitlesApiKey].
 */
class OpenSubtitlesApi(
    private val apiKey: String,
    private val userAgent: String = "Moo-vie v$appVersionName",
) {

    // `coerceInputValues` n'est pas décoratif : l'API renvoie `"from_trusted": null`
    // sur certains résultats, là où son propre schéma annonce un booléen. Sans
    // cette tolérance, **un seul** résultat mal formé fait échouer le décodage de
    // toute la page — 153 sous-titres perdus pour un null. Constaté sur la sonde.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    /**
     * Un seul client, deux usages. Le lien de téléchargement est servi par une
     * autre couche, avec son propre plafond : on n'y envoie **ni clé ni jeton**
     * — il n'en a pas besoin, et le jeton n'a rien à faire hors de l'API. Cette
     * séparation tient donc aux en-têtes posés, pas à la configuration du
     * client, qui était déjà identique des deux côtés.
     */
    private val client = clientRest

    /** Jeton de l'utilisateur connecté. Null tant qu'il ne l'est pas. */
    @Volatile
    var token: String? = null

    /**
     * L'API plafonne à 5 requêtes par seconde et par IP, et rend 429 au-delà.
     * On s'espace nous-mêmes plutôt que de découvrir la limite en la dépassant.
     */
    private val throttle = Mutex()
    private var lastCallAtMs = 0L

    val configured: Boolean get() = apiKey.isNotBlank()

    /**
     * Recherche. Gratuite et sans quota côté OpenSubtitles — c'est le
     * téléchargement qui coûte.
     *
     * [languages] est trié et mis en minuscules ici : la doc l'exige, et un
     * ordre différent provoque une redirection.
     */
    suspend fun search(
        tmdbId: Int,
        languages: List<String>,
        season: Int? = null,
        episode: Int? = null,
    ): Result<OsSearchResponse> {
        val params = buildMap {
            // Uniquement des valeurs utiles : la doc supprime les défauts, les
            // envoyer coûte une redirection.
            put("languages", languages.map { it.lowercase() }.sorted().joinToString(","))
            put("tmdb_id", tmdbId.toString())
            season?.let { put("season_number", it.toString()) }
            episode?.let { put("episode_number", it.toString()) }
        }
        val query = params.entries
            // Tri alphabétique exigé par la doc, « page » comprise.
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value.encodeParam()}" }

        return get("$BASE_URL/subtitles?$query")
    }

    /**
     * Demande un lien de téléchargement. **Consomme le quota**, y compris si le
     * fichier n'est jamais récupéré ensuite.
     */
    suspend fun requestDownload(fileId: Long): Result<OsDownloadResponse> =
        appel { client.post("$BASE_URL/download") { entetes(); corpsJson("""{"file_id":$fileId}""") } }

    /** Récupère le fichier lui-même. Ne consomme rien : le quota est déjà payé. */
    suspend fun fetchFile(link: String): Result<String> = withContext(dispatcherEs) {
        runCatching {
            val reponse = client.get(link) { header("User-Agent", userAgent) }
            if (!reponse.status.isSuccess()) error("HTTP ${reponse.status.value}")
            reponse.bodyAsText()
        }
    }

    /** Quota de l'utilisateur connecté. Exige un jeton — 401 sans compte. */
    suspend fun userInfo(): Result<OsUserInfoResponse> = get("$BASE_URL/infos/user")

    /**
     * Ouvre une session utilisateur. Le jeton vaut 24 h et **il n'existe aucun
     * endpoint de rafraîchissement** : le renouveler impose de repasser par ici
     * avec les identifiants.
     */
    suspend fun login(username: String, password: String): Result<OsLoginResponse> {
        val corps = json.encodeToString(
            OsLoginRequest.serializer(),
            OsLoginRequest(username, password),
        )
        return appel { client.post("$BASE_URL/login") { entetes(); corpsJson(corps) } }
    }

    suspend fun logout(): Result<Unit> = withContext(dispatcherEs) {
        runCatching {
            space()
            client.delete("$BASE_URL/logout") { entetes() }
            token = null
        }
    }

    @Serializable
    private data class OsLoginRequest(val username: String, val password: String)

    private suspend inline fun <reified T> get(url: String): Result<T> =
        appel { client.get(url) { entetes() } }

    /**
     * Exécute l'appel, respecte l'espacement, puis décode — ou lève une
     * [OsHttpException] **porteuse du corps**.
     *
     * Le corps est lu dans tous les cas, succès comme échec : c'est là
     * qu'OpenSubtitles loge la différence entre un identifiant invalide et un
     * quota épuisé, que le seul code 406 ne permet pas de trancher.
     */
    private suspend inline fun <reified T> appel(
        crossinline requete: suspend () -> io.ktor.client.statement.HttpResponse,
    ): Result<T> = withContext(dispatcherEs) {
        runCatching {
            space()
            val reponse = requete()
            val brut = reponse.bodyAsText()
            if (!reponse.status.isSuccess()) throw OsHttpException(reponse.status.value, brut)
            json.decodeFromString<T>(brut)
        }
    }

    /** Ajoute les en-têtes exigés : sans `User-Agent` valide, l'API rend 403. */
    private fun HttpRequestBuilder.entetes() {
        header("Api-Key", apiKey)
        header("User-Agent", userAgent)
        header("Accept", "application/json")
        token?.let { header("Authorization", "Bearer $it") }
    }

    /**
     * `Content-Type` est posé par le corps et non par [entetes] : Ktor le
     * dérive de ce qu'on envoie, et le forcer en en-tête sur un GET sans corps
     * produirait une requête que l'API refuse.
     */
    private fun HttpRequestBuilder.corpsJson(corps: String) {
        contentType(ContentType.Application.Json)
        setBody(corps)
    }

    /** Laisse au moins [MIN_INTERVAL_MS] entre deux appels. */
    private suspend fun space() {
        throttle.withLock {
            val now = maintenantMs()
            val wait = MIN_INTERVAL_MS - (now - lastCallAtMs)
            if (wait > 0) delay(wait)
            lastCallAtMs = maintenantMs()
        }
    }

    private companion object {
        /** 5 requêtes/seconde autorisées : on vise 4 pour garder de la marge. */
        const val MIN_INTERVAL_MS = 250L
    }
}

/** Échec HTTP porteur du corps, où l'API loge ses messages (« quota exceeded »). */
class OsHttpException(val code: Int, val body: String) : Exception("HTTP $code")

/**
 * Traduit un échec en cause exploitable.
 *
 * Le 406 mérite d'être distingué : la doc l'attribue à un `file_id` invalide,
 * mais c'est aussi ce que rend l'API quand le quota est épuisé — d'où la lecture
 * du corps plutôt que du seul code.
 */
fun Throwable.asOsFailure(): OsFailure = when (this) {
    is OsHttpException -> when {
        code == 401 -> OsFailure.Unauthorized
        code == 429 -> OsFailure.RateLimited
        code == 406 && body.contains("quota", ignoreCase = true) ->
            OsFailure.QuotaExhausted(null)
        else -> OsFailure.Http(code)
    }
    else -> OsFailure.Network
}

/** Encodage des valeurs de requête : la doc demande « + » pour l'espace. */
private fun String.encodeParam(): String = replace(" ", "+")
