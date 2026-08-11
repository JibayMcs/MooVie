package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
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
 * Mesure la couverture **des animés**, que les sondes générales ne disent pas :
 * leur panier stratifié tire dans le catalogue TMDB entier, où l'animé est
 * noyé, et un seul titre japonais sur quarante ne prouve rien.
 *
 * Le sujet mérite sa mesure parce que la chaîne y est différente : un seul
 * catalogue spécialisé (`animesama`), des saisons qui ne suivent pas la
 * numérotation TMDB, et une préférence VOSTFR là où le reste de l'application
 * vise la VF. Un titre peut donc échouer pour des raisons qui n'existent nulle
 * part ailleurs.
 *
 * On mesure trois choses, du plus important au moins important :
 *
 * 1. combien de titres ont **au moins un lien jouable**, toutes langues
 *    confondues — c'est la question « est-ce que ça se lance » ;
 * 2. combien n'en ont qu'un seul, donc sans redondance ;
 * 3. **qui** fournit ce lien, ce qui révèle une mono-dépendance.
 *
 * Lancer avec `-Dmoovie.probe=1`.
 */
class AnimeCoverageProbeTest {

    private data class Anime(val id: Int, val nom: String, val saison: Int, val episode: Int)

    /**
     * Panier volontairement mixte : des classiques au long cours (One Piece,
     * Naruto), des succès récents (Frieren, Dandadan) et des séries à saisons
     * multiples (L'Attaque des Titans). Les trois cassent pour des raisons
     * distinctes — un catalogue qui n'indexe que le récent, une numérotation de
     * saison qui dérive sur les longues séries, un titre trop neuf pour être
     * doublé.
     */
    private val panier = listOf(
        Anime(1429, "L'Attaque des Titans", 1, 1),
        Anime(1429, "L'Attaque des Titans", 3, 5),
        Anime(37854, "One Piece", 1, 1),
        Anime(46260, "Naruto", 1, 1),
        Anime(31910, "Naruto Shippuden", 1, 1),
        Anime(85937, "Demon Slayer", 1, 1),
        Anime(95479, "Jujutsu Kaisen", 1, 1),
        Anime(65930, "My Hero Academia", 1, 1),
        Anime(30984, "Bleach", 1, 1),
        Anime(31911, "Fullmetal Alchemist Brotherhood", 1, 1),
        Anime(209867, "Frieren", 1, 1),
        Anime(240411, "Dandadan", 1, 1),
    )

    @Test
    fun probeAnimeCoverage() {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde animés] ignorée (relancer avec -Dmoovie.probe=1)")
            return
        }

        runBlocking {
            val gate = Semaphore(4)
            println("%-26s %-38s %s".format("titre", "catalogues VF", "1er lien VF jouable"))
            println("─".repeat(100))

            var couverts = 0
            var fragiles = 0
            val fournisseurs = mutableMapOf<String, Int>()

            panier.forEach { a ->
                val media = MediaRef.Episode(a.id, a.nom, null, a.saison, a.episode)

                val liens: List<EmbedLink> = coroutineScope {
                    ProviderRegistry.all.map { p ->
                        async {
                            gate.withPermit {
                                runCatching { p.sourcesFor(media) }.getOrDefault(emptyList())
                                    .map { it.copy(provider = p.name) }
                            }
                        }
                    }.awaitAll().flatten()
                }

                val vf = liens.filter { it.language.equals("VF", true) }
                val parCatalogue = vf.groupingBy { it.provider ?: "?" }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .joinToString(" ") { "${it.key}:${it.value}" }
                    .ifBlank { "—" }

                // Le premier qui se résout **et** répond : c'est ce que
                // l'utilisateur obtiendrait en appuyant sur Lire.
                var jouable: String? = null
                var jouables = 0
                for (lien in vf) {
                    val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull() ?: continue
                    if (!runCatching { isStreamPlayable(flux) }.getOrDefault(false)) continue
                    jouables++
                    if (jouable == null) {
                        jouable = lien.provider ?: lien.hoster
                        fournisseurs[jouable] = (fournisseurs[jouable] ?: 0) + 1
                    }
                    if (jouables >= 2) break
                }

                if (jouable != null) couverts++
                if (jouables <= 1) fragiles++

                val etiquette = "${a.nom} S${a.saison}E${a.episode}"
                println(
                    "%-26s %-38s %s".format(
                        etiquette.take(26),
                        parCatalogue.take(38),
                        if (jouable != null) "✅ $jouable" else "⛔ aucune VF (${liens.size} liens toutes langues)",
                    ),
                )
            }

            println()
            println("════════ SYNTHÈSE ════════")
            println("couverture VF   : $couverts / ${panier.size}")
            println("sans redondance : $fragiles / ${panier.size}  (0 ou 1 lien jouable)")
            println("qui fournit le lien retenu :")
            fournisseurs.entries.sortedByDescending { it.value }
                .forEach { println("   %-16s %d".format(it.key, it.value)) }
        }
    }
}
