package fr.moovie.tv.data.cast

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Un message du protocole CASTV2, tel qu'il circule sur le port 8009.
 *
 * @param source identifiant de l'émetteur. `sender-0` par convention, mais
 *   n'importe quelle chaîne stable convient : le récepteur la recopie dans ses
 *   réponses, c'est ce qui permet de savoir à qui elles s'adressent.
 * @param destination `receiver-0` pour le récepteur lui-même ; l'identifiant de
 *   session (`transportId`) une fois une application lancée.
 * @param namespace le canal, par exemple `urn:x-cast:com.google.cast.receiver`.
 * @param payload du JSON. Le protocole prévoit aussi du binaire, qu'aucun canal
 *   qui nous intéresse n'utilise.
 */
data class CastMessage(
    val source: String,
    val destination: String,
    val namespace: String,
    val payload: String,
)

/** Poignée de main et fermeture de canal. */
const val CAST_NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"

/** `PING` / `PONG`. Le récepteur ferme la connexion s'il n'a plus de nouvelles. */
const val CAST_NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"

/** État du récepteur, lancement et arrêt d'applications, volume. */
const val CAST_NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"

/** Chargement et transport d'un média, une fois l'application lancée. */
const val CAST_NS_MEDIA = "urn:x-cast:com.google.cast.media"

/**
 * Le récepteur média par défaut de Google.
 *
 * **C'est lui qui évite le SDK et l'inscription payante.** Publier un récepteur
 * à soi impose de s'enregistrer chez Google et d'héberger une page web ; celui-ci
 * est public, présent sur tous les appareils, et sait lire HLS, DASH et MP4 —
 * tout ce que nos extracteurs rendent.
 */
const val CAST_DEFAULT_RECEIVER = "CC1AD845"

/**
 * Encode un message, préfixé de sa longueur sur quatre octets en gros-boutien.
 *
 * ## Pourquoi c'est écrit à la main
 *
 * `CastMessage` est un protobuf de **six champs**, tous scalaires. Tirer une
 * bibliothèque de génération de code pour ça — et la chaîne de compilation qui
 * va avec — coûterait plus cher que les quarante lignes ci-dessous, pour un
 * schéma qui n'a pas bougé depuis 2013. C'est ce qui permet de parler Cast sans
 * la moindre dépendance Google, et c'est tout l'objet de l'exercice.
 *
 * Les numéros de champ viennent du schéma officiel `cast_channel.proto` :
 * 1 = version du protocole, 2 = source, 3 = destination, 4 = namespace,
 * 5 = type de charge utile, 6 = charge utile texte.
 */
fun encodeCastMessage(message: CastMessage): ByteArray {
    val corps = buildList {
        add(champVarint(1, 0)) // CASTV2_1_0
        add(champTexte(2, message.source))
        add(champTexte(3, message.destination))
        add(champTexte(4, message.namespace))
        add(champVarint(5, 0)) // payload_type = STRING
        add(champTexte(6, message.payload))
    }.reduce { a, b -> a + b }

    return byteArrayOf(
        (corps.size ushr 24).toByte(),
        (corps.size ushr 16).toByte(),
        (corps.size ushr 8).toByte(),
        corps.size.toByte(),
    ) + corps
}

/**
 * Relit un message, ou rend null si la trame est illisible.
 *
 * **Tolérante par construction.** On saute tout champ qu'on ne connaît pas au
 * lieu d'échouer : le protocole peut en gagner, et un récepteur plus récent que
 * nous ne doit pas rendre la connexion inutilisable. C'est la même discipline
 * que le `ignoreUnknownKeys` du client de télécommande, pour la même raison —
 * les deux bouts ne se mettent pas à jour ensemble.
 */
fun decodeCastMessage(corps: ByteArray): CastMessage? {
    var i = 0
    var source = ""
    var destination = ""
    var namespace = ""
    var payload = ""

    while (i < corps.size) {
        val (cle, apresCle) = litVarint(corps, i) ?: return null
        i = apresCle
        when ((cle and 0x7L).toInt()) {
            0 -> i = (litVarint(corps, i) ?: return null).second
            2 -> {
                val (taille, apresTaille) = litVarint(corps, i) ?: return null
                val fin = apresTaille + taille.toInt()
                if (taille < 0 || fin > corps.size) return null
                val valeur = String(corps, apresTaille, taille.toInt())
                when ((cle ushr 3).toInt()) {
                    2 -> source = valeur
                    3 -> destination = valeur
                    4 -> namespace = valeur
                    6 -> payload = valeur
                }
                i = fin
            }
            // Un champ d'un type qu'on ne sait pas sauter : impossible de
            // retrouver la suite, mieux vaut le dire que deviner.
            else -> return null
        }
    }
    if (namespace.isEmpty()) return null
    return CastMessage(source, destination, namespace, payload)
}

private fun champVarint(numero: Int, valeur: Long): ByteArray =
    varint((numero shl 3).toLong()) + varint(valeur)

private fun champTexte(numero: Int, valeur: String): ByteArray {
    val data = valeur.toByteArray()
    return varint((numero shl 3 or 2).toLong()) + varint(data.size.toLong()) + data
}

private fun varint(valeur: Long): ByteArray {
    var reste = valeur
    val sortie = ArrayList<Byte>(4)
    while (true) {
        val septBits = (reste and 0x7F).toInt()
        reste = reste ushr 7
        sortie.add(if (reste != 0L) (septBits or 0x80).toByte() else septBits.toByte())
        if (reste == 0L) return sortie.toByteArray()
    }
}

/** Rend la valeur et l'indice suivant, ou null si la trame est tronquée. */
private fun litVarint(corps: ByteArray, depart: Int): Pair<Long, Int>? {
    var resultat = 0L
    var decalage = 0
    var i = depart
    while (i < corps.size) {
        val octet = corps[i].toInt() and 0xFF
        resultat = resultat or ((octet and 0x7F).toLong() shl decalage)
        i++
        if (octet and 0x80 == 0) return resultat to i
        decalage += 7
        // Au-delà de dix octets, ce n'est plus un varint mais du bruit.
        if (decalage > 63) return null
    }
    return null
}

/**
 * Lit un `MEDIA_STATUS` et en tire ce dont l'écran a besoin.
 *
 * ## Pourquoi ce n'est pas dans le client
 *
 * C'est la seule partie du protocole qui **décide de ce qu'on affiche**, et donc
 * la seule qui puisse mentir visiblement. Une durée à zéro donne une barre
 * figée, une position mal convertie fait sauter le curseur. Sortie du client,
 * elle se vérifie sur des charges utiles réelles sans ouvrir de socket.
 *
 * ## Les champs manquants ne remettent rien à zéro
 *
 * Le récepteur émet des `MEDIA_STATUS` **partiels** : un simple changement de
 * volume ne réénonce ni la durée ni l'identifiant de session. Les écraser avec
 * zéro ferait clignoter la barre de progression à chaque message — d'où
 * [precedent], sur lequel on retombe. C'est la même leçon que le mini-lecteur
 * de la télécommande, où un relevé perdu effaçait l'affichage.
 */
fun parseMediaStatus(corps: JsonObject, precedent: CastStatus = CastStatus()): CastStatus? {
    val etat = corps["status"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
    val duree = etat["media"]?.jsonObject?.get("duration")?.jsonPrimitive?.doubleOrNull
    val session = etat["mediaSessionId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    return CastStatus(
        playing = etat["playerState"]?.jsonPrimitive?.contentOrNull == "PLAYING",
        positionMs = ((etat["currentTime"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong(),
        durationMs = duree?.times(1000)?.toLong()?.takeIf { it > 0 } ?: precedent.durationMs,
        mediaSessionId = session ?: precedent.mediaSessionId,
    )
}

/**
 * Le son du récepteur, tel qu'il l'annonce dans ses `RECEIVER_STATUS`.
 *
 * @param level entre 0 et 1.
 * @param muted coupé. **Indépendant du niveau** : un récepteur muet garde le
 *   sien, et le rétablir ne consiste donc pas à remonter le volume.
 * @param reglable faux quand le récepteur ne laisse pas fixer son niveau —
 *   `controlType: "fixed"`, le cas d'une sortie HDMI dont le téléviseur garde la
 *   main. Lui envoyer un niveau ne produit alors **rien**, sans erreur : d'où
 *   l'intérêt de le savoir avant d'afficher un curseur qui ne ferait rien.
 * @param step le pas que l'appareil déclare (`stepInterval`), pour que les
 *   touches physiques avancent de ce qu'il attend plutôt que d'une valeur à nous.
 */
data class CastVolume(
    val level: Double = 1.0,
    val muted: Boolean = false,
    val reglable: Boolean = true,
    val step: Double = PAS_VOLUME_DEFAUT,
)

/**
 * Le pas de volume par défaut, quand le récepteur n'en déclare pas.
 *
 * Vingt crans du silence au maximum : c'est l'ordre de grandeur des quinze crans
 * d'Android, donc un appui sur la bascule produit un effet comparable à ce que le
 * geste produit d'habitude.
 */
const val PAS_VOLUME_DEFAUT = 0.05

/**
 * Lit le volume d'un `RECEIVER_STATUS`, ou rend null s'il n'en porte pas.
 *
 * Même discipline que [parseMediaStatus] : les champs absents retombent sur
 * [precedent] au lieu d'être remis à zéro. Un `RECEIVER_STATUS` émis pour une
 * autre raison — une application qui se lance — ne réénonce pas forcément le
 * `stepInterval`, et le perdre ferait ralentir les touches physiques en cours de
 * route sans que rien ne l'explique.
 */
fun parseReceiverVolume(corps: JsonObject, precedent: CastVolume = CastVolume()): CastVolume? {
    val volume = corps["status"]?.jsonObject?.get("volume")?.jsonObject ?: return null
    val controle = volume["controlType"]?.jsonPrimitive?.contentOrNull
    return CastVolume(
        level = volume["level"]?.jsonPrimitive?.doubleOrNull?.coerceIn(0.0, 1.0) ?: precedent.level,
        muted = volume["muted"]?.jsonPrimitive?.booleanOrNull ?: precedent.muted,
        // Seul « fixed » interdit de régler. « master » et « attenuation »
        // l'autorisent tous deux, et un type qu'on ne connaît pas est présumé
        // réglable : refuser par défaut priverait du curseur sur la foi d'un mot.
        reglable = controle?.let { !it.equals("fixed", ignoreCase = true) } ?: precedent.reglable,
        step = volume["stepInterval"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 } ?: precedent.step,
    )
}

/**
 * `transportId` du récepteur média dans un `RECEIVER_STATUS`, ou null.
 *
 * On cherche **notre** application et pas la première venue : un Chromecast au
 * repos annonce son écran de veille (Google Photos, mesuré sur un appareil
 * réel), et lui parler ne mènerait nulle part.
 */
fun transportIdOf(corps: JsonObject): String? =
    corps["status"]?.jsonObject
        ?.get("applications")?.jsonArray
        ?.map { it.jsonObject }
        ?.firstOrNull { it["appId"]?.jsonPrimitive?.contentOrNull == CAST_DEFAULT_RECEIVER }
        ?.get("transportId")?.jsonPrimitive?.contentOrNull

/**
 * Type MIME d'un flux, **d'après ce qu'il est** et non d'après l'URL qu'on sert.
 *
 * ## Le défaut que ça corrige
 *
 * Le récepteur par défaut choisit son moteur sur le type MIME. Or l'URL qu'on
 * lui donne est celle du relais, qui finit par du base64 et n'a donc **aucune
 * extension** : la déduction retombait sur `video/mp4`, et un HLS annoncé en MP4
 * est refusé. Mesuré sur un vrai Chromecast — `LOAD_FAILED`, puis
 * `idleReason: "ERROR"`, sans un mot sur le type.
 *
 * Le format, lui, est connu depuis l'extraction. On le prend à la source plutôt
 * que de le redeviner à l'arrivée.
 */
fun castContentType(format: fr.moovie.tv.core.sources.model.StreamFormat, url: String): String =
    when (format) {
        fr.moovie.tv.core.sources.model.StreamFormat.HLS -> "application/vnd.apple.mpegurl"
        fr.moovie.tv.core.sources.model.StreamFormat.DASH -> "application/dash+xml"
        fr.moovie.tv.core.sources.model.StreamFormat.MP4 -> "video/mp4"
        // Format inconnu : l'URL d'origine reste le meilleur indice qu'on ait.
        fr.moovie.tv.core.sources.model.StreamFormat.UNKNOWN -> castContentType(url)
    }

/** Type MIME déduit de l'URL, quand c'est tout ce qu'on a. */
fun castContentType(url: String): String = when {
    url.substringBefore('?').endsWith(".m3u8", true) -> "application/vnd.apple.mpegurl"
    url.substringBefore('?').endsWith(".mpd", true) -> "application/dash+xml"
    else -> "video/mp4"
}
