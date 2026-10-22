package fr.moovie.tv.data.sources

import fr.moovie.tv.shared.appBuildId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Une entrée de cache écrite par un autre build ne doit pas être resservie.
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
 * La comparaison porte sur l'identité de build, pas sur le numéro de version :
 * un test local installé par-dessus la release garde le même `versionName` et
 * ressert pourtant les listes de l'ancien code (constaté le 22/10/2026 sur la
 * Mi Box).
 *
 * Le test porte sur la comparaison seule, sans magasin : les tests unitaires
 * Android n'ont pas de DataStore, et c'est bien cette décision qui compte.
 */
class SourceCacheVersionTest {

    @Test
    fun `le build courant est accepte`() {
        assertTrue(writtenByRunningVersion(appBuildId))
    }

    /**
     * Le cas de la Mi Box : même numéro de version, build différent. L'ancienne
     * identité était le seul `versionName`, qui était alors identique.
     */
    @Test
    fun `un autre build de meme numero de version est rejete`() {
        assertFalse(writtenByRunningVersion("$appBuildId.autre"))
    }

    @Test
    fun `un autre build est rejete`() {
        assertFalse(writtenByRunningVersion("0.0.1+1.abcdef0"))
    }

    /** Entrée d'avant l'existence du champ : à refaire, pas à resservir. */
    @Test
    fun `une entree sans identite de build est rejetee`() {
        assertFalse(writtenByRunningVersion(""))
    }

    /** Sans identité lisible, la garde ne se déclencherait jamais. */
    @Test
    fun `l'identite de build n'est pas vide`() {
        assertTrue(appBuildId.isNotBlank())
    }
}
