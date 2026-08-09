package fr.moovie.tv.desktop

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
 * Relais HTTP en boucle locale, placé entre libVLC et l'hébergeur.
 *
 * **Pourquoi.** Beaucoup de CDN n'acceptent une requête qu'accompagnée d'un
 * `Referer` *et* d'un `User-Agent` — mesuré sur vidzy : chacun seul rend 403,
 * les deux ensemble 200. libVLC sait poser ces en-têtes sur l'URL qu'on lui
 * donne, mais **pas sur les segments** : son démultiplexeur adaptatif ouvre ses
 * propres connexions HTTP, qui n'héritent pas du `Referer`. Les quatre façons
 * de le lui passer ont été essayées (option de média, option d'instance, les
 * deux, plus le `User-Agent` en instance) : aucune ne marche.
 *
 * Le symptôme n'a rien d'un refus. La playlist se lit, donc la source est
 * annoncée jouable ; puis aucun segment n'arrive, donc aucune piste, donc
 * `0:00 / 0:00` — et libVLC déclare la fin, ce que l'application prenait pour
 * un épisode terminé. Android l'ignore : ExoPlayer applique les en-têtes à
 * toutes ses requêtes.
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
internal class LocalStreamProxy(private val headers: Map<String, String>) {

    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))

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

    /** URL locale à donner au lecteur à la place de [url]. */
    fun localUrl(url: String): String = "http://127.0.0.1:${server.localPort}${localPath(url)}"

    fun shutdown() {
        running = false
        runCatching { server.close() }
        runCatching { workers.shutdownNow() }
    }

    private fun localPath(url: String): String =
        "/u/" + Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray())

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
        val target = decodeTarget(path)
        if (target == null) {
            respondStatus(output, 404, "Not Found")
            return
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

    private fun decodeTarget(path: String): String? {
        if (!path.startsWith("/u/")) return null
        val encoded = path.removePrefix("/u/")
        return runCatching { String(Base64.getUrlDecoder().decode(encoded)) }.getOrNull()
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

    private companion object {
        val URI_ATTRIBUTE = Regex("""URI="([^"]+)"""")
    }
}
