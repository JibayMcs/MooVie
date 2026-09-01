package fr.moovie.tv.ui.player

import fr.moovie.tv.core.subtitles.model.SubtitleStyle
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVMediaCharacteristicAudible
import platform.AVFoundation.AVMediaCharacteristicLegible
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.asset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentMediaSelection
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.selectMediaOption
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

/**
 * Lecteur iOS, adossé à `AVPlayer`.
 *
 * ## Ce qu'il joue, et ce qu'il ne joue pas
 *
 * L'extraction ne rend que trois formats — HLS, MP4 et DASH. Les deux premiers
 * sont natifs sur iOS : HLS est le format d'Apple, et un MP4 en H.264 ou HEVC
 * passe sans rien demander. **DASH, non** : AVPlayer ne l'a jamais implémenté,
 * Apple ayant poussé HLS à la place. Le seul producteur de DASH dans l'app est
 * le manifeste fabriqué pour les bandes-annonces YouTube, qui devra être refait
 * en HLS — l'artifice est le même, `EXT-X-MEDIA` réunissant deux pistes
 * séparées aussi bien qu'une `AdaptationSet`.
 *
 * Reste un angle mort étroit : du HEVC empaqueté en segments MPEG-TS. Apple
 * exige le fMP4 pour du HEVC en HLS, là où ExoPlayer et mpv l'acceptent. Les
 * hébergeurs servent du H.264 en TS, donc le cas est rare — mais il échouerait
 * en silence.
 *
 * ## Les en-têtes, qui ne sont pas un détail
 *
 * Les CDN des hébergeurs refusent toute requête sans `Referer` ni
 * `User-Agent` — le port `HttpGateway` le documente déjà pour l'extraction, et
 * c'est vrai aussi du flux lui-même. `AVURLAsset` ne les accepte que par une
 * option non documentée, `AVURLAssetHTTPHeaderFieldsKey`, seule voie publique
 * pour les poser sans écrire un `AVAssetResourceLoaderDelegate` entier.
 *
 * ## Ce que ce contrôleur ne sait pas encore faire
 *
 * Le style des sous-titres est ignoré : sur iOS il se règle par
 * `AVTextStyleRule`, qui ne recouvre pas les mêmes possibilités que le rendu
 * maison de mpv. Et [videoFps] garde son zéro par défaut — la cadence n'est
 * lisible que via `AVAssetTrack.nominalFrameRate`, chargé de façon asynchrone,
 * et elle ne sert qu'à un affichage de diagnostic.
 *
 * **Ce fichier compile mais n'a jamais été exécuté.** Il demande un essai sur
 * appareil : une image sans son, un son sans image ou un carré noir se
 * compilent tous parfaitement.
 */
@OptIn(ExperimentalForeignApi::class)
class AvPlayerController(
    url: String,
    headers: Map<String, String> = emptyMap(),
) : MooviePlayerController {

    val player: AVPlayer

    private var vitesse: Float = 1f

    init {
        // Catégorie `playback` : sans elle, le son se tait quand l'utilisateur
        // bascule le commutateur silencieux, ce qui n'a aucun sens pour un
        // film. C'est aussi elle qui autorise la lecture écran verrouillé,
        // déclarée dans l'Info.plist par `UIBackgroundModes`.
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)
        }

        val nsUrl = NSURL.URLWithString(url) ?: NSURL.fileURLWithPath(url)
        val asset = if (headers.isEmpty()) {
            AVURLAsset(nsUrl, null)
        } else {
            AVURLAsset(nsUrl, mapOf("AVURLAssetHTTPHeaderFieldsKey" to headers))
        }
        player = AVPlayer(playerItem = AVPlayerItem(asset))
        player.play()
    }

    override val isPlaying: Boolean
        get() = player.timeControlStatus == AVPlayerTimeControlStatusPlaying

    /**
     * `WaitingToPlayAtSpecifiedRate` : une cadence non nulle est demandée et
     * AVPlayer ne peut pas la tenir. C'est l'état exact de l'attente de données,
     * et il se lit sans observateur — `timeControlStatus` est une propriété,
     * là où `playbackLikelyToKeepUp` demanderait un KVO à enregistrer et à
     * libérer à la main, pour la même réponse.
     */
    override val isBuffering: Boolean
        get() = player.timeControlStatus ==
            AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate

    override fun positionMs(): Long = secondesEnMs(CMTimeGetSeconds(player.currentTime()))

    override fun durationMs(): Long {
        val item = player.currentItem ?: return 0L
        return secondesEnMs(CMTimeGetSeconds(item.duration))
    }

    /**
     * Fin de la première plage chargée.
     *
     * `loadedTimeRanges` est un tableau de `NSValue` enveloppant des
     * `CMTimeRange`, que Kotlin/Native ne sait pas déballer sans cinterop
     * supplémentaire. La barre de mise en mémoire tampon est un confort
     * d'affichage, pas une donnée dont dépend la lecture : rendre la position
     * revient à ne rien annoncer d'avance, ce qui est honnête, plutôt qu'à
     * inventer une valeur.
     */
    override fun bufferedMs(): Long = positionMs()

    override fun togglePause() {
        if (isPlaying) player.pause() else reprendre()
    }

    override fun pause() = player.pause()

    /**
     * Reprendre par `rate` et non par `play()` : `play()` remet la vitesse à 1
     * et effacerait un réglage à 1,5× au premier appui sur pause.
     */
    private fun reprendre() {
        player.rate = vitesse
    }

    override fun seekTo(positionMs: Long) {
        // 600 comme échelle de temps : c'est le multiple commun des cadences
        // usuelles (24, 25, 30, 60), donc un déplacement qui tombe sur une
        // frontière d'image plutôt qu'entre deux.
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 600))
    }

    override fun seekBy(deltaMs: Long) {
        val duree = durationMs()
        val cible = (positionMs() + deltaMs).coerceAtLeast(0L)
        seekTo(if (duree > 0) cible.coerceAtMost(duree) else cible)
    }

    override val speed: Float get() = vitesse

    override fun setSpeed(value: Float) {
        vitesse = value
        // N'appliquer que si ça joue : écrire `rate` sur un lecteur en pause le
        // relancerait, ce qui n'est pas ce qu'on demande en changeant la
        // vitesse depuis le menu.
        if (isPlaying) player.rate = value
    }

    override fun tracks(): PlayerTracks {
        val item = player.currentItem ?: return PlayerTracks()
        return PlayerTracks(
            subtitles = pistes(item, AVMediaCharacteristicLegible),
            audio = pistes(item, AVMediaCharacteristicAudible),
        )
    }

    override fun selectSubtitle(trackId: String?) =
        selectionner(AVMediaCharacteristicLegible, trackId)

    override fun selectAudio(trackId: String) =
        selectionner(AVMediaCharacteristicAudible, trackId)

    /**
     * Les sous-titres externes ne passent pas par là sur iOS.
     *
     * AVPlayer ne sait pas charger une piste depuis un fichier à côté du flux :
     * il faut soit un HLS qui la déclare, soit un `AVMutableComposition`
     * assemblé à la main. Les sous-titres téléchargés depuis OpenSubtitles sont
     * déjà convertis en WebVTT par `SrtToVtt` — la voie propre sera de les
     * injecter dans une playlist HLS locale, comme le fait déjà le stockage des
     * manifestes de bandes-annonces.
     */
    override fun loadExternalSubtitle(path: String?) = Unit

    /** Voir la documentation de la classe : `AVTextStyleRule`, non branché. */
    override fun applySubtitleStyle(style: SubtitleStyle) = Unit

    // Les constantes `AVMediaCharacteristic*` sont nullables dans le binding
    // Kotlin/Native : ce sont des `NSString *` sans annotation côté Apple.
    private fun pistes(item: AVPlayerItem, caracteristique: String?): List<PlayerTrack> {
        if (caracteristique == null) return emptyList()
        val groupe = item.asset.mediaSelectionGroupForMediaCharacteristic(caracteristique)
            ?: return emptyList()
        val choisie = item.currentMediaSelection.selectedMediaOptionInMediaSelectionGroup(groupe)
        return groupe.options.mapIndexedNotNull { index, brute ->
            val option = brute as? platform.AVFoundation.AVMediaSelectionOption
                ?: return@mapIndexedNotNull null
            PlayerTrack(
                id = index.toString(),
                label = option.displayName,
                selected = option == choisie,
            )
        }
    }

    private fun selectionner(caracteristique: String?, trackId: String?) {
        if (caracteristique == null) return
        val item = player.currentItem ?: return
        val groupe = item.asset.mediaSelectionGroupForMediaCharacteristic(caracteristique) ?: return
        val option = trackId?.toIntOrNull()
            ?.let { groupe.options.getOrNull(it) as? platform.AVFoundation.AVMediaSelectionOption }
        // null désactive : c'est le contrat de `selectSubtitle`, et AVPlayer
        // l'accepte tel quel pour un groupe qui l'autorise.
        item.selectMediaOption(option, groupe)
    }

    /**
     * `CMTimeGetSeconds` rend NaN sur un temps indéfini — un flux live, ou un
     * média pas encore prêt. Le contrat de l'interface dit « 0 si inconnue » ;
     * laisser passer un NaN donnerait une barre de progression à l'infini.
     */
    private fun secondesEnMs(secondes: Double): Long =
        if (secondes.isNaN() || secondes.isInfinite() || secondes < 0) 0L
        else (secondes * 1000).toLong()

    /** À appeler en quittant l'écran : sans ça le son continue en fond. */
    fun liberer() {
        player.pause()
        runCatching { AVAudioSession.sharedInstance().setActive(false, null) }
    }
}
