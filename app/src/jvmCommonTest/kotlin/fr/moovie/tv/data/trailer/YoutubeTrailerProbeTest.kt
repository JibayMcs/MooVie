package fr.moovie.tv.data.trailer

import fr.moovie.tv.data.sources.ExtractorRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire. Lancer avec `-Dmoovie.probe=1`.
 *
 * Mesure ce qu'un taux de réussite seul ne dit pas : **quel client** InnerTube
 * porte réellement les bandes-annonces. Trois sont câblés en cascade ; si le
 * relevé montre que tout passe par le premier, c'est qu'il n'y a pas de repli en
 * pratique, et la fonctionnalité tombera le jour où YouTube le restreindra.
 *
 * Le panier mêle des bandes-annonces récentes et anciennes : YouTube ne sert pas
 * les mêmes formats selon l'âge de la vidéo, et une vieille bande-annonce n'a
 * souvent plus que du progressif 360p.
 */
class YoutubeTrailerProbeTest {

    private data class V(val cle: String, val titre: String)

    private val bandesAnnonces = listOf(
        V("JfVOs4VSpmA", "Spider-Man : No Way Home (2021)"),
        V("d9MyW72ELq0", "Avengers : Endgame (2019)"),
        V("qSqVVswa420", "Fight Club (1999)"),
        V("6ZfuNTqbHE8", "Avatar (2009)"),
        V("uYPbbksJxIg", "Oppenheimer (2023)"),
        V("BdJKm16Co6M", "Avatar : La Voie de l'eau (2022)"),
    )

    @Test
    fun sonde() = runBlocking {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde bandes-annonces] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }

        val extracteur = YoutubeTrailerExtractor(ExtractorRegistry.gateway)
        val parClient = mutableMapOf<String, Int>()
        var reussites = 0

        println("\n[sonde bandes-annonces] ${bandesAnnonces.size} titres")
        for (v in bandesAnnonces) {
            val r = runCatching { extracteur.resolveDetailed(v.cle, "fr") }.getOrNull()
            if (r == null) {
                println("  ✗ ${v.titre}  — aucun client n'a rendu de flux")
                continue
            }
            reussites++
            parClient[r.client] = (parClient[r.client] ?: 0) + 1
            val qualite = r.stream.quality ?: "adaptatif"
            println(
                "  ✓ ${v.titre}  — ${r.client}, ${r.stream.format}, $qualite, " +
                    "${r.durationSeconds}s",
            )
        }

        println("\n  Couverture : $reussites/${bandesAnnonces.size}")
        println("  Par client : " + parClient.entries.joinToString { "${it.key}=${it.value}" })
        if (parClient.size == 1 && reussites > 0) {
            println("  ⚠ un seul client répond : la cascade n'a pas de repli effectif.")
        }
    }
}
