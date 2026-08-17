package fr.moovie.tv.data.pairing

import fr.moovie.tv.data.remote.PlayRequest
import fr.moovie.tv.data.remote.RemoteLaunch
import fr.moovie.tv.data.remote.RemoteKey
import fr.moovie.tv.data.remote.RemoteNowPlaying
import fr.moovie.tv.data.remote.RemoteState
import fr.moovie.tv.data.remote.RemoteSyncIdentity
import fr.moovie.tv.data.remote.RemoteTyping
import kotlinx.serialization.encodeToString
import fr.moovie.tv.data.remote.remoteAvailable
import fr.moovie.tv.shared.deviceName
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
 * Le jeton n'est plus tiré ici mais **fourni** ([tokenOf]), parce qu'il doit
 * durer : voir [fr.moovie.tv.data.remote.RemoteTokenRepository]. Un fournisseur
 * suspendu plutôt qu'une valeur, pour que sa lecture — qui touche le disque —
 * se fasse dans la coroutine de [start] et laisse la construction synchrone.
 *
 * Il n'y a pas de TLS : un certificat auto-signé ferait afficher au téléphone un
 * avertissement de sécurité, ce qui apprendrait exactement le mauvais réflexe
 * pour protéger un échange qui ne quitte pas le domicile.
 */
class PairingServer(
    private val fields: suspend () -> List<PairingField>,
    private val apply: suspend (Map<String, String>) -> Int,
    private val texts: PairingTexts,
    private val tokenOf: suspend () -> String,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * `@Volatile` parce que [start] l'écrit sur sa coroutine et que [handle] le
     * lit sur celles des connexions. Aucune requête ne peut arriver avant
     * l'écriture — la socket n'existe pas encore — mais la visibilité entre
     * threads, elle, ne se déduit pas de l'ordre du code.
     */
    @Volatile
    private var token: String = ""

    /**
     * Lien qui rebascule le téléphone vers l'application, jeton compris.
     *
     * Vide tant que l'adresse n'est pas connue : la page reste alors une page,
     * ce qu'elle doit de toute façon rester pour un téléphone sans Moo-vie.
     */
    private fun appLink(): String {
        val url = _url.value ?: return ""
        val authority = url.removePrefix("http://").substringBefore('/')
        val host = authority.substringBefore(':')
        val port = authority.substringAfter(':', "")
        if (host.isBlank() || port.isBlank()) return ""
        val name = java.net.URLEncoder.encode(deviceName, "UTF-8")
        val target = "remote?h=$host&p=$port&t=$token&n=$name"
        // `S.browser_fallback_url` ramène ici quand l'application est absente,
        // au lieu de la page d'erreur d'un schéma inconnu.
        val fallback = java.net.URLEncoder.encode(url + "/remote", "UTF-8")
        return "intent://$target#Intent;scheme=moovie;package=fr.moovie.tv;" +
            "S.browser_fallback_url=$fallback;end"
    }
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
            // Avant la socket : sans jeton il n'y a pas d'adresse à publier, et
            // écouter sans savoir quoi autoriser reviendrait à tout ouvrir. Le
            // `runCatching` est là parce qu'une exception nue sur ce `launch`
            // remonterait au gestionnaire par défaut du thread, c'est-à-dire à
            // un arrêt de l'application pour une lecture de préférences.
            val fresh = runCatching { tokenOf() }.getOrNull()
            if (fresh.isNullOrBlank()) {
                _failure.value = "no-socket"
                return@launch
            }
            token = fresh
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

    /**
     * Relit le jeton et réécrit l'adresse, sans couper l'écoute.
     *
     * C'est ce qui donne son effet à « Oublier les télécommandes » : le dépôt a
     * déjà tiré un jeton neuf, il reste à ce que le serveur en vive. Tout ce qui
     * était appairé tombe alors en 404, et le QR affiché à côté montre la
     * nouvelle adresse — celle qu'il faut rescanner.
     *
     * Sans effet tant que le serveur n'écoute pas : il lira le nouveau jeton de
     * lui-même à son démarrage.
     */
    suspend fun refreshToken() {
        val authority = _url.value?.removePrefix("http://")?.substringBefore('/') ?: return
        val fresh = runCatching { tokenOf() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        token = fresh
        _url.value = "http://$authority/$fresh"
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

        // --- Sonde ----------------------------------------------------------
        //
        // « Le téléviseur est-il là, et mon jeton vaut-il encore ? » Les deux
        // d'un coup, puisqu'un jeton périmé est déjà tombé en 404 au-dessus.
        //
        // Une route à elle, plutôt que de charger `/remote` : cette dernière
        // **arme la session**, ce qui fait survivre le serveur à la modale.
        // Sonder ne doit rien changer à l'état du téléviseur, sans quoi le
        // simple fait de regarder si la télécommande est joignable la
        // maintiendrait allumée.
        if (route == "ping") {
            respond(sock.getOutputStream(), 204, "text/plain; charset=utf-8", "")
            return@use
        }

        // --- Lecture demandée par le téléphone -------------------------------
        //
        // Hors du bloc « télécommande » ci-dessous, et ce n'est pas un détail :
        // celui-ci exige `remoteAvailable()`, c'est-à-dire une session armée par
        // l'ouverture de la page. Envoyer un titre depuis l'application n'a pas
        // à passer par là — c'est le geste qui *démarre* l'usage, il ne peut pas
        // supposer qu'on a déjà ouvert une télécommande.
        //
        // Le corps est un formulaire comme les autres routes, pas du JSON : le
        // serveur sait déjà les décoder, et cinq champs ne justifient pas un
        // second format à analyser à la main.
        if (route == "play") {
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            if (length !in 0..MAX_BODY_BYTES) {
                respond(sock.getOutputStream(), 413, "text/plain; charset=utf-8", "Too large")
                return@use
            }
            val form = decodeForm(String(readExactly(input, length), Charsets.UTF_8))
            val tmdbId = form["id"]?.toIntOrNull()
            if (tmdbId == null || tmdbId <= 0) {
                respond(sock.getOutputStream(), 400, "text/plain; charset=utf-8", "Bad id")
                return@use
            }
            val accepted = RemoteLaunch.request(
                PlayRequest(
                    tmdbId = tmdbId,
                    isTv = form["tv"] == "1",
                    season = form["s"]?.toIntOrNull() ?: 0,
                    episode = form["e"]?.toIntOrNull() ?: 0,
                    title = form["t"].orEmpty(),
                    subtitle = form["st"].orEmpty(),
                    artwork = form["art"].orEmpty(),
                    positionMs = form["pos"]?.toLongOrNull() ?: 0,
                    durationMs = form["dur"]?.toLongOrNull() ?: 0,
                    record = form["norec"] != "1",
                ),
            )
            // 409 et non 500 : l'adresse est bonne, c'est l'état du téléviseur
            // qui ne s'y prête pas — personne n'écoute encore. Le téléphone
            // peut le dire au lieu de basculer sur une télécommande vide.
            respond(
                sock.getOutputStream(),
                if (accepted) 204 else 409,
                "text/plain; charset=utf-8",
                "",
            )
            return@use
        }

        // --- Télécommande ---------------------------------------------------
        //
        // Séparée du formulaire : un appui de flèche ne doit ni recharger la
        // page ni traverser la lecture des réglages. Réponses vides et courtes,
        // parce qu'il y en a une par appui.
        if (route == "remote" || route == "key" || route == "text" ||
            route == "state" || route == "seek"
        ) {
            if (!remoteAvailable()) {
                respond(sock.getOutputStream(), 404, "text/plain; charset=utf-8", "No remote")
                return@use
            }
            when (route) {
                // Tout ce que le téléviseur raconte de lui : ce qui joue, et le
                // champ qui attend une saisie. Toujours 200, même quand les deux
                // sont vides — « rien en cours » est une réponse, et le
                // téléphone doit pouvoir la distinguer d'un silence. Les
                // confondre faisait clignoter son mini-lecteur au premier
                // paquet perdu.
                "state" -> {
                    val state = RemoteState(
                        now = RemoteNowPlaying.state.value,
                        typing = RemoteTyping.field.value,
                        syncFingerprint = RemoteSyncIdentity.fingerprint,
                        lastPlayed = RemoteNowPlaying.last,
                    )
                    respond(sock.getOutputStream(), 200, JSON_TYPE, JSON.encodeToString(state))
                }

                // Déplacement absolu, en millisecondes. Absolu et non relatif :
                // le doigt vise un endroit de la barre, pas un décalage — et un
                // relatif calculé sur une position vieille d'une seconde
                // atterrirait à côté.
                "seek" -> {
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    if (length !in 0..MAX_BODY_BYTES) {
                        respond(sock.getOutputStream(), 413, "text/plain; charset=utf-8", "Too large")
                        return@use
                    }
                    val form = decodeForm(String(readExactly(input, length), Charsets.UTF_8))
                    val target = form["p"]?.toLongOrNull()
                    val done = target != null && RemoteNowPlaying.seek(target)
                    // 409 plutôt que 404 : l'adresse est bonne, c'est l'état qui
                    // ne s'y prête pas — le lecteur n'est pas ouvert.
                    respond(
                        sock.getOutputStream(),
                        if (done) 204 else 409,
                        "text/plain; charset=utf-8",
                        "",
                    )
                }
                "remote" -> {
                    // Charger la page arme la session : c'est le geste explicite
                    // qui autorise le serveur à survivre à la modale.
                    PairingSession.armRemote()
                    respond(
                        sock.getOutputStream(),
                        200,
                        HTML,
                        remotePage(texts, "/$token", appLink()),
                    )
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
                        // Écrire dans le champ annoncé si l'écran s'est prêté au
                        // jeu, sinon taper les caractères. La différence n'est
                        // pas cosmétique : l'injection clavier **ajoute** à la
                        // fin, si bien que renvoyer un texte corrigé depuis le
                        // téléphone le concaténait à celui déjà saisi.
                        form["t"]?.let { text ->
                            if (!RemoteTyping.write(text)) sendRemoteText(text)
                        }
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
            // Rien de ce qui est servi ici ne doit être gardé : la page reflète
            // des réglages qui viennent de changer, et le jeton de l'adresse
            // peut être révoqué d'un instant à l'autre.
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
        const val JSON_TYPE = "application/json; charset=utf-8"

        /**
         * `encodeDefaults` : sans lui, un champ resté à sa valeur par défaut —
         * une lecture à la position 0, un titre vide — serait **absent** du
         * corps. Le téléphone le relirait comme sa propre valeur par défaut,
         * ce qui tombe juste ici par chance, mais cesserait de tomber juste au
         * premier champ dont le défaut diffère d'un bout à l'autre.
         */
        val JSON = kotlinx.serialization.json.Json { encodeDefaults = true }

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
