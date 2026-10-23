package fr.moovie.tv.core.sources.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
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

    // ── Catalogues à redemander ──────────────────────────────────────────────

    /**
     * Le cas qui motive la fonction : le préchargement a écrit l'entrée sans
     * swiftflow, qui avait dépassé son délai. Les liens des autres se servent,
     * swiftflow seul est redemandé.
     */
    @Test
    fun `un catalogue expire est le seul a redemander`() {
        assertEquals(
            setOf("swiftflow"),
            cataloguesManquants(listOf("fstream", "vidapi"), listOf("fstream", "swiftflow", "vidapi")),
        )
    }

    @Test
    fun `une entree complete n a rien a redemander`() {
        assertEquals(emptySet(), cataloguesManquants(listOf("fstream", "swiftflow"), listOf("swiftflow", "fstream")))
    }

    /** Un catalogue désactivé depuis ne manque pas : on n'attend plus rien de lui. */
    @Test
    fun `un catalogue desactive ne manque pas`() {
        assertEquals(emptySet(), cataloguesManquants(listOf("fstream", "coflix"), listOf("fstream")))
    }

    /** Entrée d'une version qui n'écrivait pas la liste : tout est à redemander. */
    @Test
    fun `sans liste de catalogues tout manque`() {
        assertEquals(setOf("fstream", "vidapi"), cataloguesManquants(emptyList(), listOf("fstream", "vidapi")))
    }

    /** Les deux lectures disent la même chose : complet, c'est rien à redemander. */
    @Test
    fun `complet veut dire rien a redemander`() {
        val consulte = listOf("fstream")
        val attendu = listOf("fstream", "swiftflow")
        assertEquals(cataloguesManquants(consulte, attendu).isEmpty(), isCacheComplete(consulte, attendu))
        assertFalse(isCacheComplete(consulte, attendu))
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
