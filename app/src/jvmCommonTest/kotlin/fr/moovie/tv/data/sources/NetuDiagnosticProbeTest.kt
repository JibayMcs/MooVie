package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * `netu` est le plus gros gisement inexploité (27 propositions, 0 flux jouable),
 * mais avant d'écrire un extracteur il faut savoir **où pointent** ces liens.
 * `FstreamProvider` développe un identifiant nu en `https://www.fembed.com/v/…`,
 * or fembed a fermé en 2022 : si c'est bien là qu'on envoie le client, le défaut
 * n'est pas l'absence d'extracteur mais une URL morte — le même piège que le
 * gabarit périmé de waaw.
 */
class NetuDiagnosticProbeTest {

    @Test
    fun probeNetu() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val medias = listOf(
            MediaRef.Movie(693134, "Dune : Deuxième partie", "2024"),
            MediaRef.Movie(550, "Fight Club", "1999"),
            MediaRef.Episode(1396, "Breaking Bad", null, 2, 1),
        )

        for (media in medias) {
            val netu = ProviderRegistry.all.flatMap { p ->
                runCatching { p.sourcesFor(media) }.getOrDefault(emptyList())
                    .map { it to p.name }
            }.filter { it.first.hoster.contains("netu", ignoreCase = true) }

            println("\n════════ $media ════════")
            println("liens netu : ${netu.size}")

            for ((link, provider) in netu.take(4)) {
                println("\n  catalogue : $provider")
                println("  langue    : ${link.language}")
                println("  URL       : ${link.url}")

                val resp = ExtractorRegistry.gateway.fetch(
                    HttpRequest(url = link.url, headers = mapOf("User-Agent" to Ua.BROWSER)),
                )
                println("  statut    : ${resp?.status ?: "échec réseau (hôte injoignable ?)"}")
                println("  URL finale: ${resp?.url}")
                println("  type      : ${resp?.header("Content-Type")}")
                resp?.body?.take(180)?.replace("\n", " ")?.let { println("  début     : $it") }
                resp?.body?.let { b ->
                    val hints = listOf("m3u8", "eval(function", "sources", "jwplayer", "atob", "mp4")
                        .filter { h -> h in b }
                    println("  indices   : " + hints.joinToString().ifEmpty { "aucun" })
                }
            }
        }
    }
}
