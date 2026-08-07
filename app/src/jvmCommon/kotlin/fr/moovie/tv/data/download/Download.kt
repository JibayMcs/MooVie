package fr.moovie.tv.data.download

import kotlinx.serialization.Serializable

/** Où en est un téléchargement. */
enum class DownloadState {
    /** En attente d'un créneau : on ne télécharge qu'un titre à la fois. */
    QUEUED,

    RUNNING,

    /** Interrompu volontairement, ou par une coupure. Reprenable. */
    PAUSED,

    DONE,

    /** Échec définitif. Les fichiers partiels restent, la reprise les réutilise. */
    FAILED,
}

/**
 * Un titre téléchargé, ou en cours de l'être.
 *
 * **La liste est commune à tous les profils**, contrairement à la progression.
 * Un fichier n'existe qu'une fois sur le disque : le masquer aux autres profils
 * mènerait soit à le télécharger deux fois, soit à mentir sur la place occupée.
 * C'est cohérent avec ce que les profils séparent — la progression, pas l'accès.
 */
@Serializable
data class Download(
    /** Clé média : `movie:550`, `tv:1396:s1e1`. */
    val key: String,
    val title: String,
    /** « S1 · E1 — Pilote » pour un épisode, vide pour un film. */
    val subtitle: String = "",
    val imageUrl: String? = null,
    val tmdbId: Int = 0,
    val isTv: Boolean = false,

    val state: DownloadState = DownloadState.QUEUED,

    /**
     * Avancement en **segments**, pas en octets.
     *
     * Un flux HLS n'annonce pas sa taille : on ne connaît le total qu'après
     * avoir tout récupéré. Le nombre de segments, lui, est connu dès la lecture
     * de la playlist, ce qui donne une barre honnête dès la première seconde
     * plutôt qu'un compteur d'octets sans dénominateur.
     */
    val doneSegments: Int = 0,
    val totalSegments: Int = 0,

    /** Octets sur le disque, pour dire ce que ça occupe. */
    val bytes: Long = 0,

    val createdAt: Long = 0,

    /**
     * La langue et l'hébergeur retenus, pour pouvoir **re-résoudre** la source.
     *
     * Une URL de flux expire en moins de deux heures ; un téléchargement de
     * plusieurs gigaoctets peut dépasser ce délai. Sans de quoi retrouver la
     * source, une reprise repartirait de zéro à chaque expiration — et un film
     * assez gros ne finirait jamais.
     */
    val language: String = "",

    /** Message de la dernière panne, tel qu'on peut le montrer. */
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalSegments <= 0) 0f else doneSegments.toFloat() / totalSegments

    val isPlayable: Boolean get() = state == DownloadState.DONE
}
