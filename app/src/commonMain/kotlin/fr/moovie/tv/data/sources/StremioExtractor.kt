package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.SourceExtractor

/**
 * Laisse passer les liens de [StremioProvider], qui sont **déjà des fichiers**.
 *
 * Un extracteur sans extraction, donc, et c'est tout l'intérêt : là où les sept
 * autres catalogues rendent une page d'hébergeur qu'il faut ouvrir, désobfusquer
 * et dont il faut deviner le `Referer`, un debrid rend l'URL du média. Il n'y a
 * rien à en tirer de plus.
 *
 * ## Pourquoi il ne revendique aucun domaine
 *
 * [canHandle] rend toujours false. La résolution l'atteint par son **nom** —
 * première règle de `StreamResolution`, qui route sur `EmbedLink.hoster` — et
 * c'est la seule voie sûre ici : l'URL d'un flux Stremio dépend de l'addon et du
 * debrid de chaque utilisateur, et prétendre reconnaître cette famille à son
 * domaine reviendrait à tenir une liste qui sera fausse chez le prochain.
 *
 * L'effet de bord évité est concret : `DirectStreamExtractor` revendique tout
 * `.mp4`, donc sans le routage par nom un fichier `.mp4` servi par un debrid lui
 * reviendrait, avec un `Referer` qui n'a pas lieu d'être.
 *
 * ## Le format
 *
 * `UNKNOWN` pour tout ce qui n'est pas reconnu à son extension, et en
 * particulier pour le `.mkv` qui domine ce catalogue. Ce n'est pas un aveu
 * d'ignorance mais le seul choix juste : l'énumération n'a pas d'entrée
 * Matroska, et le ranger sous `MP4` le ferait annoncer en `video/mp4` au
 * récepteur Chromecast — un type faux, refusé à l'arrivée. `UNKNOWN` laisse
 * chaque lecteur reconnaître le conteneur, ce que Media3 fait sur le contenu.
 */
class StremioExtractor : SourceExtractor {

    override val hoster = HOSTER

    override fun canHandle(url: String) = false

    override suspend fun extract(link: EmbedLink): PlayableStream? {
        if (link.url.isBlank()) return null
        return PlayableStream(
            url = link.url,
            format = formatOf(link.url),
            // Pas de `Referer` ni d'`Origin` : ces CDN n'en demandent pas, et en
            // inventer un est le genre de détail qui fait répondre 403 sans
            // qu'on sache pourquoi. Seul l'agent est envoyé, comme partout.
            headers = mapOf("User-Agent" to Ua.BROWSER),
            language = link.language,
            quality = link.variant,
        )
    }

    companion object {

        /** Doit rester égal au `hoster` émis par [StremioProvider] : c'est la jointure. */
        const val HOSTER = "stremio"

        private fun formatOf(url: String): StreamFormat {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                path.endsWith(".m3u8") -> StreamFormat.HLS
                path.endsWith(".mpd") -> StreamFormat.DASH
                path.endsWith(".mp4") || path.endsWith(".m4v") -> StreamFormat.MP4
                else -> StreamFormat.UNKNOWN
            }
        }
    }
}
