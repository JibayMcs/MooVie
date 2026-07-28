package fr.moovie.tv.data.intro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

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

/**
 * Client TheIntroDB (https://api.theintrodb.org). Fournit les horodatages
 * d'intro et de générique (outro) pour proposer les boutons « Passer ».
 * API non bloquée par les FAI → DNS système (pas besoin du DoH).
 */
class IntroDbRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    /**
     * Récupère les segments. [durationMs] aide l'API à choisir la bonne version
     * (director's cut, etc.). Renvoie null si aucune donnée (404) ou réseau HS.
     */
    suspend fun fetch(
        tmdbId: Int,
        isTv: Boolean,
        season: Int,
        episode: Int,
        durationMs: Long,
    ): IntroMedia? = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append("https://api.theintrodb.org/v3/media?tmdb_id=").append(tmdbId)
                if (isTv) append("&season=").append(season).append("&episode=").append(episode)
                if (durationMs > 0) append("&duration_ms=").append(durationMs)
            }
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                json.decodeFromString<IntroMedia>(resp.body!!.string())
            }
        }.getOrNull()
    }
}
