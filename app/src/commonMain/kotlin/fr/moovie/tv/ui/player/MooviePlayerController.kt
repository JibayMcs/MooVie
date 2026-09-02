package fr.moovie.tv.ui.player

import fr.moovie.tv.core.subtitles.model.SubtitleStyle

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

    /**
     * Le lecteur veut jouer et n'a pas de quoi : ouverture du flux, mise en
     * mémoire tampon, rechargement après un saut.
     *
     * Distinct d'une pause, qui est une décision du spectateur. C'est cette
     * distinction qui permet à un écran de n'afficher un indicateur d'attente
     * que quand l'attente n'est pas voulue — un rond qui tourne sur une pause
     * demandée serait un mensonge.
     *
     * Faux par défaut, et c'est la bonne réponse pour deux des trois
     * plateformes : Android rend sa vidéo dans une `PlayerView` et le desktop
     * dans mpv, qui affichent l'un et l'autre leur propre indicateur. iOS
     * dessine dans un `AVPlayerLayer` nu, qui ne montre rien de lui-même.
     */
    val isBuffering: Boolean get() = false

    /** Position courante en millisecondes, 0 si inconnue. */
    fun positionMs(): Long

    /** Durée totale en millisecondes, 0 si inconnue (flux live, média non prêt). */
    fun durationMs(): Long

    /**
     * Fin de la portion déjà mise en mémoire tampon, en millisecondes.
     *
     * C'est la limite au-delà de laquelle un saut oblige à retélécharger : sur un
     * hôte qui ne gère pas les requêtes `Range`, reprendre à 1 h d'un film de 2 h
     * cale tant que le flux n'est pas chargé jusque-là. La barre de progression
     * en dessine une seconde piste pour rendre cette limite visible.
     *
     * 0 = inconnue ; la piste de chargement n'est alors pas dessinée.
     */
    fun bufferedMs(): Long

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

    /**
     * Charge un fichier de sous-titres externe, ou le retire si [path] est null.
     *
     * Le fichier est déjà **recalé** quand il arrive ici : le décalage et la
     * correction de cadence sont appliqués au fichier, pas au lecteur. C'est le
     * seul moyen d'obtenir le même comportement des deux côtés — libVLC sait
     * décaler des sous-titres, Media3 non, et aucun des deux ne sait étirer une
     * cadence, qui est pourtant la correction qui compte.
     */
    fun loadExternalSubtitle(path: String?)

    /**
     * Applique l'apparence des sous-titres — taille, couleur, fond.
     *
     * Contrairement au recalage, qui se fait sur le **fichier** parce qu'aucun
     * des deux lecteurs ne sait étirer une cadence, l'apparence se règle sur le
     * **lecteur** : chacun dessine son propre texte et sait le faire. Le port ne
     * décrit donc qu'une intention, et chaque plateforme la traduit dans ses
     * termes (CaptionStyleCompat côté Media3, propriétés `sub-*` côté mpv).
     *
     * Peut être appelée à tout moment, y compris sans sous-titre monté : le
     * réglage s'applique au suivant.
     */
    fun applySubtitleStyle(style: SubtitleStyle)

    /**
     * Cadence du flux en images par seconde, 0 si inconnue.
     *
     * Sert à corriger la dérive d'un sous-titre calé sur une autre cadence — le
     * `moviehash` d'OpenSubtitles étant hors d'atteinte sur des flux segmentés,
     * c'est notre seul point d'appui pour synchroniser autre chose qu'à la main.
     */
    fun videoFps(): Double = 0.0
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

/** Contenu déduit de la mediaKey ("movie:<id>" ou "tv:<id>:s<S>e<E>"). */
data class PlaybackId(val tmdbId: Int, val isTv: Boolean, val season: Int, val episode: Int)

/**
 * Décompose la clé de lecture. Null quand elle est vide ou hors format : le
 * titre est alors inconnu, et sans lui il n'y a ni intro ni générique à
 * demander à TheIntroDB. Partagé par les deux lecteurs.
 */
fun parseMediaKey(key: String): PlaybackId? {
    val parts = key.split(":")
    return when {
        parts.size >= 2 && parts[0] == "movie" ->
            parts[1].toIntOrNull()?.let { PlaybackId(it, false, 0, 0) }
        parts.size >= 3 && parts[0] == "tv" -> {
            val tmdb = parts[1].toIntOrNull() ?: return null
            val m = Regex("s(\\d+)e(\\d+)").find(parts[2]) ?: return null
            PlaybackId(tmdb, true, m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        else -> null
    }
}

/** Menu ouvert par la barre de contrôles. */
enum class PlayerDialogKind { SUBTITLES, SETTINGS, REPORT, EPISODES }

/** `1:23:45` au-delà d'une heure, `23:45` sinon. */
fun formatPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        // Chiffres et deux-points : rien de localisé ici, `padStart` suffit.
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}
