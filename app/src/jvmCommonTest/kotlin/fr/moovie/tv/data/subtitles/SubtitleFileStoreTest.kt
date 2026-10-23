package fr.moovie.tv.data.subtitles

import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.usecase.SubtitleTiming
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtitleFileStoreTest {

    @Test
    fun fileIdWithSlashesStaysInsideTheStoreDirectory() {
        val tmp = Files.createTempDirectory("subs").toString().toPath()
        val dir = tmp / "subtitles"
        val store = SubtitleFileStore(dir = dir)
        val candidate = SubtitleCandidate(
            fileId = "https://subs5.strem.io/fre/../x/sub.srt",
            language = "fr",
        )

        val file = store.storeOriginal("movie:27205", candidate, "1\n00:00:01,000 --> 00:00:02,000\nSalut\n")

        assertEquals(dir, file.parent)
        assertTrue(java.io.File(file.toString()).exists())
    }

    @Test
    fun storedIdsMatchesExactlyAndIgnoresRetimedVersions() {
        val dir = Files.createTempDirectory("subs").toString().toPath() / "subtitles"
        val store = SubtitleFileStore(dir = dir)
        val a = SubtitleCandidate(fileId = "100", language = "fr")
        val b = SubtitleCandidate(fileId = "10", language = "fr")
        val c = SubtitleCandidate(fileId = "200", language = "fr")
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nSalut\n"
        val original = store.storeOriginal("movie:1", a, srt)
        store.storeOriginal("movie:2", c, srt)
        store.retimed(original, SubtitleTiming(offsetMs = 500, scale = 1.0))

        val presents = store.storedIds("movie:1", listOf(a, b, c))

        // "10" est un préfixe de "100" et "200" appartient à un autre média.
        assertEquals(setOf("100"), presents)
    }
}
