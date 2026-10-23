package fr.moovie.tv.data.subtitles

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.subtitles.model.DownloadedSubtitle
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.model.SubtitleQuota
import fr.moovie.tv.core.subtitles.port.SubtitleCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ChainedSubtitleCatalogTest {

    private class Fake(
        override val name: String,
        override val available: Boolean,
        private val result: List<SubtitleCandidate>,
        private val throws: Boolean = false,
    ) : SubtitleCatalog {
        var searches = 0
        override suspend fun search(media: MediaRef, languages: List<String>): List<SubtitleCandidate> {
            searches++
            if (throws) error("boom")
            return result
        }
        override suspend fun download(candidate: SubtitleCandidate): DownloadedSubtitle? = null
        override suspend fun quota(): SubtitleQuota = SubtitleQuota.Unknown
    }

    private val media = MediaRef.Movie(tmdbId = 27205, title = "Inception")
    private val one = listOf(SubtitleCandidate(fileId = "a", language = "fr"))
    private val two = listOf(SubtitleCandidate(fileId = "b", language = "fr"))

    @Test
    fun unavailableFirstFallsBackToSecond() = runBlocking {
        val first = Fake("first", available = false, result = one)
        val second = Fake("second", available = true, result = two)
        val chain = ChainedSubtitleCatalog(listOf(first, second))
        assertEquals(two, chain.search(media, listOf("fr")))
        assertEquals(0, first.searches)
    }

    @Test
    fun emptyFirstFallsBackToSecond() = runBlocking {
        val first = Fake("first", available = true, result = emptyList())
        val second = Fake("second", available = true, result = two)
        val chain = ChainedSubtitleCatalog(listOf(first, second))
        assertEquals(two, chain.search(media, listOf("fr")))
        assertEquals(1, first.searches)
    }

    @Test
    fun nonEmptyFirstWinsAndSecondIsNotQueried() = runBlocking {
        val first = Fake("first", available = true, result = one)
        val second = Fake("second", available = true, result = two)
        val chain = ChainedSubtitleCatalog(listOf(first, second))
        assertEquals(one, chain.search(media, listOf("fr")))
        assertEquals(0, second.searches)
    }

    @Test
    fun noneAvailableMeansNotAvailable() {
        val chain = ChainedSubtitleCatalog(listOf(Fake("x", false, one)))
        assertFalse(chain.available)
    }

    @Test
    fun throwingCatalogFallsBackToNext() = runBlocking {
        val first = Fake("first", available = true, result = one, throws = true)
        val second = Fake("second", available = true, result = two)
        val chain = ChainedSubtitleCatalog(listOf(first, second))
        assertEquals(two, chain.search(media, listOf("fr")))
        assertEquals(1, first.searches)
    }

    @Test
    fun cancellationIsNotSwallowed() {
        val annule = object : SubtitleCatalog {
            override val name = "annule"
            override val available = true
            override suspend fun search(media: MediaRef, languages: List<String>): List<SubtitleCandidate> =
                throw CancellationException("annulé")
            override suspend fun download(candidate: SubtitleCandidate): DownloadedSubtitle? = null
            override suspend fun quota(): SubtitleQuota = SubtitleQuota.Unknown
        }
        val second = Fake("second", available = true, result = two)
        val chain = ChainedSubtitleCatalog(listOf(annule, second))
        assertFailsWith<CancellationException> { runBlocking { chain.search(media, listOf("fr")) } }
        assertEquals(0, second.searches)
    }

    @Test
    fun nonQuotaCatalogHasNoQuota() = runBlocking {
        val chain = ChainedSubtitleCatalog(listOf(Fake("addon", available = true, result = one)))
        chain.search(media, listOf("fr"))
        assertFalse(chain.aUnQuota(one.single()))
    }
}
