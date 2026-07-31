package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Provider anime-sama (anime-sama.to) — port de API/Mainapi/routes animeSama.
 * Flux : fetch.php (recherche) → page catalogue (`panneauAnime("Nom","saisonX/lang")`)
 * → episodes.js par langue (`var epsN = ['https://…embed…']`, index = épisode).
 * Renvoie des liens d'embed (ansembed, vidmoly, sibnet…) à résoudre ensuite.
 */
class AnimeSamaProvider(private val http: OkHttpClient) : SourceProvider {

    override val name = "animesama"

    // anime-sama s'indexe par slug de catalogue, pas par TMDB : l'ID TMDB de
    // MediaRef ne lui sert à rien.
    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> = withContext(Dispatchers.IO) {
        when (media) {
            is MediaRef.Movie -> linksFor(media.title, seasonPrefix = "film", episode = 1)
            is MediaRef.Episode ->
                linksFor(media.title, seasonPrefix = "saison${media.season}", episode = media.episode)
        }
    }

    private fun linksFor(title: String, seasonPrefix: String, episode: Int): List<EmbedLink> {
        val catalogueUrl = search(title) ?: return emptyList()
        val page = get(catalogueUrl) ?: return emptyList()

        // (nom, chemin) ; on ne garde que les vraies entrées (chemin saisonX/lang ou film/lang).
        val panels = PANEL.findAll(page).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val wanted = panels.filter { it.second.startsWith("$seasonPrefix/") }

        val links = mutableListOf<EmbedLink>()
        for ((_, path) in wanted) {
            val lang = if (path.substringAfterLast('/').equals("vf", true)) "VF" else "VOSTFR"
            val js = get("$catalogueUrl$path/episodes.js") ?: continue
            for (arr in parseEpsArrays(js)) {
                arr.getOrNull(episode - 1)?.let { url ->
                    links += EmbedLink(url = url, hoster = hosterOf(url), language = lang)
                }
            }
        }
        return links.distinctBy { it.url }
    }

    private fun search(title: String): String? {
        val body = FormBody.Builder().add("query", title).build()
        val req = Request.Builder()
            .url("$BASE/template-php/defaut/fetch.php")
            .post(body)
            .header("User-Agent", Ua.BROWSER)
            .build()
        val html = runCatching {
            http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return null
        val href = CATALOGUE_HREF.find(html)?.groupValues?.get(1) ?: return null
        return "$BASE$href".let { if (it.endsWith("/")) it else "$it/" }
    }

    private fun parseEpsArrays(js: String): List<List<String>> =
        EPS.findAll(js).map { m ->
            URL.findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
        }.toList()

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", Ua.BROWSER).build()
        return runCatching {
            http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()
    }

    companion object {
        const val BASE = "https://anime-sama.to"
        private val CATALOGUE_HREF = Regex("""href="(/catalogue/[^"]+)"""")
        private val PANEL = Regex("""panneauAnime\("([^"]+)",\s*"([^"]+)"\)""")
        private val EPS = Regex("""var\s+eps\w+\s*=\s*\[([\s\S]*?)\]""")
        private val URL = Regex("""'([^']+)'""")
    }
}
