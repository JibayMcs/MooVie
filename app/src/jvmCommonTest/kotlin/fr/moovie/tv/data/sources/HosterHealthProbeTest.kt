package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.test.Test

/**
 * Santé **par hébergeur**, et non par titre.
 *
 * ```
 * ./gradlew :app:desktopTest --tests '*HosterHealthProbeTest' -Dmoovie.probe=1
 * ```
 *
 * ## Ce que les autres sondes ne voient pas
 *
 * [QuickCoverageProbeTest] s'arrête au **premier** lien jouable, comme la
 * cascade : c'est la bonne mesure pour « le titre est-il regardable », et c'est
 * exactement pourquoi elle masque ce qu'on cherche ici. Tant qu'un hébergeur
 * tient, les neuf autres peuvent être morts sans que le chiffre bouge.
 *
 * Or c'est ce que l'utilisateur voit : un panneau de vingt sources dont dix-neuf
 * affichent « Ne répond pas ». Le titre est lisible, et l'application a pourtant
 * l'air cassée — parce qu'elle l'est, pour dix-neuf lignes sur vingt.
 *
 * ## Trois colonnes, trois pannes différentes
 *
 * - **résolus < liens** : personne ne sait extraire cet hébergeur, ou son
 *   extracteur ne reconnaît plus ses domaines ;
 * - **jouables < résolus** : l'URL est produite mais le CDN la refuse —
 *   en-tête manquant, jeton périmé, hébergeur mort ;
 * - **liens à zéro** : l'hébergeur a disparu des catalogues, ce qui n'est pas
 *   un défaut de notre côté.
 */
class HosterHealthProbeTest {

    private val panier = listOf(
        MediaRef.Movie(550, "Fight Club", "1999"),
        MediaRef.Movie(77338, "Intouchables", "2011"),
        MediaRef.Movie(603, "Matrix", "1999"),
        MediaRef.Episode(1416, "Grey's Anatomy", null, 1, 1),
        MediaRef.Episode(5920, "Mentalist", null, 1, 1),
    )

    @Test
    fun probeHosters() = runBlocking {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde hébergeurs] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }

        // Tous les liens de tous les catalogues, groupés par hébergeur.
        val porte = Semaphore(6)
        val parHoster = HashMap<String, MutableList<EmbedLink>>()
        panier.map { media ->
            async {
                porte.withPermit {
                    ProviderRegistry.all.flatMap { p ->
                        runCatching { p.sourcesFor(media) }.getOrDefault(emptyList())
                    }
                }
            }
        }.awaitAll().flatten().distinctBy { it.url }.forEach { lien ->
            parHoster.getOrPut(lien.hoster.lowercase()) { mutableListOf() } += lien
        }

        println("\n════════ SANTE PAR HEBERGEUR ════════")
        println("%-18s %6s %8s %8s  %s".format("hébergeur", "liens", "résolus", "jouables", "verdict"))

        val resultats = parHoster.entries.sortedBy { it.key }.map { (hoster, liens) ->
            async {
                porte.withPermit {
                    var resolus = 0
                    var jouables = 0
                    val echantillon = liens.take(ECHANTILLON)
                    for (lien in echantillon) {
                        val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull()
                            ?: continue
                        resolus++
                        if (runCatching { isStreamPlayable(flux) }.getOrDefault(false)) jouables++
                    }
                    Ligne(hoster, echantillon.size, resolus, jouables, liens.size)
                }
            }
        }.awaitAll()

        resultats.forEach { l ->
            println(
                "%-18s %6d %8d %8d  %s".format(
                    l.hoster, l.testes, l.resolus, l.jouables,
                    when {
                        l.jouables > 0 -> "✅"
                        l.resolus == 0 -> "⛔ AUCUN RESOLU — extracteur manquant ou domaine changé"
                        else -> "⛔ résolu mais refusé — en-tête, jeton ou hébergeur mort"
                    },
                ),
            )
        }

        val vivants = resultats.count { it.jouables > 0 }
        println("\nHEBERGEURS VIVANTS $vivants/${resultats.size}")
        println("LIENS TESTES ${resultats.sumOf { it.testes }} sur ${resultats.sumOf { it.total }}")
        resultats.filter { it.jouables == 0 }
            .forEach { println("MORT ${it.hoster}") }
    }

    private data class Ligne(
        val hoster: String,
        val testes: Int,
        val resolus: Int,
        val jouables: Int,
        val total: Int,
    )

    private companion object {
        /** Assez pour trancher, pas assez pour y passer la nuit. */
        const val ECHANTILLON = 3
    }
}
