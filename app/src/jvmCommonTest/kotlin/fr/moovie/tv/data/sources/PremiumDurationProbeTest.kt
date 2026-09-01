package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.getBody
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Les sources « Premium » servent parfois un logo ou une bande-annonce à la
 * place du film. Cette sonde relève, pour chaque lien d'un titre, le format et
 * la **durée réelle du flux**, pour la comparer à la durée attendue (TMDB).
 * C'est la mesure qui dira si un garde-fou de durée est faisable avant lecture.
 */
class PremiumDurationProbeTest {

    // Dune (2021) : 155 min chez TMDB.
    private val media = MediaRef.Movie(438631, "Dune", "2021")
    private val expectedMin = 155

    @Test
    fun probeDurations() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val links = FstreamProvider(ExtractorRegistry.gateway).sourcesFor(media)
        println("\nfstream : ${links.size} liens (attendu ≈ $expectedMin min)")
        println("%-14s %-8s %-9s %s".format("hôte", "langue", "durée", "url"))
        println("-".repeat(94))

        for (link in links.take(12)) {
            val stream = ExtractorRegistry.resolve(link)
            if (stream == null) {
                println("%-14s %-8s %-9s %s".format(link.hoster, link.language, "—", "non résolu"))
                continue
            }
            val mins = hlsDurationMinutes(stream.url, stream.headers)
            // Deux verdicts : sans puis avec le garde-fou de durée. Les comparer
            // isole ce que le garde-fou écarte de ce qui était déjà rejeté —
            // sans quoi on lui attribue des rejets qui ne sont pas les siens.
            val sansGarde = isStreamPlayable(stream, null)
            val avecGarde = isStreamPlayable(stream, expectedMin)
            println(
                "%-14s %-8s %-9s %-12s %-12s %s".format(
                    link.hoster, link.language,
                    mins?.let { "$it min" } ?: "? (${stream.format})",
                    if (sansGarde) "joignable" else "⛔ injoignable",
                    if (avecGarde) "✅ retenu" else "⛔ écarté",
                    stream.url.take(34),
                ),
            )
        }
    }

    /**
     * Durée d'un flux HLS : somme des `#EXTINF`. Si l'URL est une master
     * playlist (elle liste des variantes au lieu de segments), on descend dans
     * la première variante.
     */
    private suspend fun hlsDurationMinutes(url: String, headers: Map<String, String>): Int? {
        val body = ExtractorRegistry.gateway.getBody(url, headers) ?: return null
        if (!body.startsWith("#EXTM3U")) return null

        val extinf = Regex("""#EXTINF:([\d.]+)""").findAll(body)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
        if (extinf > 0) return (extinf / 60).toInt()

        // Master playlist : première URI non commentée = une variante.
        val variant = body.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .firstOrNull() ?: return null
        val abs = if (variant.startsWith("http")) variant
        else url.substringBeforeLast('/') + "/" + variant
        val child = ExtractorRegistry.gateway.getBody(abs, headers) ?: return null
        val total = Regex("""#EXTINF:([\d.]+)""").findAll(child)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
        return if (total > 0) (total / 60).toInt() else null
    }
}
