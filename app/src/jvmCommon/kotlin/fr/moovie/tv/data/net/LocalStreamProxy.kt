package fr.moovie.tv.data.net

import fr.moovie.tv.data.sources.ExtractorRegistry
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Base64
import java.util.concurrent.Executors

/**
 * Relais HTTP en boucle locale, placé entre le lecteur et l'hébergeur.
 *
 * **Pourquoi, aujourd'hui : le DNS.** Le relais refait chaque requête avec le
 * client HTTP de l'application, donc avec sa résolution DoH — sans laquelle les
 * domaines d'hébergeurs sont bloqués par le DNS des fournisseurs d'accès. Le
 * lecteur, lui, résout par le système : sur une connexion réelle, un flux
 * parfaitement valide devient « aucune source » sans le relais.
 *
 * **Pourquoi, à l'origine : les en-têtes.** Beaucoup de CDN n'acceptent une
 * requête qu'accompagnée d'un `Referer` *et* d'un `User-Agent` — mesuré sur
 * vidzy : chacun seul rend 403, les deux ensemble 200. libVLC, le lecteur de
 * l'époque, ne transmettait pas ces en-têtes à ses requêtes de segment, et le
 * symptôme n'avait rien d'un refus : playlist lue, aucun segment, `0:00/0:00`,
 * fin déclarée — prise pour un épisode terminé. Le moteur mpv, lui, les
 * transmet (verrouillé par `MpvHeadersTest`) ; le relais les pose toujours,
 * en ceinture avec les bretelles.
 *
 * **Comment.** Le lecteur reçoit une URL locale ; chaque requête qu'il émet est
 * refaite vers l'origine avec les en-têtes, et la réponse recopiée telle
 * quelle. Les playlists sont réécrites au passage : toute URI qu'elles
 * contiennent est remplacée par une URL locale, si bien qu'aucun segment ne
 * peut échapper au relais — y compris quand la playlist pointe vers un autre
 * domaine que celui d'où elle vient.
 *
 * L'adressage est direct : `/u/<url absolue en base64url>`. Pas de table à
 * tenir, donc rien à expirer, et un segment demandé longtemps après reste
 * résoluble.
 *
 * Écoute sur la boucle locale uniquement, sur un port éphémère : rien de tout
 * ceci n'est joignable depuis le réseau.
 */
internal class LocalStreamProxy(
    private val headers: Map<String, String>,
    /**
     * Redécoupe les requêtes en plages bornées.
     *
     * googlevideo **exige** une plage, et pas n'importe laquelle. Mesuré sur
     * une URL vivante : aucun `Range` → 403 ; `bytes=0-` → 403 ;
     * `bytes=0-1048575` → 206 ; et `bytes=0-<taille-1>`, c'est-à-dire le
     * fichier entier, → 403 à nouveau. Ce n'est donc pas « une plage » qu'il
     * réclame, c'est un **morceau** — et aucun lecteur n'en demande
     * spontanément. Le découpage est invisible pour le lecteur : il voit une
     * réponse normale, de la bonne longueur, pendant qu'on va la chercher
     * morceau par morceau. Réservé aux flux qui le demandent — un segment HLS
     * de quelques secondes n'a rien à y gagner.
     */
    private val borneLesPlages: Boolean = false,
    /**
     * Ouvre l'écoute au **réseau local** au lieu de la seule boucle locale.
     *
     * Un Chromecast n'est pas dans notre processus : il lui faut une URL qu'il
     * puisse joindre depuis le Wi-Fi. C'est le seul cas où on sort de la boucle,
     * et il ne se prend pas à la légère — voir [jeton].
     *
     * Faux partout ailleurs : le lecteur local, lui, n'a aucune raison d'être
     * joignable par le voisin.
     */
    private val ouvertAuReseau: Boolean = false,
    /**
     * Adresse du récepteur visé, quand on la connaît.
     *
     * Elle ne sert qu'à choisir la **bonne interface** — voir [adresseLocale].
     * Nulle, on vise l'interface par défaut, ce qui suffit tant qu'il n'y a pas
     * de VPN ni de second réseau.
     */
    private val versHote: String? = null,
) {

    /**
     * Jeton de session, dans le chemin de chaque URL servie.
     *
     * **C'est ce qui rend [ouvertAuReseau] acceptable.** L'adressage du relais
     * est `/u/<url absolue en base64>` : sans garde, l'ouvrir au Wi-Fi ferait de
     * l'appareil un **proxy ouvert**, que n'importe qui sur le réseau pourrait
     * faire pointer où il veut — y compris vers un service interne que lui ne
     * joint pas. Le jeton ne protège pas le flux, qui n'a rien de secret ; il
     * empêche qu'on se serve du relais comme d'un rebond.
     *
     * Tiré au montage, jamais persisté : il meurt avec la lecture.
     */
    private val jeton: String =
        java.math.BigInteger(96, java.security.SecureRandom()).toString(36)

    private val server = ServerSocket(
        0,
        8,
        // `null` fait écouter sur toutes les interfaces ; sinon la boucle seule.
        if (ouvertAuReseau) null else InetAddress.getByName("127.0.0.1"),
    )

    /**
     * Un fil par connexion. libVLC en ouvre plusieurs de front — la playlist,
     * le segment courant, celui qu'il précharge — et une file séquentielle les
     * ferait s'attendre, ce qui se verrait à l'image.
     */
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "moovie-stream-proxy").apply { isDaemon = true }
    }

    @Volatile
    private var running = true

    init {
        workers.execute {
            while (running) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                workers.execute { runCatching { serve(socket) }; runCatching { socket.close() } }
            }
        }
    }

    /**
     * URL à donner au lecteur à la place de [url].
     *
     * En boucle locale c'est `127.0.0.1` ; ouvert au réseau, c'est l'adresse de
     * l'appareil sur le Wi-Fi — un Chromecast ne saurait rien faire de
     * `localhost`, qui désignerait le Chromecast lui-même.
     */
    fun localUrl(url: String): String =
        "http://${hote()}:${server.localPort}${localPath(url)}"

    /** Adresse joignable par le lecteur visé. */
    private fun hote(): String {
        if (!ouvertAuReseau) return "127.0.0.1"
        // **Jamais de repli silencieux sur la boucle locale.** L'annoncer à un
        // récepteur distant lui désigne lui-même : il ne trouve rien, et le
        // symptôme — `LOAD_FAILED` — ne dit rien de l'adresse. Une exception
        // remonte au moins jusqu'à l'écran, qui sait dire que ça n'a pas pris.
        return adresseLocale()
            ?: error("aucune adresse de réseau local : impossible de servir un récepteur distant")
    }

    fun shutdown() {
        running = false
        runCatching { server.close() }
        runCatching { workers.shutdownNow() }
    }

    private fun localPath(url: String): String =
        "/$jeton/u/" + Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray())

    private fun serve(socket: Socket) {
        val input = socket.getInputStream().bufferedReader()
        val requestLine = input.readLine() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1]

        // On ne retient qu'un en-tête du client : la plage demandée. Le reste
        // (User-Agent de libVLC, Referer absent) est justement ce qu'on remplace.
        var range: String? = null
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
        }

        val output = BufferedOutputStream(socket.getOutputStream())

        // `Range` n'est pas un en-tête « simple » : le récepteur envoie un
        // préflight avant chaque segment. Y répondre ne coûte rien ; ne pas y
        // répondre annule la requête qui suit, sans trace.
        if (method.equals("OPTIONS", ignoreCase = true)) {
            writeHead(output, 204, "No Content", "text/plain", 0L, null)
            output.flush()
            return
        }

        val target = decodeTarget(path)
        if (target == null) {
            respondStatus(output, 404, "Not Found")
            return
        }

        if (borneLesPlages && !method.equals("HEAD", ignoreCase = true)) {
            if (sersEnMorceaux(output, target, range)) return
        }

        val request = Request.Builder().url(target)
            .apply {
                headers.forEach { (name, value) -> header(name, value) }
                range?.let { header("Range", it) }
                if (method.equals("HEAD", ignoreCase = true)) head()
            }
            .build()

        // Le client de l'application, et non un neuf : c'est lui qui porte la
        // résolution DoH, sans laquelle les domaines de sources sont bloqués
        // par le DNS du fournisseur d'accès.
        val response = runCatching { ExtractorRegistry.http.newCall(request).execute() }.getOrNull()
        if (response == null) {
            respondStatus(output, 502, "Bad Gateway")
            return
        }

        response.use {
            val contentType = it.header("Content-Type")
            val body = it.body
            if (body == null) {
                respondStatus(output, it.code, it.message)
                return
            }
            if (isPlaylist(target, contentType)) {
                // Assez petit pour tenir en mémoire, et il faut de toute façon
                // le lire en entier pour le réécrire.
                val rewritten = rewritePlaylist(body.string(), target).toByteArray()
                writeHead(output, it.code, it.message, contentType ?: "application/vnd.apple.mpegurl", rewritten.size.toLong(), null)
                output.write(rewritten)
                output.flush()
                return
            }
            writeHead(
                output,
                it.code,
                it.message,
                contentType ?: "application/octet-stream",
                body.contentLength().takeIf { len -> len >= 0 },
                it.header("Content-Range"),
            )
            if (!method.equals("HEAD", ignoreCase = true)) {
                // Recopie en flux : un MP4 progressif ne passera pas en mémoire.
                body.byteStream().copyTo(output, DEFAULT_BUFFER_SIZE)
            }
            output.flush()
        }
    }

    /**
     * Sert [target] en allant le chercher morceau par morceau.
     *
     * @return vrai si la réponse a été écrite ; faux pour laisser le chemin
     *   normal reprendre la main — l'origine ne gère pas les plages, ou n'a pas
     *   voulu dire sa taille, et rien ne justifie alors de la contrarier.
     */
    @Suppress("ReturnCount", "LongMethod")
    private fun sersEnMorceaux(output: OutputStream, target: String, range: String?): Boolean {
        val taille = tailleTotale(target) ?: return false

        // Le mur, s'il a déjà été rencontré : googlevideo ne sert des
        // bandes-annonces de studio qu'une fenêtre d'environ une minute de
        // média aux clients sans jeton PO — au-delà, 403 définitif, quel que
        // soit le client, le rythme ou la fraîcheur de l'URL (mesuré, offsets
        // identiques sur des URL neuves). On ne peut pas l'abattre ; on peut
        // faire que le fichier *s'arrête là* proprement, au lieu de laisser le
        // lecteur sur une promesse de longueur qui ne viendra jamais — l'image
        // figée sur une barre qui avance.
        val servable = murs[target] ?: taille

        val (debut, finDemandee) = analysePlage(range, servable)
        if (debut >= taille) return false
        if (debut >= servable) {
            // Au-delà du mur, le monde tronqué répond « fin de fichier » : zéro
            // octet, et le lecteur conclut au lieu d'attendre.
            writeHead(output, 200, "OK", "application/octet-stream", 0L, null)
            output.flush()
            return true
        }
        val fin = finDemandee.coerceAtMost(servable - 1)
        val debit = debitOctets(target, taille)

        // 206 seulement si le lecteur a demandé une plage. Sans quoi il reçoit
        // une réponse ordinaire, de la longueur du fichier : le découpage ne le
        // regarde pas.
        val partiel = range != null
        writeHead(
            output,
            if (partiel) 206 else 200,
            if (partiel) "Partial Content" else "OK",
            "application/octet-stream",
            fin - debut + 1,
            if (partiel) "bytes $debut-$fin/$servable" else null,
        )

        // Le budget se compte par **URL sur toute sa vie**, pas par connexion :
        // c'est le modèle du serveur. Compté par connexion, un seek repartait
        // avec une rafale neuve et demandait des octets que l'horloge n'avait
        // pas encore « mérités » — googlevideo répondait 403, et ces refus de
        // débit se faisaient prendre pour le mur permanent (faux plafond à
        // 53,8 s enregistré en session réelle).
        val compteur = compteurs.getOrPut(target) { Compteur(System.currentTimeMillis()) }
        var position = debut
        var echecs = 0
        var premierRefusA = 0L
        while (position <= fin) {
            // Cadence le téléchargement sur l'horloge murale, au débit que
            // l'URL déclare elle-même. Réduire nos tampons ne suffit pas : ceux
            // du client avalent des mégaoctets — mesuré, 11,5 Mo servis en 16 s
            // sur un flux à 200 Ko/s, puis 403 sur tout. La rafale laisse au
            // lecteur de quoi ouvrir le média et quelques secondes d'avance ;
            // au-delà, on ne devance jamais la lecture.
            if (debit > 0) {
                while (compteur.servis.get() >=
                    RAFALE + (System.currentTimeMillis() - compteur.depuis) * debit / 1000
                ) {
                    Thread.sleep(PAS_CADENCE_MS)
                }
            }
            val borne = borneDuMorceau(position, fin, taille)
            val requete = Request.Builder().url(target)
                .apply { headers.forEach { (nom, valeur) -> header(nom, valeur) } }
                .header("Range", "bytes=$position-$borne")
                .build()
            val reponse = runCatching { ExtractorRegistry.http.newCall(requete).execute() }.getOrNull()

            if (reponse == null || !reponse.isSuccessful) {
                val code = reponse?.code
                runCatching { reponse?.close() }
                echecs++
                // Un 403 n'est pas forcément le mur : la limitation de débit en
                // rend aussi, et ceux-là **cèdent en quelques secondes** quand le
                // budget se reconstitue. Le mur, lui, ne cède jamais — mesuré,
                // encore 403 dix-huit secondes plus tard. On ne le déclare donc
                // qu'après des refus persistants : pris trop vite, un refus de
                // débit devenait un faux plafond à 53,8 s enregistré à vie.
                if (code == 403) {
                    if (premierRefusA == 0L) premierRefusA = System.currentTimeMillis()
                    if (System.currentTimeMillis() - premierRefusA >= PERSISTANCE_MUR_MS && position > debut) {
                        murs[target] = position
                        println(
                            "[relais] plafond googlevideo à $position/$taille octets " +
                                "(${position * 100 / taille} %) — flux tronqué là",
                        )
                        return true
                    }
                    Thread.sleep(ATTENTE_403_MS)
                    continue
                }
                if (echecs > TENTATIVES) {
                    // L'en-tête est déjà parti : couper est tout ce qui reste.
                    // Mais le dire, sinon le lecteur voit une fin de fichier
                    // prématurée et l'image se fige sans explication.
                    println("[relais] abandon sur bytes=$position-$borne (${code ?: "réseau"})")
                    return true
                }
                Thread.sleep(ATTENTE_MS * echecs)
                continue
            }

            val ecrit = reponse.use { r ->
                val corps = r.body ?: return true
                runCatching { corps.byteStream().copyTo(output, DEFAULT_BUFFER_SIZE) }.getOrNull()
            } ?: return true
            if (ecrit <= 0L) return true
            echecs = 0
            premierRefusA = 0L
            compteur.servis.addAndGet(ecrit)
            position += ecrit
        }
        output.flush()
        return true
    }

    /** Octets réellement servis pour une URL, et l'instant de son premier usage. */
    private class Compteur(val depuis: Long) {
        val servis = java.util.concurrent.atomic.AtomicLong()
    }

    /**
     * Fin du morceau à demander à partir de [position].
     *
     * Plafonnée à [MORCEAU], **et** jamais égale à la fin du fichier quand on
     * part du début : googlevideo refuse une plage qui couvre tout — mesuré,
     * `bytes=0-<taille-1>` rend 403 alors que `bytes=0-1048575` rend 206. Un
     * fichier plus petit qu'un morceau, comme une piste audio de deux
     * mégaoctets, tomberait sinon exactement dans ce cas, et c'est ce qui
     * faisait échouer l'ouverture du son.
     */
    private fun borneDuMorceau(position: Long, fin: Long, taille: Long): Long {
        val borne = minOf(position + MORCEAU - 1, fin)
        val couvreTout = position == 0L && borne >= taille - 1
        return if (couvreTout && taille > 1) taille - 2 else borne
    }

    /**
     * Taille du fichier, lue dans le `Content-Range` d'une requête de deux
     * octets. C'est le seul moyen fiable : googlevideo refuse une requête sans
     * plage, donc ni `HEAD` ni `GET` nu ne diront jamais `Content-Length`.
     *
     * Seuls les succès sont mémorisés : retenir un échec — un hoquet réseau à
     * la première demande — condamnerait l'URL au chemin non découpé, dont on
     * sait qu'il rend 403, pour toute la vie du relais.
     */
    private fun tailleTotale(target: String): Long? {
        tailles[target]?.let { return it }
        val requete = Request.Builder().url(target)
            .apply { headers.forEach { (nom, valeur) -> header(nom, valeur) } }
            .header("Range", "bytes=0-1")
            .build()
        val reponse = runCatching { ExtractorRegistry.http.newCall(requete).execute() }.getOrNull()
            ?: return null
        val taille = reponse.use {
            it.header("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull() ?: 0L
        }
        if (taille <= 0) return null
        tailles[target] = taille
        return taille
    }

    /**
     * Débit nominal du média en octets par seconde, ou 0 si l'URL ne le dit
     * pas. Les URL googlevideo portent la durée du média en query
     * (`dur=138.560`), et la taille vient du `Content-Range` : le rapport des
     * deux est le débit moyen réel de la piste — la seule référence qui vaille
     * pour se caler sur le budget du serveur, qui la connaît aussi.
     */
    private fun debitOctets(target: String, taille: Long): Long {
        val dur = DUREE_QUERY.find(target)?.groupValues?.get(1)?.toDoubleOrNull() ?: return 0L
        if (dur <= 0.0) return 0L
        return (taille / dur).toLong()
    }

    /** `bytes=12-99` → 12..99 ; `bytes=12-` et l'absence → jusqu'au bout. */
    private fun analysePlage(range: String?, taille: Long): Pair<Long, Long> {
        val brut = range?.substringAfter("bytes=", "")?.trim().orEmpty()
        if (brut.isEmpty()) return 0L to taille - 1
        val debut = brut.substringBefore('-').trim().toLongOrNull() ?: 0L
        val fin = brut.substringAfter('-', "").trim().toLongOrNull() ?: (taille - 1)
        return debut to fin
    }

    /**
     * Extrait l'URL visée, **et refuse tout ce qui ne porte pas le jeton**.
     *
     * C'est le seul contrôle d'accès du relais, et il suffit à sa raison d'être :
     * empêcher qu'un tiers du réseau s'en serve comme rebond. Voir [jeton].
     */
    private fun decodeTarget(path: String): String? {
        val prefixe = "/$jeton/u/"
        if (!path.startsWith(prefixe)) return null
        val encoded = path.removePrefix(prefixe)
        return runCatching { String(Base64.getUrlDecoder().decode(encoded)) }.getOrNull()
    }

    /**
     * Adresse de cet appareil **par laquelle le récepteur nous joindra**.
     *
     * ## Deux méthodes, parce qu'une seule ment
     *
     * **La route d'abord.** Un socket UDP « connecté » n'émet aucun paquet mais
     * oblige le noyau à choisir la route, donc l'interface, donc l'adresse
     * source. C'est la bonne réponse quand elle vient — mesuré sur le poste de
     * développement, où parcourir les interfaces désignait un pont Docker parmi
     * quatre candidates.
     *
     * **Le sous-réseau ensuite.** Sur Android, `localAddress` ne rend pas une
     * `Inet4Address` et la première méthode échoue. Le repli d'alors était la
     * boucle locale : on annonçait `127.0.0.1` à un Chromecast, pour qui cette
     * adresse désigne **lui-même**. Il ne frappait jamais à la porte du relais
     * et répondait `LOAD_FAILED`, sans que rien ne dise que l'adresse était en
     * cause. On cherche donc l'interface dont le réseau contient le récepteur :
     * elle est joignable par construction.
     *
     * Rend null plutôt que la boucle locale — voir [hote] : mieux vaut une
     * diffusion qui échoue franchement qu'une URL que personne ne peut suivre.
     */
    private fun adresseLocale(): String? = parLaRoute() ?: parLeSousReseau()

    /** Le noyau choisit l'interface ; on lui demande laquelle. */
    private fun parLaRoute(): String? = runCatching {
        java.net.DatagramSocket().use { sonde ->
            sonde.connect(java.net.InetAddress.getByName(versHote ?: "8.8.8.8"), 9)
            sonde.localAddress
                ?.takeIf { it is java.net.Inet4Address && !it.isAnyLocalAddress && !it.isLoopbackAddress }
                ?.hostAddress
        }
    }.getOrNull()

    /**
     * L'interface dont le réseau contient le récepteur.
     *
     * On compare les adresses masquées par la longueur de préfixe annoncée par
     * l'interface : c'est ce qui distingue le Wi-Fi du salon d'un pont Docker ou
     * d'un VPN, sans avoir à les nommer.
     */
    private fun parLeSousReseau(): String? = runCatching {
        val cible = versHote?.let { java.net.InetAddress.getByName(it) } as? java.net.Inet4Address
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }

        interfaces.forEach { face ->
            face.interfaceAddresses.forEach { adr ->
                val locale = adr.address as? java.net.Inet4Address ?: return@forEach
                if (cible != null && memeReseau(locale, cible, adr.networkPrefixLength.toInt())) {
                    return@runCatching locale.hostAddress
                }
            }
        }
        // Sans récepteur connu, la moins mauvaise réponse reste une adresse
        // privée quelconque — mais jamais la boucle locale.
        interfaces.asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    private fun memeReseau(a: java.net.Inet4Address, b: java.net.Inet4Address, prefixe: Int): Boolean {
        if (prefixe !in 1..32) return false
        val masque = (-1 shl (32 - prefixe))
        fun entier(adr: java.net.Inet4Address) =
            adr.address.fold(0) { acc, octet -> (acc shl 8) or (octet.toInt() and 0xFF) }
        return (entier(a) and masque) == (entier(b) and masque)
    }

    private fun isPlaylist(url: String, contentType: String?): Boolean =
        contentType?.contains("mpegurl", ignoreCase = true) == true ||
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    /**
     * Réécrit toutes les URI d'une playlist pour qu'elles repassent par ici.
     *
     * Les relatives sont d'abord résolues contre l'URL d'origine : les laisser
     * relatives « marcherait » — libVLC les résoudrait contre l'URL locale, qui
     * mène bien ici — mais seulement tant que la playlist et ses segments
     * partagent un domaine, ce qui n'est pas garanti.
     */
    private fun rewritePlaylist(body: String, baseUrl: String): String {
        val base = runCatching { URI(baseUrl) }.getOrNull() ?: return body
        fun proxied(raw: String): String {
            val absolute = runCatching { base.resolve(raw).toString() }.getOrDefault(raw)
            return localPath(absolute)
        }
        return body.lineSequence().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                // Les attributs URI="…" des balises : clé de chiffrement,
                // pistes audio et sous-titres alternatives.
                line.startsWith("#") && URI_ATTRIBUTE.containsMatchIn(line) ->
                    URI_ATTRIBUTE.replace(line) { m -> "URI=\"${proxied(m.groupValues[1])}\"" }
                line.startsWith("#") -> line
                else -> proxied(line.trim())
            }
        }
    }

    private fun writeHead(
        output: OutputStream,
        code: Int,
        message: String,
        contentType: String,
        contentLength: Long?,
        contentRange: String?,
    ) {
        val head = buildString {
            append("HTTP/1.1 $code ${message.ifBlank { "OK" }}\r\n")
            append("Content-Type: $contentType\r\n")
            contentLength?.let { append("Content-Length: $it\r\n") }
            contentRange?.let { append("Content-Range: $it\r\n") }
            append("Accept-Ranges: bytes\r\n")
            // **CORS, et seulement quand on sert quelqu'un d'autre.**
            //
            // Le récepteur Cast par défaut est une page web : il lit une
            // playlist HLS en XHR, donc le navigateur exige l'autorisation
            // d'origine. Sans elle, le récepteur répond `LOAD_FAILED` puis
            // `idleReason: "ERROR"` — mesuré — sans jamais dire que c'est le
            // CORS qui manque. Le lecteur local, lui, n'en a que faire ; on ne
            // les pose donc pas en boucle locale.
            if (ouvertAuReseau) {
                append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
                append("Access-Control-Allow-Headers: *\r\n")
                // Sans exposition, le lecteur ne voit ni la longueur ni la
                // plage servie : il ne sait plus où il en est dans le fichier.
                append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
            }
            // Une connexion par requête : libVLC en ouvre autant qu'il veut, et
            // la gestion du maintien en vie n'apporterait rien en local.
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray())
    }

    private fun respondStatus(output: OutputStream, code: Int, message: String) {
        writeHead(output, code, message, "text/plain", 0L, null)
        output.flush()
    }

    /** Taille par URL, relevée une fois (succès seulement). */
    private val tailles = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Cadence par URL, sur toute sa vie : le modèle de budget du serveur. */
    private val compteurs = java.util.concurrent.ConcurrentHashMap<String, Compteur>()

    /**
     * Premier octet refusé par URL — le « mur » googlevideo, une fois
     * rencontré. Passé ce constat, l'URL est servie comme un fichier qui
     * s'arrête au mur : le lecteur voit une fin, pas une panne.
     */
    private val murs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private companion object {
        val URI_ATTRIBUTE = Regex("""URI="([^"]+)"""")

        /** `dur=138.560` dans la query d'une URL googlevideo. */
        val DUREE_QUERY = Regex("""[?&]dur=([0-9.]+)""")

        /**
         * Taille d'un morceau. **Un mégaoctet, et c'est mesuré** : sur une même
         * URL googlevideo, 256 Ko à 2 Mo passent en 206, 4 Mo finit en 403.
         */
        const val MORCEAU = 1L * 1024 * 1024

        /**
         * Avance servie sans attendre, avant que la cadence ne prenne la main.
         * Elle couvre l'ouverture — sondage des pistes, en-tête MP4 — et donne
         * au lecteur quelques secondes de réserve contre les à-coups, en
         * restant sous la marge mesurée du budget googlevideo (~7 Mo).
         */
        const val RAFALE = 4L * 1024 * 1024

        /** Pas d'attente de la cadence : court, pour suivre le temps réel de près. */
        const val PAS_CADENCE_MS = 100L

        /** Tentatives par morceau avant d'abandonner, hors mur (pannes ordinaires). */
        const val TENTATIVES = 10
        const val ATTENTE_MS = 500L

        /**
         * Durée de refus persistant avant d'y voir le mur, et l'espacement des
         * réessais. Huit secondes : la limitation de débit cède avant, le
         * plafond PO jamais (mesuré, encore 403 dix-huit secondes après).
         */
        const val PERSISTANCE_MUR_MS = 8_000L
        const val ATTENTE_403_MS = 2_000L
    }
}
