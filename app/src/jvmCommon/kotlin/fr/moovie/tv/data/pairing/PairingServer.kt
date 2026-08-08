package fr.moovie.tv.data.pairing

import fr.moovie.tv.data.remote.RemoteKey
import fr.moovie.tv.data.remote.remoteAvailable
import fr.moovie.tv.data.remote.sendRemoteKey
import fr.moovie.tv.data.remote.sendRemoteText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.random.Random

/**
 * Serveur d'appairage : une page, servie sur le réseau local, le temps de la
 * saisie.
 *
 * Raison d'être : saisir une clé B2 de 31 caractères à la télécommande est le
 * pire moment de l'application. Le téléviseur affiche un QR code, le téléphone
 * l'ouvre, on tape au clavier tactile.
 *
 * **Aucune bibliothèque de serveur.** Une page et deux routes ne justifient ni
 * Ktor ni NanoHTTPD ; le protocole utilisé ici tient en une ligne de requête,
 * des en-têtes et un corps de formulaire.
 *
 * ### Ce qui le rend acceptable
 *
 * - Il ne vit **que** pendant que l'écran d'appairage est affiché — sauf si la
 *   télécommande a été armée, auquel cas il survit le temps qu'on navigue, et
 *   pas au-delà du premier plan. Voir [PairingSession], qui en est propriétaire.
 * - L'adresse porte un jeton tiré au hasard ; sans lui, tout est 404. Le jeton
 *   ne transite que par le QR code affiché sur le téléviseur : ne pas être
 *   devant l'écran, c'est ne pas avoir l'adresse.
 * - La page **n'affiche jamais une valeur existante**, seulement si elle est
 *   renseignée. La commodité de saisie ne doit pas devenir une fuite.
 *
 * Il n'y a pas de TLS : un certificat auto-signé ferait afficher au téléphone un
 * avertissement de sécurité, ce qui apprendrait exactement le mauvais réflexe
 * pour protéger un échange qui ne quitte pas le domicile.
 */
class PairingServer(
    private val fields: suspend () -> List<PairingField>,
    private val apply: suspend (Map<String, String>) -> Int,
    private val texts: PairingTexts,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val token = randomToken()
    private var socket: ServerSocket? = null

    private val _url = MutableStateFlow<String?>(null)
    /** Adresse à encoder dans le QR, null tant que le serveur n'écoute pas. */
    val url: StateFlow<String?> = _url.asStateFlow()

    private val _opened = MutableStateFlow(false)
    /** Vrai dès que la page a été chargée une fois : le téléphone a trouvé la TV. */
    val opened: StateFlow<Boolean> = _opened.asStateFlow()

    private val _saved = MutableStateFlow(0)
    /** Nombre cumulé de réglages enregistrés depuis le téléphone. */
    val saved: StateFlow<Int> = _saved.asStateFlow()

    private val _failure = MutableStateFlow<String?>(null)
    /** Renseigné quand le serveur n'a pas pu démarrer (pas de réseau, port pris). */
    val failure: StateFlow<String?> = _failure.asStateFlow()

    fun start() {
        scope.launch {
            val address = localAddress()
            if (address == null) {
                // Sans adresse de réseau local, il n'y a rien à afficher : un QR
                // pointant sur localhost enverrait le téléphone sur lui-même.
                _failure.value = "no-network"
                return@launch
            }
            val server = runCatching { ServerSocket(PREFERRED_PORT) }
                .recoverCatching { ServerSocket(0) } // port occupé : n'importe lequel fera l'affaire
                .getOrNull()
            if (server == null) {
                _failure.value = "no-socket"
                return@launch
            }
            socket = server
            _url.value = "http://$address:${server.localPort}/$token"

            while (isActive && !server.isClosed) {
                // accept() lève quand close() intervient : c'est la sortie
                // normale de la boucle, pas une panne à signaler.
                val client = runCatching { server.accept() }.getOrNull() ?: break
                launch { runCatching { handle(client) } }
            }
        }
    }

    /** Ferme la socket et arrête tout. Appelable plusieurs fois. */
    fun close() {
        runCatching { socket?.close() }
        socket = null
        _url.value = null
        scope.cancel()
    }

    private suspend fun handle(client: Socket) = client.use { sock ->
        sock.soTimeout = SOCKET_TIMEOUT_MS
        val input = sock.getInputStream().buffered()
        val request = readRequestLine(input) ?: return@use
        val headers = readHeaders(input)

        // Le jeton est la seule autorisation. Tout le reste — favicon, sondes,
        // curiosité — tombe ici.
        val path = request.path.trimEnd('/')
        if (path != "/$token" && !path.startsWith("/$token/")) {
            respond(sock.getOutputStream(), 404, "text/plain; charset=utf-8", "Not found")
            return@use
        }
        val route = path.removePrefix("/$token").trimStart('/')

        // --- Télécommande ---------------------------------------------------
        //
        // Séparée du formulaire : un appui de flèche ne doit ni recharger la
        // page ni traverser la lecture des réglages. Réponses vides et courtes,
        // parce qu'il y en a une par appui.
        if (route == "remote" || route == "key" || route == "text") {
            if (!remoteAvailable()) {
                respond(sock.getOutputStream(), 404, "text/plain; charset=utf-8", "No remote")
                return@use
            }
            when (route) {
                "remote" -> {
                    // Charger la page arme la session : c'est le geste explicite
                    // qui autorise le serveur à survivre à la modale.
                    PairingSession.armRemote()
                    respond(sock.getOutputStream(), 200, HTML, remotePage(texts, "/$token"))
                }
                else -> {
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    if (length !in 0..MAX_BODY_BYTES) {
                        respond(sock.getOutputStream(), 413, "text/plain; charset=utf-8", "Too large")
                        return@use
                    }
                    val form = decodeForm(String(readExactly(input, length), Charsets.UTF_8))
                    if (route == "key") {
                        runCatching { RemoteKey.valueOf(form["k"].orEmpty()) }
                            .getOrNull()?.let(::sendRemoteKey)
                    } else {
                        form["t"]?.let(::sendRemoteText)
                    }
                    // 204 : rien à rendre, et le navigateur n'a rien à redessiner.
                    respond(sock.getOutputStream(), 204, "text/plain; charset=utf-8", "")
                }
            }
            return@use
        }

        if (request.method == "POST") {
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            if (length !in 0..MAX_BODY_BYTES) {
                respond(sock.getOutputStream(), 413, "text/plain; charset=utf-8", "Too large")
                return@use
            }
            val body = String(readExactly(input, length), Charsets.UTF_8)
            _saved.value += apply(decodeForm(body))
            respond(sock.getOutputStream(), 200, HTML, pairingDonePage(texts))
            return@use
        }

        _opened.value = true
        respond(sock.getOutputStream(), 200, HTML, pairingPage(fields(), texts, "/$token"))
    }

    private fun respond(out: OutputStream, status: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 $status ${reason(status)}\r\n")
            append("Content-Type: $type\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            // Rien de ce qui est servi ici ne doit survivre à l'appairage : le
            // jeton change à chaque ouverture, une page en cache serait morte.
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        204 -> "No Content"
        404 -> "Not Found"
        413 -> "Payload Too Large"
        else -> "Error"
    }

    private companion object {
        const val HTML = "text/html; charset=utf-8"

        /**
         * Port tenté d'abord, pour que l'adresse de repli reste courte à recopier
         * à la main quand l'appareil photo ne veut pas du QR.
         */
        const val PREFERRED_PORT = 8687
        const val MAX_BODY_BYTES = 64 * 1024
        const val SOCKET_TIMEOUT_MS = 15_000
    }
}

/** Ligne de requête HTTP, réduite à ce dont on se sert. */
internal data class RequestLine(val method: String, val path: String)

/**
 * Lit la ligne de requête. Null si la connexion se ferme ou n'a pas cette forme.
 *
 * On lit **octet par octet** plutôt qu'avec un lecteur tamponné : un lecteur
 * avalerait une partie du corps dans son tampon, et le `Content-Length` ne
 * retrouverait plus ses petits. Classique, et silencieux quand ça arrive.
 */
internal fun readRequestLine(input: InputStream): RequestLine? {
    val parts = readLine(input)?.split(' ') ?: return null
    if (parts.size < 2) return null
    return RequestLine(parts[0].uppercase(), parts[1].substringBefore('?'))
}

/** En-têtes jusqu'à la ligne vide, noms normalisés en minuscules. */
internal fun readHeaders(input: InputStream): Map<String, String> {
    val out = mutableMapOf<String, String>()
    while (true) {
        val line = readLine(input) ?: break
        if (line.isEmpty()) break
        val name = line.substringBefore(':', "").trim().lowercase()
        if (name.isNotEmpty()) out[name] = line.substringAfter(':').trim()
    }
    return out
}

/**
 * Lit exactement [count] octets, ou moins si le flux se ferme avant.
 *
 * Écrit à la main plutôt qu'avec `InputStream.readNBytes` : cette méthode est
 * du Java 9, absente d'Android avant l'API 33, et l'application descend à 23.
 * Le piège est qu'elle **compile** et passe les tests, qui tournent sur le JVM
 * du poste ; l'échec n'apparaît que sur l'appareil, à la première requête avec
 * un corps, sous la forme d'une connexion fermée sans réponse.
 */
internal fun readExactly(input: InputStream, count: Int): ByteArray {
    val out = ByteArray(count)
    var read = 0
    while (read < count) {
        // Une lecture peut rendre moins que demandé sans que le flux soit fini :
        // supposer le contraire tronquerait la dernière valeur du formulaire.
        val n = input.read(out, read, count - read)
        if (n <= 0) break
        read += n
    }
    return if (read == count) out else out.copyOf(read)
}

private fun readLine(input: InputStream): String? {
    val buffer = StringBuilder()
    while (true) {
        val byte = input.read()
        if (byte == -1) return buffer.takeIf { it.isNotEmpty() }?.toString()
        if (byte == '\n'.code) return buffer.removeSuffix("\r").toString()
        if (buffer.length > 8192) return null // en-tête déraisonnable : on abandonne
        buffer.append(byte.toChar())
    }
}

private fun StringBuilder.removeSuffix(suffix: String): StringBuilder =
    if (endsWith(suffix)) also { setLength(length - suffix.length) } else this

/**
 * Décode un corps `application/x-www-form-urlencoded`.
 *
 * `URLDecoder` traite `+` comme une espace, ce qui est bien la règle des
 * formulaires — et pas celle des URL. C'est la bonne fonction ici.
 */
internal fun decodeForm(body: String): Map<String, String> = body
    .split('&')
    .filter { it.isNotBlank() }
    .mapNotNull { pair ->
        val name = pair.substringBefore('=', "")
        if (name.isEmpty()) return@mapNotNull null
        val value = pair.substringAfter('=', "")
        runCatching {
            URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }.getOrNull()
    }
    .toMap()

/**
 * Adresse IPv4 de l'appareil sur le réseau local, ou null.
 *
 * On garde une adresse de site (192.168.x, 10.x, 172.16-31.x) : c'est la seule
 * qu'un téléphone du même réseau pourra joindre. Le parcours des interfaces
 * plutôt qu'un nom en dur, parce qu'une box filaire et une box en Wi-Fi n'ont
 * pas la même.
 *
 * **Les interfaces virtuelles sont écartées, et c'est indispensable sur poste de
 * développement.** Docker pose `docker0` en 172.17.0.1 et un pont par réseau
 * composé, tous « actifs » et tous en adresse de site : prendre la première
 * venue encode dans le QR une adresse que le téléphone ne joindra jamais. Le
 * symptôme est le pire qui soit — un QR parfaitement lisible qui ne mène nulle
 * part, donc une fonctionnalité qu'on croit cassée alors que c'est l'adresse qui
 * est fausse. `isVirtual()` ne les attrape pas : il ne repère que les
 * sous-interfaces du type `eth0:1`. D'où le filtre par nom.
 */
internal fun localAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }
        .filterNot { VIRTUAL_INTERFACE.containsMatchIn(it.name) }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

/** Ponts et tunnels : Docker, libvirt, VMware, VPN, Tailscale/ZeroTier. */
private val VIRTUAL_INTERFACE =
    Regex("""^(docker|br-|veth|virbr|vmnet|vboxnet|tun|tap|wg|zt|utun)""", RegexOption.IGNORE_CASE)

/**
 * Jeton d'adresse. Huit caractères sans ambiguïté visuelle — ni `0`/`O`, ni
 * `1`/`l` — parce qu'il arrive qu'on le recopie depuis l'écran.
 */
private fun randomToken(): String {
    val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
    return (1..8).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
}
