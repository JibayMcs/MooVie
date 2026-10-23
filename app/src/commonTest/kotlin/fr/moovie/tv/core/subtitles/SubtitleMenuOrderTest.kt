package fr.moovie.tv.core.subtitles

import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.usecase.orderForMenu
import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleMenuOrderTest {

    private fun c(id: String) = SubtitleCandidate(fileId = id, language = "fr")
    private val liste = listOf(c("a"), c("b"), c("c"), c("d"))

    private fun ids(l: List<SubtitleCandidate>) = l.map { it.fileId }

    @Test
    fun `sans rien d'essaye, l'ordre du classement est conserve`() {
        assertEquals(listOf("a", "b", "c", "d"), ids(orderForMenu(liste, activeId = null, tried = emptySet())))
    }

    @Test
    fun `l'actif passe en tete, les essayes en queue`() {
        assertEquals(
            listOf("c", "a", "d", "b"),
            ids(orderForMenu(liste, activeId = "c", tried = setOf("b", "c"))),
        )
    }

    @Test
    fun `aucun candidat n'est perdu ni duplique`() {
        val ordonnes = orderForMenu(liste, activeId = "b", tried = setOf("a", "b", "d"))
        assertEquals(liste.map { it.fileId }.sorted(), ids(ordonnes).sorted())
    }
}
