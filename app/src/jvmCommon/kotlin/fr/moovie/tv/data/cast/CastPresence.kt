package fr.moovie.tv.data.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Les récepteurs Cast vus sur le réseau, **maintenant**.
 *
 * ## Pourquoi un état global et pas une découverte à la demande
 *
 * Le bouton « diffuser » doit exister ou non **avant** qu'on le touche. Or une
 * découverte mDNS prend quelques secondes : la lancer au moment du geste ferait
 * attendre devant un bouton qu'on vient d'appuyer, ou pire, ouvrirait une liste
 * vide qui se remplit après coup.
 *
 * Même motif et même raison que [fr.moovie.tv.data.remote.RemotePresence] : on
 * sonde périodiquement, l'écran lit le résultat. Ici la sonde est plus lourde —
 * un balayage mDNS, pas un ping — donc plus espacée.
 *
 * ## Ce qui disparaît n'est pas oublié tout de suite
 *
 * Un balayage qui ne trouve rien **n'efface pas** la liste précédente du premier
 * coup. Un Chromecast peut manquer une annonce sans être éteint, et le voir
 * s'effacer de la liste au milieu d'un choix serait la pire des surprises. Il
 * faut [OUBLIS_AVANT_ABSENCE] balayages muets d'affilée — la même discipline que
 * les silences du mini-lecteur.
 */
object CastPresence {

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())

    /** Ce qu'on a trouvé au dernier balayage concluant. */
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val balayage = Mutex()
    private var muets = 0

    /**
     * Cherche, et met la liste à jour. Ne lève jamais : au pire il n'y a rien,
     * ce qui est une réponse.
     */
    suspend fun refresh(timeoutMs: Long = 4_000) = balayage.withLock {
        val trouves = runCatching { CastDiscovery.discover(timeoutMs) }.getOrDefault(emptyList())
        if (trouves.isNotEmpty()) {
            muets = 0
            _devices.value = trouves
            return@withLock
        }
        muets++
        if (muets >= OUBLIS_AVANT_ABSENCE) _devices.value = emptyList()
    }

    /** Vide la liste sans attendre — à l'oubli d'un réseau, par exemple. */
    fun forget() {
        muets = 0
        _devices.value = emptyList()
    }

    /**
     * Deux balayages muets d'affilée. Un seul ne prouve rien : une annonce mDNS
     * se perd comme n'importe quel paquet.
     */
    private const val OUBLIS_AVANT_ABSENCE = 2
}
