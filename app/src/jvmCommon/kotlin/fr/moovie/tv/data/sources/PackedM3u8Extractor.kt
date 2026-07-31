package fr.moovie.tv.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Renifleur générique : page d'embed empaquetée au packer « Dean Edwards » dont
 * le script dé-packé contient une URL HLS.
 *
 * Il ne revendique **aucun domaine** ([canHandle] renvoie toujours false) et
 * n'est essayé qu'en dernier recours par [ExtractorRegistry], une fois les
 * extracteurs spécifiques épuisés.
 *
 * Raison d'être : une grande partie des hébergeurs FR partagent le même moule
 * — `/v/{id}` ou `/e/{id}`, un `jwplayer(...).setup({sources:[{file:"…m3u8"}]})`
 * empaqueté — et se contentent de changer de domaine. Constaté sur un seul
 * échantillon de 10 films : `minochinos.com` et `dingtezuni.com`, ce dernier
 * redirigeant vers `callistanise.com`. Les nommer un par un revient à publier
 * une release à chaque rotation ; reconnaître la *forme* de la page les couvre
 * tous, y compris ceux qui n'existent pas encore.
 *
 * Le prix est une requête HTTP sur des liens qui, sans lui, seraient perdus de
 * toute façon. Il renvoie null sans effet de bord quand la page n'a pas cette
 * forme, ce qui le rend sûr à enchaîner.
 */
class PackedM3u8Extractor(private val http: OkHttpClient) : SourceExtractor {

    override val hoster = "packed"

    /** Renifleur pur : il ne préempte jamais un extracteur spécifique. */
    override fun canHandle(url: String): Boolean = false

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        runCatching {
            val origin = originOf(link.url, "")
            val req = Request.Builder()
                .url(link.url)
                .header("User-Agent", Ua.BROWSER)
                .header("Referer", if (origin.isEmpty()) link.url else "$origin/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            // L'origine réellement servie peut différer de celle demandée : ces
            // hébergeurs redirigent d'un alias à l'autre (dingtezuni →
            // callistanise) et le CDN vérifie un Referer cohérent avec elle.
            // Variable locale, jamais un champ : les liens sont résolus en
            // parallèle, un état partagé serait écrasé d'une extraction à l'autre.
            var served = origin
            val html = http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                served = resp.request.url.let { "${it.scheme}://${it.host}" }
                resp.body?.string()
            } ?: return@runCatching null

            val m3u8 = PackedJs.findM3u8(html, link.url) ?: return@runCatching null

            PlayableStream(
                url = m3u8,
                format = StreamFormat.HLS,
                headers = mapOf("Referer" to "$served/", "User-Agent" to Ua.BROWSER),
                language = link.language,
            )
        }.getOrNull()
    }
}
