package fr.moovie.tv.data.sources

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire (réseau requis).
 *
 * Déroule la chaîne complète pour de vrais titres : provider → liens d'embed →
 * extracteurs → flux jouable. Donne la seule métrique qui compte pour ce
 * chantier : **combien de liens VF sont réellement résolus**, et non combien
 * sont trouvés.
 *
 *     ./gradlew :app:desktopTest --tests '*CinestreamEndToEndProbeTest*' \
 *         -Dmoovie.probe=1
 */
class CinestreamEndToEndProbeTest {

    private val titles = listOf(
        Triple(438631, "Dune", "2021"),
        Triple(872585, "Oppenheimer", "2023"),
        Triple(27205, "Inception", "2010"),
    )

    @Test
    fun probeChain() = runBlocking {
        if (System.getProperty("moovie.probe") == null) {
            println("[sonde cinestream] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }

        val provider = CinestreamProvider(ExtractorRegistry.http)

        for ((tmdbId, title, year) in titles) {
            val links = provider.movieSources(tmdbId, title, year)
            val vf = links.filter { it.language == "VF" }

            println("\n════ $title ($year) — ${links.size} embeds, dont ${vf.size} VF")

            var resolved = 0
            for (link in vf) {
                val handled = ExtractorRegistry.canResolve(link.url)
                val stream = if (handled) ExtractorRegistry.resolve(link) else null
                if (stream != null) resolved++

                val state = when {
                    stream != null -> "✅ ${stream.format}"
                    handled -> "⚠ extracteur présent mais échec"
                    else -> "❌ aucun extracteur"
                }
                println("   %-16s %-30s %s".format(link.hoster, state, link.url.take(48)))
            }
            println("   → $resolved / ${vf.size} liens VF jouables")
        }
    }
}
