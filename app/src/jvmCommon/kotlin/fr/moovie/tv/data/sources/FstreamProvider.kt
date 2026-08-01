package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

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
class FstreamProvider(private val http: OkHttpClient) : SourceProvider {

    override val name = "fstream"

    private val json = Json { ignoreUnknownKeys = true }

    // fstream ne connaît que son moteur de recherche interne : l'ID TMDB de
    // MediaRef ne lui sert à rien, il travaille sur le titre.
    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> = withContext(Dispatchers.IO) {
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
    private fun searchBestMatch(title: String, year: String?, season: Int?): SearchResult? {
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
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    private fun search(query: String): List<SearchResult> {
        val body = FormBody.Builder().add("query", query).add("page", "1").build()
        val req = Request.Builder()
            .url("$BASE/engine/ajax/search.php")
            .post(body)
            .headers(baseHeaders())
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        val html = runCatching {
            http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return emptyList()

        val doc = Jsoup.parse(html)
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

    private fun filmPlayers(pageId: String, pageUrl: String): List<EmbedLink> {
        val req = Request.Builder()
            .url("$BASE/engine/ajax/film_api.php?id=$pageId")
            .headers(baseHeaders())
            .header("Referer", pageUrl)
            .header("Accept", "application/json, text/plain, */*")
            .build()
        val payload = runCatching {
            http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return emptyList()

        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return emptyList()
        val players = root["players"]?.let { it as? JsonObject } ?: return emptyList()

        val links = mutableListOf<EmbedLink>()
        for ((hoster, versionsEl) in players) {
            val versions = versionsEl as? JsonObject ?: continue
            for ((version, urlEl) in versions) {
                val raw = (urlEl as? JsonPrimitive)?.content?.trim().orEmpty()
                if (raw.isBlank()) continue
                links += EmbedLink(
                    url = expandUrl(hoster, raw),
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

    private fun episodePlayers(pageId: String, pageUrl: String, episode: Int): List<EmbedLink> {
        val req = Request.Builder()
            .url("$BASE/engine/ajax/episodes_p.php?id=$pageId")
            .headers(baseHeaders())
            .header("Referer", pageUrl)
            .header("Accept", "application/json, text/plain, */*")
            .build()
        val payload = runCatching {
            http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return emptyList()

        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return emptyList()
        val links = mutableListOf<EmbedLink>()
        // Structure : { vf: { "1": { premium: url, ... } }, vostfr: {...}, vo: {...} }
        for (lang in listOf("vf", "vostfr", "vo")) {
            val byEpisode = root[lang] as? JsonObject ?: continue
            val providers = byEpisode[episode.toString()] as? JsonObject ?: continue
            for ((hoster, urlEl) in providers) {
                val raw = (urlEl as? JsonPrimitive)?.content?.trim().orEmpty()
                if (raw.isBlank()) continue
                links += EmbedLink(url = expandUrl(hoster, raw), hoster = hoster, language = mapLanguage(lang))
            }
        }
        return links.distinctBy { it.url }
    }

    // --- Helpers --------------------------------------------------------------

    private fun baseHeaders() = okhttp3.Headers.Builder()
        .add("User-Agent", UA)
        .add("Cookie", COOKIE)
        .build()

    private fun extractPageId(url: String): String? = PAGE_ID.find(url)?.groupValues?.get(1)

    /** netu : "vid123" (sans protocole) → lien fembed complet. */
    private fun expandUrl(hoster: String, raw: String): String =
        if (hoster.equals("netu", true) && !raw.startsWith("http")) "https://www.fembed.com/v/$raw" else raw

    private fun mapLanguage(version: String): String = when (version.lowercase()) {
        // "default" sur French-Stream = version française du site.
        "vf", "vff", "vfq", "premium", "default" -> "VF"
        "vostfr" -> "VOSTFR"
        "vo", "voeng", "vostfr_hd" -> "VO"
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

        private val LOCATION_HREF = Regex("""location\.href=['"]([^'"]+)['"]""")
        private val YEAR_PAREN = Regex("""\((\d{4})\)""")
        private val YEAR_URL = Regex("""-(\d{4})\.html""")
        private val SEASON = Regex("""Saison\s+(\d+)""", RegexOption.IGNORE_CASE)
        private val SEASON_SUFFIX = Regex("""\s*-\s*Saison\s+\d+.*$""", RegexOption.IGNORE_CASE)
        private val PAGE_ID = Regex("""/(\d+)-[^/]+\.html""")
    }
}
