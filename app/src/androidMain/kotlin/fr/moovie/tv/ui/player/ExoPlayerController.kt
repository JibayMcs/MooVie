package fr.moovie.tv.ui.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

/**
 * [MooviePlayerController] adossé à Media3/ExoPlayer.
 *
 * Les identifiants de piste exposés à la chrome partagée sont de la forme
 * `indexDeGroupe:indexDePiste`. C'est volontairement opaque côté UI : seul cet
 * adaptateur sait les retraduire en `TrackSelectionOverride`.
 */
internal class ExoPlayerController(private val player: Player) : MooviePlayerController {

    override val isPlaying: Boolean get() = player.isPlaying

    override fun positionMs(): Long = player.currentPosition.coerceAtLeast(0)

    override fun durationMs(): Long =
        player.duration.let { if (it == C.TIME_UNSET) 0L else it }

    override fun togglePause() {
        player.playWhenReady = !player.playWhenReady
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    override fun seekBy(deltaMs: Long) {
        val duration = durationMs()
        val max = if (duration > 0) duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
    }

    override val speed: Float get() = player.playbackParameters.speed

    override fun setSpeed(value: Float) {
        player.setPlaybackSpeed(value)
    }

    override fun tracks(): PlayerTracks {
        val groups = player.currentTracks.groups
        return PlayerTracks(
            subtitles = groups.toPlayerTracks(C.TRACK_TYPE_TEXT),
            audio = groups.toPlayerTracks(C.TRACK_TYPE_AUDIO),
        )
    }

    override fun selectSubtitle(trackId: String?) {
        val target = trackId?.let { resolve(it) }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, target == null)
            .apply {
                if (target != null) {
                    setOverrideForType(
                        TrackSelectionOverride(target.first.mediaTrackGroup, target.second),
                    )
                }
            }
            .build()
    }

    override fun selectAudio(trackId: String) {
        val target = resolve(trackId) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setOverrideForType(
                TrackSelectionOverride(target.first.mediaTrackGroup, target.second),
            )
            .build()
    }

    /** `groupe:piste` → le groupe et l'index correspondants, null si obsolète. */
    private fun resolve(trackId: String): Pair<Tracks.Group, Int>? {
        val parts = trackId.split(':')
        if (parts.size != 2) return null
        val groupIndex = parts[0].toIntOrNull() ?: return null
        val trackIndex = parts[1].toIntOrNull() ?: return null
        val group = player.currentTracks.groups.getOrNull(groupIndex) ?: return null
        if (trackIndex !in 0 until group.length) return null
        return group to trackIndex
    }

    private fun List<Tracks.Group>.toPlayerTracks(type: Int): List<PlayerTrack> = buildList {
        this@toPlayerTracks.forEachIndexed { groupIndex, group ->
            if (group.type != type) return@forEachIndexed
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                add(
                    PlayerTrack(
                        id = "$groupIndex:$i",
                        label = group.trackLabel(i),
                        selected = group.isTrackSelected(i),
                    ),
                )
            }
        }
    }

    private fun Tracks.Group.trackLabel(index: Int): String {
        val format = getTrackFormat(index)
        return format.label
            ?: format.language?.takeIf { it.isNotBlank() && it != "und" }
            ?: "#${index + 1}"
    }
}
