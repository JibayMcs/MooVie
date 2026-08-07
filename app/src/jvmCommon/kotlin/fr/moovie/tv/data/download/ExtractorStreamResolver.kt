package fr.moovie.tv.data.download

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.data.sources.ExtractorRegistry

/**
 * Rejoue le lien d'embed pour obtenir un flux frais.
 *
 * On ne refait **pas** la recherche de sources : elle interroge tous les
 * catalogues, prend plusieurs secondes et pourrait rendre un autre hébergeur —
 * donc d'autres segments, incompatibles avec ceux déjà sur le disque. Rejouer le
 * même lien rend le même flux avec un jeton neuf, ce qui est précisément ce qui
 * manquait.
 */
class ExtractorStreamResolver(
    private val repo: DownloadRepository,
) : StreamResolver {

    override suspend fun resolve(key: String, language: String): PlayableStream? {
        val download = repo.get(key) ?: return null
        if (download.sourceUrl.isBlank()) return null
        return ExtractorRegistry.resolve(
            EmbedLink(
                url = download.sourceUrl,
                hoster = download.hoster,
                language = download.language.takeIf { it.isNotBlank() },
            ),
        )
    }
}
