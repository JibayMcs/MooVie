package fr.moovie.tv.data.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comparaison de versions de l'updater.
 *
 * C'est le seul calcul qui décide si une bannière s'affiche chez tout le monde.
 * Une erreur ici ne se voit pas au build : elle se voit en production, soit en
 * harcelant les utilisateurs à jour, soit en n'annonçant jamais rien.
 */
class VersionCompareTest {

    private val repo = UpdateRepository()

    @Test
    fun `un tag plus recent est propose`() {
        assertTrue(repo.isNewer("v1.15.0", "1.14.0"))
        assertTrue(repo.isNewer("v2.0.0", "1.99.9"))
        assertTrue(repo.isNewer("v1.14.1", "1.14.0"))
    }

    @Test
    fun `la version courante ou plus ancienne ne l'est pas`() {
        assertFalse(repo.isNewer("v1.14.0", "1.14.0"))
        assertFalse(repo.isNewer("v1.13.1", "1.14.0"))
    }

    /**
     * Le cœur du dispositif de préversion : celui qui a installé une release
     * candidate à la main doit recevoir la version définitive.
     */
    @Test
    fun `une stable est proposee a qui tient une preversion du meme numero`() {
        assertTrue(repo.isNewer("v1.15.0", "1.15.0-rc.1"))
        assertTrue(repo.isNewer("v1.15.0", "1.15.0-beta.3"))
    }

    /** L'inverse n'est jamais vrai : une rc ne remplace pas une stable. */
    @Test
    fun `une preversion ne remplace pas la stable du meme numero`() {
        assertFalse(repo.isNewer("v1.15.0-rc.1", "1.15.0"))
    }

    /**
     * Le numéro prime sur le suffixe : une préversion d'une version supérieure
     * reste une mise à jour — c'est ce qui permettra un jour d'enchaîner les rc
     * si on décide de les publier en release ordinaire.
     */
    @Test
    fun `le numero prime sur le suffixe`() {
        assertTrue(repo.isNewer("v1.16.0-rc.1", "1.15.0"))
        assertFalse(repo.isNewer("v1.14.0-rc.1", "1.15.0"))
    }

    /** Une réponse illisible ne doit pas déclencher de mise à jour. */
    @Test
    fun `une version illisible ne propose rien`() {
        assertFalse(repo.isNewer("nightly", "1.14.0"))
        assertFalse(repo.isNewer("v1.15.0", ""))
    }
}
