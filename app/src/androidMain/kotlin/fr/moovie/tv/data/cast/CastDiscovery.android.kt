package fr.moovie.tv.data.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import fr.moovie.tv.data.net.NsdGate
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
        NsdGate.balayant { balaye(timeoutMs) }
    }

    private suspend fun balaye(timeoutMs: Long): List<CastDevice> {
        val manager = nsd ?: run {
            CastScan.rapporte(demarre = false, annonces = 0, resolus = 0)
            return emptyList()
        }
        val trouves = mutableMapOf<String, CastDevice>()

        // **Résolutions sérialisées.** Avant Android 12, `resolveService` ne
        // supporte pas deux appels simultanés et rend `FAILURE_ALREADY_ACTIVE`
        // sur le second. Or un salon avec un Chromecast et une enceinte Nest en
        // a déjà deux : sans ce verrou, l'un des deux disparaît de la liste, et
        // rien ne dit lequel ni pourquoi.
        //
        // Le verrou est celui de [NsdGate], **partagé avec la découverte des
        // téléviseurs Moo-vie** : chacune avait le sien, ce qui les rendait
        // correctes séparément et fausses ensemble.
        suspend fun resoud(info: NsdServiceInfo) = NsdGate.resolvant {
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
        // **Un refus de démarrer se dit.** C'était un `Unit` : la pile pouvait
        // refuser le balayage, on attendait quatre secondes et on rendait une
        // liste vide, impossible à distinguer d'un réseau sans Chromecast. Voir
        // CastScanReport pour ce que cette distinction sert à trancher.
        // Atomique et non `@Volatile` : le rappel arrive sur le fil de la pile
        // NSD, la lecture se fait ici après l'attente. Sans barrière mémoire, un
        // refus pourrait rester invisible — précisément ce qu'on cherche à voir.
        val refus = java.util.concurrent.atomic.AtomicBoolean(false)
        val parcours = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                refus.set(true)
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(vus) { vus += info }
            }
        }

        runCatching {
            manager.discoverServices(CAST_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, parcours)
        }.onFailure {
            CastScan.rapporte(demarre = false, annonces = 0, resolus = 0)
            return emptyList()
        }

        // On laisse courir, puis on résout : résoudre pendant la découverte fait
        // tourner les deux mécanismes ensemble, ce que la pile supporte mal.
        delay(timeoutMs)
        runCatching { manager.stopServiceDiscovery(parcours) }

        val annonces = synchronized(vus) { vus.toList() }
        annonces.forEach { resoud(it) }
        CastScan.rapporte(
            demarre = !refus.get(),
            annonces = annonces.size,
            resolus = trouves.size,
        )
        return trouves.values.sortedBy { it.name.lowercase() }
    }

    /**
     * Deux secondes suffisaient sur un réseau sain et pas sur un réseau chargé,
     * où un Chromecast vu mais non résolu est un Chromecast perdu jusqu'au
     * balayage suivant. Le coût d'attendre plus n'est payé que par les appareils
     * qui ne répondent pas, et il n'y en a jamais beaucoup.
     */
    private const val RESOLUTION_MS = 4_000L
}
