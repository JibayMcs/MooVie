package fr.moovie.tv.data.net

import android.content.Context
import android.net.wifi.WifiManager
import fr.moovie.tv.data.store.appContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Le point de passage unique de tout ce que l'application fait en mDNS.
 *
 * ## Le défaut que ça corrige
 *
 * Deux objets balaient le réseau — [fr.moovie.tv.data.remote.RemoteBeacons] pour
 * les téléviseurs Moo-vie, [fr.moovie.tv.data.cast.CastDiscovery] pour les
 * Chromecast — et **chacun sérialisait ses résolutions avec son propre verrou**.
 * Chacun était donc correct isolément, et les deux ensemble ne l'étaient pas :
 * avant Android 12, `resolveService` ne supporte pas deux appels simultanés et
 * rend `FAILURE_ALREADY_ACTIVE` sur le second.
 *
 * Or les deux balayages tournent en même temps — l'un depuis le bouton flottant
 * de télécommande, l'autre depuis la fiche — et la collision se solde par un
 * `onResolveFailed` qui ne journalise rien. Le Chromecast disparaît de la liste
 * un balayage sur deux, sans que rien ne le dise. Les deux fichiers portaient la
 * leçon en commentaire ; il leur manquait de partager le verrou.
 *
 * ## Le voile multicast
 *
 * mDNS est du multicast, et beaucoup de puces Wi-Fi le filtrent en veille pour
 * économiser la batterie. `MulticastLock` lève ce filtre. Le service système
 * pose le sien, ce qui explique que la découverte marche sur la plupart des
 * appareils sans rien demander — mais pas sur ceux dont le constructeur serre
 * l'économie d'énergie, et là **rien ne distingue le filtrage d'un réseau vide**.
 * Le verrou coûte quelques milliampères le temps d'un balayage.
 */
object NsdGate {

    /**
     * Le verrou de résolution, **partagé par tous les balayages**.
     *
     * Il ne protège pas la découverte, qui supporte d'être multiple : seulement
     * `resolveService`, qui est la partie fragile.
     */
    private val resolution = Mutex()

    suspend fun <T> resolvant(bloc: suspend () -> T): T = resolution.withLock { bloc() }

    private val wifi: WifiManager?
        get() = runCatching {
            appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        }.getOrNull()

    /**
     * Exécute un balayage en tenant le filtre multicast levé.
     *
     * Le verrou est **compté** : deux balayages simultanés en prennent chacun un,
     * et le filtre ne retombe qu'au dernier relâché. Tout est enveloppé parce que
     * la pile Wi-Fi lève sur des états que rien n'annonce — c'est la discipline
     * déjà appliquée à NSD dans les deux découvertes.
     */
    suspend fun <T> balayant(bloc: suspend () -> T): T {
        val verrou = runCatching {
            wifi?.createMulticastLock("moovie-mdns")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        }.getOrNull()
        return try {
            bloc()
        } finally {
            runCatching { verrou?.takeIf { it.isHeld }?.release() }
        }
    }
}
