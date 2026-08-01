package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.core.sources.port.SourceExtractor

/**
 * Extracteur LuluStream (luluvdo.com, luluvid.com…).
 *
 * L'hébergeur le plus présent après Voe sur les sites FR : 12 embeds sur les 109
 * relevés en échantillonnant 10 films chez cinestream. La page d'embed contient
 * un `jwplayer(...).setup({sources:[{file:"…master.m3u8?t=…"}]})` empaqueté au
 * packer « Dean Edwards » — le même que fsvid/vidzy, donc [PackedJs] le traite
 * déjà sans rien ajouter.
 *
 * La m3u8 porte un jeton horodaté (`t`, `s`, `e`) : elle expire, d'où la
 * résolution au moment de lire et non au moment de lister.
 */
class LuluExtractor(private val http: HttpGateway) : SourceExtractor {

    override val hoster = "lulustream"

    // Familles de domaines observées. Volontairement large : LuluStream tourne
    // ses TLD (luluvdo.com, luluvid.com, lulustream.com…) sans changer de format.
    private val hostPattern = Regex("""luluvdo|luluvid|lulustream|lulu\.st""", RegexOption.IGNORE_CASE)

    override fun canHandle(url: String): Boolean = hostPattern.containsMatchIn(url)

    override suspend fun extract(link: EmbedLink): PlayableStream? {
        // Referer sur le domaine de l'embed lui-même : le CDN le vérifie, et le
        // domaine bouge — d'où originOf() plutôt qu'une constante.
        val origin = originOf(link.url, "https://luluvdo.com")

        val html = http.getBody(
            link.url,
            mapOf(
                "User-Agent" to Ua.BROWSER,
                "Referer" to "$origin/",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            ),
        ) ?: return null

        val m3u8 = PackedJs.findM3u8(html, link.url) ?: return null

        return PlayableStream(
            url = m3u8,
            format = StreamFormat.HLS,
            headers = mapOf("Referer" to "$origin/", "User-Agent" to Ua.BROWSER),
            language = link.language,
        )
    }
}
