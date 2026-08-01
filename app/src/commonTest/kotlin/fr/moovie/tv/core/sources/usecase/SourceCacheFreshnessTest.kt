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
}
