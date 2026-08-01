package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Affiche le **type de contenu** réellement servi par les URL que nos
 * extracteurs produisent. Un 200 ne suffit pas : ExoPlayer a rejeté un flux
 * DoodStream avec `UnrecognizedInputFormatException` alors que la sonde de
 * jouabilité le déclarait bon — le serveur répondait 200 sur une page HTML.
 */
class ResolvedContentTypeProbeTest {

    private val links = listOf(
        EmbedLink("https://playmogo.com/e/mnqc1ufsh761", "playmogo", "VF"),
        EmbedLink("https://dsvplay.com/e/dsgza9kyjbin", "dsvplay", "VF"),
        EmbedLink("https://luluvdo.com/e/hjuwursdp0qf", "luluvdo", "VF"),
        EmbedLink("https://uqload.is/embed-9745q65n1mj2.html", "uqload", "VF"),
        EmbedLink("https://waaw.to/e/0NMBfsRoP0NU", "waaw", "VF"),
    )

    @Test
    fun probeContentTypes() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        println("\n%-12s %-6s %-6s %-28s %s".format("hôte", "résolu", "code", "content-type", "url"))
        println("-".repeat(100))

        for (link in links) {
            val stream = ExtractorRegistry.resolve(link)
            if (stream == null) {
                println("%-12s %-6s".format(link.hoster, "non"))
                continue
            }
            // HEAD *et* GET borné : un 405 sur HEAD ne dit rien du flux, il dit
            // seulement que la méthode est refusée. Confondre les deux, c'est
            // conclure « page HTML » sur un en-tête d'erreur de méthode.
            val head = ExtractorRegistry.gateway.fetch(
                HttpRequest(url = stream.url, method = HttpMethod.HEAD, headers = stream.headers),
            )
            val get = ExtractorRegistry.gateway.fetch(
                HttpRequest(
                    url = stream.url,
                    headers = stream.headers + ("Range" to "bytes=0-64"),
                ),
            )
            println(
                "%-12s %-5s HEAD %-4s %-22s │ GET %-4s %-26s %s".format(
                    link.hoster, stream.format,
                    head?.status ?: "—", head?.header("Content-Type")?.take(20) ?: "—",
                    get?.status ?: "—", get?.header("Content-Type")?.take(24) ?: "—",
                    get?.body?.take(24)?.replace(Regex("[^\\x20-\\x7e]"), "."),
                ),
            )
        }
    }
}
