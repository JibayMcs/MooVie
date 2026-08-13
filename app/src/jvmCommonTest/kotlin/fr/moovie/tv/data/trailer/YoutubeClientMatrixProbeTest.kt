package fr.moovie.tv.data.trailer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire. Lancer avec `-Dmoovie.probe=1`.
 *
 * **Quel client InnerTube rend des URL que googlevideo sert en entier ?**
 *
 * Mesuré par ailleurs ([GoogleVideoFetchProbeTest]) : sur le client iOS, une
 * bande-annonce se coupe à ~38 % du fichier, quelle que soit la façon de la
 * demander — requête unique, plages, cadence temps réel. Le mur est
 * **positionnel** et il tient sur une URL neuve, donc il vient du serveur et
 * non de nous. Rogner la lecture à cinquante secondes n'est pas une
 * correction : c'est vivre avec.
 *
 * Cette sonde balaie donc les clients, et pour chacun relève ce qui décide :
 *
 *  - le `playabilityStatus` : le client répond-il seulement ;
 *  - un **flux progressif** (`formats`, image et son ensemble) : ce sont les
 *    URL historiquement non bridées ;
 *  - un **manifeste HLS** : servi par un autre chemin, dont on sait qu'il
 *    délivre ses derniers segments (mesuré sur une vidéo ordinaire) ;
 *  - et surtout le verdict : une plage prise **à 80 % du fichier**, sur une URL
 *    fraîche jamais sollicitée. C'est là que le mur se voit.
 */
class YoutubeClientMatrixProbeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class Client(
        val id: Int,
        val nom: String,
        val version: String,
        val agent: String,
        val extra: JsonObject = buildJsonObject { },
        /** Certains clients n'obtiennent des flux qu'avec une page hôte. */
        val embedUrl: String? = null,
    )

    private val clients = listOf(
        Client(
            5, "IOS", "20.10.4",
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)",
            buildJsonObject {
                put("deviceMake", "Apple")
                put("deviceModel", "iPhone16,2")
                put("osName", "iPhone")
                put("osVersion", "18.3.2.22D82")
            },
        ),
        Client(
            3, "ANDROID", "20.10.38",
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14; SM-S928B) gzip",
            buildJsonObject {
                put("osName", "Android")
                put("osVersion", "14")
                put("androidSdkVersion", 34)
            },
        ),
        Client(
            2, "MWEB", "2.20250312.04.00",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
        ),
        Client(
            1, "WEB", "2.20250312.04.00",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.4 Safari/605.1.15",
        ),
        Client(
            56, "WEB_EMBEDDED_PLAYER", "1.20250310.01.00",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/134.0.0.0 Safari/537.36",
            embedUrl = "https://www.youtube.com/",
        ),
        Client(
            85, "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0",
            "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/16.0 Safari/605.1.15",
            buildJsonObject { put("clientScreen", "EMBED") },
            embedUrl = "https://www.youtube.com/",
        ),
        Client(
            7, "TVHTML5", "7.20250122.14.00",
            "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1032734-gold (unlike Gecko) " +
                "v8/8.8.278.8-jit gles Starboard/16, SystemIntegratorName_DESKTOP_2025/Firmware",
        ),
    )

    @Test
    fun sonde() {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde clients] ignorée (relancer avec -Dmoovie.probe=1)")
            return
        }
        val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
        val visiteur = identiteVisiteur(http)
        println("\n[sonde clients] visitorData ${if (visiteur != null) "obtenu" else "absent"}\n")

        for (video in listOf(VIDEO_BA, VIDEO_ORDINAIRE)) {
            println("  ── vidéo $video ──")
            for (client in clients) {
                val racine = reponseJoueur(http, client, video, visiteur)
                if (racine == null) {
                    println("    ${client.nom.padEnd(30)} pas de réponse")
                    continue
                }
                val statut = racine["playabilityStatus"]?.jsonObject
                    ?.get("status")?.jsonPrimitive?.content ?: "?"
                if (statut != "OK") {
                    println("    ${client.nom.padEnd(30)} $statut")
                    continue
                }
                val data = racine["streamingData"]?.jsonObject
                val progressifs = data?.get("formats")?.jsonArray?.size ?: 0
                val hls = data?.get("hlsManifestUrl")?.jsonPrimitive?.content
                val verdict = verdictMur(http, client, data)
                println(
                    "    ${client.nom.padEnd(30)} OK  progressif=$progressifs  " +
                        "hls=${if (hls != null) "oui" else "non"}  $verdict",
                )
            }
            println()
        }
    }

    /**
     * Le test qui tranche : une plage d'un mégaoctet à 80 % du fichier, sur une
     * URL jamais touchée. 206 = le client rend des URL servies en entier.
     */
    private fun verdictMur(http: OkHttpClient, client: Client, data: JsonObject?): String {
        val adaptatifs = data?.get("adaptiveFormats")?.jsonArray?.mapNotNull { it.jsonObject }
            .orEmpty()
            .filter { it["url"]?.jsonPrimitive?.content?.startsWith("https://") == true }
        val video = adaptatifs
            .filter { it["mimeType"]?.jsonPrimitive?.content?.contains("avc1") == true }
            .maxByOrNull { it["height"]?.jsonPrimitive?.int ?: 0 }
        val audio = adaptatifs
            .filter { it["mimeType"]?.jsonPrimitive?.content?.contains("mp4a") == true }
            .maxByOrNull { it["bitrate"]?.jsonPrimitive?.int ?: 0 }
        val progressif = data?.get("formats")?.jsonArray?.mapNotNull { it.jsonObject }
            ?.filter { it["url"]?.jsonPrimitive?.content?.startsWith("https://") == true }
            ?.maxByOrNull { it["height"]?.jsonPrimitive?.int ?: 0 }
        if (video == null && progressif == null) return "aucune URL en clair (signature chiffrée)"

        // Les trois formes, chacune sur sa propre URL fraîche : c'est le seul
        // moyen de savoir si le bridage vise le client ou le type de flux.
        return listOfNotNull(
            progressif?.let { "prog ${hauteur(it)} ${essaie(http, client, it)}" },
            video?.let { "vidéo ${hauteur(it)} ${essaie(http, client, it)}" },
            audio?.let { "audio ${essaie(http, client, it)}" },
        ).joinToString("  ")
    }

    private fun hauteur(format: JsonObject): String =
        format["height"]?.jsonPrimitive?.int?.let { "${it}p" } ?: "?"

    /** Une plage d'un mégaoctet à 80 % : 206 = servi en entier. */
    private fun essaie(http: OkHttpClient, client: Client, format: JsonObject): String {
        val url = format["url"]!!.jsonPrimitive.content
        val clen = format["contentLength"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: url.substringAfter("clen=", "").substringBefore('&').toLongOrNull()
            ?: return "(taille ?)"
        val debut = (clen * 0.8).toLong()
        val requete = Request.Builder().url(url)
            .header("User-Agent", client.agent)
            .header("Range", "bytes=$debut-${(debut + 1024 * 1024 - 1).coerceAtMost(clen - 2)}")
            .build()
        val code = runCatching { http.newCall(requete).execute().use { it.code } }.getOrNull() ?: -1
        return if (code == 206 || code == 200) "→$code✅" else "→$code"
    }

    /** `visitorData`, que plusieurs clients exigent pour rendre des flux. */
    private fun identiteVisiteur(http: OkHttpClient): String? {
        val client = clients.first { it.nom == "WEB" }
        val racine = reponseJoueur(http, client, VIDEO_ORDINAIRE, null) ?: return null
        return racine["responseContext"]?.jsonObject
            ?.get("visitorData")?.jsonPrimitive?.content
    }

    private fun reponseJoueur(
        http: OkHttpClient,
        client: Client,
        videoId: String,
        visiteur: String?,
    ): JsonObject? {
        val contexte = buildJsonObject {
            put(
                "client",
                buildJsonObject {
                    put("clientName", client.nom)
                    put("clientVersion", client.version)
                    put("hl", "fr")
                    put("gl", "FR")
                    visiteur?.let { put("visitorData", it) }
                    client.extra.forEach { (k, v) -> put(k, v) }
                },
            )
            client.embedUrl?.let { url ->
                put("thirdParty", buildJsonObject { put("embedUrl", url) })
            }
        }
        val corps = buildJsonObject {
            put("context", contexte)
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val requete = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player")
            .header("Content-Type", "application/json")
            .header("User-Agent", client.agent)
            .header("X-Youtube-Client-Name", client.id.toString())
            .header("X-Youtube-Client-Version", client.version)
            .header("Origin", "https://www.youtube.com")
            .apply { visiteur?.let { header("X-Goog-Visitor-Id", it) } }
            .post(corps.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val texte = runCatching {
            http.newCall(requete).execute().use { it.body?.string() }
        }.getOrNull() ?: return null
        return runCatching { json.parseToJsonElement(texte).jsonObject }.getOrNull()
    }

    private companion object {
        /** Une bande-annonce de studio : le cas d'usage. */
        const val VIDEO_BA = "d9MyW72ELq0"

        /** Une vidéo ordinaire : le témoin, pour distinguer client et contenu. */
        const val VIDEO_ORDINAIRE = "dQw4w9WgXcQ"
    }
}
