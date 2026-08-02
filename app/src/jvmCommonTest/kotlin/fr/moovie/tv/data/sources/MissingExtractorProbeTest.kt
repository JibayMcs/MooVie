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
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Choisit **quel** extracteur écrire ensuite.
 *
 * La sonde de redondance a montré que le point faible n'est plus le catalogue
 * mais l'extracteur : trois hébergeurs seulement produisent un lien jouable, et
 * perdre uqload à lui seul coûterait 4 épisodes sur 17. Reste à savoir lequel
 * ajouter — et la réponse n'est pas « celui qu'on connaît », c'est celui qui
 * revient le plus souvent dans les listes **sans jamais se résoudre**.
 *
 * On compte donc, par hébergeur : combien de fois il est proposé, combien de
 * fois il donne un flux jouable, et surtout sur combien de **titres distincts**
 * il serait le seul recours. Ce dernier chiffre est le vrai gain : un hébergeur
 * qui n'apparaît que là où uqload marche déjà n'apporte aucune redondance.
 */
class MissingExtractorProbeTest {

    private val panier = listOf(
        MediaRef.Movie(693134, "Dune : Deuxième partie", "2024"),
        MediaRef.Movie(872585, "Oppenheimer", "2023"),
        MediaRef.Movie(634649, "Spider-Man : No Way Home", "2021"),
        MediaRef.Movie(550, "Fight Club", "1999"),
        MediaRef.Movie(278, "Les Évadés", "1994"),
        MediaRef.Movie(1246049, "Dracula", "2025"),
        MediaRef.Episode(1429, "L'Attaque des Titans", null, 1, 1),
        MediaRef.Episode(1429, "L'Attaque des Titans", null, 3, 5),
        MediaRef.Episode(1396, "Breaking Bad", null, 2, 1),
        MediaRef.Episode(94997, "House of the Dragon", null, 1, 1),
        MediaRef.Episode(1416, "Grey's Anatomy", null, 5, 8),
        MediaRef.Episode(60625, "Rick et Morty", null, 6, 2),
    )

    private data class Tally(
        var listed: Int = 0,
        var resolved: Int = 0,
        val titles: MutableSet<String> = mutableSetOf(),
    )

    @Test
    fun probeMissingExtractors() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val slots = Semaphore(4)
        val tally = mutableMapOf<String, Tally>()

        // Titres où un hébergeur non résolu serait le *seul* apport : ceux dont
        // la couverture actuelle est nulle ou tient à un unique hébergeur.
        val fragileTitles = mutableSetOf<String>()

        for (media in panier) {
            val label = when (media) {
                is MediaRef.Movie -> media.title
                is MediaRef.Episode -> "${media.title} S${media.season}E${media.episode}"
            }

            val links = ProviderRegistry.all.flatMap { p ->
                runCatching { p.sourcesFor(media) }.getOrDefault(emptyList())
            }.filter { it.language == "VF" }.distinctBy { it.url }

            val outcomes = coroutineScope {
                links.map { link ->
                    async {
                        slots.withPermit {
                            val stream = runCatching { ExtractorRegistry.resolve(link) }.getOrNull()
                            val playable = stream != null &&
                                runCatching { isStreamPlayable(stream) }.getOrDefault(false)
                            link.hoster to playable
                        }
                    }
                }.awaitAll()
            }

            for ((hoster, playable) in outcomes) {
                val t = tally.getOrPut(hoster) { Tally() }
                t.listed++
                if (playable) t.resolved++ else t.titles += label
            }

            val workingHosters = outcomes.filter { it.second }.map { it.first }.toSet()
            if (workingHosters.size <= 1) fragileTitles += label
        }

        println("\n%-16s %-8s %-9s %s".format("hébergeur", "proposé", "jouable", "titres où il échoue"))
        println("─".repeat(96))
        tally.entries
            .sortedWith(compareBy({ it.value.resolved }, { -it.value.listed }))
            .forEach { (hoster, t) ->
                println(
                    "%-16s %-8d %-9d %s".format(
                        hoster.take(15), t.listed, t.resolved,
                        if (t.resolved == 0) t.titles.size.toString() + " titres" else "—",
                    ),
                )
            }

        println("\n════════ QUEL EXTRACTEUR ÉCRIRE ════════")
        println("titres fragiles (0 ou 1 hébergeur qui marche) : ${fragileTitles.size} / ${panier.size}")
        println()
        println("gain réel d'un nouvel extracteur = nombre de titres fragiles où")
        println("cet hébergeur est proposé mais ne se résout pas :")
        tally.entries
            .filter { it.value.resolved == 0 }
            .map { (hoster, t) -> hoster to t.titles.count { it in fragileTitles } }
            .sortedByDescending { it.second }
            .forEach { (hoster, gain) -> println("   %-16s %d titres débloqués".format(hoster, gain)) }
    }
}
