package fr.moovie.tv.data.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeLayoutTest {

    private fun genre(id: Int, name: String = "Genre $id") =
        HomeLayoutEntry.of(PinnedGenre(isTv = false, genreId = id, name = name))

    private fun ids(layout: List<HomeLayoutEntry>) = layout.map { it.id }

    /**
     * Le cas de la mise à jour : une installation existante n'a rien de stocké,
     * et son accueil ne doit pas se vider.
     */
    @Test
    fun `sans rien de stocke, la disposition par defaut`() {
        assertEquals(defaultHomeLayout, mergeHomeLayout(null))
        assertEquals(defaultHomeLayout, mergeHomeLayout(emptyList()))
    }

    /**
     * Une rangée ajoutée par une version ultérieure doit apparaître, sans quoi
     * elle resterait invisible pour tous ceux qui ont déjà réorganisé.
     */
    @Test
    fun `une rangee integree absente est ajoutee en fin`() {
        val stored = listOf(HomeLayoutEntry(HomeRowKind.TOP_MOVIES), genre(16))

        val merged = mergeHomeLayout(stored)

        assertEquals(HomeRowKind.TOP_MOVIES, merged.first().kind)
        assertEquals("GENRE:movie:16", merged[1].id)
        assertTrue(merged.map { it.kind }.containsAll(HomeRowKind.builtIn))
    }

    /**
     * La distinction qui justifie `visible` : une rangée retirée par
     * l'utilisateur est stockée masquée, et ne doit surtout pas être « rendue »
     * par la complétion à la mise à jour suivante.
     */
    @Test
    fun `une rangee masquee le reste apres fusion`() {
        val stored = HomeRowKind.builtIn.map {
            HomeLayoutEntry(it, visible = it != HomeRowKind.TRENDING_TV)
        }

        val merged = mergeHomeLayout(stored)

        assertEquals(1, merged.count { !it.visible })
        assertEquals(HomeRowKind.TRENDING_TV, merged.first { !it.visible }.kind)
    }

    /** Un fichier abîmé ne doit pas produire une rangée sans titre ni contenu. */
    @Test
    fun `une entree de genre sans genre est ecartee`() {
        val stored = listOf(HomeLayoutEntry(HomeRowKind.GENRE), genre(16))

        assertEquals(1, mergeHomeLayout(stored).count { it.kind == HomeRowKind.GENRE })
    }

    @Test
    fun `sans ancre, on epingle en fin`() {
        val layout = defaultHomeLayout

        val after = insertHomeEntry(layout, genre(16))

        assertEquals("GENRE:movie:16", after.last().id)
        assertEquals(layout.size + 1, after.size)
    }

    @Test
    fun `avant et apres une ancre`() {
        val layout = listOf(
            HomeLayoutEntry(HomeRowKind.RESUME),
            HomeLayoutEntry(HomeRowKind.TRENDING_TV),
        )

        val before = insertHomeEntry(layout, genre(16), anchorId = "TRENDING_TV", after = false)
        val andAfter = insertHomeEntry(layout, genre(16), anchorId = "TRENDING_TV", after = true)

        assertEquals(listOf("RESUME", "GENRE:movie:16", "TRENDING_TV"), ids(before))
        assertEquals(listOf("RESUME", "TRENDING_TV", "GENRE:movie:16"), ids(andAfter))
    }

    /** Ancre disparue entre l'ouverture de la modale et la validation. */
    @Test
    fun `une ancre inconnue renvoie en fin`() {
        val after = insertHomeEntry(defaultHomeLayout, genre(16), anchorId = "GENRE:tv:42")

        assertEquals("GENRE:movie:16", after.last().id)
    }

    /** Réépingler exprime une position, pas une envie de doublon. */
    @Test
    fun `reepingler deplace au lieu de dupliquer`() {
        val layout = insertHomeEntry(defaultHomeLayout, genre(16))

        val moved = insertHomeEntry(layout, genre(16), anchorId = "RESUME", after = false)

        assertEquals(1, moved.count { it.id == "GENRE:movie:16" })
        assertEquals("GENRE:movie:16", moved.first().id)
    }

    /**
     * Le mode fusion promet que rien n'est perdu : un genre épinglé seulement
     * sur l'appareil doit survivre à l'import d'une sauvegarde qui l'ignore.
     */
    @Test
    fun `la fusion garde les genres propres a l'appareil`() {
        val current = insertHomeEntry(defaultHomeLayout, genre(16))
        val incoming = listOf(HomeLayoutEntry(HomeRowKind.TOP_MOVIES), genre(28))

        val merged = mergeHomeLayouts(current, incoming)

        assertEquals("TOP_MOVIES", merged.first().id)
        assertTrue(merged.any { it.id == "GENRE:movie:28" })
        assertTrue(merged.any { it.id == "GENRE:movie:16" })
    }

    /** L'ordre, lui, ne se fusionne pas : celui du fichier l'emporte. */
    @Test
    fun `la fusion reprend l'ordre du fichier`() {
        val current = listOf(
            HomeLayoutEntry(HomeRowKind.TOP_MOVIES),
            HomeLayoutEntry(HomeRowKind.RESUME),
        )
        val incoming = listOf(
            HomeLayoutEntry(HomeRowKind.RESUME),
            HomeLayoutEntry(HomeRowKind.TOP_MOVIES),
        )

        val merged = mergeHomeLayouts(current, incoming)

        assertEquals(listOf("RESUME", "TOP_MOVIES"), ids(merged).take(2))
    }

    /** L'ordre de déclaration de l'enum *est* la disposition par défaut. */
    @Test
    fun `la disposition par defaut couvre toutes les rangees integrees`() {
        assertEquals(HomeRowKind.builtIn, defaultHomeLayout.map { it.kind })
        assertTrue(defaultHomeLayout.all { it.visible })
        assertTrue(defaultHomeLayout.none { it.kind == HomeRowKind.GENRE })
    }
}
