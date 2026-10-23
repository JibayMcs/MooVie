package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.port.getBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Provider Nakios - une API JSON indexée par **id TMDB**, qui rend des MP4 directs.
 *
 * ```
 * /api/sources/movie/{tmdbId}
 * /api/sources/tv/{tmdbId}/{saison}/{épisode}
 * ```
 *
 * Pas de recherche par titre, donc pas de risque d'homonyme. L'API rend 404 sans
 * `User-Agent` de navigateur (le Turnstile ne protège que le site), et depuis le
 * 23/10/2026 aussi sans `Origin` / `Referer` du site.
 *
 * ### La langue vient du champ `lang`, mais pas à n'importe quel prix
 *
 * Seuls VF, VOSTFR et VO sont admis : un lien dont l'étiquette est inconnue ou
 * absente est écarté, pas deviné. Une langue fausse empoisonnerait le filtre de
 * langue (voir le cas de `mouve.php` dans CLAUDE.md).
 *
 * La forme exacte de l'enveloppe n'ayant pu être recapturée, le JSON est
 * parcouru sans en dépendre : tout objet portant `lang` et une URL MP4 est un
 * lien. [NakiosExtractor] pose le `Referer` que le CDN exige.
 */
class NakiosProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "nakios"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        val base = DomainBook.base(http, name, API_BASE)
        val route = when (media) {
            is MediaRef.Movie -> "$base/api/sources/movie/${media.tmdbId}"
            is MediaRef.Episode -> "$base/api/sources/tv/${media.tmdbId}/${media.season}/${media.episode}"
        }
        val body = http.getBody(
            route,
            mapOf(
                "User-Agent" to Ua.BROWSER,
                "Accept" to "application/json",
                "Origin" to SITE_ORIGIN,
                "Referer" to "$SITE_ORIGIN/",
            ),
        ) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        return linksOf(root)
    }

    companion object {
        const val API_BASE = "https://api.nakios.rent"
        const val HOSTER = "nakios"
        const val SITE_ORIGIN = "https://nakios.rent"
        private val LANGUAGES = setOf("VF", "VOSTFR", "VO")

        /** Étiquette reconnue, ou null : on n'invente pas une langue. */
        internal fun languageOf(raw: String?): String? =
            raw?.trim()?.uppercase()?.takeIf { it in LANGUAGES }

        internal fun linksOf(root: JsonElement): List<EmbedLink> {
            val out = LinkedHashMap<String, EmbedLink>()
            fun text(v: JsonElement?): String? =
                (v as? JsonPrimitive)?.takeIf { it.isString }?.content

            fun visit(e: JsonElement) {
                when (e) {
                    is JsonObject -> {
                        val lang = languageOf(text(e["lang"]))
                        val url = e.values.firstNotNullOfOrNull { v ->
                            text(v)?.takeIf {
                                // https seul : pas de requête en clair (règle de DomainBook).
                                it.startsWith("https://") && it.substringBefore('?').endsWith(".mp4")
                            }
                        }
                        if (lang != null && url != null) {
                            out.getOrPut(url) { EmbedLink(url = url, hoster = HOSTER, language = lang) }
                        }
                        e.values.forEach(::visit)
                    }
                    is JsonArray -> e.forEach(::visit)
                    else -> Unit
                }
            }
            visit(root)
            return out.values.toList()
        }
    }
}
