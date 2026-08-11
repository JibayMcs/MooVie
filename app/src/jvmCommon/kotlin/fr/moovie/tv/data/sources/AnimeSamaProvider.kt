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

        // Les panneaux servent à savoir **si la saison existe**, pas dans quelle
        // langue : la page n'en annonce généralement qu'une, le VOSTFR, alors
        // que la VF est là, en chemin frère, sans être listée nulle part.
        //
        // Mesuré sur anime-sama : `naruto/saison1/vf/episodes.js` rend 660 URL
        // quand la page ne montre que `saison1/vostfr`. Idem pour Frieren,
        // Fullmetal Alchemist, One Piece, Demon Slayer — tous doublés, tous
        // invisibles. Lire les panneaux revenait donc à ne jamais proposer de VF
        // sur le seul catalogue d'animés de l'application.
        if (panels.none { it.second.startsWith("$seasonPrefix/") }) return emptyList()

        val links = mutableListOf<EmbedLink>()
        for ((suffix, lang) in LANGS) {
            val js = get("$catalogueUrl$seasonPrefix/$suffix/episodes.js") ?: continue
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
        val href = cataloguePath(html) ?: return null
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

        /**
         * Le chemin de la première fiche d'une page de résultats, ou null.
         *
         * Isolée et visible pour être testable sans réseau : c'est la ligne qui
         * a rendu tout le catalogue animé silencieux, et elle ne se voyait
         * qu'en mesurant la couverture.
         */
        internal fun cataloguePath(html: String): String? =
            CATALOGUE_HREF.find(html)?.groupValues?.get(1)
        /**
         * Le lien de la fiche, dans les résultats de recherche.
         *
         * Le domaine est **facultatif** : anime-sama rendait des liens relatifs
         * (`/catalogue/…`) et rend désormais des liens absolus
         * (`https://anime-sama.to/catalogue/…`). N'accepter que la première
         * forme ne faisait pas échouer le provider — il rendait une liste vide,
         * ce qui se confond avec « ce titre n'est pas au catalogue ». Le seul
         * catalogue spécialisé de l'application ne fournissait donc plus **aucun
         * lien, sur aucun animé**, sans que rien ne le signale ; c'est une sonde
         * de couverture qui l'a montré, pas un test ni un rapport d'erreur.
         *
         * On ne capture que le chemin, pour que la reconstruction avec [BASE]
         * reste vraie quel que soit le domaine du jour.
         */
        private val CATALOGUE_HREF = Regex("""href="(?:https?://[^"/]+)?(/catalogue/[^"]+)"""")
        /**
         * Les chemins de langue à sonder, et l'étiquette qu'ils portent.
         *
         * La VF d'abord : c'est ce que l'application privilégie, et l'ordre
         * décide de ce qui est proposé en premier à qualité égale.
         *
         * `vf1` et `vf2` viennent du portage de Movix, qui connaît neuf
         * identifiants de langue. Ils rendent 404 sur tout ce qu'on a mesuré,
         * mais ils ne coûtent qu'un aller-retour sur un fichier de quelques
         * kilo-octets — et un doublage qui existe sous ce nom vaut mieux qu'une
         * requête épargnée. Les langues non francophones (`vosteng`, `vj`…) sont
         * volontairement laissées de côté : rien dans l'application ne sait quoi
         * en faire.
         */
        private val LANGS = listOf(
            "vf" to "VF",
            "vf1" to "VF",
            "vf2" to "VF",
            "vostfr" to "VOSTFR",
        )
        private val PANEL = Regex("""panneauAnime\("([^"]+)",\s*"([^"]+)"\)""")
        private val EPS = Regex("""var\s+eps\w+\s*=\s*\[([\s\S]*?)\]""")
        private val URL = Regex("""'([^']+)'""")
    }
}
