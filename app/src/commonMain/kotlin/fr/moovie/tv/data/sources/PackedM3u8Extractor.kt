package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.SourceExtractor

/**
 * Renifleur générique : page d'embed empaquetée au packer « Dean Edwards » dont
 * le script dé-packé contient une URL HLS.
 *
 * Il ne revendique **aucun domaine** ([canHandle] renvoie toujours false) et
 * n'est essayé qu'en dernier recours, une fois les extracteurs spécifiques
 * épuisés.
 *
 * Raison d'être : une grande partie des hébergeurs FR partagent le même moule
 * — `/v/{id}` ou `/e/{id}`, un `jwplayer(...).setup({sources:[{file:"…m3u8"}]})`
 * empaqueté — et se contentent de changer de domaine. Constaté sur un seul
 * échantillon de 10 films : `minochinos.com`, `vidmoly.net` et `dingtezuni.com`,
 * ce dernier redirigeant vers `callistanise.com`. Les nommer un par un revient à
 * publier une release à chaque rotation ; reconnaître la *forme* de la page les
 * couvre tous, y compris ceux qui n'existent pas encore.
 *
 * Le prix est une requête HTTP sur des liens qui, sans lui, seraient perdus de
 * toute façon. Il renvoie null sans effet de bord quand la page n'a pas cette
 * forme, ce qui le rend sûr à enchaîner.
 */
class PackedM3u8Extractor(private val http: HttpGateway) : SourceExtractor {

    override val hoster = "packed"

    /** Renifleur pur : il ne préempte jamais un extracteur spécifique. */
    override fun canHandle(url: String): Boolean = false

    override suspend fun extract(link: EmbedLink): PlayableStream? {
        val origin = originOf(link.url, link.url)
        val resp = http.fetch(
            HttpRequest(
                url = link.url,
                headers = mapOf(
                    "User-Agent" to Ua.BROWSER,
                    "Referer" to "$origin/",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                ),
            ),
        ) ?: return null
        if (!resp.isSuccessful) return null

        val m3u8 = PackedJs.findM3u8(resp.body ?: return null, link.url) ?: return null

        // L'origine réellement servie peut différer de celle demandée : ces
        // hébergeurs redirigent d'un alias à l'autre (dingtezuni → callistanise)
        // et le CDN vérifie un Referer cohérent avec elle.
        val served = originOf(resp.url, origin)

        return PlayableStream(
            url = m3u8,
            format = StreamFormat.HLS,
            headers = mapOf("Referer" to "$served/", "User-Agent" to Ua.BROWSER),
            language = link.language,
        )
    }
}
