package fr.moovie.tv.core.subtitles

import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.usecase.rankSubtitles
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cas mesuré sur *Fullmetal Alchemist* (2003) S01E02 : l'addon propose, sous
 * cette série, un sous-titre de *Brotherhood* arrivé en tête du catalogue.
 */
class SubtitleRankingEpisodeTitleTest {

    private val brotherhood = SubtitleCandidate(fileId = "brotherhood", language = "en", release = "Fullmetal.Alchemist.Brotherhood.E02.720p.BDRip.XviD.AC3-ViSiON")
    private val sanctioned = SubtitleCandidate(fileId = "sanctioned", language = "en", release = "02 - Body of the Sanctioned")
    private val shinsen = SubtitleCandidate(fileId = "shinsen", language = "en", release = "[Shinsen-Subs]_Fullmetal_Alchemist_2_-_Brotherhood_-_02")

    @Test
    fun `le titre de l'episode dans la release passe devant les autres`() {
        val classes = rankSubtitles(
            listOf(brotherhood, shinsen, sanctioned), listOf("en"),
            episodeTitles = listOf("Body of the Sanctioned"),
        )
        assertEquals(listOf("sanctioned", "brotherhood", "shinsen"), classes.map { it.fileId })
    }

    @Test
    fun `sans titre d'episode, l'ordre du catalogue est conserve`() {
        val classes = rankSubtitles(listOf(brotherhood, shinsen, sanctioned), listOf("en"))
        assertEquals(listOf("brotherhood", "shinsen", "sanctioned"), classes.map { it.fileId })
    }

    @Test
    fun `la ponctuation de la release ne gene pas la correspondance`() {
        val points = sanctioned.copy(fileId = "points", release = "FMA.02.Body.of.the.Sanctioned.DVDRip")
        val classes = rankSubtitles(listOf(brotherhood, points), listOf("en"), episodeTitles = listOf("Body of the Sanctioned"))
        assertEquals("points", classes.first().fileId)
    }

    @Test
    fun `le titre anglais rattrape un titre francais absent des releases`() {
        val classes = rankSubtitles(
            listOf(brotherhood, sanctioned), listOf("en"),
            episodeTitles = listOf("Le Corps des sacrifiés", "Body of the Sanctioned"),
        )
        assertEquals("sanctioned", classes.first().fileId)
    }

    @Test
    fun `la langue prime toujours sur le titre de l'episode`() {
        val francais = SubtitleCandidate(fileId = "fr", language = "fr", release = "FMA.E02")
        val classes = rankSubtitles(
            listOf(sanctioned, francais), listOf("fr", "en"),
            episodeTitles = listOf("Body of the Sanctioned"),
        )
        assertEquals("fr", classes.first().fileId)
    }

    @Test
    fun `un titre trop court est ignore`() {
        val classes = rankSubtitles(listOf(brotherhood, sanctioned), listOf("en"), episodeTitles = listOf("Une"))
        assertEquals(listOf("brotherhood", "sanctioned"), classes.map { it.fileId })
    }
}
