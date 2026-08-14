package fr.moovie.tv.data.update

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde : ce que les deux canaux proposent réellement, contre le vrai GitHub.
 *
 * Le défaut rapporté — « la case préversions ne trouve rien » — peut venir de
 * trois endroits qui se ressemblent de l'extérieur : la requête, la sélection
 * dans la liste, ou la comparaison de versions. Les distinguer demande de voir
 * les trois, pour un jeu de versions installées.
 *
 * Ne s'exécute qu'avec `-Dmoovie.probe=1` : elle sort sur le réseau.
 */
class UpdateChannelProbeTest {

    @Test
    fun `ce que chaque canal propose`() {
        if (System.getProperty("moovie.probe") == null) return
        val repo = UpdateRepository()

        listOf(false, true).forEach { canal ->
            val release = runBlocking { repo.latestRelease(prereleases = canal) }
            println("[sonde] canal préversions=$canal -> ${release?.tagName ?: "AUCUNE"}")
            if (release == null) return@forEach
            println("        éligible=${repo.isEligible(release, canal)} assets=${release.assets.size}")
            listOf("1.19.0", "1.20.0", "1.21.0-rc.1", "1.21.0").forEach { installee ->
                println("        depuis $installee : newer=${repo.isNewer(release.tagName, installee)}")
            }
        }
    }
}
