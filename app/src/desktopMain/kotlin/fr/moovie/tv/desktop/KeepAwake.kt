package fr.moovie.tv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sun.jna.Library
import com.sun.jna.Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Empêche l'ordinateur de s'endormir pendant la lecture.
 *
 * ### Le défaut
 *
 * Sur Windows, la machine se mettait en veille au bout d'un moment **en pleine
 * lecture**, plein écran ou non. Rien d'anormal de son point de vue : regarder
 * une vidéo n'est pas une activité utilisateur. Sans clavier ni souris, le
 * compteur d'inactivité court, et le système fait ce qu'on lui a demandé.
 *
 * Android règle ça depuis longtemps (`keepScreenOn` lié à `isPlaying`, plus
 * `setWakeMode`). Le desktop n'avait aucun équivalent : la JVM ne sait rien dire
 * au gestionnaire d'énergie.
 *
 * ### Deux systèmes, deux formes, et c'est irréductible
 *
 * **Windows n'a rien à tenir.** `SetThreadExecutionState` sans `ES_CONTINUOUS`
 * remet simplement le compteur d'inactivité à zéro ; Microsoft documente cette
 * forme pour les lecteurs vidéo, et il suffit de la répéter. La variante
 * `ES_CONTINUOUS`, elle, pose un état attaché **au fil appelant** que la mort de
 * ce fil annule : depuis une coroutine, dont le fil change au gré du
 * répartiteur, le verrou tomberait tout seul à un moment imprévisible, ce qui
 * est pire qu'un défaut franc.
 *
 * **Linux, au contraire, tient l'inhibition tant que le client vit.** D'où un
 * processus fils gardé le temps de la lecture, et tué en la quittant. La voie
 * D-Bus directe (`org.freedesktop.ScreenSaver`) a été essayée d'abord et
 * écartée : `SimulateUserActivity` répond « method not implemented » sur les
 * bureaux mesurés, et `Inhibit` se relâche à la déconnexion du client, donc
 * aussitôt qu'un `dbus-send` se termine.
 *
 * ### Ce qui n'est pas couvert
 *
 * macOS. La voie propre y passe par `IOPMAssertionCreateWithName`, et je n'ai
 * pas de machine pour l'éprouver. Livrer un appel système jamais vu tourner
 * serait pire que l'absence, qui au moins se remarque.
 */
@Composable
fun KeepAwakeWhile(active: Boolean) {
    LaunchedEffect(active) {
        if (!active || flavour == Flavour.UNKNOWN) return@LaunchedEffect

        // Linux : l'inhibition vit avec ce fils. Windows : rien à tenir.
        val held = withContext(Dispatchers.IO) { runCatching { hold() }.getOrNull() }
        try {
            while (true) {
                withContext(Dispatchers.IO) { runCatching { nudge() } }
                delay(NUDGE_INTERVAL_MS)
            }
        } finally {
            // Rendre la main est aussi important que la prendre : sans ça, la
            // machine ne se rendormirait plus jamais après une seule lecture.
            held?.destroy()
        }
    }
}

/**
 * Un intervalle confortablement sous le délai de veille le plus court qu'un
 * système accepte de configurer, qui se compte en minutes.
 */
private const val NUDGE_INTERVAL_MS = 30_000L

/**
 * Durée maximale d'une inhibition Linux.
 *
 * Bornée plutôt qu'infinie : si le processus fils survivait à un arrêt brutal de
 * l'application, une machine resterait éveillée pour toujours. Vingt-quatre
 * heures dépassent largement le plus long des films, et une fuite se soigne
 * d'elle-même en un jour.
 */
private const val INHIBIT_SECONDS = 86_400

private enum class Flavour { WINDOWS, LINUX, UNKNOWN }

private val flavour: Flavour by lazy {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    when {
        "win" in os -> Flavour.WINDOWS
        "linux" in os || "bsd" in os -> Flavour.LINUX
        else -> Flavour.UNKNOWN
    }
}

/** Prend l'inhibition qui doit être *tenue*, ou null s'il n'y en a pas. */
private fun hold(): Process? = when (flavour) {
    Flavour.LINUX -> ProcessBuilder(
        "systemd-inhibit",
        "--what=idle:sleep",
        "--who=Moo-vie",
        "--why=Lecture en cours",
        "--mode=block",
        "sleep", INHIBIT_SECONDS.toString(),
    ).redirectErrorStream(true).start()

    else -> null
}

/** Repousse la veille d'un cran, là où elle se repousse. */
private fun nudge() {
    if (flavour != Flavour.WINDOWS) return
    Kernel32.INSTANCE.SetThreadExecutionState(ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED)
}

/** `ES_SYSTEM_REQUIRED` : la machine ne se met pas en veille. */
private const val ES_SYSTEM_REQUIRED = 0x00000001

/** `ES_DISPLAY_REQUIRED` : l'écran ne s'éteint pas non plus. */
private const val ES_DISPLAY_REQUIRED = 0x00000002

/**
 * Le strict minimum de `kernel32`, déclaré à la main.
 *
 * `jna-platform` porte déjà un `Kernel32` complet, mais dépendre de sa surface
 * d'API pour une seule fonction attache le correctif à ses évolutions. Trois
 * lignes ici ne tiennent qu'au noyau de JNA, que vlcj embarque de toute façon.
 */
private interface Kernel32 : Library {
    fun SetThreadExecutionState(esFlags: Int): Int

    companion object {
        val INSTANCE: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
    }
}
