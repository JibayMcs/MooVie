package fr.moovie.tv.core.subtitles.port

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.subtitles.model.DownloadedSubtitle
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.model.SubtitleQuota

/**
 * Un catalogue de sous-titres.
 *
 * C'est un **port**, comme [fr.moovie.tv.core.sources.port.SourceProvider] : le
 * domaine ignore si l'implémentation interroge OpenSubtitles, SubDL ou un
 * dossier local. Le jour où un fournisseur ferme ou où ses quotas deviennent
 * intenables, on écrit un autre adaptateur et rien d'autre ne bouge.
 *
 * La séparation entre [search] et [download] n'est pas de la décomposition
 * gratuite : chez OpenSubtitles la recherche est illimitée et le téléchargement
 * plafonné à 5 par jour sans compte. **Les deux ne se valent pas**, et l'API du
 * port doit rendre ça impossible à confondre — c'est ce qui interdit la
 * tentation d'aller chercher un sous-titre « au cas où » à l'ouverture d'une
 * fiche.
 */
interface SubtitleCatalog {

    /** Identifiant stable, affiché à l'utilisateur. */
    val name: String

    /** Vrai si le catalogue est utilisable (clé présente, par exemple). */
    val available: Boolean

    /**
     * Sous-titres disponibles pour ce média, du plus pertinent au moins.
     *
     * Gratuit et sans quota. Ne doit pas lever : un service injoignable est un
     * cas normal et rend une liste vide.
     */
    suspend fun search(media: MediaRef, languages: List<String>): List<SubtitleCandidate>

    /**
     * Récupère le contenu d'un sous-titre. **Consomme le quota** : à n'appeler
     * que sur un geste explicite de l'utilisateur, jamais en anticipation.
     *
     * Rend null en cas d'échec, quota épuisé compris.
     */
    suspend fun download(candidate: SubtitleCandidate): DownloadedSubtitle?

    /**
     * Quota courant, sans rien dépenser. Rend [SubtitleQuota.Unknown] quand
     * l'information n'est pas accessible — c'est le cas sans compte connecté,
     * où seul un téléchargement la révèle.
     */
    suspend fun quota(): SubtitleQuota
}
