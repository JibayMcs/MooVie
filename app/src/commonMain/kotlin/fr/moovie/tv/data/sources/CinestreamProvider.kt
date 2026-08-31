package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.port.getBody
import io.ktor.http.encodeURLParameter
import fr.moovie.tv.shared.dispatcherEs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Provider CineStream (cinestream.info) — port de API/Mainapi/routes/cinestream.js.
 *
 * Le premier provider indexé par **ID TMDB** : cinestream réutilise les ID de
 * TMDB, on confirme donc le bon film sur l'identifiant et non sur une
 * ressemblance de titre. La confusion « Dune » / « Dune Dreams » devient
 * impossible — la recherche ne sert plus qu'à proposer des candidats.
 *
 * Flux (tout en GET, HTML public, aucun échange de cookie) :
 *   1. `/search?q={titre}`      → slugs `/film/{slug}` candidats
 *   2. `/film/{slug}`           → charge RSC échappée : `tmdbid` (fait autorité)
 *                                 et `players:[{name}]` dans l'ordre
 *   3. `/player/{tmdbid}/{i}`   → `<iframe src>` de l'hébergeur, index = position
 *                                 dans le tableau players
 *
 * ⚠️ L'en-tête `Accept` navigateur est **obligatoire** à l'étape 3 : sans lui la
 * page revient sans son iframe (rendue côté client). C'est ce détail qui faisait
 * croire que le lien d'embed n'était pas récupérable sans exécuter du JS.
 *
 * Séries non couvertes : cinestream ne sert que des films (chez Movix les séries
 * passent par wiflix).
 */
class CinestreamProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "cinestream"

    /** cinestream est un catalogue de films uniquement : les séries rendent vide. */
    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        if (media !is MediaRef.Movie) return emptyList()
        return withContext(dispatcherEs) {
            val film = findFilm(media.tmdbId, media.title, media.year) ?: return@withContext emptyList()
            embeds(media.tmdbId, film.players)
        }
    }

    // --- Étapes 1 et 2 : candidats puis confirmation par ID TMDB --------------

    private data class Film(val players: List<String>)

    private suspend fun findFilm(tmdbId: Int, title: String, year: String?): Film? {
        // `encodeURLParameter(spaceToPlus = true)` reproduit `URLEncoder.encode`
        // en application/x-www-form-urlencoded, où l'espace devient `+` et non
        // `%20` — c'est la forme que ce moteur de recherche attend.
        val q = title.encodeURLParameter(spaceToPlus = true)
        val html = get("$BASE/search?q=$q") ?: return null

        val slugs = SLUG.findAll(html).map { it.groupValues[1] }.distinct().toList()
        if (slugs.isEmpty()) return null

        // Les slugs finissent par leur année (« dune-premiere-partie-2021 ») :
        // on remonte les candidats de la bonne année pour éviter des pages
        // inutiles sur les titres courants. Le verdict reste l'ID TMDB.
        val ranked = if (year == null) slugs else
            slugs.sortedByDescending { YEAR.find(it)?.groupValues?.get(1) == year }

        for (slug in ranked.take(MAX_FILM_PAGES)) {
            val page = get("$BASE/film/$slug") ?: continue
            val id = TMDB_ID.find(page)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (id != tmdbId) continue

            val players = PLAYER_NAME.findAll(
                PLAYERS.find(page)?.groupValues?.get(1) ?: continue,
            ).map { it.groupValues[1] }.toList()

            return if (players.isEmpty()) null else Film(players)
        }
        return null
    }

    // --- Étape 3 : un embed par index de player -------------------------------

    private suspend fun embeds(tmdbId: Int, players: List<String>): List<EmbedLink> = coroutineScope {
        players.mapIndexed { index, name ->
            async {
                val page = get("$BASE/player/$tmdbId/$index") ?: return@async null
                val url = IFRAME_SRC.find(page)?.groupValues?.get(1)
                    ?.replace("&amp;", "&")
                    ?.takeIf { it.startsWith("http") } ?: return@async null

                EmbedLink(
                    url = url,
                    // L'hébergeur se déduit de l'URL, JAMAIS du libellé affiché :
                    // « DdStream » est servi par playmogo, « Filelions » par
                    // minochinos, « netu » par waaw.to. Seul le domaine dit vrai.
                    hoster = hosterOf(url),
                    language = languageOf(name),
                )
            }
        }.mapNotNull { it.await() }.distinctBy { it.url }
    }

    private suspend fun get(url: String): String? = http.getBody(
        url,
        mapOf(
            "User-Agent" to Ua.BROWSER,
            // Sans cet Accept, /player/ renvoie la page sans son iframe.
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "fr-FR,fr;q=0.9,en;q=0.8",
            "Referer" to "$BASE/",
        ),
    )

    companion object {
        const val BASE = "https://cinestream.info"

        /** Nombre de fiches film ouvertes avant d'abandonner la confirmation TMDB. */
        private const val MAX_FILM_PAGES = 5

        private val SLUG = Regex("""href="/film/([^"]+)"""")
        private val YEAR = Regex("""-(\d{4})$""")
        // La charge RSC est incluse échappée dans le HTML : \"tmdbid\":438631
        private val TMDB_ID = Regex("""tmdbid\\?":(\d+)""")
        // Le `]` fermant est échappé : le moteur regex ICU d'Android rejette un
        // `]` isolé (PatternSyntaxException), là où le JVM desktop l'accepte.
        // Même piège que dans PackedJs — invisible en test desktop.
        private val PLAYERS = Regex("""players\\?":(\[.*?\])""")
        private val PLAYER_NAME = Regex("""name\\?":\\?"([^"\\]+)""")
        private val IFRAME_SRC = Regex("""<iframe[^>]+src="([^"]+)"""", RegexOption.IGNORE_CASE)

        /**
         * Un player dont le libellé commence par « vostfr » est VOSTFR, tout le
         * reste est VF. Règle de cinestream, reprise telle quelle de Movix.
         */
        fun languageOf(playerName: String): String =
            if (playerName.trim().startsWith("vostfr", ignoreCase = true)) "VOSTFR" else "VF"
    }
}
