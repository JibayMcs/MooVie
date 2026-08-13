package fr.moovie.tv.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import fr.moovie.tv.data.store.appContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La réponse du système, sans sonde à nous.
 *
 * Android teste lui-même la sortie de chaque réseau qu'il monte et publie le
 * résultat sous `NET_CAPABILITY_VALIDATED`. Rien de ce qu'on écrirait ne ferait
 * mieux : c'est la même mesure que celle du point d'exclamation sur l'icône
 * Wi-Fi, faite une fois pour tout l'appareil, et elle couvre le portail captif
 * comme la box débranchée.
 *
 * Un **ensemble** de réseaux plutôt qu'un booléen : téléphone en Wi-Fi et en
 * données mobiles à la fois, perdre l'un ne veut pas dire perdre l'accès. Sans
 * ce décompte, quitter la portée du Wi-Fi basculait l'application en mode hors
 * ligne alors que la 4G avait déjà pris le relais.
 */
actual object Connectivity {

    private val _online = MutableStateFlow(true)
    actual val online: StateFlow<Boolean> = _online.asStateFlow()

    /** Réseaux actuellement validés. Gardé sous verrou : les rappels arrivent d'un fil système. */
    private val valides = mutableSetOf<Network>()

    @Volatile
    private var demarree = false

    private val rappel = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            maj(network, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }

        override fun onLost(network: Network) = maj(network, false)

        override fun onUnavailable() {
            synchronized(valides) {
                valides.clear()
                _online.value = false
            }
        }
    }

    @Synchronized
    actual fun start() {
        if (demarree) return
        val manager = manager() ?: return
        val requete = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        // `registerNetworkCallback` et non `registerDefaultNetworkCallback` :
        // le second n'existe qu'à partir de l'API 24, et minSdk vaut 23.
        val pose = runCatching { manager.registerNetworkCallback(requete, rappel) }.isSuccess
        if (!pose) return
        demarree = true
        recheck()
    }

    actual fun recheck() {
        val manager = manager() ?: return
        // Le rappel dit l'avenir, pas le présent : au démarrage il ne se
        // déclenche que si quelque chose bouge. On lit donc l'état courant une
        // fois, sans quoi une application lancée sur un réseau déjà établi
        // attendrait le prochain changement pour se croire en ligne.
        val actif = runCatching {
            @Suppress("DEPRECATION")
            manager.activeNetwork
        }.getOrNull()
        val caps = actif?.let { runCatching { manager.getNetworkCapabilities(it) }.getOrNull() }
        val valide = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        synchronized(valides) {
            if (valide && actif != null) valides.add(actif) else actif?.let(valides::remove)
            _online.value = valide || valides.isNotEmpty()
        }
    }

    private fun maj(network: Network, valide: Boolean) {
        synchronized(valides) {
            if (valide) valides.add(network) else valides.remove(network)
            _online.value = valides.isNotEmpty()
        }
    }

    private fun manager(): ConnectivityManager? = runCatching {
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()
}
