package fr.moovie.tv.ui.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks

/**
 * Le sous-titre que l'utilisateur veut voir, décrit **indépendamment des pistes
 * du moment**.
 *
 * ## Pourquoi ce détour au lieu de retenir la piste elle-même
 *
 * Media3 range les sélections forcées dans une table dont la clé est le
 * `TrackGroup`, et `TrackGroup.equals` compare son identifiant *et* le tableau
 * de `Format` qu'il porte. Or ExoPlayer republie ses pistes en cours de route —
 * l'identifiant est préfixé par l'index de période (`1:` puis `2:` après une
 * re-préparation), et un `Format` HLS se précise une fois le premier segment
 * lu. Il suffit d'un de ces deux changements pour que la clé ne corresponde
 * plus : la sélection forcée devient orpheline, ExoPlayer reprend la main, et
 * le sous-titre **disparaît** — puis revient si la publication suivante retombe
 * sur l'ancienne forme. C'est le clignotement.
 *
 * Retenir une *intention* plutôt qu'une piste permet de la réaffirmer à chaque
 * republication, ce que fait [ExoPlayerController]. Les critères ci-dessous sont
 * choisis pour survivre à ces republications.
 */
internal sealed interface SubtitleWish {

    /** Aucun sous-titre : c'est aussi l'état de départ du lecteur. */
    data object Off : SubtitleWish

    /** Le fichier que nous avons monté nous-mêmes (OpenSubtitles). */
    data object External : SubtitleWish

    /**
     * Une piste du flux.
     *
     * Trois critères plutôt qu'un seul parce qu'aucun n'est garanti : un flux
     * HLS nomme rarement ses pistes, un MKV remuxé n'a pas toujours de langue.
     * On retient ce qui existe au moment du choix et on retrouve la piste avec
     * ce qu'on a.
     */
    data class Stream(
        val formatId: String?,
        val language: String?,
        val label: String?,
    ) : SubtitleWish
}

/** Identifie la piste de sous-titres que nous ajoutons nous-mêmes. */
internal const val EXTERNAL_SUBTITLE_ID = "moovie-external-subtitle"

/**
 * Décrit la piste choisie de façon à la retrouver plus tard.
 *
 * L'identifiant est gardé **tel quel** : c'est [matches] qui le compare de
 * façon tolérante au préfixe de période.
 */
internal fun Tracks.Group.toWish(trackIndex: Int): SubtitleWish {
    val format = getTrackFormat(trackIndex)
    if (format.id?.endsWith(EXTERNAL_SUBTITLE_ID) == true) return SubtitleWish.External
    return SubtitleWish.Stream(
        formatId = format.id,
        language = format.language?.takeIf { it.isNotBlank() && it != "und" },
        label = format.label,
    )
}

/**
 * Retrouve, parmi les pistes publiées à l'instant, celle qui réalise [wish].
 *
 * Rend le groupe et l'index de piste à forcer, ou null si le souhait n'a pas
 * (encore) de piste correspondante — cas normal juste après une re-préparation,
 * le fichier externe n'apparaissant qu'une fois le média monté.
 *
 * L'appariement va **du plus précis au plus tolérant**. Un flux porte souvent
 * deux pistes d'une même langue — une complète et une « forcée » pour les
 * passages en langue étrangère — et se rabattre sur la langue dès le premier
 * tour choisirait celle qui vient en premier plutôt que celle qu'on avait.
 */
internal fun List<Tracks.Group>.findSubtitle(wish: SubtitleWish): Pair<TrackGroup, Int>? {
    if (wish is SubtitleWish.Off) return null
    return criteres(wish).firstNotNullOfOrNull { critere -> premiereQui(critere) }
}

/** La première piste texte utilisable qui satisfait [critere]. */
private fun List<Tracks.Group>.premiereQui(
    critere: (Format) -> Boolean,
): Pair<TrackGroup, Int>? {
    forEach { group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEach
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            if (critere(group.getTrackFormat(i))) return group.mediaTrackGroup to i
        }
    }
    return null
}

/**
 * Les critères d'appariement de [wish], par spécificité décroissante.
 *
 * Le fichier externe n'en a qu'un, et il est sûr : c'est nous qui l'avons
 * étiqueté. Une piste du flux se cherche d'abord sur son identifiant, puis sur
 * son libellé, enfin sur sa seule langue.
 */
private fun criteres(wish: SubtitleWish): List<(Format) -> Boolean> = when (wish) {
    is SubtitleWish.Off -> emptyList()
    is SubtitleWish.External -> listOf(::estNotreFichier)
    is SubtitleWish.Stream -> listOfNotNull(
        wish.formatId?.let { attendu ->
            { f: Format -> !estNotreFichier(f) && f.id?.let { memeIdentifiant(it, attendu) } == true }
        },
        wish.label?.let { attendu ->
            { f: Format -> !estNotreFichier(f) && f.label == attendu }
        },
        wish.language?.let { attendu ->
            { f: Format -> !estNotreFichier(f) && f.language == attendu }
        },
    )
}

/**
 * Vrai si la piste déjà sélectionnée réalise [wish].
 *
 * Sert à ne réaffirmer la sélection que lorsqu'elle a réellement sauté : réécrire
 * les paramètres provoque une nouvelle publication des pistes, et le faire sans
 * condition tournerait en rond.
 */
internal fun List<Tracks.Group>.satisfies(wish: SubtitleWish): Boolean {
    val selectionnees = filter { it.type == C.TRACK_TYPE_TEXT }
        .flatMap { group ->
            (0 until group.length).filter { group.isTrackSelected(it) }
                .map { group.getTrackFormat(it) }
        }
    if (wish is SubtitleWish.Off) return selectionnees.isEmpty()
    val critere = criteres(wish)
    return selectionnees.any { format -> critere.any { it(format) } }
}

/**
 * Vrai si cette piste est le fichier que nous avons monté.
 *
 * `endsWith` et non l'égalité : ExoPlayer préfixe l'identifiant par l'index de
 * période, qui change à chaque re-préparation du média.
 */
private fun estNotreFichier(format: Format): Boolean =
    format.id?.endsWith(EXTERNAL_SUBTITLE_ID) == true

/**
 * Compare deux identifiants en ignorant le préfixe de période (`1:`, `2:`…) que
 * MergingMediaPeriod ajoute et qui change à chaque re-préparation.
 */
private fun memeIdentifiant(gauche: String, droite: String): Boolean =
    gauche == droite || gauche.substringAfter(':') == droite.substringAfter(':')
