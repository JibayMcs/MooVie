package fr.moovie.tv.ui.offline

import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.download.DownloadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ce que l'historique peut lire quand il n'y a plus de réseau.
 *
 * La règle tient en trois lignes et se trompe silencieusement : une vignette
 * qui ne réagit pas ressemble à une panne, et une vignette qui lance le
 * mauvais épisode ne se remarque qu'après le générique.
 */
class OfflineLibraryTest {

    private fun dl(key: String, state: DownloadState = DownloadState.DONE) =
        Download(key = key, title = "T", state = state)

    @Test
    fun `un film telecharge est lisible`() {
        val liste = listOf(dl("movie:550"))
        assertEquals("movie:550", liste.playableFor(550, isTv = false)?.key)
    }

    @Test
    fun `une serie ouvre son plus petit episode disponible`() {
        // Volontairement dans le désordre : c'est la clé qui tranche, pas
        // l'ordre d'arrivée dans la file de téléchargement.
        val liste = listOf(dl("tv:1396:s2e3"), dl("tv:1396:s1e2"), dl("tv:1396:s1e1"))
        assertEquals("tv:1396:s1e1", liste.playableFor(1396, isTv = true)?.key)
    }

    @Test
    fun `un telechargement inacheve n'est pas lisible`() {
        // Le lancer donnerait une erreur de lecture, là où la vraie réponse est
        // « pas encore ».
        DownloadState.entries.filter { it != DownloadState.DONE }.forEach { etat ->
            assertNull(listOf(dl("movie:550", etat)).playableFor(550, isTv = false), "$etat")
        }
    }

    @Test
    fun `un film et une serie de meme identifiant ne se confondent pas`() {
        val liste = listOf(dl("movie:1396"), dl("tv:1396:s1e1"))
        assertEquals("movie:1396", liste.playableFor(1396, isTv = false)?.key)
        assertEquals("tv:1396:s1e1", liste.playableFor(1396, isTv = true)?.key)
    }

    @Test
    fun `rien de telecharge ne rend rien`() {
        assertNull(listOf(dl("movie:99")).playableFor(550, isTv = false))
        assertNull(emptyList<Download>().playableFor(550, isTv = false))
    }
}
