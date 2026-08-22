package fr.moovie.tv.data.cast

import kotlinx.coroutines.delay
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

    private val _moovieHosts = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Adresses qui annoncent **Moo-vie** sur le réseau, appairées ou non.
     *
     * Beaucoup d'Android TV répondent aussi au protocole Cast : sans cette
     * liste, une box qui fait tourner Moo-vie apparaîtrait comme un Chromecast,
     * par un chemin qui échoue chez elle. Voir `castTargetsFor`.
     */
    val moovieHosts: StateFlow<Set<String>> = _moovieHosts.asStateFlow()

    private val balayage = Mutex()
    private var muets = 0

    /**
     * Cherche, et met la liste à jour. Ne lève jamais : au pire il n'y a rien,
     * ce qui est une réponse.
     */
    suspend fun refresh(timeoutMs: Long = 4_000) = balayage.withLock {
        // Les deux annonces dans le même balayage : ce sont deux services mDNS,
        // et les chercher séparément doublerait le réveil de la radio.
        runCatching { fr.moovie.tv.data.remote.RemoteBeacons.discover(timeoutMs) }
            .getOrDefault(emptyList())
            .map { it.host }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { _moovieHosts.value = it }

        val trouves = runCatching { CastDiscovery.discover(timeoutMs) }.getOrDefault(emptyList())
        if (trouves.isNotEmpty()) {
            muets = 0
            _devices.value = trouves
            return@withLock
        }
        muets++
        if (muets >= OUBLIS_AVANT_ABSENCE) _devices.value = emptyList()
    }

    /**
     * Balaye en boucle, tant que l'application est au premier plan.
     *
     * ## Le défaut que ça corrige
     *
     * La veille ne tournait **que sur une fiche de titre**, à cadence fixe de
     * vingt secondes. Trois conséquences, toutes rapportées par un utilisateur
     * qui n'a jamais vu le bouton apparaître :
     *
     * - ouvrir l'application et rester ailleurs ne cherchait **rien** — ni sur
     *   l'accueil, ni dans le lecteur, où le bouton manque justement ;
     * - une annonce mDNS perdue — c'est du multicast, ça se perd comme tout le
     *   reste — coûtait vingt secondes avant la tentative suivante ;
     * - la première recherche partait au moment où la fiche s'affichait, donc
     *   plus tard que le premier regard porté sur la barre d'actions.
     *
     * ## La cadence
     *
     * Serrée tant qu'on n'a rien, lâche dès qu'on a trouvé. Chercher est ce qui
     * coûte : une fois le récepteur connu, on ne balaye plus que pour vérifier
     * qu'il est toujours là, et [OUBLIS_AVANT_ABSENCE] fait qu'il faut deux
     * échecs d'affilée pour le retirer — un appareil ne clignote donc pas.
     */
    suspend fun veille() {
        var passages = 0
        while (true) {
            runCatching { refresh() }
            passages++
            delay(
                when {
                    _devices.value.isNotEmpty() -> RELANCE_TROUVE_MS
                    passages < PASSAGES_SERRES -> RELANCE_SERREE_MS
                    else -> RELANCE_LARGE_MS
                },
            )
        }
    }

    /** Vide la liste sans attendre — à l'oubli d'un réseau, par exemple. */
    fun forget() {
        muets = 0
        _devices.value = emptyList()
        _moovieHosts.value = emptySet()
    }

    /**
     * Deux balayages muets d'affilée. Un seul ne prouve rien : une annonce mDNS
     * se perd comme n'importe quel paquet.
     */
    private const val OUBLIS_AVANT_ABSENCE = 2

    /**
     * Les premiers passages s'enchaînent presque sans pause : un balayage dure
     * déjà quatre secondes, et c'est pendant les premières secondes après
     * l'ouverture qu'on veut que le bouton apparaisse.
     */
    private const val PASSAGES_SERRES = 3
    private const val RELANCE_SERREE_MS = 2_000L

    /** Rien trouvé après les passages serrés : on ralentit sans abandonner. */
    private const val RELANCE_LARGE_MS = 20_000L

    /**
     * Un récepteur est connu : on ne balaye plus que pour vérifier qu'il est
     * toujours là. C'est le cas de loin le plus long, donc celui qui décide de
     * ce que la veille coûte en batterie.
     */
    private const val RELANCE_TROUVE_MS = 60_000L
}
