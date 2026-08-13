package fr.moovie.tv.desktop.mpv

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * L'invariant qui a fait naître `LocalStreamProxy` : les en-têtes HTTP doivent
 * atteindre **les requêtes de segment**, pas seulement la playlist.
 *
 * libVLC ne le tenait pas — son démultiplexeur adaptatif ouvrait ses propres
 * connexions sans `Referer`, et sur un CDN qui l'exige la playlist se lisait,
 * aucun segment n'arrivait, et l'application prenait ce silence pour un épisode
 * terminé. mpv passe ses options HTTP au démultiplexeur FFmpeg, qui les
 * répercute sur ses requêtes filles ; ce test verrouille ce comportement sur
 * une chaîne HLS complète, servie ici même : master → variante → segment.
 *
 * Le segment est du bruit : la lecture échouera, et c'est indifférent — ce
 * qu'on juge est ce que le serveur a **reçu**.
 */
class MpvHeadersTest {

    @Test
    fun `les en-tetes atteignent playlist, variante et segment`() {
        if (Libmpv.instance == null) {
            println("[sonde mpv en-têtes] ignorée — libmpv introuvable sur cette machine")
            return
        }

        val recus = ConcurrentHashMap<String, Map<String, String>>()
        val segmentVu = CountDownLatch(1)

        val serveur = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun sers(chemin: String, corps: ByteArray, type: String) {
            serveur.createContext(chemin) { echange ->
                recus[chemin] = mapOf(
                    "Referer" to echange.requestHeaders.getFirst("Referer").orEmpty(),
                    "User-Agent" to echange.requestHeaders.getFirst("User-Agent").orEmpty(),
                    "X-Moovie" to echange.requestHeaders.getFirst("X-Moovie").orEmpty(),
                )
                if (chemin == "/seg0.ts") segmentVu.countDown()
                echange.responseHeaders.add("Content-Type", type)
                echange.sendResponseHeaders(200, corps.size.toLong())
                echange.responseBody.use { it.write(corps) }
            }
        }

        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=100000
            variant.m3u8
        """.trimIndent().toByteArray()
        val variante = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:4
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:4.0,
            seg0.ts
            #EXT-X-ENDLIST
        """.trimIndent().toByteArray()
        sers("/master.m3u8", master, "application/vnd.apple.mpegurl")
        sers("/variant.m3u8", variante, "application/vnd.apple.mpegurl")
        sers("/seg0.ts", ByteArray(SEGMENT_OCTETS), "video/mp2t")
        serveur.start()

        val moteur = MpvEngine(surImage = {})
        try {
            val entetes = mapOf(
                "Referer" to REFERER,
                "User-Agent" to AGENT,
                "X-Moovie" to MARQUEUR,
            )
            // L'ouverture échouera peut-être — le segment est du bruit — et
            // bloquerait le fil du test : on la pose ailleurs, seul le passage
            // des requêtes nous regarde.
            thread(isDaemon = true, name = "moovie-test-ouverture") {
                moteur.ouvre("http://127.0.0.1:${serveur.address.port}/master.m3u8", entetes)
            }

            assertTrue(
                segmentVu.await(ATTENTE_SEGMENT_S, TimeUnit.SECONDS),
                "le segment n'a jamais été demandé — la chaîne HLS ne s'est pas déroulée",
            )
            for (chemin in listOf("/master.m3u8", "/variant.m3u8", "/seg0.ts")) {
                val vus = recus[chemin] ?: emptyMap()
                assertTrue(vus["Referer"] == REFERER, "$chemin : Referer absent ou faux (${vus["Referer"]})")
                assertTrue(vus["User-Agent"] == AGENT, "$chemin : User-Agent absent ou faux (${vus["User-Agent"]})")
                assertTrue(vus["X-Moovie"] == MARQUEUR, "$chemin : X-Moovie absent ou faux (${vus["X-Moovie"]})")
            }
            println("[sonde mpv en-têtes] Referer, User-Agent et X-Moovie vus sur les trois requêtes")
        } finally {
            moteur.ferme()
            serveur.stop(0)
        }
    }

    private companion object {
        const val REFERER = "https://exemple.test/lecteur"

        /** Des virgules à dessein : c'est elles qui cassent une liste mpv mal échappée. */
        const val AGENT = "Mozilla/5.0 (X11; Linux x86_64, MoovieTest) AppleWebKit/537.36 (KHTML, like Gecko)"

        const val MARQUEUR = "sonde-en-tetes"
        const val SEGMENT_OCTETS = 4_096
        const val ATTENTE_SEGMENT_S = 20L
    }
}
