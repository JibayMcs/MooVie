package fr.moovie.tv.data.backup

import fr.moovie.tv.data.watch.HistoryEntry
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.WatchlistEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeBackupTest {

    private fun resume(key: String, updatedAt: Long, position: Long = 0) =
        ResumeEntry(key = key, tmdbId = 1, isTv = false, positionMs = position, updatedAt = updatedAt)

    private fun later(key: String) = WatchlistEntry(key = key, tmdbId = 1, isTv = false)

    private fun hist(key: String, at: Long) =
        HistoryEntry(key = key, tmdbId = 1, isTv = false, watchedAt = at)

    @Test
    fun `remplacer restaure exactement la sauvegarde`() {
        val current = WatchState(watched = setOf("movie:1"), watchlist = listOf(later("movie:9")))
        val backup = MoovieBackup(watched = listOf("movie:2"), watchlist = listOf(later("movie:3")))

        val (state, _) = mergeBackup(current, backup, ImportMode.REPLACE)

        assertEquals(setOf("movie:2"), state.watched)
        assertEquals(listOf("movie:3"), state.watchlist.map { it.key })
    }

    @Test
    fun `fusionner garde ce qui est deja la`() {
        val current = WatchState(watched = setOf("movie:1"))
        val backup = MoovieBackup(watched = listOf("movie:2"))

        val (state, report) = mergeBackup(current, backup, ImportMode.MERGE)

        assertEquals(setOf("movie:1", "movie:2"), state.watched)
        assertEquals(1, report.watchedAdded)
        assertEquals(0, report.watchedAlreadyThere)
    }

    @Test
    fun `un titre deja vu des deux cotes n est compte qu une fois`() {
        val current = WatchState(watched = setOf("movie:1", "movie:2"))
        val backup = MoovieBackup(watched = listOf("movie:2", "movie:3"))

        val (state, report) = mergeBackup(current, backup, ImportMode.MERGE)

        assertEquals(setOf("movie:1", "movie:2", "movie:3"), state.watched)
        assertEquals(1, report.watchedAdded)
        assertEquals(1, report.watchedAlreadyThere)
    }

    /**
     * Le cœur de la règle : deux TV utilisées en parallèle. Une sauvegarde plus
     * ancienne ne doit pas écraser un épisode regardé hier.
     */
    @Test
    fun `la reprise la plus recente gagne, pas celle du fichier`() {
        val current = WatchState(resume = listOf(resume("tv:1:s1e1", updatedAt = 2_000, position = 900)))
        val backup = MoovieBackup(resume = listOf(resume("tv:1:s1e1", updatedAt = 1_000, position = 100)))

        val (state, report) = mergeBackup(current, backup, ImportMode.MERGE)

        assertEquals(900, state.resume.single().positionMs, )
        assertEquals(0, report.resumeUpdated)
    }

    @Test
    fun `mais une reprise plus recente dans le fichier l emporte`() {
        val current = WatchState(resume = listOf(resume("tv:1:s1e1", updatedAt = 1_000, position = 100)))
        val backup = MoovieBackup(resume = listOf(resume("tv:1:s1e1", updatedAt = 3_000, position = 2_500)))

        val (state, report) = mergeBackup(current, backup, ImportMode.MERGE)

        assertEquals(2_500, state.resume.single().positionMs)
        assertEquals(1, report.resumeUpdated)
        assertEquals(0, report.resumeAdded)
    }

    @Test
    fun `une reprise inconnue est ajoutee`() {
        val current = WatchState(resume = listOf(resume("tv:1:s1e1", 1_000)))
        val backup = MoovieBackup(resume = listOf(resume("tv:2:s1e1", 500)))

        val (state, report) = mergeBackup(current, backup, ImportMode.MERGE)

        assertEquals(2, state.resume.size)
        assertEquals(1, report.resumeAdded)
    }

    @Test
    fun `la watchlist ne se duplique pas`() {
        val current = WatchState(watchlist = listOf(later("movie:1")))
        val backup = MoovieBackup(watchlist = listOf(later("movie:1"), later("movie:2")))

        val (state, report) = mergeBackup(current, backup, ImportMode.MERGE)

        assertEquals(listOf("movie:1", "movie:2"), state.watchlist.map { it.key })
        assertEquals(1, report.watchlistAdded)
    }

    /** Même épisode, deux instants : ce sont deux visionnages, pas un doublon. */
    @Test
    fun `l historique distingue deux visionnages du meme titre`() {
        val current = WatchState(history = listOf(hist("movie:1", 100)))
        val backup = MoovieBackup(history = listOf(hist("movie:1", 100), hist("movie:1", 500)))

        val (state, report) = mergeBackup(current, backup, ImportMode.MERGE)

        assertEquals(2, state.history.size)
        assertEquals(1, report.historyAdded)
    }

    @Test
    fun `l historique fusionne est trie du plus recent au plus ancien`() {
        val current = WatchState(history = listOf(hist("movie:1", 100)))
        val backup = MoovieBackup(history = listOf(hist("movie:2", 900), hist("movie:3", 500)))

        val (state, _) = mergeBackup(current, backup, ImportMode.MERGE)

        assertEquals(listOf(900L, 500L, 100L), state.history.map { it.watchedAt })
    }

    /** Le choix fait sur cet appareil-ci vient d'un geste plus récent. */
    @Test
    fun `la piste audio de l appareil prime sur celle du fichier`() {
        val current = WatchState(audioTracks = mapOf("tv:1" to "French"))
        val backup = MoovieBackup(audioTracks = mapOf("tv:1" to "English", "tv:2" to "English"))

        val (state, _) = mergeBackup(current, backup, ImportMode.MERGE)

        assertEquals("French", state.audioTracks["tv:1"])
        assertEquals("English", state.audioTracks["tv:2"])
    }

    @Test
    fun `fusionner une sauvegarde vide ne change rien`() {
        val current = WatchState(
            watched = setOf("movie:1"),
            resume = listOf(resume("tv:1:s1e1", 10)),
            watchlist = listOf(later("movie:2")),
        )

        val (state, report) = mergeBackup(current, MoovieBackup(), ImportMode.MERGE)

        assertEquals(current.watched, state.watched)
        assertEquals(1, state.resume.size)
        assertEquals(1, state.watchlist.size)
        assertTrue(listOf(report.watchedAdded, report.resumeAdded, report.watchlistAdded).all { it == 0 })
    }

    @Test
    fun `le resume du fichier alimente l ecran d apercu`() {
        val backup = MoovieBackup(
            watched = listOf("movie:1", "movie:2"),
            resume = listOf(resume("tv:1:s1e1", 1)),
            watchlist = listOf(later("movie:3")),
            history = listOf(hist("movie:1", 5)),
            appVersion = "1.10.0",
            platform = "Android TV",
            tmdbApiKey = "abc",
        )

        val s = backup.summary()

        assertEquals(2, s.watched)
        assertEquals(1, s.resume)
        assertEquals(1, s.watchlist)
        assertEquals(1, s.history)
        assertEquals("1.10.0", s.appVersion)
        assertTrue(s.hasApiKey)
    }
}
