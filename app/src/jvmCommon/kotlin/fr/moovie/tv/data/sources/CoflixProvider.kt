package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.Normalizer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Provider Coflix (coflix.trade) — port de API/Mainapi/routes/coflix.js.
 * Flux : suggest.php (recherche) → page film → iframe → lecteur → liens d'embed
 * encodés en base64 dans les onclick `showVideo('…')`.
 *
 * ⚠️ L'étape « lecteur » passe par CycleTLS/proxy côté backend (anti-bot). En
 * natif on tente en direct ; si Cloudflare bloque, la liste sera vide.
 */
// Base64 multiplateforme (kotlin.io.encoding) : android.util.Base64 n'existe pas
// sur desktop et java.util.Base64 exigerait minSdk 26.
@OptIn(ExperimentalEncodingApi::class)
class CoflixProvider(private val http: OkHttpClient) : SourceProvider {

    override val name = "coflix"

    private val json = Json { ignoreUnknownKeys = true }

    // coflix s'indexe par slug de titre : l'ID TMDB de MediaRef ne lui sert à rien.
    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> = withContext(Dispatchers.IO) {
        when (media) {
            is MediaRef.Movie -> {
                val pageUrl = findPage(media.title, media.year, movie = true)
                    ?: return@withContext emptyList()
                playerLinks(pageUrl)
            }

            is MediaRef.Episode -> {
                // Coflix séries : page épisode construite depuis le slug (best-effort).
                val seriesUrl = findPage(media.title, media.year, movie = false)
                    ?: return@withContext emptyList()
                val slug = SLUG.find(seriesUrl)?.groupValues?.get(1) ?: return@withContext emptyList()
                playerLinks("$BASE/episode/$slug-${media.season}x${media.episode}/")
            }
        }
    }

    // --- Recherche ------------------------------------------------------------

    private fun findPage(title: String, year: String?, movie: Boolean): String? {
        val q = java.net.URLEncoder.encode(normalize(title), "UTF-8")
        val req = Request.Builder()
            .url("$BASE/suggest.php?query=$q")
            .headers(headers(BASE))
            .build()
        val body = runCatching {
            http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return null

        val arr: JsonArray = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return null
        val wanted = if (movie) setOf("movies") else setOf("series", "animes", "doramas")
        val matches = arr.mapNotNull { it as? JsonObject }.filter { (it["post_type"] as? JsonPrimitive)?.content in wanted }
        if (matches.isEmpty()) return null
        val byYear = year?.let { y -> matches.firstOrNull { (it["year"] as? JsonPrimitive)?.content == y } }
        return ((byYear ?: matches.first())["url"] as? JsonPrimitive)?.content
    }

    // --- Extraction des lecteurs ---------------------------------------------

    private fun playerLinks(pageUrl: String): List<EmbedLink> {
        val page = get(pageUrl, BASE) ?: return emptyList()
        val doc = Jsoup.parse(page)
        val iframeSrc = doc.selectFirst("article iframe")?.absUrl("src")?.ifBlank { null }
            ?: doc.selectFirst("iframe")?.absUrl("src") ?: return emptyList()

        val lecteur = get(iframeSrc, BASE) ?: return emptyList()
        val ldoc = Jsoup.parse(lecteur)
        val items = ldoc.select("li[onclick]")

        return items.mapNotNull { el ->
            val onclick = el.attr("onclick")
            val b64 = SHOW_VIDEO.find(onclick)?.groupValues?.get(1) ?: return@mapNotNull null
            val url = runCatching { Base64.Mime.decode(b64).decodeToString() }.getOrNull()
                ?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val info = el.selectFirst("p")?.text()?.lowercase().orEmpty()
            val lang = when {
                "vostfr" in info -> "VOSTFR"
                "french" in info -> "VF"
                "english" in info -> "VO"
                else -> "VF"
            }
            EmbedLink(url = url, hoster = hosterOf(url), language = lang)
        }.distinctBy { it.url }
    }

    private fun get(url: String, referer: String): String? {
        val req = Request.Builder().url(url).headers(headers(referer)).build()
        return runCatching {
            http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()
    }

    private fun headers(referer: String) = okhttp3.Headers.Builder()
        .add("User-Agent", Ua.BROWSER)
        .add("Referer", referer)
        .build()

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")

    companion object {
        const val BASE = "https://coflix.trade"
        private val SHOW_VIDEO = Regex("""showVideo\(['"]([^'"]+)['"]""")
        private val SLUG = Regex("""/(?:serie|animes)/([^/]+)""")
    }
}
