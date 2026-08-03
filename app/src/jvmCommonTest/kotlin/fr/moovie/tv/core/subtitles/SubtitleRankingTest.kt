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
        downloads: Int = 0,
        trusted: Boolean = false,
        hearingImpaired: Boolean = false,
        forced: Boolean = false,
        ai: Boolean = false,
        machine: Boolean = false,
    ) = SubtitleCandidate(
        fileId = id,
        language = language,
        fps = fps,
        downloads = downloads,
        fromTrusted = trusted,
        hearingImpaired = hearingImpaired,
        foreignPartsOnly = forced,
        aiTranslated = ai,
        machineTranslated = machine,
    )

    private fun ids(list: List<SubtitleCandidate>) = list.map { it.fileId }

    @Test
    fun `la langue preferee passe avant tout le reste`() {
        val ranked = rankSubtitles(
            candidates = listOf(
                sub("en", language = "en", downloads = 9_000, trusted = true),
                sub("fr", language = "fr", downloads = 1),
            ),
            preferredLanguages = listOf("fr", "en"),
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

    /** Une cadence identique évite la dérive : elle prime sur la popularité. */
    @Test
    fun `a langue egale la cadence du flux l emporte sur le nombre de telechargements`() {
        val ranked = rankSubtitles(
            candidates = listOf(
                sub("populaire", fps = 25.0, downloads = 50_000),
                sub("cale", fps = 23.976, downloads = 12),
            ),
            preferredLanguages = listOf("fr"),
            streamFps = 23.976,
        )

        assertEquals(listOf("cale", "populaire"), ids(ranked))
    }

    /**
     * Une cadence différente se corrige exactement ; une cadence absente laisse
     * l'utilisateur régler à la main. La première vaut donc mieux.
     */
    @Test
    fun `une cadence connue mais differente passe devant une cadence absente`() {
        val ranked = rankSubtitles(
            candidates = listOf(
                sub("sans", fps = null, downloads = 900),
                sub("avec", fps = 25.0, downloads = 1),
            ),
            preferredLanguages = listOf("fr"),
            streamFps = 23.976,
        )

        assertEquals(listOf("avec", "sans"), ids(ranked))
    }

    /** Sans cadence de flux, le critère se neutralise au lieu de trancher au hasard. */
    @Test
    fun `sans cadence de flux le classement retombe sur la popularite`() {
        val ranked = rankSubtitles(
            candidates = listOf(
                sub("peu", fps = 25.0, downloads = 10),
                sub("beaucoup", fps = 23.976, downloads = 5_000),
            ),
            preferredLanguages = listOf("fr"),
            streamFps = null,
        )

        assertEquals(listOf("beaucoup", "peu"), ids(ranked))
    }

    @Test
    fun `la traduction humaine passe devant la machine`() {
        val ranked = rankSubtitles(
            candidates = listOf(
                sub("machine", downloads = 8_000, machine = true),
                sub("ia", downloads = 4_000, ai = true),
                sub("humain", downloads = 3),
            ),
            preferredLanguages = listOf("fr"),
        )

        assertEquals(listOf("humain", "ia", "machine"), ids(ranked))
    }

    @Test
    fun `a egalite la source de confiance puis la popularite departagent`() {
        val ranked = rankSubtitles(
            candidates = listOf(
                sub("banal", downloads = 100),
                sub("confiance", downloads = 10, trusted = true),
            ),
            preferredLanguages = listOf("fr"),
        )

        assertEquals(listOf("confiance", "banal"), ids(ranked))
    }

    @Test
    fun `les sous-titres sourds et malentendants reculent, sauf demande explicite`() {
        val candidates = listOf(sub("sdh", hearingImpaired = true), sub("normal"))

        assertEquals(listOf("normal", "sdh"), ids(rankSubtitles(candidates, listOf("fr"))))
        assertEquals(
            listOf("sdh", "normal"),
            ids(rankSubtitles(candidates, listOf("fr"), preferHearingImpaired = true)),
        )
    }

    /**
     * Constaté sur la sonde : le mieux classé pour Fight Club était un
     * « Forced », qui ne sous-titre que les passages en langue étrangère. Sorti
     * en tête, il donne l'impression que les dialogues manquent.
     */
    @Test
    fun `les sous-titres forces reculent, sauf demande explicite`() {
        val candidates = listOf(
            sub("force", fps = 23.976, downloads = 18, forced = true),
            sub("complet", fps = 23.976, downloads = 5),
        )

        assertEquals(
            listOf("complet", "force"),
            ids(rankSubtitles(candidates, listOf("fr"), streamFps = 23.976)),
        )
        assertEquals(
            listOf("force", "complet"),
            ids(rankSubtitles(candidates, listOf("fr"), streamFps = 23.976, preferForced = true)),
        )
    }

    @Test
    fun `une liste vide reste vide`() {
        assertEquals(emptyList(), rankSubtitles(emptyList(), listOf("fr")))
    }
}
