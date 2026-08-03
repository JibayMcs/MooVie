package fr.moovie.tv.data.subtitles

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.subtitles.model.DownloadedSubtitle
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.model.SubtitleQuota
import fr.moovie.tv.core.subtitles.port.SubtitleCatalog
import fr.moovie.tv.shared.openSubtitlesApiKey

/**
 * Adaptateur OpenSubtitles du port [SubtitleCatalog].
 *
 * Il ne fait que traduire : le domaine ignore que le catalogue s'appelle
 * OpenSubtitles, qu'il compte ses quotas ou qu'il numérote ses fichiers en
 * `Long`. Le jour où ses conditions deviennent intenables, on écrit un autre
 * adaptateur et le reste de l'app ne bouge pas.
 *
 * Le dernier quota connu est mémorisé ici : sans compte connecté, l'API ne le
 * révèle qu'en réponse à un téléchargement, et le perdre obligerait à en
 * dépenser un autre pour le réafficher.
 */
class OpenSubtitlesCatalog(
    private val api: OpenSubtitlesApi = OpenSubtitlesApi(openSubtitlesApiKey),
) : SubtitleCatalog {

    override val name: String = "OpenSubtitles"

    override val available: Boolean get() = api.configured

    @Volatile
    private var lastKnownQuota: SubtitleQuota = SubtitleQuota.Unknown

    override suspend fun search(
        media: MediaRef,
        languages: List<String>,
    ): List<SubtitleCandidate> {
        if (!available || languages.isEmpty()) return emptyList()
        val episode = media as? MediaRef.Episode

        return api.search(
            tmdbId = media.tmdbId,
            languages = languages,
            season = episode?.season,
            episode = episode?.episode,
        ).map { response ->
            response.data.flatMap { it.toCandidates() }
        }.getOrDefault(emptyList())
    }

    /**
     * Le seul appel qui coûte. Deux étapes côté API : obtenir un lien, puis
     * récupérer le fichier — **c'est la première qui décompte le quota**, même
     * si la seconde échoue. D'où la mise à jour du quota dès la réponse.
     */
    override suspend fun download(candidate: SubtitleCandidate): DownloadedSubtitle? {
        if (!available) return null
        val fileId = candidate.fileId.toLongOrNull() ?: return null

        val response = api.requestDownload(fileId).getOrElse { failure ->
            if (failure.asOsFailure() is OsFailure.QuotaExhausted) {
                lastKnownQuota = lastKnownQuota.copy(remaining = 0)
            }
            return null
        }

        lastKnownQuota = SubtitleQuota(
            remaining = response.remaining,
            allowed = lastKnownQuota.allowed,
            resetAtUtc = response.resetTimeUtc ?: lastKnownQuota.resetAtUtc,
        )

        val content = api.fetchFile(response.link).getOrNull() ?: return null
        return DownloadedSubtitle(
            candidate = candidate,
            content = content,
            fileName = response.fileName,
            quota = lastKnownQuota,
        )
    }

    /**
     * Interroge `/infos/user` quand un compte est connecté — **le seul moyen de
     * connaître le quota sans en dépenser un**. Sans compte, on ne peut que
     * ressortir ce que le dernier téléchargement a bien voulu dire.
     */
    override suspend fun quota(): SubtitleQuota {
        if (!available) return SubtitleQuota.Unknown
        val info = api.userInfo().getOrNull()?.data ?: return lastKnownQuota
        return SubtitleQuota(
            remaining = info.remainingDownloads,
            allowed = info.allowedDownloads,
            resetAtUtc = lastKnownQuota.resetAtUtc,
        ).also { lastKnownQuota = it }
    }
}

/**
 * Un résultat porte plusieurs fichiers (sous-titres en plusieurs CD) : chacun
 * devient un candidat, puisque c'est le fichier qu'on télécharge, pas le
 * résultat. Ceux sans langue déclarée sont écartés — impossible de les classer,
 * et proposer un sous-titre de langue inconnue à cinq téléchargements par jour
 * n'a pas de sens.
 */
private fun OsSubtitle.toCandidates(): List<SubtitleCandidate> {
    val language = attributes.language?.takeIf { it.isNotBlank() } ?: return emptyList()
    return attributes.files
        .filter { it.fileId > 0 }
        .map { file ->
            SubtitleCandidate(
                fileId = file.fileId.toString(),
                language = language,
                release = attributes.release,
                fps = attributes.fps?.takeIf { it > 0 },
                downloads = attributes.downloadCount,
                fromTrusted = attributes.fromTrusted,
                hearingImpaired = attributes.hearingImpaired,
                foreignPartsOnly = attributes.foreignPartsOnly,
                aiTranslated = attributes.aiTranslated,
                machineTranslated = attributes.machineTranslated,
            )
        }
}
