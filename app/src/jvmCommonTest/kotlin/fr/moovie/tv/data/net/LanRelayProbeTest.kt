package fr.moovie.tv.data.net

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Épreuve : le relais peut-il servir un Chromecast ?
 *
 * Trois questions, et une seule compte vraiment — celle des en-têtes, que la
 * note « Diffusion vers la TV » désignait comme l'inconnue à lever avant
 * d'écrire quoi que ce soit.
 *
 * 1. **Les en-têtes.** Un récepteur tiers n'enverra jamais `Referer` ni
 *    `User-Agent`. Le serveur d'origine simulé ici refuse sans eux — comme
 *    vidzy le fait réellement, mesuré à 403 quand l'un des deux manque.
 * 2. **La portée.** Un Chromecast n'est pas dans notre processus : l'URL doit
 *    être joignable depuis le Wi-Fi, donc ni `localhost` ni la boucle locale.
 * 3. **Le rebond.** Ouvrir un proxy au réseau sans garde en ferait un relais
 *    ouvert pour le voisin.
 *
 * Sonde et non test de régression : elle monte un serveur, ouvre un port sur le
 * réseau et dépend de l'interface active. Elle ne tourne qu'avec
 * `-Dmoovie.probe=1`, comme les autres sondes du projet.
 */
class LanRelayProbeTest {

    private val actif = System.getProperty("moovie.probe") == "1"

    private val entetes = mapOf(
        "Referer" to "https://exemple.tld/",
        "User-Agent" to "Moo-vie/probe",
    )

    /** Origine qui se comporte comme un CDN exigeant : 403 sans les deux en-têtes. */
    private fun origine(): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { echange ->
            val referer = échange(echange, "Referer")
            val agent = échange(echange, "User-Agent")
            if (referer != entetes["Referer"] || agent != entetes["User-Agent"]) {
                echange.sendResponseHeaders(403, -1)
                echange.close()
                return@createContext
            }
            val corps = when {
                echange.requestURI.path.endsWith(".m3u8") ->
                    "#EXTM3U\n#EXTINF:4,\nsegment0.ts\n#EXT-X-ENDLIST\n"
                else -> "CONTENU-SEGMENT"
            }.toByteArray()
            val type = if (echange.requestURI.path.endsWith(".m3u8")) {
                "application/vnd.apple.mpegurl"
            } else {
                "video/mp2t"
            }
            echange.responseHeaders.add("Content-Type", type)
            echange.sendResponseHeaders(200, corps.size.toLong())
            echange.responseBody.use { it.write(corps) }
        }
        start()
    }

    private fun échange(e: com.sun.net.httpserver.HttpExchange, nom: String): String? =
        e.requestHeaders.getFirst(nom)

    private fun lis(url: String): Pair<Int, String> {
        val c = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 3000
        }
        val code = runCatching { c.responseCode }.getOrDefault(-1)
        val corps = runCatching { c.inputStream.bufferedReader().readText() }.getOrDefault("")
        c.disconnect()
        return code to corps
    }

    @Test
    fun `le relais sert un recepteur tiers`() {
        if (!actif) {
            println("[sonde] inactive — relancer avec -Dmoovie.probe=1")
            return
        }
        val origine = origine()
        val base = "http://127.0.0.1:${origine.address.port}"
        val relais = LocalStreamProxy(entetes, ouvertAuReseau = true)
        try {
            // ── 1. Sans les en-têtes, l'origine refuse ────────────────────────
            val (direct, _) = lis("$base/master.m3u8")
            println("[sonde] origine sans en-têtes        -> HTTP $direct")
            assertEquals(403, direct, "l'origine simulée devrait refuser")

            // ── 2. Par le relais, elle accepte ────────────────────────────────
            val url = relais.localUrl("$base/master.m3u8")
            println("[sonde] URL remise au récepteur      -> $url")
            val (parRelais, playlist) = lis(url)
            println("[sonde] par le relais                -> HTTP $parRelais")
            assertEquals(200, parRelais, "le relais doit reposer les en-têtes")

            // ── 3. L'URL est joignable hors boucle locale ─────────────────────
            val hote = URL(url).host
            println("[sonde] hôte annoncé                 -> $hote")
            assertTrue(
                hote != "127.0.0.1" && hote != "localhost",
                "un Chromecast ne saurait rien faire de $hote",
            )

            // ── 4. La playlist est réécrite, segments compris ─────────────────
            // La réécriture rend des chemins **relatifs** : un lecteur les
            // résout contre l'URL de la playlist, donc contre le relais. C'est
            // suffisant, et c'est ce que fait un récepteur Cast.
            val ligne = playlist.lineSequence().first { it.isNotBlank() && !it.startsWith("#") }
            val segment = URL(URL(url), ligne).toString()
            println("[sonde] segment réécrit              -> ${segment.take(78)}…")
            val (codeSegment, contenu) = lis(segment)
            println("[sonde] segment par le relais        -> HTTP $codeSegment")
            assertEquals(200, codeSegment)
            assertEquals("CONTENU-SEGMENT", contenu)

            // ── 5. Sans le jeton, le relais refuse ────────────────────────────
            val sansJeton = url.replace(Regex("/[a-z0-9]+/u/"), "/u/")
            val (rebond, _) = lis(sansJeton)
            println("[sonde] rebond sans jeton            -> HTTP $rebond")
            assertEquals(404, rebond, "le relais ne doit pas servir de rebond")

            // ── 6. Facultatif : laisser le relais en vie pour qu'un vrai
            // appareil du réseau vienne chercher le flux lui-même. C'est la
            // seule preuve qui vaille pour un Chromecast.
            System.getProperty("moovie.probe.hold")?.toLongOrNull()?.let { secondes ->
                println("[sonde] RELAIS MAINTENU ${secondes}s SUR $url")
                Thread.sleep(secondes * 1000)
            }
        } finally {
            relais.shutdown()
            origine.stop(0)
        }
    }
}
