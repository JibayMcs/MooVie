package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.SourceExtractor

/**
 * Résout un lien [NakiosProvider] : le MP4 est déjà là, il manque le `Referer`.
 *
 * Mesuré le 23/10/2026 : l'URL du CDN redirige (302) vers `falzey.lol`, qui,
 * avec `Referer: https://nakios.rent/`, rend une URL signée répondant 206, et
 * sans lui renvoie vers t.me. `DirectStreamExtractor` revendiquerait ce `.mp4`
 * et partirait sans Referer : cet extracteur est donc placé avant lui.
 *
 * [canHandle] ne revendique **aucune URL** : le Referer `nakios.rent` ne doit être
 * posé que sur les liens du catalogue, jamais sur un lien d'un autre provider qui
 * tomberait sur le même CDN `zen-blk`. C'est la résolution par nom d'hébergeur
 * (`hoster = "nakios"`) qui choisit cet extracteur. [SwiftFlowExtractor] et
 * `DirectStreamExtractor` gardent leur comportement pour tout autre lien.
 */
class NakiosExtractor : SourceExtractor {

    override val hoster = NakiosProvider.HOSTER

    override fun canHandle(url: String): Boolean = false

    override suspend fun extract(link: EmbedLink): PlayableStream = PlayableStream(
        url = link.url,
        format = StreamFormat.MP4,
        // Le Referer ne voyage jamais en clair.
        headers = if (link.url.startsWith("https://")) {
            mapOf("Referer" to REFERER, "User-Agent" to Ua.BROWSER)
        } else {
            mapOf("User-Agent" to Ua.BROWSER)
        },
        language = link.language,
    )

    private companion object {
        const val REFERER = "https://nakios.rent/"
    }
}
