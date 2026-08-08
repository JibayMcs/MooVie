package fr.moovie.tv.data.download

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Le résumé qui alimente les pastilles des affiches et des sélecteurs de saison.
 *
 * Tout se lit dans la **clé**, jamais dans `isTv` : un chemin d'écriture l'a
 * déjà oublié, ce qui avait éclaté une série en autant de groupes que
 * d'épisodes. La clé est l'identité du média, elle ne peut pas manquer.
 */
class TitleDownloadsTest {

    private fun dl(key: String, state: DownloadState, done: Int = 0, total: Int = 0) =
        Download(key = key, title = "x", state = state, doneSegments = done, totalSegments = total)

    @Test
    fun `regroupe les episodes sous leur serie`() {
        val byTitle = listOf(
            dl("tv:1396:s1e1", DownloadState.DONE),
            dl("tv:1396:s3e9", DownloadState.DONE),
            dl("movie:550", DownloadState.DONE),
        ).byTitle()
        assertEquals(2, byTitle["tv:1396"]?.ready)
        assertEquals(1, byTitle["movie:550"]?.ready)
    }

    /**
     * La barre ne doit refléter que ce qui **transfère**. Y mêler les épisodes
     * finis la ferait monter à mesure qu'on télécharge, ce qui se lirait comme
     * l'avancement de la saison et non celui du transfert en cours.
     */
    @Test
    fun `la progression ignore ce qui est deja fini`() {
        val summary = listOf(
            dl("tv:1:s1e1", DownloadState.DONE, done = 10, total = 10),
            dl("tv:1:s1e2", DownloadState.RUNNING, done = 2, total = 10),
        ).byTitle()["tv:1"]
        assertEquals(1, summary?.ready)
        assertEquals(1, summary?.active)
        assertEquals(0.2f, summary?.progress)
    }

    @Test
    fun `compte les episodes prets d une saison`() {
        val items = listOf(
            dl("tv:1396:s2e1", DownloadState.DONE),
            dl("tv:1396:s2e2", DownloadState.DONE),
            dl("tv:1396:s2e3", DownloadState.RUNNING),
            dl("tv:1396:s3e1", DownloadState.DONE),
        )
        // En cours ne compte pas : la saison n'est pas disponible tant que le
        // fichier n'est pas là.
        assertEquals(2, items.readyInSeason(1396, 2))
        assertEquals(1, items.readyInSeason(1396, 3))
        assertEquals(0, items.readyInSeason(1396, 1))
    }

    /** `s1e1` ne doit pas compter pour la saison 1 d'une **autre** série. */
    @Test
    fun `ne confond pas deux series`() {
        val items = listOf(dl("tv:1400:s1e1", DownloadState.DONE))
        assertEquals(0, items.readyInSeason(1396, 1))
    }

    /** `s1e10` ne doit pas être pris pour `s1e1`. */
    @Test
    fun `ne confond pas e1 et e10`() {
        val items = listOf(
            dl("tv:1:s1e1", DownloadState.DONE),
            dl("tv:1:s1e10", DownloadState.DONE),
        )
        assertEquals(2, items.readyInSeason(1, 1))
    }
}
