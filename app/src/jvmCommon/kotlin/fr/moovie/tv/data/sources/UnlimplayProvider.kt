package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.port.getBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Provider unlimplay (unlimplay.com) — catalogue hispanophone (LAT/CAST/VOSE),
 * en remplacement d'embed69 : voir `ExtractorRegistry` pour pourquoi ce
 * dernier a été retiré (444 systématique, y compris hors datacenter).
 *
 * ## Une seule requête, une API publique et documentée
 *
 * unlimplay se présente lui-même comme une API (page d'accueil "UnlimPlay API
 * — Streaming Infrastructure", documentation publique sur `/#endpoints`) et
 * pas seulement comme le backend d'un site en particulier. Elle accepte
 * directement l'ID TMDB (numérique) **ou** IMDB (`tt…`) — aucun scraping d'un
 * site tiers n'est nécessaire :
 *
 *   `unlimplay.com/f/embed/movie/{tmdb}`          ← films
 *   `unlimplay.com/f/embed/tv/{tmdb}/{s}/{e}`      ← épisodes (vérifié en direct)
 *
 * Un site comme cinehax.com n'est qu'**un client parmi d'autres** de cette
 * API (c'est de là que la chaîne a été découverte : ses boutons "Fuentes de
 * vídeo" pointent tous vers unlimplay). Interroger directement l'API retire
 * cette dépendance intermédiaire : que cinehax reste en ligne ou non n'a plus
 * d'incidence sur ce provider.
 *
 * La page rend `const EMBEDS = { "latino": { "voe": "https://voe.sx/e/…", … },
 * "subtitulado": { … } }` en HTML statique — le JS de la page ne fait que
 * *lire* cet objet, il n'y a rien à désobfusquer.
 *
 * ## Ce que ça apporte tout de suite, sans extracteur à écrire
 *
 * `EMBEDS` sert plusieurs hébergeurs par langue ; `voe` et `doodstream` sont
 * déjà résolus par l'app (VoeExtractor, DoodStreamExtractor), et les liens
 * `direct`/`direct N` sont des `.m3u8` bruts que DirectStreamExtractor
 * revendique sans code supplémentaire. Les autres (streamwish, vidhide,
 * filemoon, filelions, streamtape, remux) n'ont pas d'extracteur pour
 * l'instant : ils s'affichent comme n'importe quelle source sans extracteur
 * connu (voir `MissingExtractorProbeTest`), sans empêcher les sources
 * jouables de fonctionner.
 */
class UnlimplayProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "unlimplay"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        val embedUrl = when (media) {
            is MediaRef.Movie -> "$BASE/f/embed/movie/${media.tmdbId}"
            is MediaRef.Episode -> "$BASE/f/embed/tv/${media.tmdbId}/${media.season}/${media.episode}"
        }

        val body = http.getBody(embedUrl, headers()) ?: return emptyList()
        val embedsJson = extractEmbedsJson(body) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(embedsJson).jsonObject }.getOrNull()
            ?: return emptyList()

        val links = mutableListOf<EmbedLink>()
        for ((langKey, value) in root) {
            val language = languageOf(langKey) ?: continue
            val servers = value as? JsonObject ?: continue
            for (urlEl in servers.values) {
                val url = (urlEl as? JsonPrimitive)?.content?.takeIf { it.startsWith("http") }
                    ?: continue
                links += EmbedLink(url = url, hoster = hosterOf(url), language = language)
            }
        }
        return links.distinctBy { it.url }
    }

    private fun headers() = mapOf(
        "User-Agent" to Ua.BROWSER,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "es-ES,es;q=0.9,en;q=0.8",
        "Referer" to "$BASE/",
    )

    companion object {
        const val BASE = "https://unlimplay.com"

        private val EMBEDS_MARKER = Regex("""const\s+EMBEDS\s*=""")

        /**
         * Coupe l'objet JSON assigné à `EMBEDS` par comptage d'accolades — un
         * regex non gourmand casserait sur la première `}` d'une URL signée
         * (les liens `direct` en contiennent dans leur requête). Le comptage
         * ignore les accolades **à l'intérieur d'une chaîne JSON** : sans ça,
         * une seule `}` littérale dans une valeur (un paramètre de requête non
         * échappé, par exemple) tronquait l'objet avant sa vraie fin.
         */
        internal fun extractEmbedsJson(body: String): String? {
            val markerEnd = EMBEDS_MARKER.find(body)?.range?.last ?: return null
            val start = body.indexOf('{', markerEnd)
            if (start < 0) return null
            var depth = 0
            var inString = false
            var escaped = false
            for (i in start until body.length) {
                val c = body[i]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return body.substring(start, i + 1)
                    }
                }
            }
            return null
        }

        /**
         * unlimplay étiquette en espagnol (latino/castellano/subtitulado) ;
         * `StreamLanguage` attend LAT/CAST/VOSE. Tout le reste est rendu null
         * plutôt que deviné — même règle que partout ailleurs dans les
         * providers.
         */
        internal fun languageOf(raw: String): String? = when (raw.trim().lowercase()) {
            "latino" -> "LAT"
            "castellano", "español", "espanol" -> "CAST"
            "subtitulado" -> "VOSE"
            else -> null
        }
    }
}
