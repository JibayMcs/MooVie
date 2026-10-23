package fr.moovie.tv.core.subtitles.model

/**
 * Un sous-titre proposé par un catalogue, **avant** téléchargement.
 *
 * La distinction compte : chercher ne coûte rien, télécharger est un geste de
 * l'utilisateur. Tout ce qui aide à choisir doit donc tenir dans cet objet.
 *
 * @param fileId identifiant à passer au téléchargement. Opaque : sa forme
 *   appartient au catalogue, le domaine ne l'interprète jamais.
 * @param fps cadence pour laquelle le sous-titre a été calé. **La donnée la plus
 *   utile de la liste** : un sous-titre calé en 23,976 joué sur un flux en 25
 *   dérive progressivement — plus de quatre minutes d'écart en fin de film — et
 *   aucun décalage constant ne rattrape ça. Voir [fr.moovie.tv.core.subtitles.usecase.timingFor].
 *   Null quand le catalogue ne la déclare pas, ce qui est fréquent.
 */
data class SubtitleCandidate(
    val fileId: String,
    val language: String,
    val release: String = "",
    val fps: Double? = null,
)

/** Un sous-titre téléchargé : son contenu. */
data class DownloadedSubtitle(
    val candidate: SubtitleCandidate,
    val content: String,
    val fileName: String = "",
)
