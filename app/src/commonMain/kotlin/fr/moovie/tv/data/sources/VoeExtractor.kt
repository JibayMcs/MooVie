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

        /**
         * Les cookies posés le long de la marche, renvoyés à chaque saut.
         *
         * VOE pose un `sid` sur sa page de contrôle et l'attend au saut suivant.
         * Sans lui, chaque requête se présente comme un client neuf au milieu
         * d'une chaîne — exactement le motif qu'un filtre anti-abus punit :
         * mesuré le 23/09/2026, `429 Too many requests` puis rebond vers une
         * régie publicitaire, sur un alias par ailleurs vivant. Un navigateur ne
         * fait rien d'autre que ça.
         */
        val cookies = mutableMapOf<String, String>()

        repeat(MAX_HOPS) {
            val resp = hop(url, cookies) ?: return null
            resp.header("Set-Cookie")?.let { pose ->
                val paire = pose.substringBefore(';')
                val nom = paire.substringBefore('=').trim()
                if (nom.isNotBlank()) cookies[nom] = paire.substringAfter('=').trim()
            }

            val next = nextHop(resp)
            if (next != null && next != url) {
                url = next
                return@repeat
            }

            // Page terminale : soit elle porte la charge utile, soit ce n'est
            // pas du VOE et on se tait (contrat du reniflage).
            if (!resp.isSuccessful) return null
            val page = resp.body ?: return null

            fun flux(source: String) = PlayableStream(
                url = source,
                // `source` est une master playlist déjà complète ; les replis
                // `file` / `direct_access_url` de VoePayload sont des mp4.
                format = if (source.contains(".m3u8")) StreamFormat.HLS else StreamFormat.MP4,
                headers = mapOf("Referer" to originOf(resp.url, url) + "/", "User-Agent" to Ua.BROWSER),
                language = link.language,
            )

            // 1. Les marqueurs de l'ancien déploiement. Tentés d'abord : quand
            //    ils conviennent, la page suffit et on s'épargne une requête.
            VoePayload.findSource(page)?.let { return flux(it) }

            // 2. Sinon, la table telle que **cette page-ci** la publie. VOE la
            //    régénère à chaque déploiement, et c'est la seule raison pour
            //    laquelle l'extraction tombait : deux liens sur trois rendaient
            //    `null` au relevé du 23/09/2026, indistinguables d'une page
            //    étrangère.
            VoePayload.bundleUrl(page)?.let { src ->
                val js = bundle(resolve(resp.url, src), resp.url)
                val markers = js?.let { VoePayload.markersFrom(it) }
                if (markers != null) VoePayload.findSource(page, markers)?.let { return flux(it) }
            }

            // 3. Dernier recours, la page servie sans chiffrement. Réservé à ce
            //    qu'on sait être du VOE : ces motifs (`"hls":"…"`) sont trop
            //    communs pour qu'un renifleur s'autorise à revendiquer sur eux
            //    la page d'un hébergeur inconnu.
            if (VoePayload.hasPayload(page) || canHandle(url)) {
                VoePayload.plainSource(page)?.let { return flux(it) }
            }
            return null
        }
        return null // plafond de sauts atteint sans page terminale
    }

    private suspend fun hop(url: String, cookies: Map<String, String>): HttpResponse? = http.fetch(
        HttpRequest(
            url = url,
            headers = buildMap {
                put("User-Agent", Ua.BROWSER)
                put("Referer", originOf(url, url) + "/")
                put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                if (cookies.isNotEmpty()) {
                    put("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            },
            followRedirects = false,
        ),
    )

    /**
     * Le bundle JS de la page, pour y lire la table des marqueurs.
     *
     * Redirections suivies, contrairement aux sauts de la page elle-même : un
     * bundle servi depuis un CDN répond volontiers par un 301, et ici la chaîne
     * est courte et sans piège. `Referer` de la page qui le réclame.
     */
    private suspend fun bundle(url: String, pageUrl: String): String? = http.fetch(
        HttpRequest(
            url = url,
            headers = mapOf(
                "User-Agent" to Ua.BROWSER,
                "Referer" to pageUrl,
                "Accept" to "*/*",
            ),
        ),
    )?.takeIf { it.isSuccessful }?.body

    /** Destination du saut suivant : en-tête `Location`, ou redirection JS. */
    private fun nextHop(resp: HttpResponse): String? {
        if (resp.isRedirect) return resp.header("Location")?.let { resolve(resp.url, it) }
        val body = resp.body.orEmpty()
        return JS_REDIRECTS.firstNotNullOfOrNull { motif ->
            motif.find(body)?.groupValues?.get(1)
        }?.let { resolve(resp.url, it) }
    }

    /** Résout une cible éventuellement relative contre l'URL courante. */
    private fun resolve(base: String, target: String): String = when {
        target.startsWith("http") -> target
        target.startsWith("/") -> originOf(base, base) + target
        else -> base.substringBeforeLast('/') + "/" + target
    }

    /** `internal` et non `private` : `VoeTraceProbeTest` déroule la même marche. */
    internal companion object {
        /**
         * 28 redirections mesurées + le saut JS + une marge. Ce plafond n'est pas
         * une optimisation : c'est le garde-fou qui distingue « chaîne longue »
         * de « boucle infinie ».
         */
        const val MAX_HOPS = 40

        /**
         * Les formes que prend le dernier saut, **toutes** — c'est là que VOE
         * échappait.
         *
         * On ne reconnaissait que `window.location.href = '…'`. VOE sert
         * désormais, sur une partie de son pool d'alias, une page de 496 octets
         * dont tout le contenu est `window.location.replace('…?ch=1&js=<JWT>')` :
         * un contrôle qui appose un jeton avant de rendre la vraie page. Faute
         * de la reconnaître, la marche s'arrêtait là et l'extracteur rendait
         * `null` — indistinguable d'une page étrangère, donc invisible.
         *
         * Relevé du 23/09/2026 : quatre liens sur onze s'arrêtaient à cette
         * page. Reconnaître le saut ne les a pas ressuscités pour autant — ils
         * se sont révélés morts plus loin (429 puis régie publicitaire, 520,
         * 404). Ce qui est réparé ici n'est donc pas ce relevé-là, c'est la
         * capacité à franchir cette forme de saut : tant qu'elle manquait,
         * aucun alias la servant ne pouvait aboutir, vivant ou non.
         *
         * La cible n'est plus tenue d'être absolue : ces pages écrivent
         * volontiers un chemin nu, que [resolve] recolle à l'origine courante.
         * Exiger `https?://` revenait à rater le saut en silence.
         */
        val JS_REDIRECTS = listOf(
            Regex("""(?:window\.)?location(?:\.href)?\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""(?:window\.)?location\.(?:replace|assign)\s*\(\s*['"]([^'"]+)['"]"""),
        )
    }
}
