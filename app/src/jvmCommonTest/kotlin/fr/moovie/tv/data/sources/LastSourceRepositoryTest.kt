package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** La source mémorisée par titre : un aller-retour disque, un oubli, une clé vide. */
class LastSourceRepositoryTest {

    @BeforeTest
    fun fresh() = useInMemoryStores()

    @AfterTest
    fun restore() = useFileStores()

    private val lien = EmbedLink(
        url = "https://vidzy.org/e/abc",
        hoster = "vidzy",
        language = "VO",
        provider = "fstream",
        variant = "1080p",
    )

    @Test
    fun `un lien memorise se relit tel quel`() = runTest {
        val repo = LastSourceRepository()
        repo.remember("tv:1:s1e2", lien)

        assertEquals(lien, repo.get("tv:1:s1e2"))
    }

    @Test
    fun `chaque titre garde sa source`() = runTest {
        val repo = LastSourceRepository()
        val autre = lien.copy(url = "https://voe.sx/e/xyz", hoster = "voe", language = "VF")
        repo.remember("tv:1:s1e2", lien)
        repo.remember("tv:1:s1e3", autre)

        assertEquals(lien, repo.get("tv:1:s1e2"))
        assertEquals(autre, repo.get("tv:1:s1e3"))
    }

    @Test
    fun `un lien oublie ne revient pas`() = runTest {
        val repo = LastSourceRepository()
        repo.remember("movie:1", lien)
        repo.forget("movie:1")

        assertNull(repo.get("movie:1"))
    }

    @Test
    fun `une cle vide ne memorise rien`() = runTest {
        val repo = LastSourceRepository()
        repo.remember("", lien)

        assertNull(repo.get(""))
    }
}
