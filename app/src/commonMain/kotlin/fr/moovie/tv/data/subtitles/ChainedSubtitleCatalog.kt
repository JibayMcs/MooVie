package fr.moovie.tv.data.subtitles

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.subtitles.model.DownloadedSubtitle
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.model.SubtitleQuota
import fr.moovie.tv.core.subtitles.port.SubtitleCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Protège [actif] et [proprietaire] : une recherche et un téléchargement
     * peuvent s'exécuter en même temps (le choix d'un sous-titre pendant qu'une
     * autre liste se charge). Jamais tenu pendant un appel réseau.
     */
    private val verrou = Mutex()

    override suspend fun search(
        media: MediaRef,
        languages: List<String>,
    ): List<SubtitleCandidate> {
        for (catalog in catalogs) {
            if (!catalog.available) continue
            // Un catalogue qui lève ne doit pas priver l'utilisateur du suivant :
            // le port promet de ne pas lever, on ne parie pas dessus.
            // L'annulation, elle, doit remonter : la cascade s'arrête avec le ViewModel.
            val trouves = try {
                catalog.search(media, languages)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (trouves.isEmpty()) continue
            verrou.withLock {
                actif = catalog
                proprietaire.clear()
                trouves.forEach { proprietaire[it.fileId] = catalog }
            }
            return trouves
        }
        verrou.withLock { actif = null }
        return emptyList()
    }

    override suspend fun download(candidate: SubtitleCandidate): DownloadedSubtitle? {
        val source = verrou.withLock { proprietaire[candidate.fileId] ?: actif } ?: return null
        return source.download(candidate)
    }

    override suspend fun quota(): SubtitleQuota {
        val source = verrou.withLock { actif } ?: return SubtitleQuota.Unknown
        return source.quota()
    }

    /**
     * Vrai si le candidat vient d'un catalogue dont les téléchargements sont
     * comptés, c'est-à-dire OpenSubtitles. Sert à savoir si un échec de
     * téléchargement peut vraisemblablement être un quota épuisé.
     */
    suspend fun aUnQuota(candidate: SubtitleCandidate): Boolean =
        verrou.withLock { proprietaire[candidate.fileId] ?: actif } is OpenSubtitlesCatalog
}
