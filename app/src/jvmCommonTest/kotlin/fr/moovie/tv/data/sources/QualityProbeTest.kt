package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.getBody
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic : peut-on afficher une vraie qualité vidéo sur les boutons ?
 *
 * Aucune source ne l'annonce au listing. La seule donnée fiable est le
 * `RESOLUTION=` de la master playlist HLS — mais il faut résoudre le lien pour
 * l'atteindre. Cette sonde mesure le coût et le taux de succès.
 */
class QualityProbeTest {
    @Test
    fun probeQuality() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking
        val links = ProviderRegistry.all.flatMap {
            runCatching { it.sourcesFor(MediaRef.Movie(438631, "Dune", "2021")) }.getOrDefault(emptyList())
                .map { l -> it.name to l }
        }.filter { it.second.language == "VF" }

        println("%-12s %-14s %-10s %-12s %s".format("catalogue", "hebergeur", "declaree", "mesuree", "ms"))
        var ok = 0
        val t0 = System.currentTimeMillis()
        for ((prov, link) in links) {
            val start = System.currentTimeMillis()
            val stream = ExtractorRegistry.resolve(link)
            val res = stream?.let {
                val body = ExtractorRegistry.gateway.getBody(it.url, it.headers)
                Regex("""RESOLUTION=(\d+)x(\d+)""").find(body.orEmpty())?.groupValues?.get(2)
            }
            if (res != null) ok++
            println("%-12s %-14s %-10s %-12s %d".format(
                prov, link.hoster, link.variant ?: "-", res?.let { "${it}p" } ?: "-",
                System.currentTimeMillis() - start))
        }
        println("\n-> $ok / ${links.size} qualites mesurees en ${System.currentTimeMillis() - t0} ms")
    }
}
