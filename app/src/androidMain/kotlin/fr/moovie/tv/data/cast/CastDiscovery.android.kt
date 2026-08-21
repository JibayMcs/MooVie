package fr.moovie.tv.data.cast

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
 * Découverte des récepteurs Cast par `NsdManager`, le mDNS d'Android.
 *
 * Le même mécanisme que [fr.moovie.tv.data.remote.RemoteBeacons], avec ses
 * contraintes — dont une qui coûte cher si on l'ignore : voir la sérialisation
 * des résolutions plus bas.
 */
actual object CastDiscovery {

    private val nsd: NsdManager?
        get() = runCatching {
            appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        }.getOrNull()

    /** Un balayage à la fois : deux découvertes simultanées se gênent. */
    private val scanning = Mutex()

    actual suspend fun discover(timeoutMs: Long): List<CastDevice> = scanning.withLock {
        val manager = nsd ?: return emptyList()
        val trouves = mutableMapOf<String, CastDevice>()

        // **Résolutions sérialisées.** Avant Android 12, `resolveService` ne
        // supporte pas deux appels simultanés et rend `FAILURE_ALREADY_ACTIVE`
        // sur le second. Or un salon avec un Chromecast et une enceinte Nest en
        // a déjà deux : sans ce verrou, l'un des deux disparaît de la liste, et
        // rien ne dit lequel ni pourquoi.
        val resolution = Mutex()

        suspend fun resoud(info: NsdServiceInfo) = resolution.withLock {
            val fini = CompletableDeferred<CastDevice?>()
            val ecoute = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) = fini.complete(null).let {}

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val hote = info.host?.hostAddress
                    if (hote.isNullOrBlank()) {
                        fini.complete(null)
                        return
                    }
                    // Les TXT arrivent en octets bruts : c'est là que vit le nom
                    // que l'utilisateur a donné à son appareil.
                    val attributs = runCatching {
                        info.attributes.mapValues { (_, v) -> v?.toString(Charsets.UTF_8) }
                    }.getOrDefault(emptyMap())

                    fini.complete(
                        CastDevice(
                            name = castFriendlyName(attributs, repli = info.serviceName.orEmpty()),
                            host = hote,
                            port = info.port.takeIf { it > 0 } ?: CastTls.PORT,
                            model = attributs["md"].orEmpty(),
                        ),
                    )
                }
            }
            runCatching { manager.resolveService(info, ecoute) }
                .onFailure { fini.complete(null) }
            withTimeoutOrNull(RESOLUTION_MS) { fini.await() }?.let { trouves[it.host] = it }
        }

        val vus = mutableListOf<NsdServiceInfo>()
        val parcours = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(vus) { vus += info }
            }
        }

        runCatching {
            manager.discoverServices(CAST_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, parcours)
        }.onFailure { return emptyList() }

        // On laisse courir, puis on résout : résoudre pendant la découverte fait
        // tourner les deux mécanismes ensemble, ce que la pile supporte mal.
        delay(timeoutMs)
        runCatching { manager.stopServiceDiscovery(parcours) }

        synchronized(vus) { vus.toList() }.forEach { resoud(it) }
        return trouves.values.sortedBy { it.name.lowercase() }
    }

    private const val RESOLUTION_MS = 2_000L
}
