package fr.moovie.tv.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Extracteur SeekStreaming / embed4me — port de seekstreaming_extract_handler.
 * Appelle l'API `/api/v1/video`, qui renvoie un hex chiffré AES-CBC ; on
 * déchiffre (clé/IV statiques) et on lit l'URL m3u8 dans le JGON (`cf`/`source`).
 */
class SeekStreamingExtractor(private val http: OkHttpClient) : SourceExtractor {

    override val hoster = "seekstreaming"

    private val json = Json { ignoreUnknownKeys = true }

    override fun canHandle(url: String): Boolean =
        HOST.containsMatchIn(url)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        runCatching {
            val domain = DOMAIN.find(link.url)?.groupValues?.get(1) ?: "lpayer.embed4me.com"
            val videoId = extractVideoId(link.url) ?: return@runCatching null
            val apiUrl = "https://$domain/api/v1/video?id=$videoId&w=1920&h=1080&r="

            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", Ua.BROWSER)
                .header("Accept", "*/*")
                .header("Referer", "https://$domain/")
                .header("Origin", "https://$domain")
                .build()
            val hex = http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                ?.trim()?.trim('"') ?: return@runCatching null

            val decrypted = decrypt(hex) ?: return@runCatching null
            val obj = json.parseToJsonElement(decrypted).jsonObject
            val m3u8 = obj.str("cf").ifBlank { obj.str("source") }
            if (m3u8.isBlank()) return@runCatching null

            PlayableStream(
                url = m3u8,
                format = StreamFormat.HLS,
                headers = mapOf("Referer" to "https://$domain/", "Origin" to "https://$domain", "User-Agent" to Ua.BROWSER),
                language = link.language,
            )
        }.getOrNull()
    }

    private fun extractVideoId(url: String): String? = when {
        url.contains('#') -> url.substringAfterLast('#').trim().ifBlank { null }
        url.contains("/embed/", ignoreCase = true) -> url.trimEnd('/').substringAfterLast('/').trim().ifBlank { null }
        else -> url.trimEnd('/').substringAfterLast('/').trim().ifBlank { null }
    }

    private fun decrypt(hex: String): String? = runCatching {
        val data = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(KEY, "AES"),
            IvParameterSpec(IV),
        )
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content?.trim().orEmpty()

    companion object {
        private val KEY = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
        private val IV = "1234567890oiuytr".toByteArray(Charsets.UTF_8)
        private val HOST = Regex("""embed4me|embedseek|seekstreaming""", RegexOption.IGNORE_CASE)
        private val DOMAIN = Regex("""^https?://([^/]+)""")
    }
}
