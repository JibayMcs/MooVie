package fr.moovie.tv.data.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Une sonde à nous, faute d'équivalent en JVM pure.
 *
 * Java sait dire qu'une interface est active (`NetworkInterface.isUp`) et rien
 * de plus : ni si elle route, ni si elle sort. Sur un poste avec une interface
 * virtuelle — Docker, VPN éteint, machine virtuelle — la réponse est oui en
 * permanence, ce qui en fait un signal inutilisable. On teste donc ce dont
 * l'application a réellement besoin.
 *
 * ### Une connexion, pas une requête HTTP
 *
 * Ouvrir une socket TCP vers TMDB suffit : on éprouve le DNS, la route et la
 * présence du serveur, sans transférer d'octets ni consommer de quota. Le
 * client OkHttp de l'application n'est volontairement pas réutilisé — le sien
 * passe par DoH pour l'extraction, et un résolveur chiffré teste autre chose
 * que ce qu'on veut savoir ici.
 *
 * ### Deux cadences
 *
 * Une minute en ligne, dix secondes hors ligne. L'asymétrie est voulue : perdre
 * le réseau peut attendre le prochain relevé — rien n'est en train d'échouer
 * puisque tout marchait — alors que le **retour** est ce qu'on attend devant
 * l'écran, et une minute d'attente devant un écran qui a déjà tort est longue.
 */
actual object Connectivity {

    private val _online = MutableStateFlow(true)
    actual val online: StateFlow<Boolean> = _online.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var demarree = false

    /** Réveille le relevé en cours d'attente : c'est le bouton « Réessayer ». */
    private val reveil = MutableStateFlow(0)

    @Synchronized
    actual fun start() {
        if (demarree) return
        demarree = true
        scope.launch {
            while (isActive) {
                _online.value = joignable()
                val attente = if (_online.value) PERIODE_EN_LIGNE else PERIODE_HORS_LIGNE
                // Attente découpée : un « Réessayer » ne doit pas patienter
                // jusqu'au bout d'une minute pour être entendu.
                val debut = reveil.value
                var reste = attente
                while (reste > 0 && reveil.value == debut) {
                    delay(minOf(reste, PAS_ATTENTE))
                    reste -= PAS_ATTENTE
                }
            }
        }
    }

    actual fun recheck() {
        reveil.value += 1
        if (!demarree) start()
    }

    private suspend fun joignable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HOTE, PORT), DELAI_MS)
                true
            }
        }.getOrDefault(false)
    }

    /** L'hôte dont dépend l'accueil : le tester, c'est tester ce qui compte. */
    private const val HOTE = "api.themoviedb.org"
    private const val PORT = 443
    private const val DELAI_MS = 2_500

    private const val PERIODE_EN_LIGNE = 60_000L
    private const val PERIODE_HORS_LIGNE = 10_000L
    private const val PAS_ATTENTE = 1_000L
}
