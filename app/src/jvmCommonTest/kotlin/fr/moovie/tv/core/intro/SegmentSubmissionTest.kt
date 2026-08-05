package fr.moovie.tv.core.intro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SegmentSubmissionTest {

    private fun intro(startMs: Long? = null, endMs: Long? = null) = SegmentSubmission(
        tmdbId = 1429, isTv = true, kind = SegmentKind.INTRO,
        season = 4, episode = 5, startMs = startMs, endMs = endMs,
    )

    private fun credits(startMs: Long? = null, endMs: Long? = null) = SegmentSubmission(
        tmdbId = 1429, isTv = true, kind = SegmentKind.CREDITS,
        season = 4, episode = 5, startMs = startMs, endMs = endMs,
    )

    /** Le cas courant : un seul appui, à la fin de l'intro. */
    @Test
    fun `une intro se signale par sa seule fin`() {
        assertNull(validateSubmission(intro(endMs = 90_000)))
    }

    /** Idem pour le générique, mais par son début. */
    @Test
    fun `un generique se signale par son seul debut`() {
        assertNull(validateSubmission(credits(startMs = 1_380_000)))
    }

    @Test
    fun `une intro sans fin ne veut rien dire`() {
        assertEquals(SubmissionProblem.MISSING_MARK, validateSubmission(intro()))
    }

    @Test
    fun `un generique sans debut ne veut rien dire`() {
        assertEquals(SubmissionProblem.MISSING_MARK, validateSubmission(credits(endMs = 60_000)))
    }

    /**
     * Un segment de longueur nulle servait à déclarer « pas d'intro ici ». Ce
     * n'est plus proposé, donc deux bornes confondues ne sont plus qu'une
     * maladresse de marquage — et se refusent comme telle.
     */
    @Test
    fun `une duree nulle n est plus un signalement valide`() {
        assertEquals(SubmissionProblem.TOO_SHORT, validateSubmission(intro(endMs = 0)))
        assertEquals(
            SubmissionProblem.TOO_SHORT,
            validateSubmission(credits(startMs = 0, endMs = 0)),
        )
    }

    @Test
    fun `sous cinq secondes ce n est pas un segment`() {
        assertEquals(SubmissionProblem.TOO_SHORT, validateSubmission(intro(endMs = 4_000)))
        assertEquals(
            SubmissionProblem.TOO_SHORT,
            validateSubmission(credits(startMs = 100_000, endMs = 103_000)),
        )
    }

    /**
     * Sans début marqué, l'API compte depuis zéro : une intro qui se termine à
     * 3 min 30 dépasse alors son plafond. C'est précisément pourquoi le lecteur
     * fait marquer les deux bornes d'une intro.
     */
    @Test
    fun `une intro comptee depuis zero peut depasser le plafond`() {
        assertEquals(SubmissionProblem.TOO_LONG, validateSubmission(intro(endMs = 210_000)))
    }

    /** Et elle passe dès que le cold open est exclu. */
    @Test
    fun `la meme intro passe une fois son debut marque`() {
        assertNull(validateSubmission(intro(startMs = 120_000, endMs = 210_000)))
    }

    /**
     * Le cas rapporté sur AoT S4E13 : cold open jusqu'à 2 min 11, intro jusqu'à
     * 3 min 41. Marquée des deux bouts, elle ne fait que 90 s et passe — alors
     * qu'elle était refusée quand seule sa fin était relevée.
     */
    @Test
    fun `une intro precedee d un cold open passe avec ses deux bornes`() {
        assertNull(validateSubmission(intro(startMs = 131_000, endMs = 221_000)))
        assertEquals(SubmissionProblem.TOO_LONG, validateSubmission(intro(endMs = 221_000)))
    }

    /** Un générique sans fin va jusqu'au bout : aucune durée à contrôler. */
    @Test
    fun `un generique sans fin n a pas de duree a valider`() {
        assertNull(validateSubmission(credits(startMs = 5_000_000)))
    }

    @Test
    fun `un generique fini trop long est refuse`() {
        assertEquals(
            SubmissionProblem.TOO_LONG,
            validateSubmission(credits(startMs = 0, endMs = 1_900_000)),
        )
    }

    @Test
    fun `une fin avant le debut est incoherente`() {
        assertEquals(
            SubmissionProblem.REVERSED,
            validateSubmission(intro(startMs = 90_000, endMs = 30_000)),
        )
    }

    @Test
    fun `au-dela de six heures rien n est plausible`() {
        assertEquals(
            SubmissionProblem.OUT_OF_RANGE,
            validateSubmission(intro(endMs = 21_600_001)),
        )
        assertEquals(
            SubmissionProblem.OUT_OF_RANGE,
            validateSubmission(credits(startMs = -1)),
        )
    }

    /** Une série se signale par épisode : sans lui, la soumission n'a pas de cible. */
    @Test
    fun `une serie sans saison ni episode est refusee`() {
        val orphan = SegmentSubmission(
            tmdbId = 1429, isTv = true, kind = SegmentKind.INTRO, endMs = 90_000,
        )

        assertEquals(SubmissionProblem.MISSING_EPISODE, validateSubmission(orphan))
    }

    @Test
    fun `un film n a besoin ni de saison ni d episode`() {
        val movie = SegmentSubmission(
            tmdbId = 550, isTv = false, kind = SegmentKind.INTRO, endMs = 90_000,
        )

        assertNull(validateSubmission(movie))
    }
}
