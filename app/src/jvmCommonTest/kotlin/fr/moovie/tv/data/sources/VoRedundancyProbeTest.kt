package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Répond à une seule question : **combien de catalogues distincts servent de la
 * VO pour un titre donné ?** Un seul, et la langue tombe entièrement le jour où
 * ce catalogue s'arrête — exactement la situation que frembed avait créée pour
 * les séries.
 *
 * On compte le `VO` et rien d'autre. Le VOSTFR porte des sous-titres français
 * incrustés : le compter ici gonflerait le chiffre d'une couverture que le
 * spectateur visé ne peut pas utiliser.
 *
 * Se lance avec `-Dmoovie.probe=1`.
 */
class VoRedundancyProbeTest {

    private data class Cible(val libelle: String, val media: MediaRef)

    private val panier = listOf(
        Cible("Fight Club (1999)", MediaRef.Movie(550, "Fight Club", "1999")),
        Cible("Inception (2010)", MediaRef.Movie(27205, "Inception", "2010")),
        Cible("Dune (2021)", MediaRef.Movie(438631, "Dune", "2021")),
        Cible("Oppenheimer (2023)", MediaRef.Movie(872585, "Oppenheimer", "2023")),
        Cible("Breaking Bad S1E1", MediaRef.Episode(1396, "Breaking Bad", "2008", 1, 1)),
        Cible("Severance S1E1", MediaRef.Episode(95396, "Severance", "2022", 1, 1)),
    )

    @Test
    fun probeRedondanceVo() {
        if (System.getProperty("moovie.probe") == null) {
            println("[sonde VO] ignorée (relancer avec -Dmoovie.probe=1)")
            return
        }

        val providers = ProviderRegistry.all
        val redondance = mutableListOf<Int>()

        runBlocking {
            println("%-22s %s".format("", providers.joinToString(" ") { it.name.take(9).padEnd(10) }))
            for (cible in panier) {
                val cellules = providers.map { provider ->
                    val liens = withTimeoutOrNull(20_000) {
                        runCatching { provider.sourcesFor(cible.media) }.getOrNull()
                    }.orEmpty()
                    val vo = liens.count { it.language == "VO" }
                    "%-10s".format(if (vo > 0) "VO x$vo" else if (liens.isNotEmpty()) "·" else "—")
                }
                redondance += cellules.count { it.trimStart().startsWith("VO") }
                println("%-22s %s".format(cible.libelle.take(21), cellules.joinToString(" ")))
            }
        }

        val fragiles = redondance.count { it <= 1 }
        println()
        println("[sonde VO] catalogues servant la VO, par titre : ${redondance.joinToString(", ")}")
        println("[sonde VO] $fragiles/${panier.size} titres reposent sur un seul catalogue ou zéro")
    }
}
