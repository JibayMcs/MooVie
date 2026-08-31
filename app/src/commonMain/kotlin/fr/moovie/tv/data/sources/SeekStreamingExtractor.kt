package fr.moovie.tv.data.sources

import fr.moovie.tv.shared.dechiffrerAesCbc
import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.core.sources.port.SourceExtractor
import fr.moovie.tv.shared.dispatcherEs
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Extracteur SeekStreaming / embed4me — port de seekstreaming_extract_handler.
 * Appelle l'API `/api/v1/video`, qui renvoie un hex chiffré AES-CBC ; on
 * déchiffre (clé/IV statiques) et on lit l'URL m3u8 dans le JGON (`cf`/`source`).
 */
class SeekStreamingExtractor(private val http: HttpGateway) : SourceExtractor {

    override val hoster = "seekstreaming"

    private val json = Json { ignoreUnknownKeys = true }

    override fun canHandle(url: String): Boolean =
        HOST.containsMatchIn(url)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(dispatcherEs) {
        runCatching {
            val domain = DOMAIN.find(link.url)?.groupValues?.get(1) ?: "lpayer.embed4me.com"
            val videoId = extractVideoId(link.url) ?: return@runCatching null
            val apiUrl = "https://$domain/api/v1/video?id=$videoId&w=1920&h=1080&r="

            val hex = http.getBody(
                apiUrl,
                mapOf(
                    "User-Agent" to Ua.BROWSER,
                    "Accept" to "*/*",
                    "Referer" to "https://$domain/",
                    "Origin" to "https://$domain",
                ),
            )?.trim()?.trim('"') ?: return@runCatching null

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
        dechiffrerAesCbc(data, KEY, IV)?.decodeToString()
    }.getOrNull()

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content?.trim().orEmpty()

    companion object {
        private val KEY = "kiemtienmua911ca".encodeToByteArray()
        private val IV = "1234567890oiuytr".encodeToByteArray()
        /**
         * Les domaines de la famille SeekStreaming.
         *
         * Cinq de plus que la liste d'origine, relevés dans le registre de
         * Movix : le même hébergeur se présente sous des noms qui n'ont rien à
         * voir entre eux, et un domaine non reconnu n'échoue pas bruyamment —
         * il rend simplement une source de moins, ce qui ne se remarque pas.
         */
        private val HOST = Regex(
            """embed4me|embedseek|seekstreaming|servicecatalog|technicalcatalog|""" +
                """seekplayer|seeks\.cloud|seekplays""",
            RegexOption.IGNORE_CASE,
        )
        private val DOMAIN = Regex("""^https?://([^/]+)""")
    }
}
