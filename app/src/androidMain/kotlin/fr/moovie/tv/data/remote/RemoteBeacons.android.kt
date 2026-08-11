package fr.moovie.tv.data.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import fr.moovie.tv.data.store.appContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Annonce et découverte via `NsdManager`, le mDNS d'Android.
 *
 * Tout est enveloppé de `runCatching` : la pile NSD lève sur des états que rien
 * n'annonce — un enregistrement déjà en cours, un balayage arrêté deux fois,
 * un Wi-Fi qui tombe entre l'appel et le rappel. Aucun de ces cas ne mérite de
 * faire tomber l'application : au pire la télécommande ne se trouve pas, et
 * l'appairage par QR reste là.
 */
actual object RemoteBeacons {

    private val nsd: NsdManager?
        get() = runCatching {
            appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        }.getOrNull()

    private var registration: NsdManager.RegistrationListener? = null

    /** Un seul balayage à la fois : deux en parallèle se marchent dessus. */
    private val scanning = Mutex()

    actual fun advertise(name: String, port: Int) {
        stopAdvertising()
        val manager = nsd ?: return
        val info = NsdServiceInfo().apply {
            // Le nom est indicatif : Android le suffixe tout seul en cas de
            // collision (« Salon (2) »), ce qui est exactement ce qu'on veut.
            serviceName = name
            serviceType = REMOTE_SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        runCatching {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            registration = listener
        }
    }

    actual fun stopAdvertising() {
        val manager = nsd ?: return
        registration?.let { runCatching { manager.unregisterService(it) } }
        registration = null
    }

    actual suspend fun discover(timeoutMs: Long): List<RemoteBeacon> = scanning.withLock {
        val manager = nsd ?: return emptyList()
        val found = mutableMapOf<String, RemoteBeacon>()

        // La résolution est sérialisée à la main : sur les versions antérieures
        // à Android 12, `resolveService` ne supporte pas deux appels
        // simultanés et rend FAILURE_ALREADY_ACTIVE sur le second. Deux
        // téléviseurs sur le réseau suffisaient à en perdre un.
        val resolveLock = Mutex()

        suspend fun resolve(info: NsdServiceInfo) = resolveLock.withLock {
            val done = CompletableDeferred<RemoteBeacon?>()
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                    done.complete(null)
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress
                    done.complete(
                        if (host.isNullOrBlank()) null
                        else RemoteBeacon(info.serviceName.orEmpty(), host, info.port),
                    )
                }
            }
            runCatching { manager.resolveService(info, listener) }
                .onFailure { done.complete(null) }
            withTimeoutOrNull(1_500) { done.await() }?.let { found[it.host] = it }
        }

        val pending = mutableListOf<NsdServiceInfo>()
        val browser = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(pending) { pending += info }
            }
        }

        runCatching {
            manager.discoverServices(REMOTE_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, browser)
        }.onFailure { return emptyList() }

        // On laisse le balayage courir, puis on résout ce qu'il a vu. Résoudre
        // pendant la découverte ferait tourner les deux mécanismes en même
        // temps, ce que la pile supporte mal sur les anciennes versions.
        delay(timeoutMs)
        runCatching { manager.stopServiceDiscovery(browser) }

        val seen = synchronized(pending) { pending.toList() }
        seen.forEach { resolve(it) }
        return found.values.toList()
    }
}
