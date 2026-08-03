package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Mesure la couverture **en version originale**, la seule qui intéresse un
 * public non francophone. Le taux global des autres sondes ne dit rien ici :
 * il est porté par des catalogues qui n'étiquettent presque jamais en VO.
 *
 * Le panier mêle volontairement des œuvres **anglophones** (où VO = anglais, le
 * cas du public visé), un titre **non anglophone** (où VO est autre chose que de
 * l'anglais — c'est bien la piste d'origine qu'on veut, pas « la version
 * anglaise ») et des épisodes, la partie historiquement la plus fragile.
 *
 * Se lance avec `-Dmoovie.probe=1` ; ignorée sinon, pour ne pas taper sur un
 * site vivant à chaque build.
 */
class VidapiCoverageProbeTest {

    private data class Cible(val libelle: String, val media: MediaRef)

    private val panier = listOf(
        Cible("Fight Club (1999)", MediaRef.Movie(550, "Fight Club", "1999")),
        Cible("Inception (2010)", MediaRef.Movie(27205, "Inception", "2010")),
        Cible("Dune (2021)", MediaRef.Movie(438631, "Dune", "2021")),
        Cible("Parasite (2019, VO coréenne)", MediaRef.Movie(496243, "Parasite", "2019")),
        Cible("Breaking Bad S1E1", MediaRef.Episode(1396, "Breaking Bad", "2008", 1, 1)),
        Cible("Breaking Bad S5E14", MediaRef.Episode(1396, "Breaking Bad", "2008", 5, 14)),
        Cible("Severance S1E1", MediaRef.Episode(95396, "Severance", "2022", 1, 1)),
        Cible("Attack on Titan S4E5", MediaRef.Episode(1429, "Attack on Titan", "2013", 4, 5)),
    )

    @Test
    fun probeVo() {
        if (System.getProperty("moovie.probe") == null) {
            println("[sonde vidapi] ignorée (relancer avec -Dmoovie.probe=1)")
            return
        }

        val provider = VidapiProvider(ExtractorRegistry.gateway)
        var couverts = 0

        runBlocking {
            for (cible in panier) {
                val liens = runCatching { provider.sourcesFor(cible.media) }.getOrDefault(emptyList())
                val vo = liens.count { it.language == "VO" }
                if (vo > 0) couverts++
                val etat = if (vo > 0) "✅" else "⛔"
                val qualite = liens.firstOrNull()?.variant.orEmpty()
                println("%-32s %-3s %d lien(s) VO  %s".format(cible.libelle, etat, vo, qualite))
            }
        }

        println("[sonde vidapi] $couverts/${panier.size} titres couverts en VO")
    }
}
