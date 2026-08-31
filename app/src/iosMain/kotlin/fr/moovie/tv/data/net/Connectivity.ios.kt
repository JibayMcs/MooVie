package fr.moovie.tv.data.net

import fr.moovie.tv.shared.Verrou
import fr.moovie.tv.shared.avec
import fr.moovie.tv.shared.dispatcherEs
import io.ktor.client.request.head
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Une sonde périodique, comme sur desktop — et **pas** `NWPathMonitor`.
 *
 * Le choix mérite d'être justifié parce que le framework Network existe et
 * qu'on aurait pu l'appeler. Mais il répond à la question que la documentation
 * de [Connectivity] écarte d'emblée : il dit qu'une interface est *satisfaite*,
 * pas qu'elle sort. Un iPhone accroché au Wi-Fi d'un hôtel derrière un portail
 * captif est « satisfait », et l'application afficherait un accueil vide au
 * lieu de sa bibliothèque hors ligne.
 *
 * On teste donc ce dont l'application a réellement besoin, exactement comme le
 * desktop : joindre l'hôte de TMDB. Une requête HEAD plutôt qu'une socket TCP —
 * Kotlin/Native n'expose pas de socket brute et NSURLSession fait déjà tout ce
 * qu'il faut — ce qui éprouve DNS, route et présence du serveur sans
 * transférer de corps ni consommer de quota.
 *
 * Les deux cadences sont celles du desktop, pour la même raison : perdre le
 * réseau peut attendre le prochain relevé, le **retour** est ce qu'on attend
 * devant l'écran.
 */
actual object Connectivity {

    private val _online = MutableStateFlow(true)
    actual val online: StateFlow<Boolean> = _online.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + dispatcherEs)

    /**
     * Client dédié, comme côté desktop où le client OkHttp de l'app n'est
     * volontairement pas réutilisé : celui de l'extraction porte des réglages
     * qui testeraient autre chose que ce qu'on veut savoir ici.
     */
    private val sonde: HttpClient by lazy {
        HttpClient(Darwin) {
            expectSuccess = false
            install(HttpTimeout) { requestTimeoutMillis = DELAI_MS }
        }
    }

    private val verrou = Verrou()

    @Volatile
    private var demarree = false

    /** Réveille le relevé en cours d'attente : c'est le bouton « Réessayer ». */
    private val reveil = MutableStateFlow(0)

    actual fun start() {
        verrou.avec {
            if (demarree) return
            demarree = true
        }
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

    /**
     * N'importe quelle réponse vaut « en ligne », y compris un 4xx : ce qu'on
     * mesure est la capacité à *atteindre* l'hôte, pas son humeur. Seule une
     * exception — DNS muet, route absente, délai dépassé — signifie hors ligne.
     */
    private suspend fun joignable(): Boolean = runCatching {
        // Le plafond est celui du client : `HttpTimeout` y est installé avec
        // `requestTimeoutMillis`, inutile de le répéter par requête.
        sonde.head("https://$HOTE/")
        true
    }.getOrDefault(false)

    /** L'hôte dont dépend l'accueil : le tester, c'est tester ce qui compte. */
    private const val HOTE = "api.themoviedb.org"
    private const val DELAI_MS = 2_500L

    private const val PERIODE_EN_LIGNE = 60_000L
    private const val PERIODE_HORS_LIGNE = 10_000L
    private const val PAS_ATTENTE = 1_000L
}
