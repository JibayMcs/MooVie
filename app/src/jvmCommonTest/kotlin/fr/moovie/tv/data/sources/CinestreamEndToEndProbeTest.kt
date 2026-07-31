package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
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
            val links = provider.sourcesFor(MediaRef.Movie(tmdbId, title, year))
            val vf = links.filter { it.language == "VF" }

            println("\n════ $title ($year) — ${links.size} embeds, dont ${vf.size} VF")

            var resolved = 0
            for (link in vf) {
                // Toujours passer par resolve() : un domaine que personne ne
                // revendique reste résoluble par reniflage (VOE et ses alias
                // tournants). Conditionner l'appel à canResolve() masquerait
                // précisément ce que le reniflage est censé rattraper.
                val claimed = ExtractorRegistry.canResolve(link.url)
                val stream = ExtractorRegistry.resolve(link)

                // « Résolu » ne veut pas dire « jouable » : un extracteur trop
                // permissif peut rendre une URL de gabarit périmée, laissée dans
                // la page par l'hébergeur. On sonde donc réellement le flux —
                // c'est ce que fait la cascade avant d'ouvrir le lecteur.
                val playable = stream != null && isStreamPlayable(stream)
                if (playable) resolved++

                val how = if (claimed) "" else " (reniflé)"
                val state = when {
                    playable -> "✅ ${stream!!.format}$how"
                    stream != null -> "⚠ résolu mais injouable$how"
                    claimed -> "⚠ extracteur présent mais échec"
                    else -> "❌ non résolu"
                }
                println("   %-16s %-30s %s".format(link.hoster, state, link.url.take(48)))
            }
            println("   → $resolved / ${vf.size} liens VF jouables")
        }
    }
}
