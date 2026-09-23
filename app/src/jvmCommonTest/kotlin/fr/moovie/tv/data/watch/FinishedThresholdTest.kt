package fr.moovie.tv.data.watch

import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le passage automatique en « vu », sous les angles où il manquait.
 *
 * Le seuil valait dix secondes restantes, et personne ne les atteint : on
 * arrête un film au générique de fin, on quitte un épisode pendant le résumé de
 * la semaine prochaine, et le flux d'un hébergeur s'arrête volontiers deux
 * secondes trop tôt. Restaient deux chemins vers « vu » — la fin de lecture pour
 * de bon et le bouton « Suivant » — d'où des épisodes regardés qui traînaient
 * indéfiniment dans « Reprendre la lecture ».
 *
 * Ce qui se teste ici est donc la règle, pas le lecteur : elle vit dans [save],
 * appelée par les trois plateformes.
 */
class FinishedThresholdTest {

    @BeforeTest
    fun avant() = useInMemoryStores()

    @AfterTest
    fun apres() = useFileStores()

    private fun episode(key: String) = ResumeEntry(
        key = key,
        tmdbId = 108978,
        isTv = true,
        season = 1,
        episode = 1,
        title = "Reacher",
    )

    private val heure = 60 * 60 * 1000L

    @Test
    fun `un film arrete au generique de fin est vu`() = runTest {
        val depot = WatchProgressRepository()
        depot.register(episode("movie:550"))

        // Deux heures de film, arrêté à 1 h 54 : le générique défile depuis
        // longtemps, et c'est là que la plupart des gens quittent.
        depot.save("movie:550", (1.9 * heure).toLong(), 2 * heure)

        assertContains(depot.watched.first(), "movie:550")
    }

    @Test
    fun `un episode quitte cinq minutes avant la fin reste en cours`() = runTest {
        val depot = WatchProgressRepository()
        depot.register(episode("tv:108978:s1e1"))

        // 50 minutes d'épisode, quitté à 45 : il reste de l'histoire, pas du
        // générique. La reprise doit le garder.
        depot.save("tv:108978:s1e1", 45 * 60_000L, 50 * 60_000L)

        assertFalse(depot.watched.first().contains("tv:108978:s1e1"))
        assertTrue(depot.continueWatching.first().any { it.key == "tv:108978:s1e1" })
    }

    @Test
    fun `un media court garde la marge de dix secondes`() = runTest {
        val depot = WatchProgressRepository()
        depot.register(episode("tv:108978:s1e1"))

        // 2 minutes : 8 % n'en font que dix secondes, moins que la marge. C'est
        // elle qui doit l'emporter, sinon un format court se marque vu à
        // 1 min 50 comme à 1 min 52.
        depot.save("tv:108978:s1e1", 115_000, 120_000)

        assertContains(depot.watched.first(), "tv:108978:s1e1")
    }

    @Test
    fun `la fin de lecture marque vu meme sans entree de reprise`() = runTest {
        val depot = WatchProgressRepository()

        // Ce que fait un lecteur qui finit un épisode : la boucle périodique
        // passe dans les derniers pourcents, ce qui marque vu **et enterre
        // l'entrée de reprise** ; puis `markFinished` écrit la fin une seconde
        // fois, et la sortie du lecteur une troisième. Tout ce qui suivait la
        // première écriture ne trouvait plus d'entrée et repartait sans rien
        // faire — au point qu'un épisode dont la boucle avait manqué le passage
        // n'avait plus aucun chemin vers « vu ».
        depot.save("tv:108978:s1e1", 50 * 60_000L, 50 * 60_000L)

        assertContains(depot.watched.first(), "tv:108978:s1e1")
        assertTrue(
            depot.history.first().any { it.key == "tv:108978:s1e1" },
            "l'historique doit porter l'épisode, même écrit sans entrée de reprise",
        )
    }

    @Test
    fun `une duree inconnue ne marque jamais vu`() = runTest {
        val depot = WatchProgressRepository()
        depot.register(episode("tv:108978:s1e1"))

        // Flux sans durée annoncée : sans elle, aucune position ne prouve quoi
        // que ce soit, et surtout pas la fin.
        depot.save("tv:108978:s1e1", 42 * 60_000L, 0)

        assertFalse(depot.watched.first().contains("tv:108978:s1e1"))
    }
}
