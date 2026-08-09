package fr.moovie.tv.desktop

import fr.moovie.tv.ui.player.MooviePlayerController
import fr.moovie.tv.ui.player.PlayerTrack
import fr.moovie.tv.ui.player.PlayerTracks
import uk.co.caprica.vlcj.player.base.MediaPlayer
import java.io.File
import uk.co.caprica.vlcj.media.MediaSlaveType
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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

    /**
     * Corrections de seek différées.
     *
     * Un fil à part de [commands] : mesurer où libVLC s'est réellement posé
     * demande d'attendre quelques secondes, et attendre sur le fil de commandes
     * y retarderait d'autant la pause ou le seek suivant.
     */
    private val corrections = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "moovie-vlc-seek").apply { isDaemon = true }
    }

    /** À appeler quand le lecteur est libéré : sinon les threads survivent. */
    fun shutdown() {
        runCatching { commands.shutdownNow() }
        runCatching { corrections.shutdownNow() }
    }

    // ── Modèle de seek ────────────────────────────────────────────────────
    //
    // Mesuré sur un vrai flux (voir VlcSeekProbeTest) : en HLS, libVLC se pose
    // au **début du segment** qui contient la cible, soit jusqu'à dix secondes
    // avant elle. Deux conséquences visibles, et c'est le même défaut :
    //
    //  - « Passer l'intro » retombe *dans* l'intro, la bannière revient, et le
    //    bouton paraît sans effet.
    //  - « Avancer de 10 s » avance en réalité de 0 à 10 s au hasard.
    //
    // S'ajoute un second piège : pendant ~1,5 s après un `setTime`, libVLC rend
    // la position **demandée** et non la sienne. Une avance relative calculée
    // sur cette valeur repart donc d'une position imaginaire, et deux appuis
    // rapprochés n'avancent que d'un seul pas.

    /** Cible du seek en cours, ou -1. Elle fait autorité tant qu'elle vaut. */
    @Volatile
    private var pendingTarget = -1L

    /** Échéance au-delà de laquelle on cesse de croire [pendingTarget]. */
    @Volatile
    private var pendingUntil = 0L

    /**
     * Retard observé sur le dernier seek, ajouté aux suivants.
     *
     * Appris plutôt que codé en dur : il vaut la durée d'un segment, qui change
     * d'un hébergeur à l'autre, et **zéro sur un fichier local**, où viser trop
     * loin serait un défaut à son tour.
     */
    @Volatile
    private var seekBias = 0L

    /** Numéro du seek courant : une correction périmée ne doit rien déplacer. */
    private val seekGen = AtomicLong()

    /** Nouveau média : le retard appris ne vaut que pour le flux qui l'a produit. */
    fun onMediaChanged() {
        seekGen.incrementAndGet()
        seekBias = 0L
        pendingTarget = -1L
    }

    override val isPlaying: Boolean
        get() = runCatching { player.status().isPlaying }.getOrDefault(false)

    override fun positionMs(): Long {
        // Pendant un seek, la position qui fait foi est celle qu'on a demandée :
        // c'est là que l'utilisateur a cliqué, et libVLC n'y est pas encore.
        val target = pendingTarget
        if (target >= 0 && System.currentTimeMillis() < pendingUntil) return target
        return runCatching { player.status().time().coerceAtLeast(0) }.getOrDefault(0L)
    }

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
        seek(positionMs.coerceAtLeast(0))
    }

    override fun seekBy(deltaMs: Long) {
        val length = durationMs()
        val max = if (length > 0) length else Long.MAX_VALUE
        // Calculé sur [positionMs], donc sur la cible en cours quand un seek
        // est en vol : deux appuis rapprochés avancent bien de deux pas.
        seek((positionMs() + deltaMs).coerceIn(0L, max))
    }

    /**
     * Va à [target] — et vérifie qu'on y est.
     *
     * Le premier saut applique le retard déjà appris. On relit la position une
     * fois libVLC reposé : s'il est resté court, on note le retard et on
     * recommence en visant d'autant plus loin. Mesuré, ça fait passer d'un
     * « toujours 7 à 10 s avant la cible » à « 2 à 5 s après » — jamais avant,
     * ce qui est tout ce que « passer l'intro » demande.
     */
    private fun seek(target: Long) {
        val gen = seekGen.incrementAndGet()
        pendingTarget = target
        pendingUntil = System.currentTimeMillis() + PENDING_MS
        command { player.controls().setTime(target + seekBias) }
        corrections.schedule({
            if (gen != seekGen.get()) return@schedule
            val landed = runCatching { player.status().time() }.getOrDefault(-1L)
            val undershoot = target - landed
            if (landed >= 0 && undershoot > TOLERANCE_MS) {
                seekBias = (seekBias + undershoot).coerceAtMost(MAX_BIAS_MS)
                pendingUntil = System.currentTimeMillis() + PENDING_MS
                command { player.controls().setTime(target + seekBias) }
                // Rendre la main à l'horloge du lecteur une fois le rattrapage
                // digéré, sinon la barre resterait figée sur la cible.
                corrections.schedule({
                    if (gen == seekGen.get()) pendingTarget = -1L
                }, SETTLE_MS, TimeUnit.MILLISECONDS)
            } else {
                pendingTarget = -1L
            }
        }, SETTLE_MS, TimeUnit.MILLISECONDS)
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
    private companion object {
        /** Délai au bout duquel libVLC a fini de se replacer (mesuré : ~2 s). */
        const val SETTLE_MS = 4_000L

        /** Durée pendant laquelle la cible prime sur l'horloge du lecteur. */
        const val PENDING_MS = 5_000L

        /** En deçà, l'écart relève de l'image-clé, pas du segment. */
        const val TOLERANCE_MS = 1_500L

        /** Un segment HLS dépasse rarement quinze secondes. */
        const val MAX_BIAS_MS = 20_000L
    }

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
