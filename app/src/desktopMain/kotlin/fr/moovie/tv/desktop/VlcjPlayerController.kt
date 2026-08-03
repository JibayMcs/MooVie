package fr.moovie.tv.desktop

import fr.moovie.tv.ui.player.MooviePlayerController
import fr.moovie.tv.ui.player.PlayerTrack
import fr.moovie.tv.ui.player.PlayerTracks
import uk.co.caprica.vlcj.player.base.MediaPlayer
import java.io.File
import uk.co.caprica.vlcj.media.MediaSlaveType
import java.util.concurrent.Executors

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

    /**
     * Toutes les commandes partent d'ici, jamais du thread UI.
     *
     * `libvlc_media_player_set_time` & consorts prennent un verrou natif et
     * peuvent bloquer indéfiniment ; appelées depuis une lambda Compose, elles
     * gèlent la fenêtre entière. Un seul thread, pour préserver l'ordre des
     * commandes (une bascule pause/lecture ne doit pas s'inverser).
     */
    private val commands = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "moovie-vlc-commands").apply { isDaemon = true }
    }

    private fun command(block: () -> Unit) {
        runCatching { commands.execute { runCatching(block) } }
    }

    /** À appeler quand le lecteur est libéré : sinon le thread survit. */
    fun shutdown() {
        runCatching { commands.shutdownNow() }
    }

    override val isPlaying: Boolean
        get() = runCatching { player.status().isPlaying }.getOrDefault(false)

    override fun positionMs(): Long =
        runCatching { player.status().time().coerceAtLeast(0) }.getOrDefault(0L)

    override fun durationMs(): Long =
        runCatching { player.status().length().coerceAtLeast(0) }.getOrDefault(0L)

    /**
     * Inconnue côté libVLC 3 : l'API n'expose aucune plage tamponnée, seulement
     * un pourcentage de remplissage du cache réseau (événement `buffering`), qui
     * ne se traduit pas en position. L'écran desktop affiche donc ce pourcentage
     * en clair plutôt qu'une piste de chargement mensongère sur la barre.
     */
    override fun bufferedMs(): Long = 0L

    override fun togglePause() {
        command { player.controls().setPause(player.status().isPlaying) }
    }

    override fun pause() {
        command { player.controls().setPause(true) }
    }

    override fun seekTo(positionMs: Long) {
        command { player.controls().setTime(positionMs.coerceAtLeast(0)) }
    }

    override fun seekBy(deltaMs: Long) {
        command {
            val length = player.status().length()
            val max = if (length > 0) length else Long.MAX_VALUE
            val target = (player.status().time() + deltaMs).coerceIn(0L, max)
            player.controls().setTime(target)
        }
    }

    override val speed: Float
        get() = runCatching { player.status().rate() }.getOrDefault(1f)

    override fun setSpeed(value: Float) {
        command { player.controls().setRate(value) }
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
        command { player.subpictures().setTrack(id) }
    }

    override fun selectAudio(trackId: String) {
        val id = trackId.toIntOrNull() ?: return
        command { player.audio().setTrack(id) }
    }

    /**
     * libVLC charge un sous-titre externe **à chaud**, sans interrompre la
     * lecture : c'est un « slave » ajouté au média en cours. Rien à recharger,
     * contrairement à Media3 qui doit reconstruire son élément.
     *
     * Passe par [command] comme tout le reste : appeler libVLC depuis son propre
     * thread d'évènements ou depuis le fil d'UI bloque ou fait tomber le lecteur.
     */
    override fun loadExternalSubtitle(path: String?) {
        command {
            if (path == null) {
                player.subpictures().setTrack(-1)
            } else {
                // `select = true` l'active immédiatement, sinon il faudrait
                // deviner l'identifiant que libVLC vient de lui attribuer.
                player.media().addSlave(MediaSlaveType.SUBTITLE, File(path).toURI().toString(), true)
            }
        }
    }

    /**
     * Cadence du flux. libVLC la rend en images par seconde ; 0 quand le
     * conteneur ne la déclare pas, ce qui arrive souvent en HLS.
     */
    override fun videoFps(): Double = runCatching {
        // libVLC rend la cadence en **rationnel** — 24000/1001 pour du 23,976.
        // C'est précisément pour ça que la comparaison avec la valeur déclarée
        // par le catalogue passe par une tolérance : la division ne retombe
        // jamais exactement sur 23,976.
        val track = player.media().info()?.videoTracks()?.firstOrNull()
            ?: return@runCatching 0.0
        val numerator = track.frameRate()
        val denominator = track.frameRateBase()
        if (numerator > 0 && denominator > 0) numerator.toDouble() / denominator else 0.0
    }.getOrDefault(0.0)
}
