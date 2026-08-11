package fr.moovie.tv.data.sources

import fr.moovie.tv.shared.appVersionName
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
 *
 * Le test porte sur la comparaison seule, sans magasin : les tests unitaires
 * Android n'ont pas de DataStore, et c'est bien cette décision qui compte.
 */
class SourceCacheVersionTest {

    @Test
    fun `la version courante est acceptee`() {
        assertTrue(writtenByRunningVersion(appVersionName))
    }

    @Test
    fun `une autre version est rejetee`() {
        assertFalse(writtenByRunningVersion("0.0.1"))
    }

    /** Entrée d'avant l'existence du champ : à refaire, pas à resservir. */
    @Test
    fun `une entree sans version est rejetee`() {
        assertFalse(writtenByRunningVersion(""))
    }

    /** Sans version lisible, la garde ne se déclencherait jamais. */
    @Test
    fun `la version de l'application n'est pas vide`() {
        assertTrue(appVersionName.isNotBlank())
    }
}
