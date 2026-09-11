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
    @Volatile private var cachedMirrors: List<String> = emptyList()
    @Volatile private var cachedAt = 0L

    /** Durée de validité du domaine en cache : longue après un succès, courte après un échec. */
    @Volatile private var cachedTtl = 0L

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
     * Domaine courant de l'API, **validé** avant d'être retenu.
     *
     * `/api/dns/domains` liste les miroirs du site : le résolveur est fourni,
     * inutile d'en reverser un. Mais le lire naïvement a éteint ce catalogue
     * entier, et de deux façons à la fois — mesuré le 11/09/2026, `frembed 0`
     * sur tous les titres :
     *
     *  - **le domaine a déménagé** : `frembed.casa` répond 302 vers
     *    `frembed.surf`. Suivre la redirection ne suffit pas, parce que l'API
     *    refuse un `Referer` qui ne porte pas son propre domaine
     *    (`{"error":"Access denied - Invalid Referer"}`) et qu'un client qui
     *    suit tout seul conserve l'en-tête du domaine de départ. La chaîne est
     *    donc déroulée à la main, en **reposant le Referer à chaque saut** ;
     *  - **tous les miroirs ne sont pas l'API** : la liste annonce
     *    `fanstream.buzz` *et* `frembed.surf`, les deux étiquetés `main`. Le
     *    premier est la **vitrine** — `/api/films` y rend du HTML Next.js, pas
     *    du JSON. Prendre « le premier main » suffisait à tout perdre.
     *
     * D'où la règle : un domaine n'est retenu que si `/api/dns/domains` y rend
     * bien un tableau JSON. C'est l'appel le moins cher qui prouve qu'on parle
     * à l'API et non au site, et il rapporte au passage la liste des miroirs à
     * essayer au prochain déménagement.
     */
    private suspend fun resolveBase(): String {
        val known = cachedBase
        if (known != null && maintenantMs() - cachedAt < cachedTtl) return known

        // Dans l'ordre : le dernier domaine qui répondait, les domaines connus
        // en dur, puis les miroirs annoncés par l'API la dernière fois.
        val candidats = LinkedHashSet<String>().apply {
            known?.let { add(it) }
            addAll(BASES_CONNUES)
            addAll(cachedMirrors)
        }

        for (candidat in candidats) {
            val sonde = sonde(candidat) ?: continue
            cachedBase = sonde.base
            cachedMirrors = sonde.miroirs
            cachedAt = maintenantMs()
            cachedTtl = BASE_TTL_MS
            return sonde.base
        }

        // Aucun candidat n'a répondu. On garde le dernier domaine valide plutôt
        // que de rendre le catalogue muet — mais avec un TTL court : une panne
        // passagère ne doit pas figer le mauvais domaine pour six heures, et
        // réessayer à chaque titre relancerait une sonde par fiche ouverte.
        val repli = known ?: DEFAULT_BASE
        cachedBase = repli
        cachedAt = maintenantMs()
        cachedTtl = ECHEC_TTL_MS
        return repli
    }

    private data class Sonde(val base: String, val miroirs: List<String>)

    /**
     * Déroule les redirections depuis [depart] en reposant le `Referer` à
     * chaque saut, et ne rend un domaine que si `/api/dns/domains` y répond par
     * un tableau JSON — la vitrine, elle, rend du HTML.
     */
    private suspend fun sonde(depart: String): Sonde? {
        var base = depart
        repeat(MAX_SAUTS) {
            val resp = http.fetch(
                HttpRequest(
                    url = "$base/api/dns/domains",
                    headers = headers(base),
                    followRedirects = false,
                ),
            ) ?: return null

            if (resp.isRedirect) {
                val cible = resp.header("Location")?.takeIf { it.startsWith("http") } ?: return null
                val suivant = originOf(cible, cible)
                if (suivant == base) return null // redirection sur soi-même
                base = suivant
                return@repeat
            }
            if (!resp.isSuccessful) return null

            val miroirs = runCatching {
                json.parseToJsonElement(resp.body.orEmpty()).jsonArray
                    .mapNotNull { it.jsonObject["domain"]?.jsonPrimitive?.contentOrNull() }
                    .map { "https://$it" }
            }.getOrNull() ?: return null // pas un tableau JSON : ce n'est pas l'API

            return Sonde(base, miroirs)
        }
        return null // plafond de sauts atteint
    }

    private fun headers(base: String) = mapOf(
        "User-Agent" to Ua.BROWSER,
        "Accept" to "application/json,text/plain,*/*",
        "Referer" to "$base/",
    )

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        content.takeIf { it.isNotBlank() && it != "null" }

    companion object {
        /** Domaine servant l'API au 11/09/2026, vérifié par sonde. */
        const val DEFAULT_BASE = "https://frembed.surf"

        /**
         * Points d'entrée essayés dans l'ordre, avant les miroirs annoncés.
         *
         * `frembed.casa` est conservé **après** le domaine courant et non à sa
         * place : il ne sert plus l'API mais redirige encore vers celui qui la
         * sert, ce qui en fait un point de reprise utile le jour où `.surf`
         * déménagera à son tour — [sonde] suit la redirection et retient la
         * cible, sans mise à jour de l'app.
         */
        private val BASES_CONNUES = listOf(DEFAULT_BASE, "https://frembed.casa")

        /** Le domaine change rarement ; six heures suffisent et évitent un appel par titre. */
        private const val BASE_TTL_MS = 6L * 60 * 60 * 1000

        /** Après un échec de sonde, on réessaie vite — sans relancer un appel par fiche. */
        private const val ECHEC_TTL_MS = 60L * 1000

        /** Assez pour la chaîne `.casa → .surf` et une rotation de plus. */
        private const val MAX_SAUTS = 5

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
