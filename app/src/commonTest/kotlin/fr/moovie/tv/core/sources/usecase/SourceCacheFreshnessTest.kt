package fr.moovie.tv.core.sources.usecase

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceCacheFreshnessTest {

    @Test
    fun `entree ayant interroge tous les catalogues attendus est complete`() {
        assertTrue(isCacheComplete(listOf("fstream", "frembed"), listOf("fstream", "frembed")))
    }

    @Test
    fun `l ordre ne compte pas`() {
        assertTrue(isCacheComplete(listOf("frembed", "fstream"), listOf("fstream", "frembed")))
    }

    /** Le cas qui motive tout : une version ajoute frembed, le cache l'ignore. */
    @Test
    fun `un catalogue ajoute rend l entree incomplete`() {
        assertFalse(isCacheComplete(listOf("fstream"), listOf("fstream", "frembed")))
    }

    /** Entrée écrite par une version antérieure : aucune liste, donc suspecte. */
    @Test
    fun `entree sans catalogues est incomplete`() {
        assertFalse(isCacheComplete(emptyList(), listOf("fstream")))
    }

    /**
     * Un catalogue désactivé par l'utilisateur ne périme rien : l'entrée en sait
     * plus que nécessaire, la cascade filtrera.
     */
    @Test
    fun `un catalogue desactive ne perime pas l entree`() {
        assertTrue(isCacheComplete(listOf("fstream", "frembed", "coflix"), listOf("fstream")))
    }

    /** Aucun catalogue actif : rien à comparer, l'entrée reste utilisable. */
    @Test
    fun `sans attente l entree est complete`() {
        assertTrue(isCacheComplete(emptyList(), emptyList()))
    }

    // ── Péremption d'une mesure de qualité ───────────────────────────────────

    private val now = 1_800_000_000_000L
    private val build = "1.22.0"

    private fun fresh(
        age: Long,
        playable: Boolean = true,
        version: String = build,
    ) = isMeasureFresh(
        savedAt = now - age,
        playable = playable,
        version = version,
        runningVersion = build,
        now = now,
    )

    @Test
    fun `une mesure du jour se ressert`() {
        assertTrue(fresh(age = 60_000))
    }

    /** La définition d'un fichier est une propriété du fichier : elle tient. */
    @Test
    fun `une mesure jouable tient une journee`() {
        assertTrue(fresh(age = MEASURE_OK_TTL_MS - 1))
        assertFalse(fresh(age = MEASURE_OK_TTL_MS + 1))
    }

    /**
     * **Le test qui compte.** La sonde a des faux négatifs connus — un `HEAD`
     * refusé, un hébergeur qui a une mauvaise minute. Garder ce verdict aussi
     * longtemps qu'une réussite le rendrait définitif à l'écran : la source
     * resterait grisée au lancement suivant, et le seul recours serait de vider
     * tout le cache des sources en ayant deviné le rapport.
     */
    @Test
    fun `un echec s oublie bien plus vite qu une reussite`() {
        assertTrue(
            MEASURE_DEAD_TTL_MS < MEASURE_OK_TTL_MS,
            "un faux negatif ne doit pas survivre comme une mesure",
        )
        val age = MEASURE_DEAD_TTL_MS + 1
        assertFalse(fresh(age, playable = false), "l'echec doit etre re-sonde")
        assertTrue(fresh(age, playable = true), "la mesure, elle, tient toujours")
    }

    @Test
    fun `un echec recent evite quand meme de re-sonder`() {
        assertTrue(fresh(age = MEASURE_DEAD_TTL_MS - 1, playable = false))
    }

    /**
     * Changer de build, c'est changer le code d'extraction — donc possiblement
     * la variante retenue. C'est le péremptoire réel, bien plus sûr qu'une durée.
     * Même leçon que sur le cache des liens, où un correctif d'anime-sama est
     * resté invisible six heures.
     */
    @Test
    fun `une mesure d une autre version ne vaut rien`() {
        assertFalse(fresh(age = 60_000, version = "1.21.0"))
        assertFalse(fresh(age = 60_000, version = ""))
    }

    /** Horloge reculée : une mesure datée du futur ne doit pas devenir éternelle. */
    @Test
    fun `une mesure du futur n est pas fraiche`() {
        assertFalse(fresh(age = -60_000))
    }
}
