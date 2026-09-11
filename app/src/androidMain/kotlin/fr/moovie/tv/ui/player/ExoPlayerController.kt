package fr.moovie.tv.ui.player

import android.graphics.Color
import android.net.Uri
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import fr.moovie.tv.core.subtitles.model.SubtitleBackdrop
import fr.moovie.tv.core.subtitles.model.SubtitleStyle
import fr.moovie.tv.core.subtitles.model.toOpaqueArgb
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

    /**
     * Le sous-titre demandé, **réaffirmé à chaque republication des pistes**.
     *
     * Une sélection forcée Media3 est rangée sous une clé `TrackGroup`, dont
     * l'égalité comprend l'identifiant préfixé par la période et le tableau de
     * `Format`. ExoPlayer republie ses pistes en cours de lecture — après une
     * re-préparation, ou quand un `Format` HLS se précise — et la clé cesse
     * alors de correspondre : la sélection devient orpheline, ExoPlayer reprend
     * la main, le sous-titre disparaît, puis revient à la publication suivante.
     * Retenir l'intention plutôt que la piste permet de la reposer à chaque
     * fois. Voir [SubtitleWish].
     */
    private var wish: SubtitleWish = SubtitleWish.Off

    /**
     * La vue qui dessine les sous-titres, fournie par l'écran.
     *
     * Le contrôleur ne connaît qu'un `Player`, et l'apparence se règle sur la
     * **vue** : Media3 sépare les deux. L'écran la dépose donc ici en créant sa
     * `PlayerView`.
     *
     * Poser la vue réapplique le dernier style demandé : l'ordre des deux
     * n'est pas garanti — le style vient des réglages, donc d'une lecture
     * asynchrone, et la vue de la composition.
     */
    internal var subtitleView: SubtitleView? = null
        set(value) {
            field = value
            lastStyle?.let { applyStyleTo(value, it) }
        }

    private var lastStyle: SubtitleStyle? = null

    init {
        // Reposer le souhait dès que les pistes changent. Sans ça, la sélection
        // n'est faite qu'une fois : elle attrape bien la piste externe à son
        // apparition, mais ne survit pas aux republications suivantes — et
        // réactiver l'affichage laisse ExoPlayer choisir lui-même, qui retient
        // la piste intégrée du flux quand il y en a une.
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) = reaffirme(tracks)
        })
    }

    /**
     * Remet la sélection de sous-titres en accord avec [wish].
     *
     * Ne fait rien quand le souhait est déjà tenu : réécrire les paramètres
     * republie les pistes, et le faire sans condition tournerait en rond.
     *
     * Ne coupe rien non plus quand le souhait n'a pas encore de piste : le
     * fichier externe n'apparaît qu'une fois le média monté, et couper en
     * attendant ferait clignoter ce qu'on est justement en train d'installer.
     */
    private fun reaffirme(tracks: Tracks) {
        val groupes = tracks.groups
        if (groupes.satisfies(wish)) return
        val cible = groupes.findSubtitle(wish)
        if (wish !is SubtitleWish.Off && cible == null) return

        val nouveaux = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, cible == null)
            .apply {
                cible?.let { setOverrideForType(TrackSelectionOverride(it.first, it.second)) }
            }
            .build()
        if (nouveaux != player.trackSelectionParameters) {
            player.trackSelectionParameters = nouveaux
        }
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
    override fun applySubtitleStyle(style: SubtitleStyle) {
        lastStyle = style
        applyStyleTo(subtitleView, style)
    }

    /**
     * Traduit l'intention en `CaptionStyleCompat`.
     *
     * ## Les styles du fichier restent, les tailles du fichier partent
     *
     * `setApplyEmbeddedStyles(false)` ferait gagner nos couleurs partout — et
     * emporterait l'**italique**, que les SRT emploient pour la voix off et les
     * répliques en langue étrangère. On perdrait une distinction portée par le
     * sous-titre pour imposer une couleur que personne n'a demandé de forcer.
     *
     * Les *tailles* embarquées, elles, sont désactivées : ce sont elles qui
     * feraient mentir le réglage, en rendant tel fichier plus petit que le
     * précédent sans raison visible.
     */
    private fun applyStyleTo(view: SubtitleView?, style: SubtitleStyle) {
        val target = view ?: return

        target.setApplyEmbeddedStyles(true)
        target.setApplyEmbeddedFontSizes(false)
        target.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.size.scale)

        val edge = when (style.backdrop) {
            SubtitleBackdrop.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            SubtitleBackdrop.SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            SubtitleBackdrop.NONE, SubtitleBackdrop.BOX -> CaptionStyleCompat.EDGE_TYPE_NONE
        }
        // Le bandeau est le seul à peindre derrière le texte. La « fenêtre »
        // (troisième couleur) reste transparente dans tous les cas : elle
        // couvrirait toute la largeur, y compris là où il n'y a pas de texte.
        val background =
            if (style.backdrop == SubtitleBackdrop.BOX) Color.BLACK else Color.TRANSPARENT

        target.setStyle(
            CaptionStyleCompat(
                style.color.rgb.toOpaqueArgb(),
                background,
                Color.TRANSPARENT,
                edge,
                Color.BLACK,
                null,
            ),
        )
    }

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

        // Retirer notre fichier ne veut pas dire couper les sous-titres. Le menu
        // appelle `clear()` *puis* `selectSubtitle` pour passer à une piste du
        // flux, et le retrait qui découle du premier n'arrive qu'après le
        // second, une recomposition plus tard : écraser le souhait ici ferait
        // disparaître la piste que l'utilisateur vient de choisir. Seul un
        // souhait qui portait sur *notre* fichier retombe donc à Off.
        wish = when {
            path != null -> SubtitleWish.External
            wish is SubtitleWish.External -> SubtitleWish.Off
            else -> wish
        }

        // Les pistes texte restent **coupées** le temps que le fichier monte.
        //
        // Les réactiver ici sans pouvoir encore désigner la bonne piste — elle
        // n'existe qu'une fois le média préparé — laisserait ExoPlayer choisir
        // seul dans l'intervalle : il prend alors la piste intégrée du flux (du
        // CEA-608, souvent), qu'on voit apparaître une seconde avant d'être
        // remplacée. C'est [reaffirme] qui rallume, au moment où il a de quoi
        // forcer le bon choix.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()

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

    /**
     * L'identifiant vient du menu, donc de la publication courante : il est
     * traduit **tout de suite** en souhait durable, avant que les index ne se
     * périment. Un identifiant déjà obsolète laisse le souhait en place plutôt
     * que de couper les sous-titres — l'utilisateur n'a rien demandé de tel.
     */
    override fun selectSubtitle(trackId: String?) {
        if (trackId == null) {
            wish = SubtitleWish.Off
        } else {
            val (group, index) = resolve(trackId) ?: return
            wish = group.toWish(index)
        }
        reaffirme(player.currentTracks)
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
