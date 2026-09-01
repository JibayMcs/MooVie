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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Provider SwiftFlow — **API JSON indexée par ID TMDB**, films et séries.
 *
 * ### Ce qu'il apporte que les autres n'ont pas
 *
 * Il ne rend pas un lien d'hébergeur mais **le fichier lui-même** : un MP4
 * progressif, servi depuis son propre CDN. Pas d'embed à ouvrir, pas de
 * JavaScript à désobfusquer, donc rien qui casse au prochain changement de
 * format — la classe d'ennuis qui a tué la plupart des scrapers portés depuis
 * Movix. C'est la source la plus robuste du catalogue, et c'est celle qui
 * demande le moins de code.
 *
 * Un MP4 progressif se comporte aussi mieux qu'un HLS sur une liaison correcte :
 * une seule connexion, pas de re-négociation par segment, et un déplacement dans
 * le film qui tombe juste au lieu d'atterrir sur la frontière de segment
 * précédente (voir le compensateur de `VlcjPlayerController`).
 *
 * ### La chaîne
 *
 * ```
 * ?route=movies/{tmdb}   → { success, data: { sources: [{ url, language, size… }] } }
 * ?route=series/{tmdb}   → { success, data: { seasons: [{ season:"S01", episodes:[…] }] } }
 * ```
 *
 * Les `url` rendues sont **non signées**. Elles répondent 302 vers une variante
 * horodatée (`?ff=…`) qu'il suffit de suivre : inutile de passer par la page du
 * lecteur et son couple `ENCRYPTED_PAYLOAD`/`ENCRYPTED_IV`, qui n'existe que
 * pour le lecteur web. Mesuré : 206 `video/mp4`, deux redirections.
 *
 * Le CDN exige en revanche un `Referer`, sans quoi Cloudflare rend 403 — c'est
 * tout l'objet de [SwiftFlowExtractor].
 *
 * ### La clé d'API
 *
 * Publique au sens propre : SwiftFlow l'embarque dans l'URL du lecteur servie à
 * tous les navigateurs. Elle appartient néanmoins au déploiement qui s'en sert,
 * et un jour où elle sera révoquée, elle le sera pour tout le monde d'un coup.
 * D'où le repli silencieux — une liste vide, jamais une erreur — et une constante
 * isolée, facile à remplacer.
 */
class SwiftFlowProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "swiftflow"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        val route = when (media) {
            is MediaRef.Movie -> "movies/${media.tmdbId}"
            // Une seule requête pour toute la série : l'API ne sait pas
            // découper par saison, et demander épisode par épisode ferait vingt
            // appels là où un seul rend le catalogue entier.
            is MediaRef.Episode -> "series/${media.tmdbId}"
        }

        val body = http.getBody(
            "$API_BASE/api/v1/index.php?route=$route&api_key=$API_KEY",
            mapOf("User-Agent" to Ua.BROWSER, "Accept" to "application/json"),
        ) ?: return emptyList()

        val data = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            if (root["success"]?.str() != "true") return emptyList()
            root["data"]?.jsonObject
        }.getOrNull() ?: return emptyList()

        val entries = when (media) {
            is MediaRef.Movie -> data["sources"]?.asArray().orEmpty()
            is MediaRef.Episode -> episodesOf(data, media.season, media.episode)
        }

        return entries.mapNotNull { element ->
            val o = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val url = o["url"]?.str()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            EmbedLink(
                url = url,
                hoster = HOSTER,
                language = languageOf(o["language"]?.str()),
                // La taille départage deux copies du même épisode bien mieux que
                // la qualité, qui vaut « Unknown » presque partout.
                variant = o["quality"]?.str()?.takeIf { it.isNotBlank() && it != "Unknown" }
                    ?: o["size"]?.str()?.takeIf { it.isNotBlank() },
            )
        }.distinctBy { it.url }
    }

    /**
     * L'épisode demandé, dans la saison demandée.
     *
     * Les saisons se déclarent « S01 », « S1 » ou « Saison 1 » selon l'entrée du
     * catalogue : on ne compare donc que les chiffres. Comparer les chaînes
     * faisait manquer des saisons entières sans que rien ne le signale.
     */
    private fun episodesOf(data: JsonObject, season: Int, episode: Int): List<JsonElement> {
        val seasons = data["seasons"]?.asArray().orEmpty()
        val match = seasons.firstOrNull { element ->
            val label = runCatching { element.jsonObject["season"]?.str() }.getOrNull()
            label?.filter { it.isDigit() }?.toIntOrNull() == season
        } ?: return emptyList()

        return runCatching { match.jsonObject["episodes"]?.asArray().orEmpty() }
            .getOrDefault(emptyList())
            .filter { element ->
                runCatching {
                    element.jsonObject["episode_number"]?.str()?.toIntOrNull() == episode
                }.getOrDefault(false)
            }
    }

    private fun JsonElement.asArray(): List<JsonElement> =
        runCatching { (this as JsonArray).jsonArray.toList() }.getOrDefault(emptyList())

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.content

    /**
     * L'API déclare déjà « VF » ou « VOSTFR ». On normalise quand même : c'est
     * cette valeur qui décide de la lecture directe et du tri, et une casse
     * inattendue la ferait passer pour une langue inconnue.
     */
    private fun languageOf(raw: String?): String? = when (raw?.trim()?.uppercase()) {
        "VF", "FRENCH", "FR" -> "VF"
        "VOSTFR", "VOST" -> "VOSTFR"
        "VO", "VOSTA", "ENGLISH", "EN" -> "VO"
        else -> raw?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val API_BASE = "https://blinkflux.lol"

        /**
         * Clé du lecteur SwiftFlow. Non secrète — elle circule en clair dans
         * l'URL de l'iframe servie à tous les navigateurs — mais partagée : si
         * elle tombe, la source disparaît proprement plutôt que d'échouer fort.
         */
        const val API_KEY =
            "ff_5ae9661d6220e612a00645cb2889d6da5231504cbb68cc32214030b1a783e8e3"

        const val HOSTER = "swiftflow"
    }
}
