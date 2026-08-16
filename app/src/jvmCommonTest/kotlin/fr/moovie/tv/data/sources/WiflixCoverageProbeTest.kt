package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire. `-Dmoovie.probe=1`.
 *
 * Un nouveau catalogue ne se juge pas au nombre de liens qu'il rend : il se
 * juge à ce qu'il **ajoute**. La question posée ici est donc en deux temps.
 *
 *  1. wiflix répond-il, et sur quels titres ? Il refuse ceux dont la date de
 *     sortie diffère de TMDB, et un refus est indiscernable d'une panne si on
 *     ne compte pas les deux séparément.
 *  2. combien de ses liens sont **jouables aujourd'hui**, et par quels
 *     hébergeurs ? Un catalogue entièrement servi par des hôtes que l'app ne
 *     sait pas résoudre n'apporte aucune redondance — il ne fait qu'allonger la
 *     liste et le temps de chargement.
 *
 * La dernière colonne est la seule qui décide : les hébergeurs que wiflix est
 * **le seul** à proposer sur ce titre.
 */
class WiflixCoverageProbeTest {

    private val panier = listOf(
        MediaRef.Movie(693134, "Dune : Deuxième partie", "2024"),
        MediaRef.Movie(872585, "Oppenheimer", "2023"),
        MediaRef.Movie(634649, "Spider-Man : No Way Home", "2021"),
        MediaRef.Movie(550, "Fight Club", "1999"),
        MediaRef.Movie(278, "Les Évadés", "1994"),
        MediaRef.Episode(1429, "L'Attaque des Titans", null, 1, 1),
        MediaRef.Episode(1429, "L'Attaque des Titans", null, 3, 5),
        MediaRef.Episode(1396, "Breaking Bad", null, 2, 1),
        MediaRef.Episode(1416, "Grey's Anatomy", null, 5, 8),
    )

    @Test
    fun probeWiflix() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val wiflix = WiflixProvider(ExtractorRegistry.gateway)
        val autres = ProviderRegistry.all.filter { it.name != "wiflix" }
        val slots = Semaphore(4)

        println("\n%-28s %-8s %-26s %s".format("titre", "liens", "jouables", "hébergeurs exclusifs"))
        println("─".repeat(104))

        var couverts = 0
        var apports = 0

        for (media in panier) {
            val label = when (media) {
                is MediaRef.Movie -> media.title
                is MediaRef.Episode -> "${media.title} S${media.season}E${media.episode}"
            }

            val liens = runCatching { wiflix.sourcesFor(media) }.getOrDefault(emptyList())
            if (liens.isEmpty()) {
                println("%-28s %-8s %-26s %s".format(label.take(27), "—", "(absent du catalogue)", ""))
                continue
            }
            couverts++

            val jouables = coroutineScope {
                liens.map { lien ->
                    async {
                        slots.withPermit {
                            val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull()
                            val ok = flux != null &&
                                runCatching { isStreamPlayable(flux) }.getOrDefault(false)
                            lien.hoster to ok
                        }
                    }
                }.awaitAll()
            }

            // Ce que les autres catalogues servent déjà pour ce titre : tout
            // hébergeur qui y figure n'est pas un apport de wiflix.
            val dejaVus = autres.flatMap { p ->
                runCatching { p.sourcesFor(media) }.getOrDefault(emptyList())
            }.map { it.hoster }.toSet()

            val exclusifs = jouables.filter { it.second }.map { it.first }.toSet() - dejaVus
            if (exclusifs.isNotEmpty()) apports++

            println(
                "%-28s %-8d %-26s %s".format(
                    label.take(27),
                    liens.size,
                    "${jouables.count { it.second }} (" +
                        jouables.filter { it.second }.joinToString(",") { it.first }.take(20) + ")",
                    exclusifs.joinToString(",").ifEmpty { "—" },
                ),
            )
        }

        println("\n════════ CE QUE WIFLIX AJOUTE ════════")
        println("titres présents au catalogue      : $couverts / ${panier.size}")
        println("titres où il apporte un hébergeur")
        println("que personne d'autre ne sert      : $apports / ${panier.size}")
    }
}
