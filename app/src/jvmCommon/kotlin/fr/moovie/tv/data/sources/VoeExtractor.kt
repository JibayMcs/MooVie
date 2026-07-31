package fr.moovie.tv.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Extracteur VOE — l'hébergeur le plus présent sur les sites FR (≈16 des 109
 * embeds relevés sur 10 films chez cinestream).
 *
 * VOE renouvelle ses domaines en permanence et fait **rebondir le client sur
 * tout son pool d'alias** avant de servir la page : mesuré à **28 redirections**
 * de suite, de façon reproductible, avec un domaine cible différent à chaque
 * appel. Deux conséquences directes sur le code :
 *
 *  - `followRedirects` est **désactivé** ici et la chaîne est déroulée à la
 *    main. OkHttp plafonne à 20 redirections en dur (`MAX_FOLLOW_UPS`, non
 *    configurable) et lève `ProtocolException: Too many follow-up requests`
 *    bien avant l'arrivée. Les cookies n'y changent rien (testé).
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
class VoeExtractor(http: OkHttpClient) : SourceExtractor {

    // Client dédié : voir la note sur les 28 redirections ci-dessus. Il partage
    // le pool de connexions et le DNS (DoH) du client commun.
    private val http: OkHttpClient = http.newBuilder().followRedirects(false).build()

    override val hoster = "voe"

    private val hostPattern = Regex(
        """voe\.sx|voe-un-block|robertordercharacterbetter|jefferycontrolmodel|""" +
            """jessicayeahcatch|bryantenunder|jessicachoosemake|matthewhotelscience""",
        RegexOption.IGNORE_CASE,
    )

    override fun canHandle(url: String): Boolean = hostPattern.containsMatchIn(url)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        runCatching {
            var url = link.url.toHttpUrlOrNull() ?: return@runCatching null

            repeat(MAX_HOPS) {
                val (next, html) = hop(url) ?: return@runCatching null

                if (next != null) {
                    url = next
                    return@repeat
                }

                // Page terminale : soit elle porte la charge utile, soit ce
                // n'est pas du VOE et on se tait (contrat du reniflage).
                val source = VoePayload.findSource(html ?: return@runCatching null)
                    ?: return@runCatching null

                return@runCatching PlayableStream(
                    url = source,
                    // `source` est une master playlist déjà complète ; le repli
                    // `direct_access_url` de VoePayload est un mp4.
                    format = if (source.contains(".m3u8")) StreamFormat.HLS else StreamFormat.MP4,
                    headers = mapOf(
                        "Referer" to "${url.scheme}://${url.host}/",
                        "User-Agent" to Ua.BROWSER,
                    ),
                    language = link.language,
                )
            }
            null // plafond de sauts atteint sans page terminale
        }.getOrNull()
    }

    /**
     * Un saut. Retourne (destination, null) s'il faut continuer — redirection
     * HTTP ou saut JavaScript — ou (null, html) si la page est terminale.
     */
    private fun hop(url: HttpUrl): Pair<HttpUrl?, String?>? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", Ua.BROWSER)
            .header("Referer", "${url.scheme}://${url.host}/")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (resp.isRedirect) {
                    // Location peut être relative : on la résout contre l'URL courante.
                    val target = resp.header("Location")?.let { url.resolve(it) }
                    return@use if (target != null) target to null else null
                }
                if (!resp.isSuccessful) return@use null

                val body = resp.body?.string() ?: return@use null
                val js = JS_REDIRECT.find(body)?.groupValues?.get(1)?.toHttpUrlOrNull()
                if (js != null && js != url) js to null else null to body
            }
        }.getOrNull()
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
