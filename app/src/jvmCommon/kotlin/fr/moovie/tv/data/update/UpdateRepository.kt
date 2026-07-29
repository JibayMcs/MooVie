package fr.moovie.tv.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GithubAsset> = emptyList(),
    val prerelease: Boolean = false,
    val draft: Boolean = false,
)

/**
 * Mises à jour via les GitHub Releases publiques du dépôt : la CI publie un APK
 * signé par tag `vX.Y.Z`, l'app compare `releases/latest` à sa propre version.
 */
class UpdateRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    /** Dernière release publiée (null si réseau HS ou aucune release). */
    suspend fun latestRelease(): GithubRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/JibayMcs/MooVie/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                json.decodeFromString<GithubRelease>(resp.body!!.string())
            }
        }.getOrNull()
    }

    /** Compare deux versions « semver » (tag `v1.2.3` vs `1.2.0`). */
    fun isNewer(tag: String, current: String): Boolean {
        fun parse(v: String) = v.removePrefix("v").split('-').first()
            .split('.').mapNotNull { it.toIntOrNull() }
        val a = parse(tag)
        val b = parse(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Télécharge l'APK vers [dest] en publiant la progression (0..1).
     * Renvoie true si le fichier est complet.
     */
    suspend fun downloadApk(url: String, dest: File, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val body = resp.body ?: return@use false
                    val total = body.contentLength()
                    dest.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                                copied += read
                                if (total > 0) onProgress(copied.toFloat() / total)
                            }
                        }
                    }
                    true
                }
            }.getOrDefault(false)
        }
}
