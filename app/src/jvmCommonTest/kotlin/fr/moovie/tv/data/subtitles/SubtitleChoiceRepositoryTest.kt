package fr.moovie.tv.data.subtitles

import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Le sous-titre choisi est retenu par média, et oublié quand on le quitte. */
class SubtitleChoiceRepositoryTest {

    @BeforeTest
    fun avant() = useInMemoryStores()

    @AfterTest
    fun apres() = useFileStores()

    private val candidat = SubtitleCandidate(
        fileId = "https://sous-titres.exemple/fr/123.srt",
        language = "fr",
        release = "Film.2024.1080p.WEB",
        fps = 23.976,
    )

    @Test
    fun `un choix retenu est rendu tel quel`() = runTest {
        val depot = SubtitleChoiceRepository()
        depot.remember("movie:42", candidat)

        assertEquals(candidat, depot.get("movie:42"))
    }

    @Test
    fun `chaque média garde son propre choix`() = runTest {
        val depot = SubtitleChoiceRepository()
        depot.remember("tv:7:1:1", candidat)

        assertNull(depot.get("tv:7:1:2"))
    }

    @Test
    fun `un nouveau choix remplace l'ancien`() = runTest {
        val depot = SubtitleChoiceRepository()
        depot.remember("movie:42", candidat)
        val autre = candidat.copy(fileId = "autre", language = "en", fps = null)
        depot.remember("movie:42", autre)

        assertEquals(autre, depot.get("movie:42"))
    }

    @Test
    fun `oublier retire le choix`() = runTest {
        val depot = SubtitleChoiceRepository()
        depot.remember("movie:42", candidat)
        depot.forget("movie:42")

        assertNull(depot.get("movie:42"))
    }

    @Test
    fun `une clé vide ne retient rien`() = runTest {
        val depot = SubtitleChoiceRepository()
        depot.remember("", candidat)

        assertNull(depot.get(""))
    }
}
