package fr.moovie.tv.desktop

import fr.moovie.tv.ui.player.MooviePlayerController
import fr.moovie.tv.ui.player.PlayerTrack
import fr.moovie.tv.ui.player.PlayerTracks
import uk.co.caprica.vlcj.player.base.MediaPlayer

/**
 * [MooviePlayerController] adossé à libVLC via VLCJ.
 *
 * Les identifiants de piste exposés à la chrome sont les identifiants libVLC
 * convertis en texte. `-1` désactive une piste côté libVLC, ce qui sert pour les
 * sous-titres.
 *
 * Tous les appels natifs sont protégés : libVLC répond par des exceptions ou des
 * listes nulles quand le média n'est pas encore prêt, et l'UI interroge le
 * lecteur en continu pour la barre de progression.
 */
internal class VlcjPlayerController(private val player: MediaPlayer) : MooviePlayerController {

    override val isPlaying: Boolean
        get() = runCatching { player.status().isPlaying }.getOrDefault(false)

    override fun positionMs(): Long =
        runCatching { player.status().time().coerceAtLeast(0) }.getOrDefault(0L)

    override fun durationMs(): Long =
        runCatching { player.status().length().coerceAtLeast(0) }.getOrDefault(0L)

    override fun togglePause() {
        runCatching { player.controls().setPause(player.status().isPlaying) }
    }

    override fun pause() {
        runCatching { player.controls().setPause(true) }
    }

    override fun seekTo(positionMs: Long) {
        runCatching { player.controls().setTime(positionMs.coerceAtLeast(0)) }
    }

    override fun seekBy(deltaMs: Long) {
        runCatching {
            val length = player.status().length()
            val max = if (length > 0) length else Long.MAX_VALUE
            val target = (player.status().time() + deltaMs).coerceIn(0L, max)
            player.controls().setTime(target)
        }
    }

    override val speed: Float
        get() = runCatching { player.status().rate() }.getOrDefault(1f)

    override fun setSpeed(value: Float) {
        runCatching { player.controls().setRate(value) }
    }

    override fun tracks(): PlayerTracks = runCatching {
        val currentAudio = player.audio().track()
        val currentSpu = player.subpictures().track()
        PlayerTracks(
            // libVLC expose une entrée « Désactiver » (id -1) dans ses
            // descriptions de sous-titres : la chrome ajoute déjà la sienne.
            subtitles = player.subpictures().trackDescriptions()
                .orEmpty()
                .filter { it.id() >= 0 }
                .map { PlayerTrack(it.id().toString(), it.description(), it.id() == currentSpu) },
            audio = player.audio().trackDescriptions()
                .orEmpty()
                .filter { it.id() >= 0 }
                .map { PlayerTrack(it.id().toString(), it.description(), it.id() == currentAudio) },
        )
    }.getOrDefault(PlayerTracks())

    override fun selectSubtitle(trackId: String?) {
        // -1 : libVLC coupe l'affichage des sous-titres.
        val id = trackId?.toIntOrNull() ?: -1
        runCatching { player.subpictures().setTrack(id) }
    }

    override fun selectAudio(trackId: String) {
        val id = trackId.toIntOrNull() ?: return
        runCatching { player.audio().setTrack(id) }
    }
}
