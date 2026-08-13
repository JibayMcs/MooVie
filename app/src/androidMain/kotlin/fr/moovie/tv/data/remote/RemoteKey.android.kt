package fr.moovie.tv.data.remote

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
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
        // Le volume sort du chemin des touches : voir adjustVolume.
        RemoteKey.VOLUME_UP -> return adjustVolume(activity, AudioManager.ADJUST_RAISE)
        RemoteKey.VOLUME_DOWN -> return adjustVolume(activity, AudioManager.ADJUST_LOWER)
        RemoteKey.MUTE -> return adjustVolume(activity, AudioManager.ADJUST_TOGGLE_MUTE)
    }
    return dispatch(activity, key, code)
}

/**
 * Règle le son de l'appareil, et laisse le système afficher sa jauge.
 *
 * **Pas de `dispatchKeyEvent` ici.** Sur une vraie télécommande, les touches de
 * volume sont interceptées par le gestionnaire de fenêtres bien avant
 * l'application : les distribuer à l'Activity ne viserait pas le bon étage, et
 * ce qu'il en reste dépend d'une implémentation interne. `adjustStreamVolume`
 * est l'appel que le système ferait lui-même, et il ne demande **aucune
 * permission**.
 *
 * `FLAG_SHOW_UI` fait apparaître la jauge native d'Android TV. C'est elle qui
 * rend le geste crédible : sans retour à l'écran, on ne sait pas si l'ordre est
 * passé, on appuie plus fort, et on se retrouve deux crans plus loin.
 *
 * `STREAM_MUSIC` **n'a aucun effet quand la sortie est en passthrough HDMI**, ou
 * quand le téléviseur garde la main par CEC : Android est alors court-circuité
 * et son propre curseur est inerte. Le repli, s'il devient nécessaire, est
 * d'atténuer dans le lecteur ; on ne l'ajoute pas d'avance, parce qu'il ferait
 * cohabiter deux volumes sans que rien ne dise lequel bouge.
 *
 * Le `runCatching` couvre le refus que lève le système en mode Ne pas déranger :
 * il n'a rien à voir avec nous, et il ne doit pas remonter sur un fil de socket.
 */
private fun adjustVolume(activity: Activity, direction: Int): Boolean {
    val audio = activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return runCatching {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }.isSuccess
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
private fun dispatch(activity: Activity, key: RemoteKey, code: Int): Boolean {
    val now = SystemClock.uptimeMillis()
    activity.runOnUiThread {
        val handled = activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        // Personne n'a consommé la touche : c'est là que `ViewRootImpl` ferait
        // la recherche de focus sur une vraie télécommande. On la refait.
        if (!handled) RemoteFocus.move(key)
    }
    return true
}
