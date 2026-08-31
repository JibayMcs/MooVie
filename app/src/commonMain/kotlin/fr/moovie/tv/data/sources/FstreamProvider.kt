package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.shared.dispatcherEs
import fr.moovie.tv.shared.enNfd
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import com.fleeksoft.ksoup.Ksoup

/**
 * Provider French-Stream — portage de API/Mainapi/routes/fstream.js.
 *
 * Flux : recherche par titre (search.php, HTML) → meilleur résultat → id de page
 * → liste de lecteurs (film_api.php / episodes_p.php, JSON) → liens d'embed.
 *
 * ⚠️ fstream est derrière Cloudflare. Le backend d'origine s'appuie sur un cookie
 * `fsschal` + des proxies + un fallback CycleTLS (empreinte JA3). En natif on tente
 * le cookie statique ci-dessous ; si Cloudflare bloque, la recherche renverra du
 * HTML de vérification et donc zéro résultat. À traiter côté appelant.
 */
class FstreamProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "fstream"

    private val json = Json { ignoreUnknownKeys = true }

    // fstream ne connaît que son moteur de recherche interne : l'ID TMDB de
    // MediaRef ne lui sert à rien, il travaille sur le titre.
    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> = withContext(dispatcherEs) {
        val season = (media as? MediaRef.Episode)?.season
        val page = searchBestMatch(media.title, media.year, season) ?: return@withContext emptyList()
        val pageId = extractPageId(page.link) ?: return@withContext emptyList()
        when (media) {
            is MediaRef.Movie -> filmPlayers(pageId, page.link)
            is MediaRef.Episode -> episodePlayers(pageId, page.link, media.episode)
        }
    }

    // --- Recherche ------------------------------------------------------------

    private data class SearchResult(val title: String, val link: String, val year: String?, val season: Int?)

    /**
     * Choisit le meilleur résultat pour un titre donné. On matche D'ABORD le titre
     * (le nom de base, année/saison retirées) : sans ça, chercher « Dune » et filtrer
     * uniquement par année 2021 renvoyait « Dune Dreams (2021) » — un autre film.
     * On départage ensuite par saison (séries) puis par année.
     */
    private suspend fun searchBestMatch(title: String, year: String?, season: Int?): SearchResult? {
        val results = search(title)
        if (results.isEmpty()) return null
        val target = normalizeTitle(title)
        // 1) titres dont le nom de base correspond exactement ; sinon repli sur tout.
        val titled = results.filter { baseTitle(it.title) == target }
        val pool = titled.ifEmpty { results }
        // 2) filtre saison (séries).
        val bySeason = if (season != null) pool.filter { it.season == null || it.season == season } else pool
        val pool2 = bySeason.ifEmpty { pool }
        // 3) préférence à l'année, sinon premier.
        return year?.let { y -> pool2.firstOrNull { it.year == y } } ?: pool2.first()
    }

    /** Nom de base d'un titre de résultat : retire « (2021) » et « - Saison N ». */
    private fun baseTitle(raw: String): String {
        val noYear = raw.substringBefore(" (")
        val noSeason = SEASON_SUFFIX.replace(noYear, "")
        return normalizeTitle(noSeason)
    }

    /** Normalise pour comparaison : sans accents, minuscules, alphanumérique seulement. */
    private fun normalizeTitle(s: String): String =
        enNfd(s)
            // Plages explicites plutôt que `\p{Mn}` : les catégories Unicode ne
            // sont pas garanties par le moteur d'expressions régulières de
            // Kotlin/Native, et une classe muette y aurait laissé passer les
            // accents sans rien signaler. Ces cinq blocs couvrent les marques
            // combinantes que produit la décomposition d'un titre latin.
            .filterNot { c ->
                c.code in 0x0300..0x036F || c.code in 0x1AB0..0x1AFF ||
                    c.code in 0x1DC0..0x1DFF || c.code in 0x20D0..0x20FF ||
                    c.code in 0xFE20..0xFE2F
            }
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    private suspend fun search(query: String): List<SearchResult> {
        val html = http.fetch(
            HttpRequest(
                url = "$BASE/engine/ajax/search.php",
                method = HttpMethod.POST,
                form = mapOf("query" to query, "page" to "1"),
                headers = baseHeaders() + ("X-Requested-With" to "XMLHttpRequest"),
            ),
        )?.takeIf { it.isSuccessful }?.body ?: return emptyList()

        val doc = Ksoup.parse(html)
        return doc.select("div.search-item").mapNotNull { el ->
            val t = el.selectFirst(".search-title")?.text()?.trim() ?: return@mapNotNull null
            val onclick = el.selectFirst("[onclick]")?.attr("onclick").orEmpty()
            val link = LOCATION_HREF.find(onclick)?.groupValues?.get(1)
                ?: el.selectFirst("a[href]")?.absUrl("href")
                ?: return@mapNotNull null
            val yr = YEAR_PAREN.find(t)?.groupValues?.get(1)
                ?: YEAR_URL.find(link)?.groupValues?.get(1)
            val se = SEASON.find(t)?.groupValues?.get(1)?.toIntOrNull()
            SearchResult(t, link, yr, se)
        }
    }

    // --- Lecteurs film --------------------------------------------------------

    private suspend fun filmPlayers(pageId: String, pageUrl: String): List<EmbedLink> {
        val payload = http.fetch(
            HttpRequest(
                url = "$BASE/engine/ajax/film_api.php?id=$pageId",
                headers = baseHeaders() + mapOf(
                    "Referer" to pageUrl,
                    "Accept" to "application/json, text/plain, */*",
                ),
            ),
        )?.takeIf { it.isSuccessful }?.body ?: return emptyList()

        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return emptyList()
        val players = root["players"]?.let { it as? JsonObject } ?: return emptyList()

        val links = mutableListOf<EmbedLink>()
        for ((hoster, versionsEl) in players) {
            val versions = versionsEl as? JsonObject ?: continue
            for ((version, urlEl) in versions) {
                val raw = (urlEl as? JsonPrimitive)?.content?.trim().orEmpty()
                if (raw.isBlank()) continue
                val url = usableUrl(raw) ?: continue
                links += EmbedLink(
                    url = url,
                    hoster = hoster,
                    language = mapLanguage(version),
                    // La clé de version distingue plusieurs copies d'un même
                    // hébergeur dans la même langue : sans elle, la liste affiche
                    // trois « Vidzy » indiscernables.
                    variant = variantLabel(version),
                )
            }
        }
        return links.distinctBy { it.url }
    }

    // --- Lecteurs épisode -----------------------------------------------------

    private suspend fun episodePlayers(pageId: String, pageUrl: String, episode: Int): List<EmbedLink> {
        val payload = http.fetch(
            HttpRequest(
                url = "$BASE/engine/ajax/episodes_p.php?id=$pageId",
                headers = baseHeaders() + mapOf(
                    "Referer" to pageUrl,
                    "Accept" to "application/json, text/plain, */*",
                ),
            ),
        )?.takeIf { it.isSuccessful }?.body ?: return emptyList()

        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return emptyList()
        val links = mutableListOf<EmbedLink>()
        // Structure : { vf: { "1": { premium: url, ... } }, vostfr: {...}, vo: {...} }
        for (lang in listOf("vf", "vostfr", "vo")) {
            val byEpisode = root[lang] as? JsonObject ?: continue
            val providers = byEpisode[episode.toString()] as? JsonObject ?: continue
            for ((hoster, urlEl) in providers) {
                val raw = (urlEl as? JsonPrimitive)?.content?.trim().orEmpty()
                if (raw.isBlank()) continue
                usableUrl(raw)?.let { url ->
                    links += EmbedLink(url = url, hoster = hoster, language = mapLanguage(lang))
                }
            }
        }
        return links.distinctBy { it.url }
    }

    // --- Helpers --------------------------------------------------------------

    private fun baseHeaders() = mapOf("User-Agent" to UA, "Cookie" to COOKIE)

    private fun extractPageId(url: String): String? = PAGE_ID.find(url)?.groupValues?.get(1)

    /**
     * Certaines entrées ne sont pas des URL mais un identifiant nu. On les
     * écarte plutôt que de les développer.
     *
     * L'ancienne version fabriquait `https://www.fembed.com/v/<id>` pour netu —
     * or **fembed a fermé en 2022** et l'hôte ne résout même plus. Chaque entrée
     * de ce type produisait donc un lien mort par construction : une ligne de
     * plus dans le panneau des sources, une résolution tentée pour rien, et un
     * utilisateur qui essaie une source condamnée d'avance. Mesuré en sondant
     * les liens « netu » : échec réseau, hôte injoignable.
     *
     * Tant qu'on ne sait pas vers quel hébergeur un identifiant nu pointe
     * réellement, ne rien proposer vaut mieux que proposer faux. Même règle
     * pour les hébergeurs qu'on a renoncé à lire, voir [UNSUPPORTED_HOST].
     */
    private fun usableUrl(raw: String): String? =
        raw.takeIf { it.startsWith("http") && !UNSUPPORTED_HOST.containsMatchIn(it) }

    private fun mapLanguage(version: String): String = when (version.lowercase()) {
        // "default" sur French-Stream = version française du site.
        "vf", "vff", "vfq", "premium", "default" -> "VF"
        // `vostfr_hd` est du VOSTFR — un palier de qualité, pas une langue. Le
        // ranger en VO gonflait la section VO de liens sous-titrés en français
        // et vidait la section VOSTFR de sa meilleure copie.
        "vostfr", "vostfr_hd" -> "VOSTFR"
        "vo", "voeng" -> "VO"
        else -> version.uppercase()
    }


    companion object {
        const val BASE = "https://french-stream.one"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val COOKIE = "fsschal=1; dle_skin=VFV25; dle_newpm=0"

        /**
         * Libellé lisible de la clé de version, ou null quand elle n'apporte rien
         * de plus que la section de langue déjà affichée.
         *
         * `vff` et `vfq` sont deux **doublages** distincts — France et Québec —
         * que les spectateurs francophones différencient parfaitement. Les écraser
         * tous les deux en « VF » privait la liste de son seul repère : c'est ce
         * qui produisait trois boutons « Vidzy » impossibles à départager.
         */
        fun variantLabel(version: String): String? = when (version.lowercase()) {
            "vff" -> "VF France"
            "vfq" -> "VF Québec"
            "premium" -> "Premium"
            "voeng" -> "VO anglais"
            "vostfr_hd" -> "VOSTFR HD"
            // "vf", "vostfr", "vo", "default" : redondants avec la langue.
            else -> null
        }

        /**
         * Hébergeurs qu'on renonce à lire — donc qu'on cesse de proposer.
         *
         * Distinction volontaire avec « Ne répond pas » : ce libellé signale une
         * source qui **pourrait** marcher et qu'on n'a pas réussi à joindre cette
         * fois-ci. Ces deux-là ne peuvent pas marcher, jamais, faute
         * d'extracteur — les afficher, c'est promettre une lecture qui n'aura
         * pas lieu. Même raison que l'abandon des identifiants nus plus haut.
         *
         * - **filemoon** ne sert plus de page lecteur mais une coquille SPA :
         *   il n'y a rien à décoder dans la réponse HTML, il faudrait exécuter
         *   son JavaScript. Trop cher pour un hébergeur.
         * - **mixdrop** (redirigé vers `miixdrop`) est extractible en théorie,
         *   mais mesuré sur 5 titres et 148 liens il n'apparaît **qu'une fois**,
         *   sur un titre offrant déjà 19 sources jouables dont du VF. Le gain ne
         *   paye pas la rétro-ingénierie d'une obfuscation maison.
         *
         * Écrire l'extracteur un jour = retirer l'entrée d'ici. Constaté servi
         * par ce seul catalogue ; si un autre s'y met, ce filtre remontera d'un
         * cran plutôt que d'être recopié.
         */
        private val UNSUPPORTED_HOST = Regex("""mi+xdrop|filemoon""", RegexOption.IGNORE_CASE)

        private val LOCATION_HREF = Regex("""location\.href=['"]([^'"]+)['"]""")
        private val YEAR_PAREN = Regex("""\((\d{4})\)""")
        private val YEAR_URL = Regex("""-(\d{4})\.html""")
        private val SEASON = Regex("""Saison\s+(\d+)""", RegexOption.IGNORE_CASE)
        private val SEASON_SUFFIX = Regex("""\s*-\s*Saison\s+\d+.*$""", RegexOption.IGNORE_CASE)
        private val PAGE_ID = Regex("""/(\d+)-[^/]+\.html""")
    }
}
