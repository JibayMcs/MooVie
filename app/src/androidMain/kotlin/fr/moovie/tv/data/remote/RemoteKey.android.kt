package fr.moovie.tv.data.remote

import android.annotation.SuppressLint
import android.app.Activity
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * L'écran qui reçoit les touches, posé par `MainActivity`.
 *
 * Renseigné dans `onResume` et effacé dans `onPause` : c'est ce qui **borne la
 * télécommande au premier plan**. Quitter Moo-vie coupe l'injection, et la
 * session d'appairage s'arrête avec — sans quoi un serveur resterait à l'écoute
 * du réseau alors que plus personne ne regarde.
 *
 * L'annotation tait l'avertissement de fuite : la référence est effacée à la
 * mise en pause, donc elle ne survit pas à l'écran qui la pose.
 */
@SuppressLint("StaticFieldLeak")
var remoteTarget: Activity? = null

actual fun remoteAvailable(): Boolean = remoteTarget != null

actual fun sendRemoteKey(key: RemoteKey): Boolean {
    val activity = remoteTarget ?: return false
    val code = when (key) {
        RemoteKey.UP -> KeyEvent.KEYCODE_DPAD_UP
        RemoteKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        // Reculer et aller à gauche sont la même touche, comme sur une vraie
        // télécommande : c'est l'écran qui décide de ce qu'elle veut dire.
        RemoteKey.LEFT, RemoteKey.REWIND -> KeyEvent.KEYCODE_DPAD_LEFT
        RemoteKey.RIGHT, RemoteKey.FORWARD -> KeyEvent.KEYCODE_DPAD_RIGHT
        RemoteKey.OK -> KeyEvent.KEYCODE_DPAD_CENTER
        RemoteKey.BACK -> KeyEvent.KEYCODE_BACK
        RemoteKey.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    }
    return dispatch(activity, code)
}

actual fun sendRemoteText(text: String): Boolean {
    val activity = remoteTarget ?: return false
    if (text.isEmpty()) return true
    // `KeyCharacterMap` traduit une chaîne en suite d'événements clavier, seule
    // façon d'écrire dans le champ focalisé sans le connaître. Il ne sait rendre
    // que ce que la disposition virtuelle peut produire : un caractère hors de
    // sa portée est ignoré plutôt que de faire échouer toute la phrase.
    val map = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    val events = map.getEvents(text.toCharArray()) ?: return false
    activity.runOnUiThread { events.forEach { activity.dispatchKeyEvent(it) } }
    return true
}

/**
 * Un appui complet : enfoncement **et** relâchement.
 *
 * N'envoyer que le premier laisse la touche « tenue » du point de vue du
 * système, et l'appui suivant se lit comme une répétition — la navigation part
 * alors en glissade.
 *
 * `dispatchKeyEvent` exige le fil principal, d'où `runOnUiThread` : l'appel
 * arrive d'un fil de socket.
 */
private fun dispatch(activity: Activity, code: Int): Boolean {
    val now = SystemClock.uptimeMillis()
    activity.runOnUiThread {
        activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }
    return true
}
