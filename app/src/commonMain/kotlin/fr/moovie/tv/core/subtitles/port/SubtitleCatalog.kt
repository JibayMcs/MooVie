package fr.moovie.tv.core.subtitles.port

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.subtitles.model.DownloadedSubtitle
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate

/**
 * Un catalogue de sous-titres.
 *
 * C'est un **port**, comme [fr.moovie.tv.core.sources.port.SourceProvider] : le
 * domaine ignore si l'implémentation interroge un addon, SubDL ou un
 * dossier local. Le jour où un fournisseur ferme, on écrit un autre
 * adaptateur et rien d'autre ne bouge.
 *
 * [search] et [download] restent séparés : on ne télécharge que sur un geste
 * explicite de l'utilisateur, jamais « au cas où » à l'ouverture d'une fiche.
 */
interface SubtitleCatalog {

    /** Identifiant stable, affiché à l'utilisateur. */
    val name: String

    /** Vrai si le catalogue est utilisable (adresse de l'addon renseignée, par exemple). */
    val available: Boolean

    /**
     * Sous-titres disponibles pour ce média, du plus pertinent au moins.
     *
     * Ne doit pas lever : un service injoignable est un
     * cas normal et rend une liste vide.
     */
    suspend fun search(media: MediaRef, languages: List<String>): List<SubtitleCandidate>

    /**
     * Récupère le contenu d'un sous-titre. **Rend le contenu** : à n'appeler
     * que sur un geste explicite de l'utilisateur, jamais en anticipation.
     *
     * Rend null en cas d'échec.
     */
    suspend fun download(candidate: SubtitleCandidate): DownloadedSubtitle?
}
