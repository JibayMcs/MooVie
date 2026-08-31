package fr.moovie.tv.data.trailer

import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Résout une clé vidéo YouTube (celle que rend TMDB) en flux jouable, **sur
 * l'appareil** — même principe que les extracteurs d'hébergeurs, même contrat :
 * en cas d'échec on rend null, jamais une URL plausible ramassée au passage.
 *
 * ## Pourquoi l'API interne et pas la page de lecture
 *
 * La page `watch` ne livre pas d'URL directe : elle livre un `signatureCipher`
 * qu'il faut déchiffrer avec une fonction extraite d'un JavaScript obfusqué que
 * YouTube renouvelle en permanence. Porter ce déchiffrement, c'est signer pour
 * une course sans fin — exactement ce que la mise en garde de fsvid/vidzy dit de
 * ne pas faire (« ne parse pas leur JavaScript pour récupérer la clé »).
 *
 * Les clients **mobiles et TV**, eux, reçoivent des URLs déjà signées : ils
 * tournent sur des appareils incapables d'exécuter ce JavaScript, donc YouTube
 * ne leur en envoie pas. On se présente comme l'un d'eux auprès de l'endpoint
 * `youtubei/v1/player` et on lit `streamingData` tel quel.
 *
 * ## Pourquoi une cascade de clients
 *
 * Aucun de ces clients n'est stable dans le temps : YouTube en restreint un tous
 * les quelques mois (jeton `po_token` exigé, `playabilityStatus` en échec). Un
 * seul client codé en dur, c'est une bande-annonce qui cesse de marcher du jour
 * au lendemain sans que rien ne le signale. On les essaie donc dans l'ordre et
 * le premier qui rend un flux gagne — même logique que la cascade de sources, où
 * un catalogue mort ne doit pas bloquer les autres.
 *
 * Il n'y a **pas de repli silencieux** : si aucun client ne répond, l'appelant
 * reçoit null et le bouton « Bande-annonce » ne s'affiche pas. Une bande-annonce
 * absente est un manque visible ; une qui tourne dans le vide est un bug.
 */
class YoutubeTrailerExtractor(
    private val http: HttpGateway,
    private val manifestStore: DashManifestStore = DashManifestStore(),
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param videoId la clé TMDB (`key` de `/videos`), pas une URL.
     * @param preferredAudio code langue à 2 lettres pour la piste audio, quand la
     *        vidéo en propose plusieurs (bande-annonce doublée).
     */
    suspend fun resolve(videoId: String, preferredAudio: String = "en"): PlayableStream? =
        resolveDetailed(videoId, preferredAudio)?.stream

    /**
     * Même résolution, en disant **qui** a répondu.
     *
     * L'app n'en a pas besoin, la sonde si : un taux de réussite global masque
     * une mono-dépendance, et le jour où deux clients sur trois sont restreints
     * on veut le voir avant que le dernier ne tombe — pas après.
     */
    suspend fun resolveDetailed(videoId: String, preferredAudio: String = "en"): TrailerResolution? {
        if (!VIDEO_ID.matches(videoId)) return null

        // Deux passes, et la raison est mesurée : sur une bande-annonce de
        // studio, googlevideo **refuse de servir les pistes séparées au-delà
        // d'environ 38 % du fichier** — 403 sur une URL neuve, quel que soit le
        // client, la cadence ou la forme de la requête. Les flux entiers (HLS,
        // ou progressif image+son) échappent à ce bridage : mesuré, le
        // progressif du client ANDROID se sert jusqu'au bout quand le 1080p du
        // même client rend 403.
        //
        // La première passe ne retient donc **que** ces formes-là, en essayant
        // tous les clients. La seconde accepte le manifeste DASH fabriqué à
        // partir des pistes séparées : une bande-annonce qui s'arrête à mi-
        // parcours reste préférable à pas de bande-annonce du tout.
        val reponses = mutableListOf<Pair<InnerTubeClient, PlayerResponse>>()
        for (client in CLIENTS) {
            val response = request(client, videoId, preferredAudio) ?: continue
            // « OK » seulement : LOGIN_REQUIRED, UNPLAYABLE et AGE_VERIFICATION
            // s'accompagnent parfois d'un streamingData partiel qui ne joue pas.
            if (response.playabilityStatus?.status != "OK") continue
            reponses += client to response
            val stream = response.streamingData?.let { pick(it, client, entierSeulement = true) }
            if (stream != null) return resolution(stream, client, response)
        }
        for ((client, response) in reponses) {
            val stream = response.streamingData?.let { pick(it, client, entierSeulement = false) }
            if (stream != null) return resolution(stream, client, response)
        }
        return null
    }

    private fun resolution(stream: PlayableStream, client: InnerTubeClient, response: PlayerResponse) =
        TrailerResolution(
            stream = stream,
            client = client.name,
            durationSeconds = response.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
        )

    private suspend fun request(
        client: InnerTubeClient,
        videoId: String,
        preferredAudio: String,
    ): PlayerResponse? {
        val body = buildJsonObject {
            put("context", buildJsonObject { put("client", client.context(preferredAudio)) })
            put("videoId", videoId)
            // Sans ces deux drapeaux, une bande-annonce classée « sensible »
            // (horreur, film interdit aux mineurs) revient en UNPLAYABLE.
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val response = http.fetch(
            HttpRequest(
                url = PLAYER_ENDPOINT,
                method = HttpMethod.POST,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "User-Agent" to client.userAgent,
                    "X-Youtube-Client-Name" to client.id.toString(),
                    "X-Youtube-Client-Version" to client.version,
                    "Origin" to "https://www.youtube.com",
                ),
                json = body.toString(),
            ),
        ) ?: return null

        if (!response.isSuccessful) return null
        val payload = response.body ?: return null
        return runCatching { json.decodeFromString<PlayerResponse>(payload) }.getOrNull()
    }

    /**
     * Choisit le flux à jouer.
     *
     * @param entierSeulement ne rend qu'un flux que googlevideo sert **jusqu'au
     *   bout** : manifeste HLS, ou progressif (image et son dans la même URL).
     *   Les pistes séparées d'une bande-annonce de studio sont bridées à ~38 %
     *   du fichier — voir [resolveDetailed]. Le progressif est souvent limité à
     *   360p, et c'est un échange qu'on assume : une bande-annonce entière en
     *   360p vaut mieux qu'une minute de 1080p suivie d'un gel.
     *
     * Un format sans `url` est un format signé qu'on ne sait pas déchiffrer. Il
     * est écarté plutôt que rendu : c'est une URL vide, pas une source.
     */
    private fun pick(
        data: StreamingData,
        client: InnerTubeClient,
        entierSeulement: Boolean,
    ): PlayableStream? {
        val headers = mapOf("User-Agent" to client.userAgent)

        data.hlsManifestUrl?.takeIf { it.startsWith("https://") }?.let {
            return PlayableStream(url = it, format = StreamFormat.HLS, headers = headers)
        }

        data.formats
            .filter { !it.url.isNullOrBlank() && it.mimeType.startsWith("video/") }
            // `audioQuality` absent = piste muette : une bande-annonce sans son
            // ressemble à un lecteur cassé, pas à une bande-annonce.
            .filter { it.audioQuality != null }
            .maxByOrNull { it.height }
            ?.let { p ->
                return PlayableStream(
                    url = p.url!!,
                    format = StreamFormat.MP4,
                    headers = headers,
                    quality = p.height.takeIf { it > 0 }?.let { "${it}p" },
                )
            }

        if (entierSeulement) return null

        val tracks = data.adaptiveFormats
            // `isDrc` marque les variantes à compression dynamique : même itag,
            // même contenu, mais deux représentations d'un même identifiant dans
            // le manifeste — ce qui le rend invalide.
            .filter { !it.isDrc && !it.url.isNullOrBlank() }
            .map { it.toTrack() }
        val manifest = buildYoutubeDashManifest(tracks) ?: return null
        val file = manifestStore.write(manifest) ?: return null

        // Les mêmes pistes, non emballées. Le manifeste reste l'URL principale
        // — Media3 le lit sans broncher et y gagne l'adaptation de qualité —
        // mais le lecteur desktop ne sait pas lire nos `BaseURL` googlevideo
        // (démuxeur DASH de FFmpeg), et préfère ouvrir les deux directement.
        // Rendre les deux formes évite d'imposer à l'un le détour qui ne sert
        // qu'à l'autre.
        return PlayableStream(
            url = file,
            format = StreamFormat.DASH,
            headers = headers,
            quality = tracks.filter { it.isVideo }.maxOfOrNull { it.height }?.let { "${it}p" },
            videoOnlyUrl = youtubeVideoTracks(tracks).firstOrNull()?.url,
            audioOnlyUrl = youtubeAudioTracks(tracks).firstOrNull()?.url,
        )
    }

    private companion object {
        const val PLAYER_ENDPOINT = "https://www.youtube.com/youtubei/v1/player"

        /** Une clé YouTube fait 11 caractères d'un alphabet base64 URL. */
        val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

        /**
         * Ordre délibéré. iOS en tête parce que c'est le seul à rendre un
         * `hlsManifestUrl` — de la vraie qualité adaptative, et servie en
         * entier. ANDROID juste après parce qu'il est le seul à rendre un
         * **flux progressif**, la seule forme que googlevideo sert jusqu'au
         * bout sur une bande-annonce de studio (mesuré : son 360p passe là où
         * le 1080p du même client rend 403 à 38 % du fichier).
         *
         * Les deux derniers sont des replis pour les jours où les premiers sont
         * restreints — ce qui arrive, et c'est toute la raison de la cascade.
         */
        val CLIENTS = listOf(
            InnerTubeClient(
                id = 5,
                name = "IOS",
                version = "20.10.4",
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)",
                extra = {
                    put("deviceMake", "Apple")
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iPhone")
                    put("osVersion", "18.3.2.22D82")
                },
            ),
            InnerTubeClient(
                id = 3,
                name = "ANDROID",
                version = "20.10.38",
                userAgent = "com.google.android.youtube/20.10.38 " +
                    "(Linux; U; Android 14; SM-S928B Build/UP1A.231005.007) gzip",
                extra = {
                    put("osName", "Android")
                    put("osVersion", "14")
                    put("androidSdkVersion", 34)
                },
            ),
            InnerTubeClient(
                id = 28,
                name = "ANDROID_VR",
                version = "1.62.27",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 " +
                    "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                extra = {
                    put("deviceMake", "Oculus")
                    put("deviceModel", "Quest 3")
                    put("osName", "Android")
                    put("osVersion", "12L")
                    put("androidSdkVersion", 32)
                },
            ),
            InnerTubeClient(
                id = 85,
                name = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                version = "2.0",
                userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/16.0 Safari/605.1.15",
                extra = { put("clientScreen", "EMBED") },
            ),
        )
    }
}

/**
 * Flux d'une bande-annonce, avec de quoi diagnostiquer d'où il vient.
 *
 * [durationSeconds] n'est pas là pour le garde-fou de durée du lecteur — une
 * bande-annonce de deux minutes contre un film de deux heures le ferait rejeter
 * à coup sûr — mais pour l'autoplay du hero, qui doit savoir quand se rendre.
 */
data class TrailerResolution(
    val stream: PlayableStream,
    val client: String,
    val durationSeconds: Int,
)

/**
 * Client YouTube usurpé. Ce ne sont pas des valeurs décoratives : l'endpoint
 * croise le nom, la version et le User-Agent, et répond en 400 dès que l'un des
 * trois détonne.
 */
private data class InnerTubeClient(
    val id: Int,
    val name: String,
    val version: String,
    val userAgent: String,
    val extra: JsonObjectBuilderScope,
) {
    fun context(preferredAudio: String): JsonObject = buildJsonObject {
        put("clientName", name)
        put("clientVersion", version)
        put("hl", preferredAudio)
        put("gl", "FR")
        extra(this)
    }
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder.() -> Unit

@Serializable
private data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
)

@Serializable
private data class PlayabilityStatus(val status: String = "", val reason: String = "")

@Serializable
private data class StreamingData(
    val hlsManifestUrl: String? = null,
    /** Flux progressifs : image et son dans le même conteneur. Vide en pratique. */
    val formats: List<YtFormat> = emptyList(),
    /** Pistes séparées, ce que YouTube sert réellement aujourd'hui. */
    val adaptiveFormats: List<YtFormat> = emptyList(),
)

@Serializable
private data class YtFormat(
    val itag: Int = 0,
    val url: String? = null,
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val bitrate: Long = 0,
    val audioQuality: String? = null,
    val audioSampleRate: String = "",
    val audioChannels: Int = 0,
    val initRange: YtRange? = null,
    val indexRange: YtRange? = null,
    val approxDurationMs: String = "",
    /** Variante à compression dynamique : doublon de l'itag, à écarter. */
    val isDrc: Boolean = false,
    @SerialName("signatureCipher") val signatureCipher: String? = null,
) {
    fun toTrack() = YtTrack(
        itag = itag,
        url = url.orEmpty(),
        mimeType = mimeType,
        bitrate = bitrate,
        initRange = initRange?.toIntRange(),
        indexRange = indexRange?.toIntRange(),
        durationMs = approxDurationMs.toLongOrNull() ?: 0L,
        width = width,
        height = height,
        fps = fps,
        audioSampleRate = audioSampleRate.toIntOrNull() ?: 0,
        audioChannels = audioChannels,
    )
}

/** Bornes d'octets, que YouTube sérialise en chaînes et non en entiers. */
@Serializable
private data class YtRange(val start: String = "", val end: String = "") {
    fun toIntRange(): IntRange? {
        val s = start.toIntOrNull() ?: return null
        val e = end.toIntOrNull() ?: return null
        return if (e >= s) s..e else null
    }
}

@Serializable
private data class VideoDetails(
    val title: String = "",
    val lengthSeconds: String = "",
)
