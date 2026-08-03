package fr.moovie.tv.ui.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem
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

    /** Vrai tant que la piste externe attend d'être sélectionnée. */
    private var awaitingExternal = false

    init {
        // Sélectionner la piste externe dès qu'elle apparaît. Sans ça, réactiver
        // l'affichage laisse ExoPlayer choisir lui-même — et il retient la piste
        // intégrée du flux, si bien que le sous-titre téléchargé est chargé,
        // listé, et jamais montré.
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                if (!awaitingExternal) return
                val group = tracks.groups.firstOrNull {
                    it.type == C.TRACK_TYPE_TEXT &&
                        it.length > 0 &&
                        // ExoPlayer préfixe l'identifiant par l'index de la
                        // période : « 1:moovie-external-subtitle ». D'où le
                        // suffixe plutôt qu'une égalité.
                        it.getTrackFormat(0).id?.endsWith(EXTERNAL_SUBTITLE_ID) == true
                } ?: return
                awaitingExternal = false
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                    .build()
            }
        })
    }

    override val isPlaying: Boolean get() = player.isPlaying

    override fun positionMs(): Long = player.currentPosition.coerceAtLeast(0)

    override fun durationMs(): Long =
        player.duration.let { if (it == C.TIME_UNSET) 0L else it }

    // Media3 tient à jour la fin réelle du tampon : c'est exactement la limite
    // jusqu'à laquelle un saut est instantané.
    override fun bufferedMs(): Long = player.bufferedPosition.coerceAtLeast(0)

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

    /**
     * Media3 n'accepte un sous-titre externe qu'au montage du média : il faut
     * donc reconstruire l'élément courant et repréparer, puis revenir à la
     * position exacte. C'est une recharge visible — d'où la règle de ne
     * l'appeler que sur un geste explicite, jamais en réaction continue à un
     * curseur de réglage.
     */
    override fun loadExternalSubtitle(path: String?) {
        val current = player.currentMediaItem ?: return
        val position = player.currentPosition
        val wasPlaying = player.playWhenReady

        val configurations = path?.let {
            listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(java.io.File(it)))
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    // Marqueur pour retrouver *notre* piste parmi celles du flux :
                    // le drapeau « par défaut » ne suffit pas, ExoPlayer lui
                    // préfère volontiers une piste intégrée (du CEA-608, ici).
                    .setId(EXTERNAL_SUBTITLE_ID)
                    .build(),
            )
        }.orEmpty()

        player.setMediaItem(current.buildUpon().setSubtitleConfigurations(configurations).build())

        // **Indispensable** : le lecteur démarre avec les pistes texte coupées,
        // pour qu'aucun sous-titre n'apparaisse sans qu'on l'ait demandé. Ajouter
        // le fichier au média ne suffit donc pas — sans cette réactivation il est
        // chargé puis ignoré, et l'utilisateur ne voit que ce que l'encodeur a
        // gravé dans l'image.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, path == null)
            .build()

        // Les pistes n'existent qu'une fois le média préparé : la sélection ne
        // peut pas se faire ici, elle est différée à leur apparition.
        awaitingExternal = path != null
        player.prepare()
        player.seekTo(position)
        player.playWhenReady = wasPlaying
    }

    /**
     * Cadence déclarée par le format vidéo. Media3 rend `Format.NO_VALUE` quand
     * le conteneur ne la porte pas — fréquent en HLS, d'où le 0 plutôt qu'une
     * valeur inventée.
     */
    override fun videoFps(): Double {
        // `videoFormat` appartient à ExoPlayer, pas à l'interface Player qu'on
        // reçoit : on lit la cadence sur la piste vidéo effectivement retenue.
        val fps = player.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.let { group ->
                (0 until group.length)
                    .firstOrNull { group.isTrackSelected(it) }
                    ?.let { group.getTrackFormat(it).frameRate }
            }
            ?: return 0.0
        return if (fps == Format.NO_VALUE.toFloat() || fps <= 0f) 0.0 else fps.toDouble()
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

/** Identifie la piste de sous-titres que nous ajoutons nous-mêmes. */
private const val EXTERNAL_SUBTITLE_ID = "moovie-external-subtitle"
