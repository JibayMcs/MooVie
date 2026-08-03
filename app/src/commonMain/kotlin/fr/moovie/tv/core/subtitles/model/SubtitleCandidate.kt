package fr.moovie.tv.core.subtitles.model

/**
 * Un sous-titre proposé par un catalogue, **avant** téléchargement.
 *
 * La distinction compte : chercher est gratuit et illimité, télécharger consomme
 * un quota très serré (5 par jour sans compte). Tout ce qui aide à choisir doit
 * donc tenir dans cet objet, pour que l'utilisateur tranche avant de dépenser.
 *
 * @param fileId identifiant à passer au téléchargement. Opaque : sa forme
 *   appartient au catalogue, le domaine ne l'interprète jamais.
 * @param fps cadence pour laquelle le sous-titre a été calé. **La donnée la plus
 *   utile de la liste** : un sous-titre calé en 23,976 joué sur un flux en 25
 *   dérive progressivement — plus de quatre minutes d'écart en fin de film — et
 *   aucun décalage constant ne rattrape ça. Voir [fr.moovie.tv.core.subtitles.usecase.timingFor].
 *   Null quand le catalogue ne la déclare pas, ce qui est fréquent.
 * @param foreignPartsOnly sous-titres « forcés » : uniquement les passages en
 *   langue étrangère, pas tous les dialogues.
 */
data class SubtitleCandidate(
    val fileId: String,
    val language: String,
    val release: String = "",
    val fps: Double? = null,
    val downloads: Int = 0,
    val fromTrusted: Boolean = false,
    val hearingImpaired: Boolean = false,
    val foreignPartsOnly: Boolean = false,
    val aiTranslated: Boolean = false,
    val machineTranslated: Boolean = false,
)

/**
 * Ce qu'il reste à dépenser aujourd'hui.
 *
 * Le quota est la ressource rare de la fonctionnalité, donc une information
 * d'interface à part entière et non un détail technique.
 *
 * @param remaining null quand on ne sait pas — et **on ne sait pas la plupart du
 *   temps** : sans compte connecté, le compte à rebours n'est renvoyé qu'en
 *   réponse à un téléchargement. Afficher zéro à la place serait un mensonge, et
 *   l'interface doit assumer l'inconnu plutôt que d'inventer.
 * @param resetAtUtc remise à zéro, à minuit UTC côté OpenSubtitles.
 */
data class SubtitleQuota(
    val remaining: Int? = null,
    val allowed: Int? = null,
    val resetAtUtc: String? = null,
) {
    val known: Boolean get() = remaining != null

    companion object {
        val Unknown = SubtitleQuota()
    }
}

/** Un sous-titre téléchargé : son contenu, et ce qu'il en a coûté. */
data class DownloadedSubtitle(
    val candidate: SubtitleCandidate,
    val content: String,
    val fileName: String = "",
    val quota: SubtitleQuota = SubtitleQuota.Unknown,
)
