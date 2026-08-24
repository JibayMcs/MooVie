package fr.moovie.tv.data.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
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

    /**
     * Le son du récepteur, à part du reste.
     *
     * Il ne vient **pas** du même message que la lecture : le volume est une
     * affaire de récepteur (`RECEIVER_STATUS`), la position une affaire de média
     * (`MEDIA_STATUS`). Les mêler dans un seul état ferait qu'un message de l'un
     * écrase ce que l'autre avait dit, ce qui est exactement le défaut que
     * [parseMediaStatus] documente déjà pour la durée.
     */
    private val _volume = MutableStateFlow(CastVolume())
    val volume: StateFlow<CastVolume> = _volume.asStateFlow()

    /** Réponses attendues, par `requestId`. */
    private val attentes = java.util.concurrent.ConcurrentHashMap<Int, (JsonObject) -> Unit>()

    // ── Cycle de vie ─────────────────────────────────────────────────────────

    /** Ouvre la connexion et démarre le battement. Rend faux si le récepteur ne répond pas. */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val s = CastTls.connect(host)
            socket = s
            sortie = s.outputStream
            // **Avant de lancer la boucle**, pas après : elle boucle sur ce
            // drapeau, et démarrée à faux elle sortait aussitôt en appelant
            // `close()`. Symptôme mesuré : le LAUNCH part, plus rien ne revient,
            // pas même le PONG — une connexion muette qui a l'air d'un problème
            // de réseau alors que c'est une course de deux lignes.
            _connecte.value = true
            // Le délai de lecture du handshake ne vaut pas pour la suite : cette
            // connexion vit des heures et se tait entre deux battements.
            runCatching { s.soTimeout = 0 }
            envoie(CAST_NS_CONNECTION, DESTINATION_RECEIVER, buildJsonObject { put("type", "CONNECT") })
            scope.launch { boucleDeLecture(DataInputStream(s.inputStream)) }
            scope.launch { battement() }
            // Le volume ne se devine pas. Sans cette demande, l'écran l'affiche
            // au maximum jusqu'au premier `RECEIVER_STATUS` — c'est-à-dire
            // jusqu'au LAUNCH — et un curseur qui saute une seconde après
            // l'ouverture se lit comme un réglage qui a bougé tout seul.
            scope.launch { refreshStatus() }
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
        // **Les enfants, pas le scope.** `cancel()` rend le scope inutilisable à
        // jamais : la boucle de lecture relancée par un `connect()` ultérieur ne
        // démarrait plus du tout. Symptôme mesuré — le LAUNCH part, aucune trame
        // ne revient, et rien ne distingue ça d'un réseau qui ne répond pas.
        //
        // Le piège se referme tout seul : `CastSession.start` ferme la session
        // précédente avant d'ouvrir la sienne, donc le tout premier `connect()`
        // héritait déjà d'un scope mort.
        runCatching { scope.coroutineContext.cancelChildren() }
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
        sousTitres: CastPisteTexte? = null,
    ): Boolean {
        val id = launch() ?: return false
        val reponse = demande(
            CAST_NS_MEDIA,
            id,
            buildJsonObject {
                put("type", "LOAD")
                put("autoplay", true)
                // **La piste doit être activée à l'appel.** Déclarée seule, elle
                // apparaît dans le menu du récepteur et rien ne s'affiche : il
                // n'allume pas de sous-titres qu'on ne lui a pas demandés, et
                // le menu n'est pas atteignable depuis un téléphone.
                sousTitres?.let {
                    put(
                        "activeTrackIds",
                        kotlinx.serialization.json.buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive(PISTE_TEXTE))
                        },
                    )
                }
                if (positionMs > 0) put("currentTime", positionMs / 1000.0)
                put(
                    "media",
                    buildJsonObject {
                        put("contentId", url)
                        put("streamType", "BUFFERED")
                        sousTitres?.let { piste ->
                            put(
                                "tracks",
                                kotlinx.serialization.json.buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("trackId", PISTE_TEXTE)
                                            put("type", "TEXT")
                                            put("subtype", "SUBTITLES")
                                            // Le récepteur **ne lit que du
                                            // WebVTT**. Un SRT est accepté au
                                            // chargement puis ignoré, sans
                                            // erreur ni piste — voir srtToVtt.
                                            put("trackContentType", "text/vtt")
                                            put("trackContentId", piste.url)
                                            put("language", piste.langue)
                                            put("name", piste.nom)
                                        },
                                    )
                                },
                            )
                        }
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
        // **`LOAD_FAILED` est une réponse.** Se contenter d'en avoir reçu une
        // faisait dire « accepté » à un chargement refusé — mesuré pendant la
        // mise au point, où l'écran aurait annoncé une diffusion partie alors
        // que la télé affichait une erreur.
        val type = reponse?.get("type")?.jsonPrimitive?.contentOrNull
        return reponse != null && type != "LOAD_FAILED" && type != "INVALID_REQUEST"
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

    /**
     * Demande l'état du récepteur — volume compris — **sans rien lancer**.
     *
     * C'est la seule requête du protocole qui ne change rien à l'appareil : elle
     * ne réveille pas l'écran, n'interrompt pas ce qui joue, ne prend la main sur
     * rien. Ce qui en fait aussi la sonde à préférer quand on veut savoir ce
     * qu'un vrai Chromecast raconte sans lui imposer quoi que ce soit.
     */
    suspend fun refreshStatus() {
        envoie(
            CAST_NS_RECEIVER,
            DESTINATION_RECEIVER,
            buildJsonObject {
                put("type", "GET_STATUS")
                put("requestId", prochainId.getAndIncrement())
            },
        )
    }

    /**
     * Règle le niveau du récepteur, entre 0 et 1.
     *
     * ## Deux choix qui se voient à l'usage
     *
     * **Le canal est celui du récepteur, pas du média.** Le volume appartient à
     * l'appareil : il survit au film, et un `transportId` n'est pas requis — ce
     * qui permet de le régler avant même qu'un média soit chargé.
     *
     * **On démute en même temps.** Monter le son d'un récepteur coupé ne produit
     * rien d'audible : on tirerait le curseur en n'entendant toujours rien, et
     * l'appareil aurait pourtant obéi. Les deux champs partent donc ensemble.
     *
     * L'état local est mis à jour sans attendre la confirmation. Le récepteur
     * répond par un `RECEIVER_STATUS` qui prend son temps, et un curseur qui
     * attendrait ce retour paraîtrait collé — le même parti que la position dans
     * [CastPlayerScreen][fr.moovie.tv.ui.remote.CastPlayerScreen].
     */
    suspend fun setVolume(level: Double) {
        val borne = level.coerceIn(0.0, 1.0)
        _volume.value = _volume.value.copy(level = borne, muted = false)
        envoie(
            CAST_NS_RECEIVER,
            DESTINATION_RECEIVER,
            buildJsonObject {
                put("type", "SET_VOLUME")
                put(
                    "volume",
                    buildJsonObject {
                        put("level", borne)
                        put("muted", false)
                    },
                )
                put("requestId", prochainId.getAndIncrement())
            },
        )
    }

    /** Coupe ou rétablit le son, **sans toucher au niveau** : il sera retrouvé tel quel. */
    suspend fun setMuted(muted: Boolean) {
        _volume.value = _volume.value.copy(muted = muted)
        envoie(
            CAST_NS_RECEIVER,
            DESTINATION_RECEIVER,
            buildJsonObject {
                put("type", "SET_VOLUME")
                put("volume", buildJsonObject { put("muted", muted) })
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
                "RECEIVER_STATUS" -> {
                    transportDe(corps)?.let { transport = it }
                    // Le récepteur en émet **spontanément** : c'est ce qui fait
                    // suivre le curseur quand quelqu'un touche à la télécommande
                    // de la télé, sans que nous ayons rien demandé.
                    parseReceiverVolume(corps, _volume.value)?.let { _volume.value = it }
                }
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
        /** Identifiant de notre unique piste texte : on n'en propose qu'une. */
        const val PISTE_TEXTE = 1

        const val SOURCE = "sender-moovie"
        const val DESTINATION_RECEIVER = "receiver-0"
        const val BATTEMENT_MS = 5_000L
        const val REPONSE_MS = 8_000L

        /** Un `RECEIVER_STATUS` fait quelques kilo-octets ; au-delà, c'est du bruit. */
        const val TRAME_MAX = 1 shl 20
    }
}
