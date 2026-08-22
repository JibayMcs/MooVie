package fr.moovie.tv.data.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le codec CASTV2, écrit à la main pour n'avoir aucune dépendance Google.
 *
 * C'est ce que ces tests protègent : le jour où l'encodage se décale d'un octet,
 * le symptôme n'est pas une exception mais un **Chromecast qui ne répond pas**.
 * Rien à l'écran ne dirait pourquoi, et on soupçonnerait le réseau, le Wi-Fi,
 * l'appareil — tout sauf un numéro de champ.
 *
 * Les octets attendus ici ont été validés contre un vrai Chromecast : la sonde
 * qui a servi à les produire obtenait `PONG` et `RECEIVER_STATUS` du « Salon ».
 */
class CastMessageTest {

    private val ping = CastMessage(
        source = "sender-0",
        destination = "receiver-0",
        namespace = CAST_NS_HEARTBEAT,
        payload = """{"type":"PING"}""",
    )

    private fun corpsDe(trame: ByteArray): ByteArray = trame.copyOfRange(4, trame.size)

    // ── La trame ─────────────────────────────────────────────────────────────

    /**
     * **Le test qui compte.** Le préfixe de longueur est ce qui délimite les
     * messages sur un flux TCP : faux, le récepteur attend des octets qui ne
     * viennent jamais, ou lit deux messages comme un seul. Dans les deux cas il
     * se tait, et un silence ne dit pas d'où il vient.
     */
    @Test
    fun `la trame porte sa longueur sur quatre octets en gros-boutien`() {
        val trame = encodeCastMessage(ping)
        val annoncee = ((trame[0].toInt() and 0xFF) shl 24) or
            ((trame[1].toInt() and 0xFF) shl 16) or
            ((trame[2].toInt() and 0xFF) shl 8) or
            (trame[3].toInt() and 0xFF)

        assertEquals(trame.size - 4, annoncee, "longueur annoncée ≠ longueur réelle")
    }

    @Test
    fun `un message fait l aller-retour sans rien perdre`() {
        val relu = decodeCastMessage(corpsDe(encodeCastMessage(ping)))

        assertEquals(ping, relu)
    }

    @Test
    fun `les accents et le json imbrique survivent`() {
        val charge = CastMessage(
            source = "sender-0",
            destination = "transport-42",
            namespace = CAST_NS_MEDIA,
            payload = """{"media":{"contentId":"http://x/é.m3u8","metadata":{"title":"L'Odyssée"}}}""",
        )

        assertEquals(charge, decodeCastMessage(corpsDe(encodeCastMessage(charge))))
    }

    /**
     * Une charge utile de plus de 127 octets fait passer le varint de longueur
     * sur deux octets. C'est exactement la taille d'un `LOAD` réel — celui qui
     * porte l'URL du relais et les métadonnées — donc le cas nominal, pas un cas
     * limite.
     */
    @Test
    fun `une charge longue encode sa taille sur plusieurs octets`() {
        val long = CastMessage(
            source = "sender-0",
            destination = "receiver-0",
            namespace = CAST_NS_MEDIA,
            payload = """{"url":"${"a".repeat(5000)}"}""",
        )

        val relu = decodeCastMessage(corpsDe(encodeCastMessage(long)))

        assertEquals(long.payload, relu?.payload)
    }

    // ── Robustesse ───────────────────────────────────────────────────────────

    /**
     * Le protocole peut gagner des champs, et les deux bouts ne se mettent pas à
     * jour ensemble : un récepteur plus récent que nous ne doit pas rendre la
     * connexion inutilisable. Même discipline que le `ignoreUnknownKeys` du
     * client de télécommande.
     */
    @Test
    fun `un champ inconnu est saute au lieu de tout faire echouer`() {
        val corps = corpsDe(encodeCastMessage(ping))
        // Champ 9, type varint (0x48), valeur 7 : un ajout hypothétique.
        val avecInconnu = corps + byteArrayOf(0x48, 0x07)

        assertEquals(ping, decodeCastMessage(avecInconnu))
    }

    @Test
    fun `une trame tronquee est refusee plutot que devinee`() {
        val corps = corpsDe(encodeCastMessage(ping))

        assertNull(decodeCastMessage(corps.copyOfRange(0, corps.size / 2)))
    }

    @Test
    fun `du bruit ne produit pas un message`() {
        assertNull(decodeCastMessage(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertNull(decodeCastMessage(ByteArray(0)))
    }

    /** Sans canal, un message ne s'adresse à personne : il n'est pas exploitable. */
    @Test
    fun `un message sans namespace est refuse`() {
        val sansCanal = CastMessage("sender-0", "receiver-0", "", """{"type":"PING"}""")

        assertNull(decodeCastMessage(corpsDe(encodeCastMessage(sansCanal))))
    }

    // ── L'ancrage sur le réel ────────────────────────────────────────────────

    /**
     * **Les octets exacts qui ont obtenu une réponse d'un vrai Chromecast.**
     *
     * Relevés le 21/08/2026 contre le « Salon » (`192.168.1.92:8009`) : ces deux
     * trames ont produit la poignée de main puis `{"type":"PONG"}`. Un test
     * d'aller-retour ne dirait rien de cela — il vérifie qu'on se relit soi-même,
     * ce qui reste vrai même si l'encodage est faux d'un bout à l'autre.
     *
     * C'est donc le seul test ici qui prouve qu'on parle **le** protocole, et
     * non un dialecte cohérent avec lui-même.
     */
    @Test
    fun `les trames sont celles qu un vrai Chromecast a acceptees`() {
        val connect = CastMessage(
            "sender-0", "receiver-0", CAST_NS_CONNECTION, """{"type":"CONNECT"}""",
        )
        val attenduConnect = "000000580800120873656e6465722d301a0a72656365697665722d3022287572" +
            "6e3a782d636173743a636f6d2e676f6f676c652e636173742e74702e636f6e6e656374696f6e28003212" +
            "7b2274797065223a22434f4e4e454354227d"
        assertEquals(attenduConnect, encodeCastMessage(connect).toHex())

        val attenduPing = "000000540800120873656e6465722d301a0a72656365697665722d3022277572" +
            "6e3a782d636173743a636f6d2e676f6f676c652e636173742e74702e686561727462656174280032" +
            "0f7b2274797065223a2250494e47227d"
        assertEquals(attenduPing, encodeCastMessage(ping).toHex())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // ── Ce que le protocole impose ───────────────────────────────────────────

    /**
     * Le récepteur média **par défaut** est ce qui permet de se passer du SDK et
     * de l'inscription payante chez Google. Le figer ici évite qu'on le remplace
     * un jour par un identifiant à nous sans mesurer ce que ça implique.
     */
    @Test
    fun `le recepteur par defaut est celui de Google`() {
        assertEquals("CC1AD845", CAST_DEFAULT_RECEIVER)
    }

    @Test
    fun `les canaux portent les noms officiels`() {
        listOf(CAST_NS_CONNECTION, CAST_NS_HEARTBEAT, CAST_NS_RECEIVER, CAST_NS_MEDIA)
            .forEach { assertTrue(it.startsWith("urn:x-cast:com.google.cast."), "canal douteux : $it") }
    }
}
