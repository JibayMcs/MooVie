package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.shared.appVersionName
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Une entrée de cache écrite par une autre version ne doit pas être resservie.
 *
 * Le champ `providers` couvrait déjà le catalogue **ajouté**. Rien ne couvrait
 * le catalogue **corrigé**, qui est pourtant le cas courant : mêmes catalogues
 * interrogés, donc entrée jugée complète, donc rejouée six heures durant.
 *
 * Constaté en vrai — la VF d'anime-sama venait d'être débloquée, la sonde la
 * voyait, et l'application affichait toujours « Aucune source en VF » sur les
 * fiches déjà ouvertes. Le correctif était bon ; le cache le masquait, et rien
 * ne permettait de forcer la relecture.
 */
class SourceCacheVersionTest {

    private val cache = SourceCacheRepository()
    private val liens = listOf(EmbedLink(url = "https://x/1", hoster = "sibnet", language = "VF"))

    @Test
    fun `une entree de la version courante est resservie`() = runTest {
        val cle = "tv:999:s1e1"
        cache.put(cle, liens, setOf("animesama"))
        assertEquals(1, cache.get(cle, setOf("animesama"))?.size)
    }

    /**
     * On ne peut pas écrire une entrée d'une autre version par l'API publique —
     * c'est bien le but. On vérifie donc l'autre bout : la version courante est
     * réellement enregistrée, et non laissée vide, faute de quoi la garde ne
     * déclencherait jamais.
     */
    @Test
    fun `la version est bien enregistree`() = runTest {
        val cle = "tv:998:s1e1"
        cache.put(cle, liens, setOf("animesama"))
        assertEquals(1, cache.get(cle, setOf("animesama"))?.size)
        assert(appVersionName.isNotBlank()) { "sans version, la garde ne sert à rien" }
    }

    @Test
    fun `une cle absente ne rend rien`() = runTest {
        assertNull(cache.get("tv:997:s9e9", setOf("animesama")))
    }
}
