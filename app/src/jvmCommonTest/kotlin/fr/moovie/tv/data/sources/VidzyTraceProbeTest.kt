package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.getBody
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Trace un lien vidzy vivant, de la page jusqu'au refus.
 *
 * ```
 * ./gradlew :app:desktopTest --tests '*VidzyTraceProbeTest' -Dmoovie.probe=1
 * ```
 *
 * L'extracteur **résout** — il rend une URL — et le flux n'est pas jouable. Deux
 * causes possibles, que seule la trace distingue :
 *
 * - il a décodé un **leurre** : ces hôtes plantent un `.m3u8` bidon en clair
 *   dans la page, et le motif attrape-tout de `PackedJs` le ramasse quand le
 *   vrai décodage échoue (voir `isDecoy`) ;
 * - il a décodé la bonne URL mais l'hôte refuse la requête — en-tête ou jeton.
 */
class VidzyTraceProbeTest {

    @Test
    fun traceVidzy() = runBlocking {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde vidzy] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }

        val medias = listOf(
            MediaRef.Movie(550, "Fight Club", "1999"),
            MediaRef.Movie(603, "Matrix", "1999"),
            MediaRef.Episode(1416, "Grey's Anatomy", null, 1, 1),
        )

        val liens = medias.flatMap { media ->
            ProviderRegistry.all.flatMap { p ->
                runCatching { p.sourcesFor(media) }.getOrDefault(emptyList())
            }
        }.filter { it.hoster.equals("vidzy", true) || it.url.contains("vidzy", true) }
            .distinctBy { it.url }

        if (liens.isEmpty()) {
            println("[sonde] aucun lien vidzy dans les catalogues")
            return@runBlocking
        }

        for (lien in liens.take(3)) {
            println("\n──── ${lien.url}")
            val html = runCatching {
                ExtractorRegistry.gateway.getBody(
                    lien.url,
                    mapOf(
                        "User-Agent" to Ua.BROWSER,
                        "Referer" to "https://vidzy.org/",
                        "Accept" to "text/html,*/*",
                    ),
                )
            }.getOrNull()

            if (html == null) {
                println("  page      : ❌ injoignable")
                continue
            }
            println("  page      : ${html.length} octets")
            println("  eval/pack : ${if (html.contains("eval(function(p,a,c,k,e")) "oui" else "non"}")
            // Ce qui traîne en clair dans la page — dont les leurres.
            Regex("""https?://[^"'\s\\]+\.m3u8[^"'\s\\]*""").findAll(html)
                .map { it.value }.distinct().take(4)
                .forEach { println("  m3u8 brut : ${it.take(110)}") }

            val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull()
            if (flux == null) {
                println("  résolu    : ❌")
                continue
            }
            println("  résolu    : ${flux.url}")
            println("  jouable   : ${runCatching { isStreamPlayable(flux) }.getOrDefault(false)}")
        }
    }
}
