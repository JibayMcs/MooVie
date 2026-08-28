package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** Trouve les flux les plus définis d'un titre et imprime leurs URL, pour ffprobe. */
class HdrTraceProbeTest {

    @Test
    fun traceHauteDefinition() = runBlocking {
        if (System.getProperty("moovie.probe") != "1") return@runBlocking
        val tmdb = System.getProperty("moovie.tmdb")?.toIntOrNull() ?: 108978
        val s = System.getProperty("moovie.season")?.toIntOrNull() ?: 3
        val e = System.getProperty("moovie.episode")?.toIntOrNull() ?: 5

        val media = MediaRef.Episode(tmdb, "Reacher", null, s, e)
        val liens = ProviderRegistry.all
            .flatMap { runCatching { it.sourcesFor(media) }.getOrDefault(emptyList()) }
            .distinctBy { it.url }
        println("[sonde] ${liens.size} lien(s)")

        for (lien in liens.take(14)) {
            val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull() ?: continue
            val h = runCatching { streamHeights(flux) }.getOrDefault(emptyList())
            val max = h.maxOrNull() ?: 0
            if (max < 1000) continue
            println("[sonde] ${lien.hoster} (${lien.language}) hauteurs=$h")
            println("[sonde] URL ${flux.url}")
            flux.headers.forEach { (k, v) -> println("[sonde] HDR $k: $v") }
        }
    }
}
