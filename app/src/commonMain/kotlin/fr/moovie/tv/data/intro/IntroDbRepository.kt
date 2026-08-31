package fr.moovie.tv.data.intro

import fr.moovie.tv.core.intro.SegmentKind
import fr.moovie.tv.core.intro.SegmentSubmission
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.shared.dispatcherEs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import fr.moovie.tv.data.net.clientRest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Un segment (intro/générique) : bornes en ms, chacune éventuellement absente. */
@Serializable
data class Segment(
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
)

/** Réponse /media : segments par type (les listes absentes = pas de données). */
@Serializable
data class IntroMedia(
    val intro: List<Segment> = emptyList(),
    val credits: List<Segment> = emptyList(),
)

/** Corps envoyé à /submit. Les champs nuls sont omis : l'API les lit comme absents. */
@Serializable
private data class SubmitBody(
    @SerialName("tmdb_id") val tmdbId: Int,
    val type: String,
    val segment: String,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
    @SerialName("video_duration_ms") val videoDurationMs: Long? = null,
)

/** Pourquoi un envoi a échoué, en termes affichables. */
enum class SubmitError { NO_KEY, UNAUTHORIZED, ALREADY_SUBMITTED, RATE_LIMITED, REJECTED, NETWORK }

/**
 * Client TheIntroDB (`api.theintrodb.org/v3`). Fournit les horodatages d'intro
 * et de générique, et permet d'en signaler quand ils manquent.
 * API non bloquée par les FAI → DNS système (pas besoin du DoH).
 *
 * **La clé est celle de l'utilisateur, pas de l'application.** C'est l'inverse
 * d'OpenSubtitles : ici elle identifie un contributeur, et la sémantique de
 * l'API le confirme — ses propres signalements comptent **dix fois plus** dans
 * la moyenne qui lui est servie. Elle se saisit donc dans les réglages, comme
 * celle de TMDB.
 */
class IntroDbRepository(
    private val settings: SettingsRepository = SettingsRepository(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // Un champ nul omis vaut « absent » pour l'API — et c'est exactement ce
        // qu'on veut dire pour une intro sans début ou un générique sans fin.
        explicitNulls = false
    }
    private val client = clientRest

    /**
     * Récupère les segments. [durationMs] aide l'API à choisir la bonne version
     * (director's cut, etc.). Renvoie null si aucune donnée (404) ou réseau HS.
     *
     * La clé est envoyée dès qu'elle existe : elle fait remonter les
     * signalements **en attente** de l'utilisateur lui-même, pondérés dix fois.
     * Autrement dit, ce qu'il vient de signaler prend effet chez lui tout de
     * suite, sans attendre le consensus de la communauté.
     */
    suspend fun fetch(
        tmdbId: Int,
        isTv: Boolean,
        season: Int,
        episode: Int,
        durationMs: Long,
    ): IntroMedia? = withContext(dispatcherEs) {
        val key = apiKey()
        runCatching {
            val url = buildString {
                append("$BASE_URL/media?tmdb_id=").append(tmdbId)
                if (isTv) append("&season=").append(season).append("&episode=").append(episode)
                if (durationMs > 0) append("&duration_ms=").append(durationMs)
            }
            val reponse = client.get(url) {
                header("Accept", "application/json")
                authorize(key)
            }
            if (!reponse.status.isSuccess()) {
                null
            } else {
                json.decodeFromString<IntroMedia>(reponse.bodyAsText())
            }
        }.getOrNull()
    }

    /** Vrai si une clé est renseignée, donc si l'utilisateur peut contribuer. */
    suspend fun canSubmit(): Boolean = apiKey().isNotBlank()

    /**
     * Envoie un signalement. Rend null en cas de succès, la cause sinon.
     *
     * Aucune validation ici : elle est faite en amont par
     * [fr.moovie.tv.core.intro.validateSubmission], pure et testée. Ce qui est
     * attrapé à ce niveau, ce sont les refus du serveur.
     */
    suspend fun submit(submission: SegmentSubmission): SubmitError? = withContext(dispatcherEs) {
        val key = apiKey()
        if (key.isBlank()) return@withContext SubmitError.NO_KEY

        val body = SubmitBody(
            tmdbId = submission.tmdbId,
            type = if (submission.isTv) "tv" else "movie",
            segment = when (submission.kind) {
                SegmentKind.INTRO -> "intro"
                SegmentKind.CREDITS -> "credits"
            },
            season = submission.season?.takeIf { submission.isTv },
            episode = submission.episode?.takeIf { submission.isTv },
            startMs = submission.startMs,
            endMs = submission.endMs,
            videoDurationMs = submission.videoDurationMs?.takeIf { it > 0 },
        )

        runCatching {
            val reponse = client.post("$BASE_URL/submit") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SubmitBody.serializer(), body))
                header("Accept", "application/json")
                authorize(key)
            }
            val brut = reponse.bodyAsText()
            val code = reponse.status.value
            when {
                reponse.status.isSuccess() -> null
                code == 401 || code == 403 -> SubmitError.UNAUTHORIZED
                code == 429 -> SubmitError.RATE_LIMITED
                // Un signalement par type de segment et par épisode : le
                // second est refusé, et ce n'est pas une panne — l'interface
                // doit le dire autrement qu'avec une erreur.
                code == 409 ||
                    brut.contains("already", ignoreCase = true) -> SubmitError.ALREADY_SUBMITTED
                else -> SubmitError.REJECTED
            }
        }.getOrElse { SubmitError.NETWORK }
    }

    private suspend fun apiKey(): String = settings.introDbApiKey.first().trim()

    private fun HttpRequestBuilder.authorize(key: String) {
        if (key.isNotBlank()) header("Authorization", "Bearer $key")
    }

    private companion object {
        const val BASE_URL = "https://api.theintrodb.org/v3"
    }
}
