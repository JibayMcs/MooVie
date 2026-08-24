package fr.moovie.tv.data.sources

import com.sun.net.httpserver.HttpServer
import fr.moovie.tv.core.sources.port.HttpRequest
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Le plafond de lecture binaire — ce qui empêche un serveur d'épuiser la mémoire.
 *
 * ## Le défaut qu'il corrige
 *
 * Les appels binaires servent à lire un en-tête MP4 : ils demandent une plage de
 * quelques centaines de kilo-octets. Mais un `Range` est une **demande**, pas
 * une garantie.
 *
 * Mesuré sur SwiftFlow le 24/08/2026 : l'URL du catalogue redirige vers un proxy
 * qui ne transmet pas l'en-tête. Le serveur répond alors 200 avec le fichier
 * entier — **3,8 Go** — et l'ancien `bytes()` entreprenait de le charger en
 * mémoire pour y lire quatre nombres. Sur la box, c'est l'`OutOfMemoryError` que
 * ce projet a déjà payé une fois sur un fichier de 1,24 Go.
 *
 * Le serveur ci-dessous se comporte exactement comme celui-là : il **ignore la
 * plage demandée** et sert tout. C'est le seul comportement qui reproduise le
 * défaut — un serveur qui honore `Range` ne l'aurait jamais révélé, ce qui est
 * précisément pourquoi il a survécu si longtemps.
 */
class BinaryCapTest {

    private var serveur: HttpServer? = null

    @AfterTest
    fun ferme() {
        serveur?.stop(0)
    }

    /** Sert [TAILLE] octets quoi qu'on lui demande, comme le proxy de SwiftFlow. */
    private fun demarre(): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { echange ->
                val corps = ByteArray(TAILLE) { 'A'.code.toByte() }
                echange.responseHeaders.add("Content-Type", "video/mp4")
                // 200, pas 206 : la plage demandée est ignorée.
                echange.sendResponseHeaders(200, corps.size.toLong())
                echange.responseBody.use { it.write(corps) }
            }
            start()
        }
        serveur = s
        return "http://127.0.0.1:${s.address.port}/film.mp4"
    }

    @Test
    fun `un serveur qui ignore Range ne fait pas charger tout le fichier`() = runBlocking {
        val url = demarre()

        val reponse = ExtractorRegistry.gateway.fetch(
            HttpRequest(url = url, headers = mapOf("Range" to "bytes=0-524287"), binary = true),
        )

        assertNotNull(reponse, "la requête aurait dû aboutir")
        val octets = reponse.bytes
        assertNotNull(octets, "un appel binaire doit rendre des octets")
        assertTrue(
            octets.size < TAILLE,
            "le corps n'a pas été plafonné : ${octets.size} octets lus sur $TAILLE",
        )
        // Assez pour qu'un en-tête MP4 « faststart » reste exploitable : plafonner
        // ne doit pas revenir à ne rien lire.
        assertTrue(octets.size >= 524_288, "plafond trop bas : ${octets.size} octets")
    }

    private companion object {
        /** Plus que le plafond, assez peu pour que le test reste rapide. */
        const val TAILLE = 3 shl 20
    }
}
