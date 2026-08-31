package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import fr.moovie.tv.core.sources.port.SourceExtractor

/**
 * Extracteur VOE — l'hébergeur le plus présent sur les sites FR (≈16 des 109
 * embeds relevés sur 10 films chez cinestream).
 *
 * VOE renouvelle ses domaines en permanence et fait **rebondir le client sur
 * tout son pool d'alias** avant de servir la page : mesuré à **28 redirections**
 * de suite, de façon reproductible, avec un domaine cible différent à chaque
 * appel. Deux conséquences directes sur le code :
 *
 *  - les redirections sont **déroulées à la main** (`followRedirects = false`).
 *    OkHttp plafonne à 20 en dur (`MAX_FOLLOW_UPS`, non configurable) et lève
 *    `ProtocolException: Too many follow-up requests` bien avant l'arrivée. Les
 *    cookies n'y changent rien (testé).
 *  - après les 302 vient un dernier saut **en JavaScript** : une page de 753 o
 *    dont tout le contenu utile est `window.location.href = '…'`. La boucle
 *    traite les deux formes de saut indifféremment.
 *
 * La page finale porte la charge utile encodée (voir [VoePayload]).
 *
 * Aucune liste de domaines ne peut suivre ce rythme. C'est pourquoi cet
 * extracteur est aussi enregistré comme **renifleur** dans [ExtractorRegistry] :
 * il est essayé sur les liens qu'aucun extracteur ne revendique et se reconnaît
 * lui-même à la présence de la charge utile, si bien qu'un alias inédit
 * fonctionne sans mise à jour de l'app. [canHandle] ne conserve les motifs
 * connus que comme voie rapide, pour s'épargner une requête.
 */
class VoeExtractor(private val http: HttpGateway) : SourceExtractor {

    override val hoster = "voe"

    /**
     * VOE tourne ses domaines de sortie environ une fois par mois, et les alias
     * ne contiennent pas « voe » — d'où une liste explicite, forcément en
     * retard sur la réalité.
     *
     * `voe\.` plutôt que `voe\.sx` : la famille se décline sur tous les TLD, et
     * ne reconnaître qu'un seul faisait perdre les autres en silence. Trois
     * lettres suivies d'un point sont assez spécifiques pour ne pas ramasser de
     * faux positifs — le reniflage attrape de toute façon ce que la liste rate,
     * puisque VOE se reconnaît aussi à sa charge utile.
     */
    private val hostPattern = Regex(
        """voe\.|voe-un-block|robertordercharacter|jefferycontrolmodel|""" +
            """jessicayeahcatch|bryantenunder|jessicachoosemake|matthewhotelscience|""" +
            // Relevés côté Movix, sur des redirections 302 observées.
            """ralphysuccessfull|claudiosepulchral|anthonysaline|auraleanline|""" +
            """letsupload|prepareddare|preferciseaccurate|conscientiousedu|""" +
            """effortlessexperim|timmaybealready""",
        RegexOption.IGNORE_CASE,
    )

    override fun canHandle(url: String): Boolean = hostPattern.containsMatchIn(url)

    override suspend fun extract(link: EmbedLink): PlayableStream? {
        var url = link.url

        repeat(MAX_HOPS) {
            val resp = hop(url) ?: return null

            val next = nextHop(resp)
            if (next != null && next != url) {
                url = next
                return@repeat
            }

            // Page terminale : soit elle porte la charge utile, soit ce n'est
            // pas du VOE et on se tait (contrat du reniflage).
            if (!resp.isSuccessful) return null
            val source = VoePayload.findSource(resp.body ?: return null) ?: return null

            return PlayableStream(
                url = source,
                // `source` est une master playlist déjà complète ; le repli
                // `direct_access_url` de VoePayload est un mp4.
                format = if (source.contains(".m3u8")) StreamFormat.HLS else StreamFormat.MP4,
                headers = mapOf("Referer" to originOf(resp.url, url) + "/", "User-Agent" to Ua.BROWSER),
                language = link.language,
            )
        }
        return null // plafond de sauts atteint sans page terminale
    }

    private suspend fun hop(url: String): HttpResponse? = http.fetch(
        HttpRequest(
            url = url,
            headers = mapOf(
                "User-Agent" to Ua.BROWSER,
                "Referer" to originOf(url, url) + "/",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            ),
            followRedirects = false,
        ),
    )

    /** Destination du saut suivant : en-tête `Location`, ou redirection JS. */
    private fun nextHop(resp: HttpResponse): String? {
        if (resp.isRedirect) return resp.header("Location")?.let { resolve(resp.url, it) }
        return JS_REDIRECT.find(resp.body.orEmpty())?.groupValues?.get(1)
    }

    /** Résout une cible éventuellement relative contre l'URL courante. */
    private fun resolve(base: String, target: String): String = when {
        target.startsWith("http") -> target
        target.startsWith("/") -> originOf(base, base) + target
        else -> base.substringBeforeLast('/') + "/" + target
    }

    private companion object {
        /**
         * 28 redirections mesurées + le saut JS + une marge. Ce plafond n'est pas
         * une optimisation : c'est le garde-fou qui distingue « chaîne longue »
         * de « boucle infinie ».
         */
        const val MAX_HOPS = 40
        val JS_REDIRECT = Regex("""window\.location\.href\s*=\s*['"](https?://[^'"]+)['"]""")
    }
}
