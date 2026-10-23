package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.SourceExtractor

/**
 * Résout un lien [PurStreamProvider] : retire le fragment `#audio=xx` (qui ne
 * servait qu'à distinguer les liens) et rend la langue au lecteur.
 *
 * Le CDN répond 403 à `okhttp/4.12` et 200 à un User-Agent de navigateur : il
 * est posé sur le flux, et le lecteur le reporte sur les segments.
 */
class PurStreamExtractor : SourceExtractor {

    override val hoster = PurStreamProvider.HOSTER

    override fun canHandle(url: String): Boolean = AUDIO.containsMatchIn(url)

    override suspend fun extract(link: EmbedLink): PlayableStream? {
        val lang = AUDIO.find(link.url)?.groupValues?.get(1) ?: return null
        return PlayableStream(
            url = link.url.substringBefore('#'),
            format = StreamFormat.HLS,
            headers = mapOf("User-Agent" to Ua.BROWSER),
            language = link.language,
            audioLanguage = lang,
        )
    }

    private companion object {
        val AUDIO = Regex("\\.m3u8[^#]*#audio=([a-z]{2})$")
    }
}
