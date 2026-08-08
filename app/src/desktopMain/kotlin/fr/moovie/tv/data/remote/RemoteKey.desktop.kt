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
    }
    post(window) { target, now ->
        listOf(
            KeyEvent(target, KeyEvent.KEY_PRESSED, now, 0, code, KeyEvent.CHAR_UNDEFINED),
            KeyEvent(target, KeyEvent.KEY_RELEASED, now, 0, code, KeyEvent.CHAR_UNDEFINED),
        )
    }
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
