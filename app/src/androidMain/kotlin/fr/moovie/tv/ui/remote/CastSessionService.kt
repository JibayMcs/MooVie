package fr.moovie.tv.ui.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import fr.moovie.tv.MainActivity
import fr.moovie.tv.data.cast.CastNow
import fr.moovie.tv.data.remote.CastPulse
import fr.moovie.tv.data.remote.CastVerdict
import fr.moovie.tv.data.remote.CastVigil
import fr.moovie.tv.data.remote.NowPlaying
import fr.moovie.tv.data.remote.RemoteClient
import fr.moovie.tv.data.remote.RemoteKey
import fr.moovie.tv.data.remote.RemoteStatus
import fr.moovie.tv.data.remote.RemoteTarget
import fr.moovie.tv.data.remote.RemoteTargetRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.cast_notification_channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Suit la box en arrière-plan, et met ses commandes dans le volet.
 *
 * ## Les deux choses qu'il apporte
 *
 * **Les commandes.** Une diffusion en cours n'était pilotable que depuis l'écran
 * de télécommande. Il fallait donc rallumer le téléphone, déverrouiller, rouvrir
 * l'application et retrouver l'écran pour mettre en pause — là où toute autre
 * lecture se met en pause depuis l'écran verrouillé. Voir [TelevisionPlayer] :
 * c'est lui qui donne au système un lecteur à dessiner.
 *
 * **La progression, en continu.** C'est le bénéfice de bord, et il est plus
 * important que le premier. Jusqu'ici la progression de la box ne revenait vers
 * le téléphone que tant que l'écran de télécommande restait ouvert ; on rangeait
 * le téléphone, la box finissait l'épisode, et le rail « Reprendre » mentait
 * jusqu'au prochain lancement. Un service qui relève en fond recopie au fil de
 * l'eau, et le rattrapage au lancement ([catchUpWithTelevision]) redevient ce
 * qu'il aurait dû être : un filet, pas le mécanisme principal.
 *
 * ## Ce qu'il ne fait pas
 *
 * Il ne joue rien. Le type de service déclaré est pourtant `mediaPlayback`, et
 * c'est le bon : c'est celui d'une lecture déportée sur un autre appareil, le
 * cas du Cast. `dataSync` — celui des téléchargements, dont la permission est
 * déjà acquise — aurait évité une permission de plus, mais Android 15 lui impose
 * un quota journalier, et une soirée de série passerait dessous.
 *
 * ## Le premier plan, dans les cinq secondes
 *
 * Android tue le processus si `startForeground` n'arrive pas dans les cinq
 * secondes qui suivent `startForegroundService` — le téléchargement l'a déjà
 * payé, en s'arrêtant sans un mot parce qu'il attendait une ressource Compose,
 * qui est suspendue. On pose donc **d'abord** une notification minimale, avec le
 * seul libellé du lanceur, et [PlayerNotificationManager] remplace ensuite la
 * sienne sur le même identifiant.
 *
 * ## Sans autorisation de notifier
 *
 * À partir d'Android 13 le système masque la notification sans rien dire à
 * l'application. Le service tourne quand même, et c'est ce qui compte : la
 * recopie de la progression n'a pas besoin d'être vue. Seules les commandes
 * manquent. La demande se fait au moment du geste, dans [rememberTvSender], où
 * elle s'explique.
 */
@UnstableApi
class CastSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val progress = WatchProgressRepository()

    private var player: TelevisionPlayer? = null
    private var session: MediaSession? = null
    private var notifications: PlayerNotificationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Synchrone, et avant toute autre chose. Voir la note de classe.
        val label = applicationInfo.loadLabel(packageManager).toString()
        ensureChannel(label)
        startInForeground(placeholder(label))
        live = true

        // Un second `startService` sur une session déjà montée ne doit pas la
        // remonter : ce serait deux sondes sur la même box, donc deux fois la
        // radio réveillée pour la même information.
        if (player != null) return START_NOT_STICKY

        scope.launch {
            ensureChannel(getString(Res.string.cast_notification_channel))

            // ── Diffusion Chromecast ─────────────────────────────────────
            //
            // **Le service n'a alors rien à relever, et tout à tenir.** Ce n'est
            // pas la box qui joue : c'est ce téléphone qui sert les octets, par
            // le relais. Son seul travail est d'empêcher Android de tuer le
            // processus — le tuer couperait le film au salon.
            //
            // Placé avant la lecture de la cible appairée, parce qu'un
            // Chromecast n'en a aucune : quelqu'un qui n'a pas d'Android TV
            // tomberait sinon sur le `stopSelf` ci-dessous, et sa diffusion
            // s'arrêterait dès l'écran éteint.
            if (CastNow.playback != null) {
                veilleSurLaDiffusion()
                return@launch
            }

            val target = RemoteTargetRepository().target.first()
            // Plus de téléviseur appairé : il n'y a rien à suivre, et une
            // notification sans destinataire serait un fantôme immédiat.
            if (target == null) {
                stopSelf()
                return@launch
            }

            val client = RemoteClient(target)
            val remote = TelevisionPlayer(
                looper = mainLooper,
                onPlayPause = { scope.launch { client.key(RemoteKey.PLAY_PAUSE) } },
                onSeek = { position -> scope.launch { client.seek(position) } },
            )
            player = remote
            session = MediaSession.Builder(this@CastSessionService, remote)
                // Un identifiant propre : le lecteur local en publie une autre,
                // et deux sessions sans identifiant distinct se disputent la
                // même place dans le volet.
                .setId(SESSION_ID)
                .build()
            attachNotification(remote)

            watch(client, remote, target)
        }
        // Le redémarrage ne doit pas ressusciter une session : c'est le geste de
        // diffusion qui décide qu'il y a quelque chose à suivre.
        return START_NOT_STICKY
    }

    /**
     * La boucle de relevé, jusqu'au verdict de retrait.
     *
     * Toute la politique — quand s'arrêter, à quel rythme relever — est dans
     * [CastVigil], hors d'Android et donc testable. Il ne reste ici que le
     * réseau, l'écriture et l'affichage.
     */
    private suspend fun watch(client: RemoteClient, remote: TelevisionPlayer, target: RemoteTarget) {
        var vigil = CastVigil()
        var lastMirrorMs = 0L

        while (true) {
            val status = client.status()
            val (next, verdict) = vigil.observe(CastPulse.of(status))
            vigil = next

            val state = (status as? RemoteStatus.Known)?.state
            // Un silence n'efface rien : il ne dit pas que la box s'est arrêtée,
            // seulement qu'elle n'a pas répondu. Effacer dessus ferait clignoter
            // la notification à chaque paquet perdu.
            if (state != null) {
                remote.publish(state.now)
                // En lecture on suit ; à l'arrêt on recopie ce qu'elle a joué en
                // dernier, ce qui couvre la fin d'épisode qu'on aurait manquée.
                (state.now ?: state.lastPlayed)
                    ?.let { mirrorProgress(progress, it, lastMirrorMs) }
                    ?.let { lastMirrorMs = it }
            }

            when (verdict) {
                CastVerdict.Retire -> {
                    stopSelf()
                    return
                }
                is CastVerdict.Watch -> delay(verdict.delayMs)
            }
        }
    }

    /**
     * Tient le processus en vie tant qu'une diffusion Chromecast dure.
     *
     * Aucun relevé réseau : le récepteur ne nous doit rien, et l'écran de
     * contrôle interroge déjà la session quand il est ouvert. On se contente
     * d'exister — c'est précisément ce qu'un service de premier plan sait faire.
     *
     * Il s'arrête dès que la diffusion cesse, quelle qu'en soit la raison :
     * l'arrêt demandé, une session tombée, ou l'application qui se ferme. Sans
     * cela ce serait la notification fantôme que [CastVigil] existe pour éviter,
     * sous une autre forme.
     */
    private suspend fun veilleSurLaDiffusion() {
        while (CastNow.playback != null) {
            delay(VEILLE_MS)
        }
        stopSelf()
    }

    /**
     * Branche la notification média sur le lecteur.
     *
     * Même chemin que celle du lecteur local ([fr.moovie.tv.ui.player.PlayerMediaNotification]) :
     * c'est le **jeton de session** qui donne le style média — jaquette en fond
     * et barre de progression dessinée par le système à partir d'Android 13.
     * Sans lui, les mêmes boutons apparaissent dans une notification de texte.
     */
    private fun attachNotification(remote: TelevisionPlayer) {
        val manager = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL_ID)
            .setMediaDescriptionAdapter(
                object : PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(player: Player): CharSequence =
                        player.mediaMetadata.title ?: ""

                    override fun getCurrentContentText(player: Player): CharSequence? =
                        player.mediaMetadata.artist

                    override fun createCurrentContentIntent(player: Player): PendingIntent =
                        PendingIntent.getActivity(
                            this@CastSessionService,
                            0,
                            // Rouvre l'application sur la télécommande : c'est
                            // ce qu'on cherche en touchant la notification d'une
                            // diffusion, pas l'accueil.
                            Intent(this@CastSessionService, MainActivity::class.java)
                                .putExtra(EXTRA_OPEN_REMOTE, true),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )

                    override fun getCurrentLargeIcon(
                        player: Player,
                        callback: PlayerNotificationManager.BitmapCallback,
                    ): Bitmap? = null
                },
            )
            .setNotificationListener(
                object : PlayerNotificationManager.NotificationListener {
                    /**
                     * C'est ici que le premier plan bascule sur la vraie
                     * notification. La provisoire portait déjà le même
                     * identifiant : le système la remplace au lieu d'en empiler
                     * une seconde.
                     */
                    override fun onNotificationPosted(
                        notificationId: Int,
                        notification: Notification,
                        ongoing: Boolean,
                    ) {
                        startInForeground(notification)
                    }

                    /**
                     * **Seulement si quelqu'un l'a balayée.**
                     *
                     * Arrêter le service sur toute annulation semblait
                     * l'évidence, et court-circuitait [CastVigil] entièrement :
                     * media3 annule aussi la notification quand le lecteur n'a
                     * plus rien à montrer, ce qui arrivait au **premier** relevé
                     * creux. Mesuré : la vigie rendait `Watch(4000)`, et deux
                     * millisecondes plus tard la session était morte — un
                     * enchaînement d'épisode suffisait donc à la perdre.
                     *
                     * La fin d'une session est la décision de la vigie. Celle-ci
                     * n'a qu'un cas à traiter : le balayage, que le drapeau
                     * `NO_CLEAR` rend d'ailleurs presque impossible.
                     */
                    override fun onNotificationCancelled(
                        notificationId: Int,
                        dismissedByUser: Boolean,
                    ) {
                        if (dismissedByUser) stopSelf()
                    }
                },
            )
            .build()

        manager.setMediaSessionToken(session!!.platformToken)
        // Une diffusion ne se balaie pas : la retirer du volet n'arrêterait pas
        // la box, et on se retrouverait avec un film qui tourne au salon sans
        // aucune commande pour l'arrêter.
        manager.setUseStopAction(false)
        // La box ne sait pas changer d'épisode sur ordre : lui offrir les
        // flèches donnerait deux boutons morts. Voir [RemoteKey].
        manager.setUsePreviousAction(false)
        manager.setUseNextAction(false)
        manager.setUseRewindAction(true)
        manager.setUseFastForwardAction(true)
        manager.setPlayer(remote)
        notifications = manager
    }

    override fun onDestroy() {
        live = false
        // Détacher le lecteur retire la notification : c'est ce qui garantit
        // qu'aucune commande ne survit à la diffusion qu'elle pilote.
        notifications?.setPlayer(null)
        notifications = null
        session?.release()
        session = null
        player?.release()
        player = null
        scope.cancel()
        super.onDestroy()
    }

    private fun placeholder(label: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(label)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notifier().createNotificationChannel(
            // `IMPORTANCE_LOW` : la diffusion vient d'être lancée par la
            // personne qui tient le téléphone. Un son par-dessus serait absurde.
            NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    private fun notifier() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        /** Extra posé sur l'intent d'ouverture : rouvrir sur la télécommande. */
        const val EXTRA_OPEN_REMOTE = "open_remote"

        /**
         * Une diffusion est-elle suivie **dans ce processus** ?
         *
         * Ce qui la rend nécessaire est un piège déjà payé par l'appairage : le
         * système rejoue l'intent qui a créé la tâche à **chaque** recréation de
         * l'Activity, et `setIntent` ne corrige que la copie locale. Une seule
         * touche sur la notification ferait donc démarrer l'application sur la
         * télécommande pour toujours — on ouvrirait Moo-vie et on tomberait sur
         * un pavé directionnel au lieu de l'accueil.
         *
         * On ne se fie donc pas à l'intent seul, mais à ce qu'il prétend : « une
         * diffusion tourne, montre-la ». Si le processus a été tué, ce drapeau
         * est faux, et l'intent rejoué ne raconte plus rien de vrai.
         */
        @Volatile
        var live: Boolean = false
            private set

        private const val CHANNEL_ID = "cast"
        private const val SESSION_ID = "moovie-cast"

        /** Distinct des téléchargements (4201) et du lecteur (4202). */
        private const val NOTIFICATION_ID = 4203

        /** Rythme de la veille : on ne fait qu'exister, inutile de s'agiter. */
        private const val VEILLE_MS = 2_000L

        /** Démarre le suivi de la box. Sans effet s'il tourne déjà. */
        fun start(context: Context) {
            val intent = Intent(context, CastSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
