package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.port.getBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Provider wiflix — films et séries, indexés par **ID TMDB**, VF et VOSTFR.
 *
 * ## Pourquoi on ne scrape pas wiflix directement
 *
 * Le site (flemmix.men aujourd'hui) est derrière un bouclier anti-bot qui juge
 * sur l'**adresse IP**, pas sur les en-têtes : une recherche depuis une IP
 * résidentielle passe, la même requête depuis n'importe où ailleurs répond
 * dix-huit octets, « Bot shield active. », quels que soient l'User-Agent, les
 * cookies, l'ordre des en-têtes ou le `h_check` que le formulaire attend.
 * Movix s'en sort parce qu'il a un backend et un parc de proxys résidentiels ;
 * Moo-vie n'a ni l'un ni l'autre, et n'en aura pas — c'est le principe de
 * l'app. Porter `wiflix.js` tel quel aurait donc donné un provider qui rend
 * une liste vide sur tous les appareils, c'est-à-dire un provider mort qui
 * ressemble à un provider sain (voir le piège du catalogue muet dans CLAUDE.md).
 *
 * On passe donc par **wavewatch**, qui fait ce scraping côté serveur et le
 * publie en clair : `apiwiflix.php` rend la page lecteur de wiflix avec sa
 * liste de sources déjà résolue, sans authentification ni cookie. Deux sites de
 * la liste — ToFlix et WaveWatch — partagent ce même backend, donc l'endpoint
 * est celui que leurs deux interfaces appellent.
 *
 * ## Chaîne
 *
 *   `?id={tmdb}`                        ← films
 *   `?id={tmdb}/{saison}&episode={n}`   ← épisodes
 *       → `let allSources = [{ url, name, language }]`
 *
 * La saison passe dans le **chemin** de `id`, l'épisode en paramètre à part :
 * c'est la forme qu'emploie `selectEpisode()` dans la page, et `?id=x&season=n`
 * ne rend rien.
 *
 * ## Ce qu'il apporte
 *
 * Un catalogue de plus, et surtout des hébergeurs que les autres ne servent pas
 * (evoload, upstream, waaw) à côté de ceux que l'app résout déjà (uqload,
 * luluvdo, playmogo, minochinos). La couverture est partielle et l'assume :
 * wiflix refuse un titre dont il n'a pas la même date de sortie que TMDB, et
 * répond alors une phrase, pas du JSON. C'est un cas normal — liste vide, la
 * cascade continue.
 */
class WiflixProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "wiflix"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        val query = when (media) {
            is MediaRef.Movie -> "id=${media.tmdbId}"
            is MediaRef.Episode -> "id=${media.tmdbId}/${media.season}&episode=${media.episode}"
        }

        val body = http.getBody("$BASE/apiwiflix.php?$query", headers()) ?: return emptyList()
        // Titre absent du catalogue : la page rend une phrase et pas de tableau.
        // Rien à signaler, la cascade passe au provider suivant.
        val raw = ALL_SOURCES.find(body)?.groupValues?.get(1) ?: return emptyList()

        val entries = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull()
            ?: return emptyList()

        return entries.mapNotNull { element ->
            val o = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val url = o["url"]?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }
                ?: return@mapNotNull null

            EmbedLink(
                url = url,
                // `name` porte déjà le domaine (« uqload.is »), mais on le déduit
                // quand même de l'URL : c'est la règle d'EmbedLink, et elle vaut
                // ici aussi le jour où wiflix y mettra un libellé d'affichage.
                hoster = hosterOf(url),
                language = languageOf(o["language"]?.jsonPrimitive?.content),
            )
        }.distinctBy { it.url }
    }

    private fun headers() = mapOf(
        "User-Agent" to Ua.BROWSER,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "fr-FR,fr;q=0.9,en;q=0.8",
        "Referer" to "$EMBED_ORIGIN/",
    )

    companion object {
        /** Le backend de scraping, partagé par ToFlix et WaveWatch. */
        const val BASE = "https://apis.wavewatch.top"

        /** Ce que la page lecteur des deux sites présente comme origine. */
        const val EMBED_ORIGIN = "https://wwembed.wavewatch.top"

        /**
         * `let allSources = [ … ];` — le tableau est du JSON valide, y compris
         * ses `\/` échappés, que kotlinx désérialise sans aide.
         */
        private val ALL_SOURCES = Regex("""let allSources\s*=\s*(\[[\s\S]*?\])\s*;""")

        /**
         * wiflix n'étiquette que « VF » et « VOSTFR ». Tout autre libellé est
         * rendu null plutôt que rangé de force en VF : une langue devinée fait
         * démarrer une lecture dans la mauvaise, ce qui est pire que pas
         * d'étiquette du tout.
         */
        fun languageOf(raw: String?): String? = when (raw?.trim()?.uppercase()) {
            "VF", "FRENCH", "TRUEFRENCH" -> "VF"
            "VOSTFR" -> "VOSTFR"
            "VO" -> "VO"
            else -> null
        }
    }
}
