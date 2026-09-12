package fr.moovie.tv.data.subtitles

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.subtitles.model.DownloadedSubtitle
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.model.SubtitleQuota
import fr.moovie.tv.core.subtitles.port.SubtitleCatalog
import kotlin.concurrent.Volatile

/**
 * Plusieurs catalogues essayés dans l'ordre, **le premier qui rend quelque chose
 * gagne**.
 *
 * ## Pourquoi une cascade et pas une fusion
 *
 * Fusionner les listes paraît plus généreux, mais les deux catalogues n'ont pas
 * les mêmes règles : OpenSubtitles plafonne les téléchargements à cinq par jour
 * sans compte, l'addon Stremio n'a aucun quota. Une liste mélangée afficherait
 * donc un quota vrai pour la moitié des entrées et faux pour l'autre, sans que
 * rien ne les distingue à l'écran. On préfère un catalogue à la fois, dont les
 * règles sont celles qu'on annonce.
 *
 * ## Pourquoi la retombée est sur *vide* et pas sur *indisponible*
 *
 * Une clé présente ne garantit pas une réponse : OpenSubtitles peut ne rien
 * avoir pour un titre, avoir épuisé un quota, ou simplement ne pas répondre.
 * Retomber uniquement quand le catalogue se déclare indisponible laisserait donc
 * l'utilisateur devant une liste vide alors qu'un autre fonds a le sous-titre.
 * C'est exactement le cas qui a donné « aucun sous-titre trouvé » sur *Inception*.
 *
 * ## Le suivi des candidats
 *
 * `fileId` est opaque et sa forme appartient au catalogue : un identifiant
 * numérique chez OpenSubtitles, une URL chez l'addon. Impossible de deviner qui
 * sait télécharger quoi, donc on le **note** au moment de la recherche. Sans ça,
 * un candidat de l'addon partirait vers OpenSubtitles, qui le refuserait sans
 * qu'on puisse dire pourquoi.
 */
class ChainedSubtitleCatalog(
    private val catalogs: List<SubtitleCatalog>,
) : SubtitleCatalog {

    override val name: String get() = actif?.name ?: catalogs.firstOrNull { it.available }?.name ?: ""

    override val available: Boolean get() = catalogs.any { it.available }

    /** Celui qui a rendu la liste courante, et donc le seul à savoir la servir. */
    @Volatile
    private var actif: SubtitleCatalog? = null

    private val proprietaire = mutableMapOf<String, SubtitleCatalog>()

    override suspend fun search(
        media: MediaRef,
        languages: List<String>,
    ): List<SubtitleCandidate> {
        for (catalog in catalogs) {
            if (!catalog.available) continue
            // Un catalogue qui lève ne doit pas priver l'utilisateur du suivant :
            // le port promet de ne pas lever, on ne parie pas dessus.
            val trouves = runCatching { catalog.search(media, languages) }.getOrDefault(emptyList())
            if (trouves.isEmpty()) continue
            actif = catalog
            proprietaire.clear()
            trouves.forEach { proprietaire[it.fileId] = catalog }
            return trouves
        }
        actif = null
        return emptyList()
    }

    override suspend fun download(candidate: SubtitleCandidate): DownloadedSubtitle? =
        (proprietaire[candidate.fileId] ?: actif)?.download(candidate)

    override suspend fun quota(): SubtitleQuota = actif?.quota() ?: SubtitleQuota.Unknown
}
