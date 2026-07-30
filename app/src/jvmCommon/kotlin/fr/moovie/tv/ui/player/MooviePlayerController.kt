package fr.moovie.tv.ui.player

/**
 * Une piste sélectionnable (sous-titre ou audio), telle que la chrome partagée
 * a besoin de la voir : un identifiant opaque, un libellé, un état.
 *
 * L'identifiant n'est jamais interprété par l'UI — il est repassé tel quel au
 * contrôleur, qui seul sait à quoi il correspond (groupe + index côté Media3,
 * numéro de piste côté libVLC).
 */
data class PlayerTrack(
    val id: String,
    val label: String,
    val selected: Boolean,
)

/** Pistes disponibles sur le média en cours. */
data class PlayerTracks(
    val subtitles: List<PlayerTrack> = emptyList(),
    val audio: List<PlayerTrack> = emptyList(),
) {
    /** Vrai si aucun sous-titre n'est actif (pour cocher l'entrée « Désactivés »). */
    val subtitlesOff: Boolean get() = subtitles.none { it.selected }
}

/**
 * Le lecteur vu par la chrome partagée : Media3/ExoPlayer côté Android TV,
 * libVLC/VLCJ côté desktop.
 *
 * L'interface reste volontairement pauvre — uniquement ce dont la barre de
 * contrôles, les menus et les minuteurs ont réellement besoin. Tout ce qui est
 * propre à une plateforme (surface vidéo, MediaSession, keepScreenOn, focus
 * D-pad) reste dans l'écran qui l'implémente.
 *
 * C'est aussi le point d'insertion si la lecture desktop passe un jour dans un
 * process séparé : seule l'implémentation change, la chrome ne bouge pas.
 */
interface MooviePlayerController {

    /** Lecture en cours (pas « playWhenReady » : l'état réel). */
    val isPlaying: Boolean

    /** Position courante en millisecondes, 0 si inconnue. */
    fun positionMs(): Long

    /** Durée totale en millisecondes, 0 si inconnue (flux live, média non prêt). */
    fun durationMs(): Long

    /** Bascule lecture / pause. */
    fun togglePause()

    /** Met en pause sans basculer (pastille de mise à jour, écran de veille). */
    fun pause()

    fun seekTo(positionMs: Long)

    /** Saut relatif, borné à [0, durée]. */
    fun seekBy(deltaMs: Long)

    /** Vitesse de lecture courante. */
    val speed: Float

    fun setSpeed(value: Float)

    /** Pistes du média, relues à chaque recomposition des menus. */
    fun tracks(): PlayerTracks

    /** [trackId] à null désactive les sous-titres. */
    fun selectSubtitle(trackId: String?)

    fun selectAudio(trackId: String)
}

/** Vitesses proposées dans le menu du lecteur, communes aux deux plateformes. */
val PLAYER_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** Pas de saut des boutons ⏪ / ⏩. */
const val PLAYER_SEEK_STEP_MS = 15_000L

/** Pas de déplacement en mode réglage sur la barre (D-pad). */
const val PLAYER_SCRUB_STEP_MS = 10_000L

/** Décompte avant l'enchaînement automatique de l'épisode suivant. */
const val PLAYER_AUTO_NEXT_SECONDS = 10

/** Durée d'affichage spontané de la pastille de mise à jour. */
const val PLAYER_UPDATE_CHIP_MS = 10_000L

/** Plafond de maintien de l'écran allumé pendant la veille. */
const val PLAYER_SCREENSAVER_AWAKE_MS = 2 * 60 * 60 * 1000L

/** Segment que le bouton « Passer » propose de sauter. */
enum class SkipKind { INTRO, CREDITS }

/** Menu ouvert par la barre de contrôles. */
enum class PlayerDialogKind { SUBTITLES, SETTINGS }

/** `1:23:45` au-delà d'une heure, `23:45` sinon. */
fun formatPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}
