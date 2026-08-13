package fr.moovie.tv.data.remote

import java.awt.EventQueue
import java.awt.Window
import java.awt.event.KeyEvent

/**
 * Fenêtre qui reçoit les touches, posée par le point d'entrée desktop.
 *
 * Un premier jet rendait simplement `false` ici, au motif que `java.awt.Robot`
 * injecte au niveau du **système** : un appui envoyé pendant que l'utilisateur
 * est dans son navigateur y atterrirait. L'argument visait le mauvais mécanisme.
 * Distribuer l'événement à **notre propre fenêtre** ne sort jamais de
 * l'application, exactement comme `Activity.dispatchKeyEvent` côté Android.
 */
var remoteWindow: Window? = null

/**
 * Comment régler le son, posé par le lecteur tant qu'il est à l'écran.
 *
 * Une lambda plutôt qu'un accès direct au moteur, exactement comme
 * [fr.moovie.tv.data.remote.RemoteNowPlaying.attachSeek] : le fil de socket ne
 * connaît ni le moteur ni le fil sur lequel on a le droit de lui parler.
 *
 * Le desktop n'a pas d'équivalent d'`AudioManager` — une JVM pure n'atteint pas
 * le mélangeur du système — donc c'est le volume **du lecteur** qui se règle
 * ici. Conséquence assumée : hors lecture, il n'y a rien à régler et la touche
 * échoue franchement au lieu de ne rien faire en silence.
 *
 * `@Volatile` parce que le lecteur l'écrit depuis la composition et que
 * [sendRemoteKey] la lit depuis une connexion.
 */
@Volatile
var remoteVolume: ((RemoteKey) -> Unit)? = null

actual fun remoteAvailable(): Boolean = remoteWindow != null

actual fun sendRemoteKey(key: RemoteKey): Boolean {
    val window = remoteWindow ?: return false
    // Les touches que le desktop écoute déjà : Échap fait retour au niveau de la
    // fenêtre, Espace met en pause dans le lecteur, les flèches naviguent et
    // reculent. Viser autre chose donnerait des boutons sans effet.
    val code = when (key) {
        RemoteKey.UP -> KeyEvent.VK_UP
        RemoteKey.DOWN -> KeyEvent.VK_DOWN
        RemoteKey.LEFT, RemoteKey.REWIND -> KeyEvent.VK_LEFT
        RemoteKey.RIGHT, RemoteKey.FORWARD -> KeyEvent.VK_RIGHT
        RemoteKey.OK -> KeyEvent.VK_ENTER
        RemoteKey.BACK -> KeyEvent.VK_ESCAPE
        RemoteKey.PLAY_PAUSE -> KeyEvent.VK_SPACE
        // Le lecteur écoute bien ↑/↓ et M pour le volume, mais eux seuls : la
        // même touche navigue partout ailleurs. Passer par la fenêtre ferait
        // donc défiler l'accueil quand on monte le son, ce qui est pire que de
        // ne rien faire. On s'adresse au lecteur, ou à personne.
        RemoteKey.VOLUME_UP, RemoteKey.VOLUME_DOWN, RemoteKey.MUTE -> {
            val apply = remoteVolume ?: return false
            EventQueue.invokeLater { apply(key) }
            return true
        }
    }
    post(window) { target, now ->
        listOf(
            KeyEvent(target, KeyEvent.KEY_PRESSED, now, 0, code, KeyEvent.CHAR_UNDEFINED),
            KeyEvent(target, KeyEvent.KEY_RELEASED, now, 0, code, KeyEvent.CHAR_UNDEFINED),
        )
    }
    // AWT ne dit pas si l'événement a été consommé : on tente le déplacement de
    // focus après coup. Compose ayant déjà bougé le focus le cas échéant, un
    // second déplacement serait visible — d'où l'ordre, `moveFocus` d'abord et
    // seulement si l'événement n'a rien trouvé. Voir RemoteFocus.
    EventQueue.invokeLater { if (window.focusOwner == null) RemoteFocus.move(key) }
    return true
}

actual fun sendRemoteText(text: String): Boolean {
    val window = remoteWindow ?: return false
    if (text.isEmpty()) return true
    // `KEY_TYPED` porte le caractère lui-même, sans passer par un code de touche :
    // les accents traversent donc sans la limite de disposition qui contraint la
    // version Android.
    post(window) { target, now ->
        text.map {
            KeyEvent(target, KeyEvent.KEY_TYPED, now, 0, KeyEvent.VK_UNDEFINED, it)
        }
    }
    return true
}

/**
 * Distribue au composant focalisé, sur le fil d'événements AWT.
 *
 * `focusOwner` plutôt que la fenêtre : c'est lui que Compose écoute. Et hors du
 * fil d'événements, AWT ignore l'événement sans rien signaler — l'appel vient
 * d'un fil de socket.
 */
private fun post(window: Window, build: (target: java.awt.Component, now: Long) -> List<KeyEvent>) {
    EventQueue.invokeLater {
        val target = window.focusOwner ?: window
        build(target, System.currentTimeMillis()).forEach { target.dispatchEvent(it) }
    }
}
