package fr.moovie.tv.ui.remote

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import fr.moovie.tv.data.remote.RemoteKey

/**
 * Les touches physiques de volume du téléphone, détournées vers le téléviseur.
 *
 * C'est le geste qu'on fait sans y penser en tenant une télécommande, et la
 * seule chose qui manquait pour que celle-ci se substitue vraiment à la vraie :
 * régler le son en gardant les yeux sur l'écran. Google Cast détourne les mêmes
 * touches, de la même façon.
 *
 * ### Seulement pendant que la télécommande est affichée
 *
 * L'écran s'inscrit à l'ouverture et se retire à la fermeture. Hors de là,
 * [handle] rend faux immédiatement et les touches font ce qu'elles ont toujours
 * fait. C'est la garantie qui rend le détournement acceptable : **le volume du
 * téléphone n'est jamais confisqué au-delà de l'écran qui le réclame**, et si le
 * téléviseur ne suit pas — sortie HDMI en passthrough, volume tenu par le
 * téléviseur en CEC — il suffit de quitter la télécommande pour le retrouver.
 *
 * ### Un objet global, comme le reste de la télécommande
 *
 * `dispatchKeyEvent` est une affaire d'Activity et l'écran vit dans la
 * composition : les deux n'ont pas d'autre point de rendez-vous. Même motif que
 * [fr.moovie.tv.data.remote.remoteTarget], pour la même raison.
 */
object RemoteVolumeKeys {

    /**
     * Où envoyer la touche, ou null si personne n'écoute.
     *
     * `@Volatile` par principe : l'écriture vient de la composition, et rien ne
     * promet que la lecture se fasse depuis le même fil qu'elle.
     */
    @Volatile
    private var sink: ((RemoteKey) -> Unit)? = null

    /** Dernier envoi, pour ne pas suivre la cadence de répétition du système. */
    private var lastAt = 0L

    /**
     * À poser par l'écran de télécommande, tant qu'il est là.
     *
     * `rememberUpdatedState` plutôt qu'un effet relancé sur [onKey] : la lambda
     * est reconstruite à chaque recomposition, et l'écran en fait beaucoup — il
     * relève l'état du téléviseur toutes les secondes. Réinscrire à chaque fois
     * marcherait, mais ferait passer le détournement par un instant où personne
     * n'écoute, à la cadence des relevés.
     */
    @Composable
    fun Capture(onKey: (RemoteKey) -> Unit) {
        val current by rememberUpdatedState(onKey)
        DisposableEffect(Unit) {
            sink = { current(it) }
            onDispose { sink = null }
        }
    }

    /**
     * Vrai si la touche a été prise en charge, et ne doit donc pas continuer.
     *
     * Le relâchement est consommé lui aussi, **sans rien envoyer**. Le laisser
     * passer ferait régler le volume du téléphone juste après celui du
     * téléviseur : `PhoneWindow` traite les deux moitiés de l'appui, et n'en
     * voir qu'une est précisément le cas qu'il ne sait pas gérer.
     *
     * La répétition du système est conservée — c'est elle qui fait monter le son
     * quand on garde le doigt — mais amortie : la cadence de répétition d'Android
     * est de l'ordre de vingt touches par seconde, soit autant de requêtes sur le
     * réseau local. On s'aligne sur la répétition du pavé directionnel, qui a
     * déjà été réglée à la main sur ce que l'œil suit (`REPEAT_MS`).
     */
    fun handle(event: KeyEvent): Boolean {
        val send = sink ?: return false
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> RemoteKey.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteKey.VOLUME_DOWN
            KeyEvent.KEYCODE_VOLUME_MUTE -> RemoteKey.MUTE
            else -> return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < MIN_INTERVAL_MS) return true
        lastAt = now
        send(key)
        return true
    }

    /** Même cadence que la répétition du pavé, pour un seul et même geste tenu. */
    private const val MIN_INTERVAL_MS = 120L
}
