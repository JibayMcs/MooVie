package fr.moovie.tv.data.backup

import fr.moovie.tv.data.watch.HistoryEntry
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.WatchlistEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le respect des suppressions.
 *
 * C'est le défaut que la sauvegarde traînait depuis toujours : démarquer un
 * épisode puis fusionner le faisait revenir. Ces tests décrivent la règle qui
 * l'en empêche, et surtout **ce qu'elle ne casse pas** pour les vieux fichiers.
 */
class TombstoneMergeTest {

    private fun merge(current: WatchState, incoming: WatchState) =
        mergeWatchState(current, incoming, ImportMode.MERGE).first

    /** Le cas rapporté : démarqué ici, encore vu dans le fichier, plus ancien. */
    @Test
    fun `un episode demarque ne ressuscite pas`() {
        val current = WatchState(watched = emptySet(), watchedAt = mapOf("tv:1:s1e1" to 200))
        val backup = WatchState(watched = setOf("tv:1:s1e1"), watchedAt = mapOf("tv:1:s1e1" to 100))

        assertFalse("tv:1:s1e1" in merge(current, backup).watched)
    }

    /** L'inverse doit marcher aussi : un retrait plus ancien ne gagne pas. */
    @Test
    fun `un vu plus recent que le retrait local l emporte`() {
        val current = WatchState(watched = emptySet(), watchedAt = mapOf("tv:1:s1e1" to 100))
        val backup = WatchState(watched = setOf("tv:1:s1e1"), watchedAt = mapOf("tv:1:s1e1" to 200))

        assertTrue("tv:1:s1e1" in merge(current, backup).watched)
    }

    /** Et un retrait venu du fichier s'applique ici. */
    @Test
    fun `un retrait venu du fichier efface le vu local`() {
        val current = WatchState(watched = setOf("movie:7"), watchedAt = mapOf("movie:7" to 100))
        val backup = WatchState(watched = emptySet(), watchedAt = mapOf("movie:7" to 300))

        assertFalse("movie:7" in merge(current, backup).watched)
    }

    /**
     * **La non-régression qui compte.** Un fichier d'avant les pierres tombales
     * ne date rien : il ne peut donc rien affirmer de ses suppressions, et on
     * refait l'union comme autrefois. Sans cette égalité, importer une vieille
     * sauvegarde effacerait des « vus » qu'elle n'a jamais prétendu retirer.
     */
    @Test
    fun `sans aucun horodatage on refait l union d autrefois`() {
        val current = WatchState(watched = setOf("movie:1"))
        val backup = WatchState(watched = setOf("movie:2"))

        assertEquals(setOf("movie:1", "movie:2"), merge(current, backup).watched)
    }

    /** Une pierre tombale se transmet, sinon le prochain import ressusciterait. */
    @Test
    fun `la pierre tombale survit a la fusion`() {
        val current = WatchState(watched = emptySet(), watchedAt = mapOf("movie:9" to 500))
        val backup = WatchState(watched = setOf("movie:9"), watchedAt = mapOf("movie:9" to 100))

        val merged = merge(current, backup)

        assertFalse("movie:9" in merged.watched)
        assertEquals(500, merged.watchedAt["movie:9"])
    }

    /** L'horodatage retenu est le plus récent des deux, quel que soit l'état. */
    @Test
    fun `l horodatage fusionne est le plus recent`() {
        val current = WatchState(watched = setOf("a"), watchedAt = mapOf("a" to 10, "b" to 80))
        val backup = WatchState(watched = setOf("b"), watchedAt = mapOf("a" to 40, "b" to 20))

        val merged = merge(current, backup)

        assertEquals(40, merged.watchedAt["a"])
        assertEquals(80, merged.watchedAt["b"])
    }

    /** Le bilan ne compte que ce qui a réellement changé d'état. */
    @Test
    fun `le bilan ignore ce qui etait deja vu`() {
        val current = WatchState(watched = setOf("movie:1"))
        val backup = WatchState(watched = setOf("movie:1", "movie:2"))

        val (_, report) = mergeWatchState(current, backup, ImportMode.MERGE)

        assertEquals(1, report.watchedAdded)
        assertEquals(1, report.watchedAlreadyThere)
    }

    private fun resume(key: String, at: Long) =
        ResumeEntry(key = key, tmdbId = 1, isTv = false, positionMs = 500, updatedAt = at)

    private fun later(key: String, at: Long) =
        WatchlistEntry(key = key, tmdbId = 1, isTv = false, addedAt = at)

    /** « Retirer de Reprendre » ne doit pas être défait par un import. */
    @Test
    fun `une reprise retiree ne revient pas`() {
        val current = WatchState(resumeRemovedAt = mapOf("movie:5" to 300))
        val backup = WatchState(resume = listOf(resume("movie:5", 100)))

        assertTrue(merge(current, backup).resume.isEmpty())
    }

    /** Mais une reprise plus récente que le retrait, elle, revient. */
    @Test
    fun `une reprise plus recente que son retrait revient`() {
        val current = WatchState(resumeRemovedAt = mapOf("movie:5" to 100))
        val backup = WatchState(resume = listOf(resume("movie:5", 300)))

        assertEquals(listOf("movie:5"), merge(current, backup).resume.map { it.key })
    }

    /** Un retrait venu du fichier s'applique à la reprise locale. */
    @Test
    fun `un retrait venu du fichier efface la reprise locale`() {
        val current = WatchState(resume = listOf(resume("movie:5", 100)))
        val backup = WatchState(resumeRemovedAt = mapOf("movie:5" to 300))

        assertTrue(merge(current, backup).resume.isEmpty())
    }

    /** Même règle pour la liste « à voir ». */
    @Test
    fun `un titre retire de la liste ne revient pas`() {
        val current = WatchState(watchlistRemovedAt = mapOf("tv:9" to 300))
        val backup = WatchState(watchlist = listOf(later("tv:9", 100)))

        assertTrue(merge(current, backup).watchlist.isEmpty())
    }

    @Test
    fun `un ajout a la liste plus recent que son retrait gagne`() {
        val current = WatchState(watchlistRemovedAt = mapOf("tv:9" to 100))
        val backup = WatchState(watchlist = listOf(later("tv:9", 300)))

        assertEquals(listOf("tv:9"), merge(current, backup).watchlist.map { it.key })
    }

    /**
     * Non-régression : sans aucune date de retrait, on retrouve l'union — deux
     * appareils qui ne savent pas dater leurs suppressions ne doivent rien
     * s'effacer mutuellement.
     */
    @Test
    fun `sans date de retrait la reprise et la liste font l union`() {
        val current = WatchState(
            resume = listOf(resume("movie:1", 10)),
            watchlist = listOf(later("tv:1", 10)),
        )
        val backup = WatchState(
            resume = listOf(resume("movie:2", 20)),
            watchlist = listOf(later("tv:2", 20)),
        )

        val merged = merge(current, backup)

        assertEquals(setOf("movie:1", "movie:2"), merged.resume.map { it.key }.toSet())
        assertEquals(setOf("tv:1", "tv:2"), merged.watchlist.map { it.key }.toSet())
    }

    /** La reprise la plus récente l'emporte toujours, retraits mis à part. */
    @Test
    fun `la reprise la plus recente gagne`() {
        val current = WatchState(resume = listOf(resume("movie:1", 100)))
        val backup = WatchState(resume = listOf(resume("movie:1", 300)))

        assertEquals(300, merge(current, backup).resume.single().updatedAt)
    }

    private fun hist(key: String, at: Long) =
        HistoryEntry(key = key, tmdbId = 1, isTv = false, title = "T", watchedAt = at)

    /**
     * Le défaut rapporté : une ligne supprimée de l'historique revenait à la
     * synchro suivante, et le soupçon tombait sur Backblaze alors que le
     * fichier arrivait intact — c'est la fusion qui faisait l'union.
     */
    @Test
    fun `une ligne d historique supprimee ne revient pas`() {
        val current = WatchState(historyRemovedAt = mapOf("movie:5" to 300))
        val backup = WatchState(history = listOf(hist("movie:5", 100)))

        assertTrue(merge(current, backup).history.isEmpty())
    }

    /** Revu depuis la suppression : c'est un nouveau visionnage, il reste. */
    @Test
    fun `un visionnage posterieur au retrait revient`() {
        val current = WatchState(historyRemovedAt = mapOf("movie:5" to 100))
        val backup = WatchState(history = listOf(hist("movie:5", 300)))

        assertEquals(listOf("movie:5"), merge(current, backup).history.map { it.key })
    }

    /** Une suppression faite ailleurs s'applique à la ligne locale. */
    @Test
    fun `un retrait venu du fichier efface la ligne locale`() {
        val current = WatchState(history = listOf(hist("movie:5", 100)))
        val backup = WatchState(historyRemovedAt = mapOf("movie:5" to 300))

        assertTrue(merge(current, backup).history.isEmpty())
    }

    /** La pierre tombale se propage, sans quoi le prochain appareil la perdrait. */
    @Test
    fun `la pierre tombale d historique survit a la fusion`() {
        val current = WatchState(historyRemovedAt = mapOf("movie:5" to 300))
        val backup = WatchState(historyRemovedAt = mapOf("movie:9" to 400))

        val merged = merge(current, backup).historyRemovedAt
        assertEquals(300, merged["movie:5"])
        assertEquals(400, merged["movie:9"])
    }

    /**
     * Un fichier d'avant cette version ne sait rien de ses suppressions : sans
     * horodatage, on refait l'union d'autrefois plutôt que d'effacer.
     */
    @Test
    fun `sans horodatage l historique refait l union`() {
        val current = WatchState(history = listOf(hist("movie:1", 100)))
        val backup = WatchState(history = listOf(hist("movie:2", 200)))

        assertEquals(setOf("movie:1", "movie:2"), merge(current, backup).history.map { it.key }.toSet())
    }

    /** Deux visionnages du même titre à deux instants restent deux lignes. */
    @Test
    fun `deux visionnages distincts ne se dedoublonnent pas`() {
        val current = WatchState(history = listOf(hist("movie:1", 100)))
        val backup = WatchState(history = listOf(hist("movie:1", 200)))

        assertEquals(2, merge(current, backup).history.size)
    }
}
