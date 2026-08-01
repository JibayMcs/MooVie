package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.core.sources.usecase.MIN_DURATION_RATIO
import fr.moovie.tv.core.sources.usecase.isDurationAcceptable
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Instruit un défaut précis : « le lecteur s'ouvre puis revient aussitôt sur la
 * fiche » sur les titres récents, Vidzy en tête. Deux causes possibles, et elles
 * demandent des correctifs opposés :
 *
 *  1. l'hébergeur sert un **substitut court** (bande-annonce, logo, message
 *     d'indisponibilité) parce que le film n'est pas encore diffusable — le
 *     garde-fou de durée fait alors exactement son travail, et le vrai défaut
 *     est qu'on n'en dit rien à l'utilisateur ;
 *  2. le flux est **complet mais mal mesuré**, et le garde-fou écarte à tort
 *     une source valide — auquel cas c'est lui qu'il faut corriger.
 *
 * On mesure donc, pour chaque lien : le format, la durée réellement lisible, la
 * durée attendue, et le verdict du garde-fou. Sans ce relevé on ne peut que
 * deviner lequel des deux on répare.
 */
class RecentTitleProbeTest {

    private data class T(val media: MediaRef, val minutes: Int, val label: String)

    private val titres = listOf(
        // Sorti la semaine du relevé : le cas décrit par l'utilisateur.
        T(MediaRef.Movie(969681, "Spider-Man : Brand New Day", "2026"), 130, "Spider-Man BND (récent)"),
        // Témoin ancien et largement diffusé : si lui aussi échoue, ce n'est pas
        // une question de fraîcheur du titre.
        T(MediaRef.Movie(693134, "Dune : Deuxième partie", "2024"), 165, "Dune 2 (témoin)"),
    )

    @Test
    fun probeRecentTitles() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        for (t in titres) {
            println("\n════════ ${t.label} — ${t.minutes} min attendues ════════")
            println("%-14s %-10s %-9s %-11s %s".format("hébergeur", "catalogue", "format", "durée lue", "verdict garde-fou"))

            val links = ProviderRegistry.all.flatMap { p ->
                runCatching { p.sourcesFor(t.media) }.getOrDefault(emptyList())
                    .filter { it.language == "VF" }
                    .map { it to p.name }
            }.distinctBy { it.first.url }

            for ((link, provider) in links) {
                val stream = runCatching { ExtractorRegistry.resolve(link) }.getOrNull()
                if (stream == null) {
                    println("%-14s %-10s %-9s %-11s %s".format(link.hoster, provider, "—", "—", "⛔ non résolu"))
                    continue
                }

                val seconds = runCatching { hlsSeconds(stream.url, stream.headers) }.getOrNull()
                val ok = isDurationAcceptable(seconds, t.minutes)
                val lu = seconds?.let { "%.0f min".format(it / 60) } ?: "illisible"
                val verdict = when {
                    seconds == null -> "⚠️ laissé passer (non mesurable)"
                    ok -> "✅ accepté"
                    else -> "⛔ REJETÉ (< ${(MIN_DURATION_RATIO * 100).toInt()} % de ${t.minutes} min)"
                }
                println(
                    "%-14s %-10s %-9s %-11s %s".format(
                        link.hoster, provider, stream.format.name.lowercase(), lu, verdict,
                    ),
                )
            }
        }
    }

    /** Durée d'un flux HLS, en secondes ; null si ce n'est pas du HLS ou illisible. */
    private suspend fun hlsSeconds(url: String, headers: Map<String, String>): Double? {
        val body = ExtractorRegistry.gateway.getBody(url, headers) ?: return null
        if (!body.startsWith("#EXTM3U")) return null

        sum(body)?.let { return it }
        val variant = body.lineSequence().map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return null
        val absolute = if (variant.startsWith("http")) variant
        else url.substringBefore('?').substringBeforeLast('/') + "/" + variant
        return ExtractorRegistry.gateway.getBody(absolute, headers)?.let { sum(it) }
    }

    private fun sum(playlist: String): Double? {
        val re = Regex("""#EXTINF:\s*([\d.]+)""")
        if (!re.containsMatchIn(playlist)) return null
        return re.findAll(playlist).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
    }
}
