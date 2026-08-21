package fr.moovie.tv.data.cast

import kotlinx.serialization.json.JsonObject
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

/** Type MIME déduit de l'URL, pour les récepteurs qui ne devinent pas. */
fun castContentType(url: String): String = when {
    url.substringBefore('?').endsWith(".m3u8", true) -> "application/vnd.apple.mpegurl"
    url.substringBefore('?').endsWith(".mpd", true) -> "application/dash+xml"
    else -> "video/mp4"
}
