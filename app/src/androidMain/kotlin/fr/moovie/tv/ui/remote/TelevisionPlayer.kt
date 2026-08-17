package fr.moovie.tv.ui.remote

import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import fr.moovie.tv.data.remote.NowPlaying
import fr.moovie.tv.data.remote.castDisplay

/**
 * Le téléviseur, présenté au système comme un lecteur média.
 *
 * ## Ce que ça achète
 *
 * Android ne sait pas ce qu'est une box Moo-vie. Il sait ce qu'est un `Player` :
 * dès qu'on lui en confie un dans une `MediaSession`, il dessine lui-même les
 * commandes du volet et de l'écran verrouillé, y route les touches média du
 * casque et des écouteurs, et affiche la jaquette avec sa barre de progression.
 * Tout cela sans une ligne d'interface de notre part.
 *
 * Ce lecteur-ci ne décode rien. Il **relaie** : un appui sur pause part en
 * requête vers la box, et son état vient du dernier relevé. C'est exactement ce
 * que fait un récepteur Cast, et [SimpleBasePlayer] existe pour ça — il ramène
 * la centaine de méthodes de `Player` à un état à rendre et deux gestes à
 * traiter.
 *
 * ## La fenêtre de confiance locale
 *
 * Sans elle, appuyer sur pause donne ceci : le bouton bascule, media3 relit
 * l'état, le dernier relevé dit encore « ça joue », et **le bouton revient tout
 * seul** — puis rebascule cinq secondes plus tard quand le vrai relevé arrive.
 * Un aller-retour que l'utilisateur lit comme un appui manqué, et qu'il répète,
 * ce qui remet la lecture.
 *
 * On tient donc la valeur locale pour vraie jusqu'à ce qu'un relevé la
 * confirme — ou que le délai expire, sans quoi une commande perdue figerait
 * l'affichage sur un mensonge. C'est le même motif que le `trustAfter` de
 * [RemoteScreen], pour la même raison, à la cadence près : le relevé de fond
 * étant cinq fois plus lent, la fenêtre l'est aussi.
 */
@UnstableApi
class TelevisionPlayer(
    looper: Looper,
    private val onPlayPause: () -> Unit,
    private val onSeek: (Long) -> Unit,
) : SimpleBasePlayer(looper) {

    @Volatile
    private var releve: NowPlaying? = null

    /** Ce qu'on a demandé et que le relevé n'a pas encore confirmé. */
    @Volatile
    private var attenduEnLecture: Boolean? = null

    @Volatile
    private var attenduPositionMs: Long = INCONNU

    @Volatile
    private var confianceJusqua: Long = 0L

    /**
     * Un nouveau relevé de la box. À appeler sur le fil principal :
     * `invalidateState` est un appel de `Player`, et media3 vérifie le fil.
     *
     * Ce qu'on affiche n'est pas le relevé brut : voir [castDisplay], qui garde
     * le dernier média sur un creux plutôt que de vider le lecteur — sans quoi
     * un enchaînement d'épisode fait disparaître la notification.
     */
    fun publish(now: NowPlaying?) {
        val montre = castDisplay(releve, now)
        releve = montre
        // Le relevé confirme ce qu'on attendait : on lâche la valeur locale
        // plutôt que d'attendre le délai. C'est ce qui fait qu'un appui suivi
        // d'un relevé rapide ne laisse aucune latence perceptible.
        if (montre != null && attenduEnLecture == montre.playing) rendLaMain()
        invalidateState()
    }

    private fun rendLaMain() {
        attenduEnLecture = null
        attenduPositionMs = INCONNU
        confianceJusqua = 0L
    }

    private fun localePrioritaire(): Boolean =
        confianceJusqua > 0 && SystemClock.uptimeMillis() < confianceJusqua

    private fun prendLaMain() {
        confianceJusqua = SystemClock.uptimeMillis() + CONFIANCE_MS
    }

    override fun getState(): State {
        val now = releve
        val builder = State.Builder().setAvailableCommands(COMMANDES)

        // Playlist vide : `SimpleBasePlayer` n'accepte alors que IDLE ou ENDED,
        // et IDLE est ce que ça veut dire — rien n'est monté sur la box.
        if (now == null || now.mediaKey.isBlank()) {
            return builder.setPlaybackState(Player.STATE_IDLE).build()
        }

        val locale = localePrioritaire()
        val enLecture = (if (locale) attenduEnLecture else null) ?: now.playing
        val position =
            (if (locale) attenduPositionMs else INCONNU).takeIf { it != INCONNU } ?: now.positionMs

        return builder
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(enLecture, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setSeekBackIncrementMs(PAS_MS)
            .setSeekForwardIncrementMs(PAS_MS)
            .setPlaylist(listOf(item(now)))
            // Entre deux relevés la barre avance toute seule. Sans cela elle
            // sauterait de cinq secondes à chaque réponse — visible comme un
            // à-coup, alors que la position est parfaitement prévisible.
            .setContentPositionMs(
                if (enLecture) PositionSupplier.getExtrapolating(position, 1f)
                else PositionSupplier.getConstant(position),
            )
            .build()
    }

    private fun item(now: NowPlaying): MediaItemData = MediaItemData.Builder(now.mediaKey)
        .setMediaItem(
            MediaItem.Builder()
                .setMediaId(now.mediaKey)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(now.title)
                        .setArtist(now.subtitle)
                        .setArtworkUri(now.artwork.takeIf { it.isNotBlank() }?.let(Uri::parse))
                        .build(),
                )
                .build(),
        )
        // Une durée nulle ferait dessiner au système une barre de progression
        // vide, qui se lit comme un flux qui n'a pas démarré.
        .setDurationUs(if (now.durationMs > 0) now.durationMs * 1_000 else C_TEMPS_INCONNU)
        .setIsSeekable(now.durationMs > 0)
        .setIsDynamic(false)
        .build()

    /**
     * Une seule commande pour les deux sens : la box écoute `PLAY_PAUSE`, une
     * touche de télécommande, et non deux ordres distincts. Lui envoyer « joue »
     * alors qu'elle joue déjà la mettrait en pause.
     */
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        attenduEnLecture = playWhenReady
        prendLaMain()
        onPlayPause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        attenduPositionMs = positionMs.coerceAtLeast(0)
        prendLaMain()
        onSeek(attenduPositionMs)
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val INCONNU = -1L

        /** `C.TIME_UNSET`, recopié pour ne pas importer `C` pour une constante. */
        const val C_TEMPS_INCONNU = Long.MIN_VALUE + 1

        /** Le pas des flèches, aligné sur celui du lecteur local. */
        const val PAS_MS = 15_000L

        /**
         * Un peu plus que le relevé le plus lent en lecture
         * ([fr.moovie.tv.data.remote.CastVigil.PLAYING_POLL_MS]) : la fenêtre
         * doit survivre jusqu'au relevé suivant, sinon elle se rouvre sur
         * l'ancienne vérité et le bouton clignote quand même.
         */
        const val CONFIANCE_MS = 7_000L

        val COMMANDES: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
            )
            .build()
    }
}
