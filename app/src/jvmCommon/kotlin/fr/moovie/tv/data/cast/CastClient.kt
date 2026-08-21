package fr.moovie.tv.data.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.DataInputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket

/** Ce que le récepteur raconte de sa lecture, réduit à ce dont l'écran a besoin. */
data class CastStatus(
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Identifiant de session média, exigé par toute commande de transport. */
    val mediaSessionId: Int? = null,
)

/**
 * Client CASTV2 — la moitié « protocole » de la diffusion vers un Chromecast.
 *
 * ## Ce qu'il fait, et dans quel ordre
 *
 * 1. TLS sur le port 8009 ([CastTls]) ;
 * 2. `CONNECT` sur le canal *connection* — sans lui, le récepteur ignore tout
 *    le reste sans jamais le dire ;
 * 3. un `PING` régulier, faute de quoi il **ferme la connexion** au bout d'une
 *    dizaine de secondes ; le symptôme est une lecture qui s'arrête seule, et
 *    rien n'en donne la raison ;
 * 4. `LAUNCH` du récepteur média par défaut, qui rend un `transportId` ;
 * 5. un second `CONNECT`, vers ce `transportId` cette fois — c'est une session
 *    distincte, et l'oublier fait échouer le `LOAD` en silence ;
 * 6. `LOAD` de l'URL du relais.
 *
 * ## Un fil de lecture, des réponses corrélées
 *
 * Le récepteur répond quand il veut, dans le désordre, et émet aussi des
 * messages spontanés — l'utilisateur qui coupe le son avec sa télécommande. On
 * lit donc en continu et on rapproche les réponses de leur demande par
 * `requestId`, plutôt que de supposer que la suivante nous concerne.
 */
class CastClient(private val host: String) {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prochainId = AtomicInteger(1)

    private var socket: SSLSocket? = null
    private var sortie: OutputStream? = null

    /** Session du récepteur média, une fois lancé. Null tant qu'il ne l'est pas. */
    @Volatile
    private var transport: String? = null

    private val _status = MutableStateFlow(CastStatus())
    val status: StateFlow<CastStatus> = _status.asStateFlow()

    private val _connecte = MutableStateFlow(false)
    val connecte: StateFlow<Boolean> = _connecte.asStateFlow()

    /** Réponses attendues, par `requestId`. */
    private val attentes = java.util.concurrent.ConcurrentHashMap<Int, (JsonObject) -> Unit>()

    // ── Cycle de vie ─────────────────────────────────────────────────────────

    /** Ouvre la connexion et démarre le battement. Rend faux si le récepteur ne répond pas. */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val s = CastTls.connect(host)
            socket = s
            sortie = s.outputStream
            envoie(CAST_NS_CONNECTION, DESTINATION_RECEIVER, buildJsonObject { put("type", "CONNECT") })
            scope.launch { boucleDeLecture(DataInputStream(s.inputStream)) }
            scope.launch { battement() }
            _connecte.value = true
            true
        }.getOrElse {
            close()
            false
        }
    }

    fun close() {
        _connecte.value = false
        transport = null
        attentes.clear()
        runCatching { socket?.close() }
        socket = null
        sortie = null
        scope.cancel()
    }

    /**
     * Le récepteur coupe une connexion muette au bout d'une dizaine de secondes.
     * Cinq laissent la marge d'un paquet perdu sans rien coûter.
     */
    private suspend fun battement() {
        while (_connecte.value) {
            delay(BATTEMENT_MS)
            runCatching {
                envoie(CAST_NS_HEARTBEAT, DESTINATION_RECEIVER, buildJsonObject { put("type", "PING") })
            }.onFailure { close() }
        }
    }

    // ── Ce que l'écran demande ───────────────────────────────────────────────

    /**
     * Lance le récepteur média par défaut et rend son `transportId`.
     *
     * Idempotent : si l'application tourne déjà — parce qu'on vient de diffuser,
     * ou qu'un autre téléphone l'a lancée — on réutilise sa session au lieu d'en
     * ouvrir une seconde, ce que le récepteur refuserait.
     */
    suspend fun launch(): String? {
        transport?.let { return it }
        val reponse = demande(
            CAST_NS_RECEIVER,
            DESTINATION_RECEIVER,
            buildJsonObject {
                put("type", "LAUNCH")
                put("appId", CAST_DEFAULT_RECEIVER)
            },
        ) ?: return null

        val id = transportDe(reponse) ?: return null
        // La session du récepteur média est **distincte** de celle du récepteur
        // lui-même : sans ce second CONNECT, le LOAD part et rien n'arrive.
        envoie(CAST_NS_CONNECTION, id, buildJsonObject { put("type", "CONNECT") })
        transport = id
        return id
    }

    /**
     * Charge une URL et démarre la lecture.
     *
     * @param url celle du relais, jamais celle de l'hébergeur : le récepteur
     *   n'enverra ni `Referer` ni `User-Agent`, et beaucoup de CDN répondent 403
     *   sans eux. Voir `LocalStreamProxy`.
     * @param contentType laissé au récepteur s'il est vide ; utile pour un HLS
     *   dont l'URL ne finit pas en `.m3u8`.
     */
    suspend fun load(
        url: String,
        title: String,
        subtitle: String = "",
        artwork: String = "",
        contentType: String = "",
        positionMs: Long = 0,
    ): Boolean {
        val id = launch() ?: return false
        val reponse = demande(
            CAST_NS_MEDIA,
            id,
            buildJsonObject {
                put("type", "LOAD")
                put("autoplay", true)
                if (positionMs > 0) put("currentTime", positionMs / 1000.0)
                put(
                    "media",
                    buildJsonObject {
                        put("contentId", url)
                        put("streamType", "BUFFERED")
                        put("contentType", contentType.ifBlank { castContentType(url) })
                        put(
                            "metadata",
                            buildJsonObject {
                                // 0 = générique. Un type « film » imposerait des
                                // champs que nous n'avons pas toujours.
                                put("metadataType", 0)
                                put("title", title)
                                if (subtitle.isNotBlank()) put("subtitle", subtitle)
                                if (artwork.isNotBlank()) {
                                    put(
                                        "images",
                                        kotlinx.serialization.json.buildJsonArray {
                                            add(buildJsonObject { put("url", artwork) })
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
        return reponse != null
    }

    suspend fun playPause() {
        val id = transport ?: return
        val session = _status.value.mediaSessionId ?: return
        val type = if (_status.value.playing) "PAUSE" else "PLAY"
        envoie(
            CAST_NS_MEDIA,
            id,
            buildJsonObject {
                put("type", type)
                put("mediaSessionId", session)
                put("requestId", prochainId.getAndIncrement())
            },
        )
    }

    suspend fun seek(positionMs: Long) {
        val id = transport ?: return
        val session = _status.value.mediaSessionId ?: return
        envoie(
            CAST_NS_MEDIA,
            id,
            buildJsonObject {
                put("type", "SEEK")
                put("mediaSessionId", session)
                put("currentTime", positionMs / 1000.0)
                put("requestId", prochainId.getAndIncrement())
            },
        )
    }

    /** Arrête la lecture et rend l'écran au récepteur. */
    suspend fun stop() {
        val id = transport ?: return
        val session = _status.value.mediaSessionId ?: return
        envoie(
            CAST_NS_MEDIA,
            id,
            buildJsonObject {
                put("type", "STOP")
                put("mediaSessionId", session)
                put("requestId", prochainId.getAndIncrement())
            },
        )
    }

    // ── Transport ────────────────────────────────────────────────────────────

    private suspend fun envoie(namespace: String, destination: String, payload: JsonObject) =
        withContext(Dispatchers.IO) {
            val flux = sortie ?: return@withContext
            val trame = encodeCastMessage(
                CastMessage(SOURCE, destination, namespace, payload.toString()),
            )
            synchronized(flux) {
                flux.write(trame)
                flux.flush()
            }
        }

    /**
     * Envoie et attend la réponse portant le même `requestId`.
     *
     * Sans corrélation, on prendrait pour réponse le premier message venu — or
     * le récepteur en émet spontanément, ne serait-ce que quand quelqu'un touche
     * au volume avec sa télécommande.
     */
    private suspend fun demande(
        namespace: String,
        destination: String,
        payload: JsonObject,
    ): JsonObject? {
        val id = prochainId.getAndIncrement()
        val complet = buildJsonObject {
            payload.forEach { (k, v) -> put(k, v) }
            put("requestId", id)
        }
        val resultat = kotlinx.coroutines.CompletableDeferred<JsonObject?>()
        attentes[id] = { reponse -> resultat.complete(reponse) }
        envoie(namespace, destination, complet)
        return kotlinx.coroutines.withTimeoutOrNull(REPONSE_MS) { resultat.await() }
            .also { attentes.remove(id) }
    }

    private suspend fun boucleDeLecture(entree: DataInputStream) {
        while (_connecte.value) {
            val message = runCatching { litTrame(entree) }.getOrNull() ?: break
            val corps = runCatching { json.parseToJsonElement(message.payload).jsonObject }.getOrNull()
                ?: continue

            corps["requestId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?.let { attentes.remove(it)?.invoke(corps) }

            when (corps["type"]?.jsonPrimitive?.contentOrNull) {
                // Le récepteur demande la fermeture : inutile d'insister.
                "CLOSE" -> break
                "MEDIA_STATUS" -> misAJour(corps)
                "RECEIVER_STATUS" -> transportDe(corps)?.let { transport = it }
            }
        }
        close()
    }

    private fun litTrame(entree: DataInputStream): CastMessage? {
        val taille = entree.readInt()
        if (taille <= 0 || taille > TRAME_MAX) return null
        val corps = ByteArray(taille)
        entree.readFully(corps)
        return decodeCastMessage(corps)
    }

    private fun misAJour(corps: JsonObject) {
        _status.value = parseMediaStatus(corps, _status.value) ?: return
    }

    private fun transportDe(corps: JsonObject): String? = transportIdOf(corps)

    private companion object {
        const val SOURCE = "sender-moovie"
        const val DESTINATION_RECEIVER = "receiver-0"
        const val BATTEMENT_MS = 5_000L
        const val REPONSE_MS = 8_000L

        /** Un `RECEIVER_STATUS` fait quelques kilo-octets ; au-delà, c'est du bruit. */
        const val TRAME_MAX = 1 shl 20
    }
}
