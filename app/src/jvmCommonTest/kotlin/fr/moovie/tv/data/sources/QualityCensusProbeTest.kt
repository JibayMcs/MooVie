package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.test.Test

/**
 * Quelle **définition** chaque catalogue sert-il réellement ?
 *
 * ```
 * ./gradlew :app:desktopTest --tests '*QualityCensusProbeTest' -Dmoovie.probe=1
 * ```
 *
 * ## La question
 *
 * L'impression à l'usage est que seul SwiftFlow sert du 1080p et que le reste
 * plafonne plus bas. Si c'est vrai, la conséquence est lourde : la qualité de
 * l'application repose sur **un seul catalogue**, et le jour où il tombe — ce
 * qui vient d'arriver — on ne perd pas seulement des sources, on perd la
 * définition.
 *
 * Une impression ne se corrige pas par une autre impression. Celle-ci compte les
 * hauteurs annoncées, par catalogue et par hébergeur.
 *
 * ## Ce qu'elle mesure, et ce qu'elle ne peut pas mesurer
 *
 * La hauteur vient de ce que le flux **annonce** : les variantes d'un master
 * HLS, ou l'en-tête `moov` d'un MP4 ([streamHeights]). C'est ce sur quoi
 * l'application se fonde pour trier et pour afficher un libellé, donc la bonne
 * mesure de ce que l'utilisateur croit obtenir.
 *
 * Ce n'est pas une mesure de **qualité perçue** : un 1080p ré-encodé au
 * lance-pierre peut être moins beau qu'un 720p propre, et rien ici ne le dira.
 */
class QualityCensusProbeTest {

    private val panier = listOf(
        MediaRef.Movie(550, "Fight Club", "1999"),
        MediaRef.Movie(603, "Matrix", "1999"),
        MediaRef.Movie(77338, "Intouchables", "2011"),
        MediaRef.Episode(1416, "Grey's Anatomy", null, 1, 1),
    )

    @Test
    fun probeQualities() = runBlocking {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde qualité] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }

        val porte = Semaphore(5)
        val mesures = ProviderRegistry.all.map { provider ->
            async {
                porte.withPermit {
                    val hauteurs = mutableListOf<Int>()
                    val parHebergeur = sortedMapOf<String, MutableList<Int>>()
                    var mesurables = 0
                    var liens = 0
                    for (media in panier) {
                        val r = runCatching { provider.sourcesFor(media) }
                            .getOrDefault(emptyList())
                            .take(LIENS_PAR_TITRE)
                        for (lien in r) {
                            liens++
                            val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull()
                                ?: continue
                            val h = runCatching { streamHeights(flux) }.getOrDefault(emptyList())
                            if (h.isEmpty()) continue
                            mesurables++
                            hauteurs += h.max()
                            parHebergeur.getOrPut(lien.hoster.lowercase()) { mutableListOf() } += h.max()
                        }
                    }
                    Ligne(provider.name, liens, mesurables, hauteurs, parHebergeur)
                }
            }
        }.awaitAll()

        println("\n════════ DEFINITIONS SERVIES, PAR CATALOGUE ════════")
        println("%-14s %6s %8s %7s %7s  %s".format("catalogue", "liens", "mesurés", "max", "médian", "répartition"))
        mesures.sortedBy { it.nom }.forEach { l ->
            val repartition = l.hauteurs.groupingBy { it }.eachCount().toSortedMap(compareByDescending { it })
            println(
                "%-14s %6d %8d %7s %7s  %s".format(
                    l.nom, l.liens, l.mesurables,
                    l.hauteurs.maxOrNull()?.let { "${it}p" } ?: "—",
                    median(l.hauteurs)?.let { "${it}p" } ?: "—",
                    repartition.entries.joinToString(" ") { "${it.key}p×${it.value}" }
                        .ifEmpty { "aucune hauteur lisible" },
                ),
            )
        }

        println("\n════════ QUI SERT DU 1080p OU MIEUX ════════")
        val parHebergeur = sortedMapOf<String, MutableList<Int>>()
        mesures.forEach { l ->
            l.parHebergeur.forEach { (h, v) -> parHebergeur.getOrPut(h) { mutableListOf() } += v }
        }
        parHebergeur.entries
            .sortedByDescending { it.value.maxOrNull() ?: 0 }
            .forEach { (h, v) ->
                val max = v.max()
                println(
                    "  %-16s max %5s   %s".format(
                        h, "${max}p",
                        if (max >= 1080) "✅ HD" else "— en dessous",
                    ),
                )
            }

        val hd = mesures.filter { (it.hauteurs.maxOrNull() ?: 0) >= 1080 }.map { it.nom }
        println("\nCATALOGUES AVEC DU 1080p : ${hd.joinToString(", ").ifEmpty { "aucun" }}")
        println("SUR ${mesures.size} CATALOGUES")
    }

    private fun median(v: List<Int>): Int? =
        v.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    private data class Ligne(
        val nom: String,
        val liens: Int,
        val mesurables: Int,
        val hauteurs: List<Int>,
        val parHebergeur: Map<String, List<Int>>,
    )

    private companion object {
        /** Mesurer une hauteur coûte une requête de plus : on borne. */
        const val LIENS_PAR_TITRE = 4
    }
}
