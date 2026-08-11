package fr.moovie.tv.ui.remote

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import fr.moovie.tv.data.store.appContext

/**
 * Retour haptique de la télécommande.
 *
 * ### Pourquoi ce fichier existe
 *
 * La télécommande a d'abord été une page web, et sa vibration n'a jamais
 * fonctionné : l'API du navigateur ne demande aucune autorisation, elle est
 * simplement inopérante sur Chrome Android selon les réglages de l'appareil, et
 * absente sur iOS. Aucun correctif n'était possible côté page — c'était le
 * mauvais support. En natif, le vibreur est une API système, sans permission
 * (`VIBRATE` est de niveau normal) et sans conditions.
 *
 * ### Trois intensités, parce qu'une seule ne dit rien
 *
 * Le doigt ne regarde pas l'écran. Un cran sec quand la direction change, une
 * frappe plus franche sur OK, un motif à deux temps sur Retour : c'est ce qui
 * permet de sentir *ce qu'on vient de faire* sans lever les yeux.
 */
object RemoteHaptics {

    enum class Tick { STEP, PRESS, BACK }

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
    val available: Boolean
        get() = runCatching { vibrator?.hasVibrator() == true }.getOrDefault(false)

    fun tick(kind: Tick) {
        val v = vibrator ?: return
        runCatching {
            when {
                // `EFFECT_TICK` est l'effet du système, calibré par le
                // constructeur : sur un moteur à masse rotative comme sur un
                // moteur linéaire, il rend le petit cran attendu. Une durée en
                // millisecondes, elle, donne un bourdonnement sur l'un et un
                // claquement sur l'autre.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && kind == Tick.STEP ->
                    v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && kind == Tick.PRESS ->
                    v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> v.vibrate(effect(kind))

                else -> {
                    @Suppress("DEPRECATION")
                    when (kind) {
                        Tick.STEP -> v.vibrate(12)
                        Tick.PRESS -> v.vibrate(24)
                        Tick.BACK -> v.vibrate(longArrayOf(0, 10, 40, 16), -1)
                    }
                }
            }
        }
    }

    private fun effect(kind: Tick): VibrationEffect = when (kind) {
        Tick.STEP -> VibrationEffect.createOneShot(12, 90)
        Tick.PRESS -> VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE)
        // Deux temps : Retour est la seule touche qu'on regrette d'avoir
        // appuyée, elle doit se reconnaître d'un autre motif.
        Tick.BACK -> VibrationEffect.createWaveform(longArrayOf(0, 10, 40, 16), -1)
    }
}
