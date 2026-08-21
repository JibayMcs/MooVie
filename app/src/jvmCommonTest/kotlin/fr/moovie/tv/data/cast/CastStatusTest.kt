package fr.moovie.tv.data.cast

import fr.moovie.tv.core.sources.model.StreamFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La lecture des statuts du récepteur — la seule partie du protocole qui décide
 * de ce qu'on affiche, donc la seule qui puisse mentir visiblement.
 *
 * Les charges utiles employées ici sont de vraies formes : le `RECEIVER_STATUS`
 * est celui qu'a rendu un Chromecast au repos, écran de veille Google Photos
 * compris.
 */
class CastStatusTest {

    private fun objet(brut: String) = Json.parseToJsonElement(brut).jsonObject

    // ── Trouver la bonne application ─────────────────────────────────────────

    /**
     * **Le test qui compte.** Un Chromecast au repos annonce son écran de veille
     * — mesuré : « Google Photos », `appId` 96084372. Prendre la première
     * application venue ferait envoyer le `LOAD` à un diaporama, qui ne
     * répondrait jamais, et la diffusion resterait sans explication.
     */
    @Test
    fun `l ecran de veille n est pas pris pour notre recepteur`() {
        val auRepos = objet(
            """
            {"requestId":1,"status":{"applications":[
              {"appId":"96084372","displayName":"Google Photos","transportId":"web-5",
               "isIdleScreen":true}
            ]}}
            """.trimIndent(),
        )

        assertNull(transportIdOf(auRepos), "l'écran de veille n'est pas une cible")
    }

    @Test
    fun `notre recepteur est reconnu a son identifiant`() {
        val lance = objet(
            """
            {"requestId":2,"status":{"applications":[
              {"appId":"$CAST_DEFAULT_RECEIVER","displayName":"Default Media Receiver",
               "transportId":"web-17","sessionId":"abc"}
            ]}}
            """.trimIndent(),
        )

        assertEquals("web-17", transportIdOf(lance))
    }

    @Test
    fun `un recepteur sans application ne donne pas de session`() {
        assertNull(transportIdOf(objet("""{"status":{"applications":[]}}""")))
        assertNull(transportIdOf(objet("""{"status":{}}""")))
    }

    // ── Lire la lecture ──────────────────────────────────────────────────────

    @Test
    fun `un statut complet donne position duree et session`() {
        val statut = objet(
            """
            {"type":"MEDIA_STATUS","status":[
              {"mediaSessionId":1,"playerState":"PLAYING","currentTime":943.291,
               "media":{"duration":9783.584,"contentId":"http://x/f.m3u8"}}
            ]}
            """.trimIndent(),
        )

        val lu = parseMediaStatus(statut)

        assertEquals(true, lu?.playing)
        assertEquals(943_291, lu?.positionMs)
        assertEquals(9_783_584, lu?.durationMs)
        assertEquals(1, lu?.mediaSessionId)
    }

    @Test
    fun `en pause le lecteur ne se dit pas en lecture`() {
        val enPause = objet(
            """{"status":[{"mediaSessionId":1,"playerState":"PAUSED","currentTime":12.0}]}""",
        )

        assertEquals(false, parseMediaStatus(enPause)?.playing)
    }

    /**
     * **Le second test qui compte.** Le récepteur émet des `MEDIA_STATUS`
     * **partiels** : toucher au volume ne réénonce ni la durée ni la session.
     * Les écraser à zéro ferait clignoter la barre de progression à chaque
     * message — la même leçon que le mini-lecteur de la télécommande, où un
     * relevé perdu effaçait l'affichage.
     */
    @Test
    fun `un statut partiel ne remet pas la duree a zero`() {
        val connu = CastStatus(playing = true, positionMs = 1000, durationMs = 9_783_584, mediaSessionId = 1)
        val partiel = objet("""{"status":[{"playerState":"PLAYING","currentTime":1200.0}]}""")

        val lu = parseMediaStatus(partiel, precedent = connu)

        assertEquals(9_783_584, lu?.durationMs, "la durée connue a été perdue")
        assertEquals(1, lu?.mediaSessionId, "la session connue a été perdue")
        assertEquals(1_200_000, lu?.positionMs)
    }

    @Test
    fun `un message sans statut ne produit rien`() {
        assertNull(parseMediaStatus(objet("""{"type":"MEDIA_STATUS","status":[]}""")))
        assertNull(parseMediaStatus(objet("""{"type":"PONG"}""")))
    }

    // ── Le type de contenu ───────────────────────────────────────────────────

    /**
     * Le récepteur par défaut choisit son moteur sur le type MIME. Un HLS
     * annoncé en `video/mp4` ne joue pas, et l'erreur remonte comme un média
     * illisible plutôt que comme un type erroné.
     */
    @Test
    fun `le type suit l extension, parametres ignores`() {
        assertEquals("application/vnd.apple.mpegurl", castContentType("http://x/a/master.m3u8"))
        assertEquals("application/vnd.apple.mpegurl", castContentType("http://x/a/master.m3u8?token=1"))
        assertEquals("application/dash+xml", castContentType("http://x/a/manifest.mpd"))
        assertEquals("video/mp4", castContentType("http://x/a/film.mp4"))
    }

    /**
     * **Le test qui compte, et il a été payé.** L'URL remise au récepteur est
     * celle du relais : elle finit par du base64 et n'a **aucune extension**.
     * Déduire le type de là faisait annoncer `video/mp4` pour un HLS, et le vrai
     * Chromecast répondait `LOAD_FAILED` puis `idleReason: "ERROR"` — sans
     * jamais dire que le type était en cause.
     *
     * Le format, lui, est connu depuis l'extraction : on le prend à la source.
     */
    @Test
    fun `le type vient du format, pas de l url relayee`() {
        val relais = "http://192.168.1.50:41833/1d9ijg34uubgdtnpem0/u/aHR0cHM6Ly94"

        assertEquals("application/vnd.apple.mpegurl", castContentType(StreamFormat.HLS, relais))
        assertEquals("application/dash+xml", castContentType(StreamFormat.DASH, relais))
        assertEquals("video/mp4", castContentType(StreamFormat.MP4, relais))
    }

    /** Format inconnu : l'URL d'origine reste le meilleur indice disponible. */
    @Test
    fun `sans format connu on retombe sur l url d origine`() {
        assertEquals(
            "application/vnd.apple.mpegurl",
            castContentType(StreamFormat.UNKNOWN, "https://h.tld/a/master.m3u8"),
        )
    }

    /**
     * Une URL de relais finit par du base64, jamais par une extension : sans
     * repli, elle n'aurait aucun type. `video/mp4` est le pari le plus sûr, et
     * l'appelant peut toujours imposer le sien.
     */
    @Test
    fun `une url de relais sans extension retombe sur un type jouable`() {
        val relais = "http://192.168.1.50:41833/1d9ijg34uubgdtnpem0/u/aHR0cDovL3g"

        assertTrue(castContentType(relais).startsWith("video/"))
    }
}
