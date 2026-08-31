package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.shared.maintenantMs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile

/**
 * Provider frembed — **API JSON**, films et séries, indexés par ID TMDB.
 *
 * Le seul catalogue rencontré qui déclare lui-même la langue *et* l'hébergeur de
 * chaque lien ; tout le reste des sources demande de les deviner. Et une API ne
 * dérive pas comme du HTML — c'est ce qui a fini par tuer les scrapers portés
 * depuis Movix.
 *
 * Chaîne, trouvée en lisant le bundle JS du site (jamais en l'exécutant) :
 *
 *   `/api/films?id={tmdb}&idType=tmdb`                    ← films
 *   `/api/series?id={tmdb}&sa={S}&epi={E}&idType=tmdb`    ← épisodes
 *       → `links[] : { lang, host: { slug }, url: "/api/stream?…server=id:N" }`
 *   `/api/stream?…` → **302** vers l'URL d'embed de l'hébergeur
 *
 * ⚠️ Le paramètre est `idType` en camelCase. Avec `IDType`, l'API répond 400 en
 * réclamant précisément les champs qu'on vient de lui passer — piège coûteux.
 *
 * Mesuré : 6/6 épisodes couverts, servis par voe et uqload — des extracteurs que
 * l'app possède déjà. C'est ce qui donne enfin une redondance aux séries, qui ne
 * tenaient que sur fstream.
 */
class FrembedProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "frembed"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cachedBase: String? = null
    @Volatile private var cachedAt = 0L

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        val base = resolveBase()
        val path = when (media) {
            is MediaRef.Movie ->
                "/api/films?id=${media.tmdbId}&idType=tmdb"
            is MediaRef.Episode ->
                "/api/series?id=${media.tmdbId}&sa=${media.season}&epi=${media.episode}&idType=tmdb"
        }

        val body = http.getBody("$base$path", headers(base)) ?: return emptyList()
        val links = runCatching {
            json.parseToJsonElement(body).jsonObject["links"]?.jsonArray
        }.getOrNull() ?: return emptyList()

        return coroutineScope {
            links.map { element ->
                async {
                    val o = runCatching { element.jsonObject }.getOrNull() ?: return@async null
                    val relative = o["url"]?.jsonPrimitive?.contentOrNull() ?: return@async null
                    val slug = o["host"]?.jsonObject?.get("slug")?.jsonPrimitive?.contentOrNull()

                    val embed = followStream(base, relative) ?: return@async null
                    EmbedLink(
                        url = embed,
                        // Ici l'hébergeur vient des métadonnées de l'API, pas d'un
                        // libellé d'affichage : contrairement à cinestream, il est
                        // fiable — et bien plus lisible que le domaine du jour
                        // (« matthewhotelscience » pour un lien Voe).
                        hoster = slug ?: hosterOf(embed),
                        language = languageOf(o["lang"]?.jsonPrimitive?.contentOrNull()),
                        // L'API porte un palier de qualité, souvent null. Quand il
                        // est là, c'est ce qui départage deux liens du même
                        // hébergeur dans la liste.
                        variant = o["quality"]?.jsonPrimitive?.contentOrNull(),
                    )
                }
            }.mapNotNull { it.await() }.distinctBy { it.url }
        }
    }

    /**
     * `/api/stream` ne rend pas l'URL : il redirige dessus. On ne suit donc pas
     * la redirection, on lit `Location` — suivre ferait télécharger la page de
     * l'hébergeur pour rien, à chaque lien de chaque titre.
     */
    private suspend fun followStream(base: String, relative: String): String? {
        val resp = http.fetch(
            HttpRequest(
                url = base + relative,
                headers = headers(base),
                followRedirects = false,
            ),
        ) ?: return null
        return resp.header("Location")?.takeIf { it.startsWith("http") }
    }

    /**
     * Domaine courant, donné par le site lui-même : `/api/dns/domains` liste ses
     * miroirs. Les sources FR changent de domaine en permanence ; ici le
     * résolveur est fourni, inutile d'en reverser un.
     *
     * En cas d'échec on garde le dernier domaine valide, et à défaut le domaine
     * par défaut — une panne de cet appel ne doit pas emporter le provider.
     */
    private suspend fun resolveBase(): String {
        val known = cachedBase
        if (known != null && maintenantMs() - cachedAt < BASE_TTL_MS) return known

        val body = http.getBody("$DEFAULT_BASE/api/dns/domains", headers(DEFAULT_BASE))
        val domain = runCatching {
            json.parseToJsonElement(body.orEmpty()).jsonArray
                .mapNotNull { it.jsonObject }
                .firstOrNull { it["label"]?.jsonPrimitive?.contentOrNull() == "main" }
                ?.get("domain")?.jsonPrimitive?.contentOrNull()
        }.getOrNull()

        val resolved = domain?.let { "https://$it" } ?: known ?: DEFAULT_BASE
        cachedBase = resolved
        cachedAt = maintenantMs()
        return resolved
    }

    private fun headers(base: String) = mapOf(
        "User-Agent" to Ua.BROWSER,
        "Accept" to "application/json,text/plain,*/*",
        "Referer" to "$base/",
    )

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        content.takeIf { it.isNotBlank() && it != "null" }

    companion object {
        const val DEFAULT_BASE = "https://frembed.casa"

        /** Le domaine change rarement ; six heures suffisent et évitent un appel par titre. */
        private const val BASE_TTL_MS = 6L * 60 * 60 * 1000

        /**
         * L'API étiquette « vf », « vostfr », « vo ». On aligne sur les libellés
         * de la cascade, qui filtre dessus.
         */
        fun languageOf(raw: String?): String? = when (raw?.lowercase()) {
            "vf", "truefrench", "french" -> "VF"
            "vostfr" -> "VOSTFR"
            "vo" -> "VO"
            else -> null
        }
    }
}
