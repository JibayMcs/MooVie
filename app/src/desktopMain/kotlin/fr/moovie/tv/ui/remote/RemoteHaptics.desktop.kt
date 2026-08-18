package fr.moovie.tv.ui.remote

import androidx.compose.runtime.Composable
import fr.moovie.tv.data.remote.RemoteKey

/**
 * Un poste de travail n'a pas de vibreur.
 *
 * [available] rend donc faux, franchement : c'est l'intérêt du champ, dire ce
 * qu'il en est plutôt que de laisser attendre un retour qui n'arrivera jamais.
 */
actual object RemoteHaptics {
    actual val available: Boolean = false
    actual fun tick(kind: HapticTick) = Unit
}

/**
 * Rien à détourner.
 *
 * Les touches de volume d'un clavier sont traitées par le système bien avant
 * qu'une JVM les voie — le même mur que celui qui a fait renoncer au volume
 * système côté desktop (voir `RemoteKey.desktop.kt`, où c'est le volume **du
 * lecteur** qui se règle). Prétendre les capturer donnerait des touches qui
 * n'atteignent ni le téléviseur ni le poste.
 */
@Composable
actual fun CaptureVolumeKeys(onKey: (RemoteKey) -> Unit) = Unit
