package fr.moovie.tv.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Le téléviseur appairé est-il là, **maintenant** ?
 *
 * ### Pourquoi une détection, et pas la simple mémoire d'un appairage
 *
 * Une cible mémorisée ne prouve rien : elle survit à un téléviseur débranché, à
 * un déménagement, à un appairage raté. Conditionner l'accès à la télécommande à
 * sa seule présence dans le magasin faisait apparaître un bouton qui ouvrait un
 * écran incapable d'envoyer quoi que ce soit — et sans rien en dire, puisqu'une
 * touche perdue est silencieuse par construction ([RemoteClient]).
 *
 * ### L'ordre des deux moyens n'est pas indifférent
 *
 * 1. **La sonde sur l'adresse mémorisée d'abord.** C'est le cas ordinaire, il
 *    coûte un aller-retour d'une seconde au pire, et quand il répond il n'y a
 *    rien d'autre à faire.
 * 2. **La découverte mDNS ensuite**, et seulement en cas d'échec. Elle dure
 *    trois secondes de radio ; les dépenser à chaque retour au premier plan
 *    alors que l'adresse est encore bonne serait du gaspillage pur.
 *
 * Ce que la découverte rattrape est exactement ce que le QR ne peut pas garder à
 * jour : l'adresse, qui bouge au renouvellement du bail DHCP, et le port, qui
 * change à chaque démarrage de l'application sur le téléviseur. Jamais le jeton
 * — il ne circule pas sur le réseau, et [RemoteTargetRepository.relocate] est
 * écrite pour ne pas pouvoir y toucher.
 *
 * Un objet global : la barre de navigation, l'écran de télécommande et le rappel
 * de reprise d'`Activity` s'en servent tous, et n'ont aucune raison de se
 * connaître.
 */
object RemotePresence {

    private val _found = MutableStateFlow(false)

    /** Vrai depuis le dernier [refresh] concluant. Faux par défaut : on ne suppose rien. */
    val found: StateFlow<Boolean> = _found.asStateFlow()

    /**
     * Un balayage à la fois. Deux `refresh` concurrents — la reprise de
     * l'`Activity` et l'ouverture de l'écran arrivent ensemble — lanceraient
     * deux découvertes mDNS, que la pile NSD supporte mal.
     */
    private val probing = Mutex()

    /**
     * Cherche le téléviseur, met à jour son adresse si elle a bougé, et rend
     * s'il a répondu.
     *
     * Ne lève jamais : au pire il n'est pas là, ce qui est une réponse.
     */
    suspend fun refresh(): Boolean = probing.withLock {
        val repo = RemoteTargetRepository()
        val target = repo.target.first()
        if (target == null) {
            _found.value = false
            return@withLock false
        }

        if (RemoteClient(target).ping()) {
            _found.value = true
            return@withLock true
        }

        val beacons = runCatching { RemoteBeacons.discover() }.getOrDefault(emptyList())
        val beacon = pickBeacon(beacons, target.name)
        if (beacon == null) {
            _found.value = false
            return@withLock false
        }

        repo.relocate(beacon.host, beacon.port)
        val ok = RemoteClient(target.copy(host = beacon.host, port = beacon.port)).ping()
        _found.value = ok
        return@withLock ok
    }

    /**
     * Le téléviseur vient de ne pas répondre à une touche.
     *
     * Appelé par l'écran plutôt que déduit d'une sonde : une touche perdue est
     * la première chose qu'on remarque, bien avant le prochain balayage.
     */
    fun lost() {
        _found.value = false
    }
}

/**
 * Laquelle des balises trouvées est le téléviseur appairé ?
 *
 * **Aucune, plutôt qu'une mauvaise.** Le salon peut avoir deux Moo-vie, ou le
 * voisin partager le Wi-Fi ; réapprendre l'adresse sur le mauvais appareil
 * enverrait les touches ailleurs, et rien à l'écran ne dirait pourquoi la
 * navigation part toute seule. C'est le seul endroit où cette décision se prend,
 * d'où une fonction pure : c'est aussi le seul endroit où elle se teste.
 *
 * Trois règles, de la plus sûre à la plus permissive :
 *
 * 1. **Le nom exact.** Le cas normal.
 * 2. **Le nom préfixé.** Android suffixe le nom annoncé en cas de collision
 *    (« Salon (2) ») : c'est bien le même appareil.
 * 3. **Un candidat unique**, et seulement s'il est unique. C'est ce qui rattrape
 *    un téléviseur renommé, où le nom mémorisé ne colle plus à rien — et ce qui
 *    ne rattrape rien du tout dès qu'il y a une ambiguïté à trancher.
 */
internal fun pickBeacon(beacons: List<RemoteBeacon>, name: String): RemoteBeacon? =
    beacons.firstOrNull { it.name == name }
        ?: beacons.firstOrNull { it.name.startsWith(name) }
        ?: beacons.singleOrNull()
