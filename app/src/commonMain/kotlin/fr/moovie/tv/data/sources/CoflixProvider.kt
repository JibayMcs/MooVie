package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.port.getBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Provider Coflix - de retour, sur `coflix.esq`.
 *
 * Il avait été retiré le 24/08/2026 parce qu'une fiche servie à un anonyme ne
 * contenait plus aucun lien. Mesuré de nouveau le 23/10/2026 : les liens sont
 * servis **sans compte ni Turnstile**.
 *
 * ## Chaîne
 *
 * 1. recherche WordPress `/?s=<titre>` : cartes `film/<slug>/` ou `serie/<slug>/` ;
 * 2. fiche (film) ou `episode/<slug>-<S>x<E>/` : `cfServers = [{"embed_url":…}]`,
 *    un lecteur (`lecteurvideo.com/embed.php?id=…`) ;
 * 3. page lecteur : un `showVideo('<base64>')` par hébergeur, l'URL est le base64.
 *
 * ## Garde-fous
 *
 * - **Pas de rapprochement flou** : la carte doit porter exactement le titre
 *   cherché (accents et casse près) et, quand la carte affiche une année, la
 *   même que celle de TMDB. « Matrix » ne devient jamais « Matrix Reloaded ».
 * - **La fiche film se confirme sur elle-même**, jamais sur la première année
 *   venue : les badges `md-card-badge year` d'une fiche appartiennent aux
 *   recommandations. Le lecteur porte `data-cfp` avec `"tmdb":603` ; présent,
 *   cet id doit être exactement celui demandé. Absent, une carte sans année
 *   n'est retenue que si la statistique « Año » de la fiche porte l'année TMDB.
 * - **La page épisode se confirme de même** : son `data-cfp` porte l'id TMDB
 *   de la série, la saison et l'épisode ; chacun, présent, doit concorder
 *   (une série homonyme a le même titre exact, pas le même id).
 * - **L'hébergeur vient du domaine** de l'embed, jamais du libellé du site.
 * - On ne garde que les hébergeurs que l'app sait résoudre : les autres
 *   (emmmmbed, waaw, veev…) seraient des liens morts dans le panneau.
 *
 * ## Langue : indice du site, NON vérifié à l'écoute
 *
 * Chaque serveur de la page lecteur est annoncé « French - … » et le site est
 * un catalogue français, mais la piste audio HLS ne porte aucune étiquette de
 * langue et aucune écoute n'a pu être faite. « VF » repose donc sur cette seule
 * mention par serveur (à vérifier à l'oreille). Un serveur qui n'est pas annoncé
 * « French » reste sans étiquette : on n'invente pas une langue.
 */
@OptIn(ExperimentalEncodingApi::class)
class CoflixProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "coflix"

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        val base = DomainBook.base(http, name, BASE)
        val movie = media is MediaRef.Movie
        val search = fetch("$base/?s=${encode(media.title)}", base) ?: return emptyList()
        val slug = findSlug(search, media.title, media.year, movie) ?: return emptyList()
        val pageUrl = when (media) {
            is MediaRef.Movie -> "$base/film/$slug/"
            is MediaRef.Episode -> "$base/episode/$slug-${media.season}x${media.episode}/"
        }
        val page = fetch(pageUrl, base) ?: return emptyList()
        val confirme = when (media) {
            is MediaRef.Movie -> ficheConfirme(page, media, cardYear = anneeAffichee(search, slug))
            is MediaRef.Episode -> episodeConfirme(page, media)
        }
        if (!confirme) return emptyList()
        val out = LinkedHashMap<String, EmbedLink>()
        for (player in playersOf(page)) {
            val html = fetch(player, base) ?: continue
            for (link in linksOf(html)) out.getOrPut(link.url) { link }
        }
        return out.values.toList()
    }

    private suspend fun fetch(url: String, referer: String): String? =
        http.getBody(url, mapOf("User-Agent" to Ua.BROWSER, "Referer" to "$referer/"))

    companion object {
        const val BASE = "https://coflix.esq"

        // `[\s\S]` plutôt que `.` sous RegexOption.DOT_MATCHES_ALL, qui n'existe
        // que sur la JVM (voir PackedJs) : ces motifs franchissent des lignes.
        private val CARD = Regex(
            "md-manga-card-cover\">([\\s\\S]*?)md-manga-card-name\">([\\s\\S]*?)</p>",
        )
        private val HREF = Regex("href=\"https?://[^/\"]+/(film|serie)/([^/\"]+)/?\"")
        private val YEAR = Regex("md-card-badge year\">\\s*(\\d{4})\\s*<")
        private val CFP = Regex("data-cfp=\"([^\"]*)\"")
        private val CFP_TMDB = Regex("\"tmdb\"\\s*:\\s*\"?(\\d+)")
        private val CFP_SEASON = Regex("\"season\"\\s*:\\s*\"?(\\d+)")
        private val CFP_EPISODE = Regex("\"episode\"\\s*:\\s*\"?(\\d+)")
        private val STAT_YEAR = Regex(
            "cf-stat-item\"\\s+title=\"(?:Año|A&ntilde;o|Année|Year)\">\\s*(?:<svg[\\s\\S]*?</svg>)?\\s*(\\d{4})\\s*</span>",
        )
        private val CF_SERVERS = Regex("cfServers\\s*=\\s*(\\[[\\s\\S]*?\\])\\s*;")
        private val ITEM = Regex(
            "showVideo\\(\\s*['\"]([^'\"]+)['\"][^)]*\\)[^>]*>([\\s\\S]*?)</li>",
        )
        private val PARA = Regex("<p>\\s*([^<]*?)\\s*</p>")

        /** Le slug de la carte dont le titre (et l'année, si affichée) est exactement le bon. */
        internal fun findSlug(html: String, title: String, year: String?, movie: Boolean): String? {
            val wanted = normalize(title)
            if (wanted.isEmpty()) return null
            val kind = if (movie) "film" else "serie"
            return CARD.findAll(html).firstNotNullOfOrNull { card ->
                val href = HREF.find(card.groupValues[1]) ?: return@firstNotNullOfOrNull null
                if (href.groupValues[1] != kind) return@firstNotNullOfOrNull null
                if (normalize(decodeEntities(card.groupValues[2])) != wanted) return@firstNotNullOfOrNull null
                val cardYear = YEAR.find(card.groupValues[1])?.groupValues?.get(1)
                // Une année affichée qui contredit TMDB = un homonyme (remake, autre époque).
                if (movie && year != null && cardYear != null && cardYear != year) return@firstNotNullOfOrNull null
                href.groupValues[2]
            }
        }

        /** Année affichée par la carte de ce slug, ou null si la carte n'en affiche aucune. */
        internal fun anneeAffichee(html: String, slug: String): String? =
            CARD.findAll(html)
                .firstOrNull { HREF.find(it.groupValues[1])?.groupValues?.get(2) == slug }
                ?.let { YEAR.find(it.groupValues[1])?.groupValues?.get(1) }

        /** Id TMDB annoncé par le lecteur de la fiche (`data-cfp`), ou null. */
        internal fun tmdbIdOf(page: String): Int? = cfpInt(page, CFP_TMDB)

        private fun cfpInt(page: String, field: Regex): Int? =
            CFP.find(page)?.groupValues?.get(1)
                ?.let { field.find(decodeEntities(it))?.groupValues?.get(1)?.toIntOrNull() }

        /** Année de l'en-tête de la fiche (statistique « Año »), ou null. */
        internal fun ficheYearOf(page: String): String? = STAT_YEAR.find(page)?.groupValues?.get(1)

        /**
         * Vrai si la fiche film est bien celle du titre TMDB demandé. L'id de
         * `data-cfp` tranche quand il est là ; sinon, une carte sans année doit
         * être confirmée par l'année de l'en-tête. Jamais par un badge : ceux
         * d'une fiche sont ceux des recommandations.
         */
        internal fun ficheConfirme(page: String, media: MediaRef, cardYear: String?): Boolean {
            tmdbIdOf(page)?.let { return it == media.tmdbId }
            if (cardYear != null || media.year == null) return true
            return ficheYearOf(page) == media.year
        }

        /**
         * Vrai si la page épisode est bien celle de la série et de l'épisode
         * demandés. Le titre exact ne suffit pas (série homonyme) : le lecteur
         * porte `data-cfp` avec `"tmdb":1396,"season":1,"episode":1`, et chaque
         * champ présent doit concorder. Absents, on s'en tient au titre.
         */
        internal fun episodeConfirme(page: String, media: MediaRef.Episode): Boolean {
            tmdbIdOf(page)?.let { if (it != media.tmdbId) return false }
            cfpInt(page, CFP_SEASON)?.let { if (it != media.season) return false }
            cfpInt(page, CFP_EPISODE)?.let { if (it != media.episode) return false }
            return true
        }

        /** URLs des lecteurs déclarés dans la fiche. */
        internal fun playersOf(page: String): List<String> {
            val raw = CF_SERVERS.find(page)?.groupValues?.get(1) ?: return emptyList()
            val arr = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyList()
            return arr.mapNotNull { ((it as? JsonObject)?.get("embed_url") as? JsonPrimitive)?.content }
                .filter { it.startsWith("https://") }
                .distinct()
        }

        /**
         * Liens de la page lecteur. Seul le bloc de visionnage compte : l'onglet
         * « Télécharger » est un autre ensemble de serveurs, qu'on ignore.
         */
        internal fun linksOf(html: String): List<EmbedLink> {
            val watch = html.substringBefore("OD_down")
            return ITEM.findAll(watch).mapNotNull { m ->
                val url = runCatching { Base64.Mime.decode(m.groupValues[1]).decodeToString() }
                    .getOrNull()?.trim()?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                if (!ExtractorRegistry.canResolve(url)) return@mapNotNull null
                val info = PARA.find(m.groupValues[2])?.groupValues?.get(1).orEmpty()
                val language = if (info.startsWith("French", ignoreCase = true)) "VF" else null
                EmbedLink(url = url, hoster = hosterOf(url), language = language)
            }.toList()
        }

        internal fun normalize(s: String): String {
            val sb = StringBuilder()
            for (c in s.lowercase()) {
                val folded = ACCENTS[c]
                when {
                    folded != null -> sb.append(folded)
                    c.isLetterOrDigit() -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private val ACCENTS: Map<Char, String> = buildMap {
            fun fold(to: String, from: String) = from.forEach { put(it, to) }
            fold("a", "àâäáãå"); fold("e", "éèêë"); fold("i", "îïíì"); fold("o", "ôöóòõ")
            fold("u", "ùûüú"); fold("c", "ç"); fold("n", "ñ"); fold("y", "ÿý")
            put('œ', "oe"); put('æ', "ae")
        }

        private fun decodeEntities(s: String): String = s
            .replace("&amp;", "&").replace("&#038;", "&").replace("&#039;", "'")
            .replace("&#8217;", "'").replace("&quot;", "\"").replace("&nbsp;", " ")

        private fun encode(s: String): String = buildString {
            for (b in s.encodeToByteArray()) {
                val c = b.toInt() and 0xFF
                when {
                    c < 128 && (c.toChar().isLetterOrDigit() || c.toChar() in "-_.~") -> append(c.toChar())
                    c == ' '.code -> append('+')
                    else -> append('%').append(c.toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}
