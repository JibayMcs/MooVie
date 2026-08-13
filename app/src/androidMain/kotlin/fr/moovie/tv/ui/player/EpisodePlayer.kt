package fr.moovie.tv.ui.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Le lecteur, augmenté de « épisode précédent » et « épisode suivant ».
 *
 * ### Le défaut qu'il corrige
 *
 * Les commandes que le volet de notifications et l'écran verrouillé affichent ne
 * sont **pas celles de notre notification** : à partir d'Android 13, le système
 * les dessine lui-même à partir de la `MediaSession`, et n'y prend que les
 * commandes que le lecteur déclare disponibles. Des actions personnalisées
 * ajoutées à la notification n'y apparaissent donc jamais.
 *
 * Or la playlist d'ExoPlayer ne contient **qu'un seul média** — on résout la
 * source d'un épisode à la fois. Media3 en concluait qu'il y avait un précédent
 * (revenir au début du média courant, ce qu'il sait toujours faire) mais pas de
 * suivant. D'où le symptôme : une flèche gauche, et aucune flèche droite, sur un
 * épisode qui a évidemment une suite.
 *
 * ### Déclarer, et rediriger
 *
 * Ce relais ment sur deux commandes et détourne les quatre méthodes qui vont
 * avec. Le système voit alors un lecteur qui sait aller au précédent et au
 * suivant ; l'appui n'atteint jamais ExoPlayer, il part vers la fiche, qui
 * résout la source de l'épisode demandé. C'est le même chemin que les flèches de
 * la barre de contrôles — une seule façon de changer d'épisode, quel que soit
 * l'endroit d'où on le demande.
 *
 * Les deux lambdas sont nulles pour un film, et [onPrecedent] l'est aussi sur le
 * premier épisode d'une saison : la commande disparaît alors, plutôt que d'être
 * grisée. Elles sont `@Volatile` parce que la composition les repose quand
 * l'épisode change, tandis que le système les lit depuis son propre fil.
 */
class EpisodePlayer(player: Player) : ForwardingPlayer(player) {

    @Volatile
    var onPrecedent: (() -> Unit)? = null

    @Volatile
    var onSuivant: (() -> Unit)? = null

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, onPrecedent != null)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, onPrecedent != null)
            .addIf(Player.COMMAND_SEEK_TO_NEXT, onSuivant != null)
            .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, onSuivant != null)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        -> onPrecedent != null
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        -> onSuivant != null
        else -> super.isCommandAvailable(command)
    }

    // `hasNext/PreviousMediaItem` est ce que consulte la notification de Media3
    // pour activer ou griser ses propres flèches. Sans ces deux-là, les boutons
    // existeraient et ne répondraient pas.
    override fun hasPreviousMediaItem(): Boolean = onPrecedent != null

    override fun hasNextMediaItem(): Boolean = onSuivant != null

    override fun seekToPrevious() {
        onPrecedent?.invoke()
    }

    override fun seekToPreviousMediaItem() {
        onPrecedent?.invoke()
    }

    override fun seekToNext() {
        onSuivant?.invoke()
    }

    override fun seekToNextMediaItem() {
        onSuivant?.invoke()
    }
}
