package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.SourceExtractor

/**
 * Laisse passer les liens **déjà jouables**.
 *
 * Certains catalogues (vidapi) rendent directement une playlist au lieu d'une
 * page d'hébergeur. Sans cet extracteur, ces liens traversaient toute la chaîne
 * pour rien : aucun extracteur ne revendique leur domaine, et les renifleurs
 * finissaient par télécharger la playlist elle-même pour tenter d'y lire une
 * page — un aller-retour gaspillé, puis un échec.
 *
 * Volontairement **sans réseau** : la jouabilité réelle est déjà sondée par la
 * cascade, qui écarte durablement un lien mort. La refaire ici doublerait les
 * requêtes.
 */
class DirectStreamExtractor : SourceExtractor {

    override val hoster = "direct"

    override fun canHandle(url: String): Boolean = formatOf(url) != null

    override suspend fun extract(link: EmbedLink): PlayableStream? {
        val format = formatOf(link.url) ?: return null
        return PlayableStream(
            url = link.url,
            format = format,
            headers = mapOf("User-Agent" to Ua.BROWSER),
            language = link.language,
            quality = link.variant,
        )
    }

    private companion object {

        /**
         * Reconnaissance sur l'**extension du chemin**, jamais sur l'URL entière :
         * une page d'hébergeur qui porte `?file=x.m3u8` en paramètre n'est pas un
         * flux, et la traiter comme tel donnerait un lecteur sur une page HTML.
         */
        fun formatOf(url: String): StreamFormat? {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                path.endsWith(".m3u8") -> StreamFormat.HLS
                path.endsWith(".mpd") -> StreamFormat.DASH
                path.endsWith(".mp4") -> StreamFormat.MP4
                else -> null
            }
        }
    }
}
