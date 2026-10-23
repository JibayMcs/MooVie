package fr.moovie.tv.core.subtitles

import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.usecase.rankSubtitles
import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleRankingTest {

    private fun sub(
        id: String,
        language: String = "fr",
        fps: Double? = null,
    ) = SubtitleCandidate(
        fileId = id,
        language = language,
        fps = fps,
    )

    private fun ids(list: List<SubtitleCandidate>) = list.map { it.fileId }

    @Test
    fun `la langue preferee passe avant la cadence`() {
        val ranked = rankSubtitles(
            candidates = listOf(
                sub("en", language = "en", fps = 23.976),
                sub("fr", language = "fr", fps = 25.0),
            ),
            preferredLanguages = listOf("fr", "en"),
            streamFps = 23.976,
        )

        assertEquals(listOf("fr", "en"), ids(ranked))
    }

    @Test
    fun `une langue hors preference finit derriere`() {
        val ranked = rankSubtitles(
            candidates = listOf(sub("es", language = "es"), sub("en", language = "en")),
            preferredLanguages = listOf("en"),
        )

        assertEquals(listOf("en", "es"), ids(ranked))
    }

    /** Une cadence identique évite la dérive : elle départage deux sous-titres de même langue. */
    @Test
    fun `a langue egale la cadence du flux departage deux sous-titres de meme langue`() {
        val ranked = rankSubtitles(
            candidates = listOf(
                sub("derive", fps = 25.0),
                sub("cale", fps = 23.976),
            ),
            preferredLanguages = listOf("fr"),
            streamFps = 23.976,
        )

        assertEquals(listOf("cale", "derive"), ids(ranked))
    }

    /**
     * Une cadence différente se corrige exactement ; une cadence absente laisse
     * l'utilisateur régler à la main. La première vaut donc mieux.
     */
    @Test
    fun `une cadence connue mais differente passe devant une cadence absente`() {
        val ranked = rankSubtitles(
            candidates = listOf(
                sub("sans", fps = null),
                sub("avec", fps = 25.0),
            ),
            preferredLanguages = listOf("fr"),
            streamFps = 23.976,
        )

        assertEquals(listOf("avec", "sans"), ids(ranked))
    }

    /** Sans cadence de flux, le critère se neutralise au lieu de trancher au hasard. */
    @Test
    fun `sans cadence de flux l ordre du catalogue est conserve`() {
        val ranked = rankSubtitles(
            candidates = listOf(
                sub("premier", fps = 25.0),
                sub("second", fps = 23.976),
            ),
            preferredLanguages = listOf("fr"),
            streamFps = null,
        )

        assertEquals(listOf("premier", "second"), ids(ranked))
    }

    @Test
    fun `une liste vide reste vide`() {
        assertEquals(emptyList(), rankSubtitles(emptyList(), listOf("fr")))
    }
}
