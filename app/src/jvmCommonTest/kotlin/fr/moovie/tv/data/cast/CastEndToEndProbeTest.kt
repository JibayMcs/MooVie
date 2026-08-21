package fr.moovie.tv.data.cast

import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Épreuve de bout en bout : un vrai Chromecast joue-t-il ce qu'on lui envoie ?
 *
 * Passe par [CastSession], donc par le code de production — relais compris. Une
 * sonde qui referait le protocole à côté ne prouverait rien de l'application.
 *
 * ```
 * ./gradlew :app:desktopTest --tests '*CastEndToEndProbeTest' \
 *   -Dmoovie.probe=1 -Dmoovie.cast.host=192.168.1.92
 * ```
 *
 * Avec `-Dmoovie.stream=<url>` pour éprouver un flux d'hébergeur réel — c'est
 * alors la pose des en-têtes par le relais qui est en jeu. Sans lui, un flux
 * public sert de témoin : il isole la chaîne Cast des aléas des catalogues.
 *
 * **Elle allume vraiment un téléviseur.** D'où la double garde : `moovie.probe`
 * comme les autres sondes, et une adresse à fournir explicitement — on ne
 * découvre pas tout seul un appareil pour se mettre à jouer dessus.
 */
class CastEndToEndProbeTest {

    private val actif = System.getProperty("moovie.probe") == "1"
    private val hote: String? = System.getProperty("moovie.cast.host")

    /** Témoin public, sans en-tête requis : il isole la chaîne Cast du reste. */
    private val temoin =
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"

    @Test
    fun `un Chromecast joue ce qu on lui envoie`() {
        if (!actif || hote.isNullOrBlank()) {
            println("[sonde] inactive — -Dmoovie.probe=1 -Dmoovie.cast.host=<ip>")
            return
        }

        val url = System.getProperty("moovie.stream")?.takeIf { it.isNotBlank() } ?: temoin
        val entetes = buildMap {
            System.getProperty("moovie.referer")?.let { put("Referer", it) }
            System.getProperty("moovie.ua")?.let { put("User-Agent", it) }
        }
        println("[sonde] récepteur   : $hote")
        println("[sonde] flux        : ${url.take(96)}")
        println("[sonde] en-têtes    : ${entetes.keys.ifEmpty { "aucun (flux témoin)" }}")

        val session = CastSession(CastDevice(name = "sonde", host = hote))
        runBlocking {
            val parti = session.start(
                stream = PlayableStream(url = url, format = StreamFormat.HLS, headers = entetes),
                title = "Moo-vie — épreuve",
                subtitle = "sonde de bout en bout",
            )
            println("[sonde] LOAD        : ${if (parti) "accepté" else "refusé"}")

            // On regarde ce que le récepteur raconte pendant quelques secondes :
            // « accepté » ne veut pas dire « joue ». Un média illisible est
            // accepté puis abandonné, et seul le statut le dit.
            repeat(10) {
                delay(2_000)
                val s = session.status.value
                println(
                    "[sonde] t+${(it + 1) * 2}s\tlecture=${s.playing}" +
                        "\tposition=${s.positionMs}ms\tdurée=${s.durationMs}ms",
                )
            }

            val fin = session.status.value
            println(
                if (fin.playing && fin.positionMs > 0) {
                    "[sonde] ✅ le récepteur joue, et la position avance"
                } else {
                    "[sonde] ⚠️ le récepteur n'a pas démarré — voir les statuts ci-dessus"
                },
            )
            session.stopPlayback()
        }
    }
}
