package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.getBody
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Vérifie de bout en bout l'API interne de frembed, trouvée en lisant son bundle
 * JS plutôt qu'en exécutant du JavaScript :
 *
 *   /api/series?id={tmdb}&sa={saison}&epi={episode}&idType=tmdb
 *       → { links: [ { lang, host: { slug }, url: "/api/stream?…server=id:N" } ] }
 *   /api/stream?…  → 302 vers l'URL d'embed de l'hébergeur
 *
 * L'API déclare **la langue et l'hébergeur** — aucune devinette. La question
 * ici : nos extracteurs ouvrent-ils ces liens ? C'est ce qui décide si frembed
 * devient le second catalogue pour les séries.
 */
class FrembedApiProbeTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://frembed.casa"

    private val headers = mapOf(
        "User-Agent" to Ua.BROWSER,
        "Accept" to "application/json,text/plain,*/*",
        "Referer" to "$base/",
    )

    /** tmdbId, nom, saison, épisode. */
    private val episodes = listOf(
        Quad(1396, "Breaking Bad", 1, 1),
        Quad(1399, "Game of Thrones", 2, 3),
        Quad(66732, "Stranger Things", 1, 2),
        Quad(60625, "Rick et Morty", 1, 1),
        Quad(94997, "House of the Dragon", 1, 1),
        Quad(1416, "Grey's Anatomy", 1, 1),
    )

    private data class Quad(val tmdb: Int, val nom: String, val sa: Int, val epi: Int)

    @Test
    fun probeFrembed() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking
        val http = ExtractorRegistry.gateway

        var couverts = 0
        val parHote = mutableMapOf<String, Int>()

        for (e in episodes) {
            val body = http.getBody(
                "$base/api/series?id=${e.tmdb}&sa=${e.sa}&epi=${e.epi}&idType=tmdb", headers,
            )
            val links = runCatching {
                json.parseToJsonElement(body.orEmpty()).jsonObject["links"]?.jsonArray
            }.getOrNull()

            println("\n${e.nom} S${e.sa}E${e.epi} → ${links?.size ?: 0} liens")
            if (links.isNullOrEmpty()) continue

            var jouable: String? = null
            for (l in links) {
                val o = l.jsonObject
                val lang = o["lang"]?.jsonPrimitive?.content ?: continue
                if (!lang.equals("vf", ignoreCase = true)) continue
                val slug = o["host"]?.jsonObject?.get("slug")?.jsonPrimitive?.content ?: "?"
                val path = o["url"]?.jsonPrimitive?.content ?: continue

                // /api/stream répond par une 302 vers l'hébergeur : on ne suit pas,
                // on lit l'en-tête Location.
                val resp = http.fetch(
                    HttpRequest(url = "$base$path", headers = headers, followRedirects = false),
                )
                val embed = resp?.header("Location")
                if (embed == null) { println("   %-10s ⛔ pas de redirection".format(slug)); continue }

                val stream = ExtractorRegistry.resolve(EmbedLink(embed, hosterOf(embed), "VF"))
                val v = when {
                    stream == null -> "⛔ non résolu"
                    isStreamPlayable(stream) -> "✅ jouable"
                    else -> "⚠ injouable"
                }
                if (stream != null && v.startsWith("✅") && jouable == null) jouable = slug
                if (v.startsWith("✅")) parHote[slug] = (parHote[slug] ?: 0) + 1
                println("   %-10s %-14s %s".format(slug, v, embed.take(46)))
            }
            if (jouable != null) couverts++
        }

        println("\n→ couverture frembed : $couverts / ${episodes.size} épisodes")
        println("hébergeurs jouables : " + parHote.toList().sortedByDescending { it.second })
    }
}
