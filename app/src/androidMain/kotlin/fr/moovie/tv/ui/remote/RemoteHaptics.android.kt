package fr.moovie.tv.ui.remote

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import fr.moovie.tv.data.remote.RemoteKey
import fr.moovie.tv.data.store.appContext

/**
 * Le vibreur du téléphone.
 *
 * En natif, c'est une API système sans permission de haut niveau (`VIBRATE` est
 * de niveau normal) et sans conditions — tout le contraire de ce que la page web
 * pouvait offrir. Voir l'`expect` pour le reste du raisonnement.
 */
actual object RemoteHaptics {

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    /** Faux quand l'appareil n'a pas de vibreur : l'écran le dit plutôt que de laisser douter. */
    actual val available: Boolean
        get() = runCatching { vibrator?.hasVibrator() == true }.getOrDefault(false)

    actual fun tick(kind: HapticTick) {
        val v = vibrator ?: return
        runCatching {
            when {
                // `EFFECT_TICK` est l'effet du système, calibré par le
                // constructeur : sur un moteur à masse rotative comme sur un
                // moteur linéaire, il rend le petit cran attendu. Une durée en
                // millisecondes, elle, donne un bourdonnement sur l'un et un
                // claquement sur l'autre.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && kind == HapticTick.STEP ->
                    v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && kind == HapticTick.PRESS ->
                    v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> v.vibrate(effect(kind))

                else -> {
                    @Suppress("DEPRECATION")
                    when (kind) {
                        HapticTick.STEP -> v.vibrate(12)
                        HapticTick.PRESS -> v.vibrate(24)
                        HapticTick.BACK -> v.vibrate(longArrayOf(0, 10, 40, 16), -1)
                    }
                }
            }
        }
    }

    private fun effect(kind: HapticTick): VibrationEffect = when (kind) {
        HapticTick.STEP -> VibrationEffect.createOneShot(12, 90)
        HapticTick.PRESS -> VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE)
        // Deux temps : Retour est la seule touche qu'on regrette d'avoir
        // appuyée, elle doit se reconnaître d'un autre motif.
        HapticTick.BACK -> VibrationEffect.createWaveform(longArrayOf(0, 10, 40, 16), -1)
    }
}

/**
 * Délègue à [RemoteVolumeKeys], qui garde l'autre moitié du détournement :
 * `handle`, appelée depuis `dispatchKeyEvent` de l'`Activity`. Les deux moitiés
 * ne peuvent pas vivre au même endroit — l'une est de la composition, l'autre
 * une affaire de fenêtre.
 */
@Composable
actual fun CaptureVolumeKeys(onKey: (RemoteKey) -> Unit) = RemoteVolumeKeys.Capture(onKey)
