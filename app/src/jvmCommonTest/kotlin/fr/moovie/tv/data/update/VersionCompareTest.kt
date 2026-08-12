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

    /**
     * Deux préversions du même numéro se départagent — c'est ce qui rend le
     * canal « préversions » utilisable.
     *
     * Tant que l'app ne lisait que `releases/latest`, qui les exclut, la
     * question ne se posait pas. Depuis qu'un testeur peut recevoir les rc par
     * l'application, ne pas les ordonner le laisserait bloqué sur celle qu'il a
     * installée : l'app comparerait deux fois « 1.18.0 », n'y verrait aucune
     * différence, et ne proposerait plus jamais rien. Une panne silencieuse.
     */
    @Test
    fun `une preversion plus recente est proposee au testeur`() {
        assertTrue(repo.isNewer("v1.18.0-rc.5", "1.18.0-rc.4"))
        assertTrue(repo.isNewer("v1.18.0-rc.10", "1.18.0-rc.9"))
        assertFalse(repo.isNewer("v1.18.0-rc.4", "1.18.0-rc.5"))
        assertFalse(repo.isNewer("v1.18.0-rc.4", "1.18.0-rc.4"))
    }

    @Test
    fun `les numeros de preversion se comparent en nombres, pas en texte`() {
        // « rc.10 » vient après « rc.9 » : comparées comme du texte, « 1 » est
        // avant « 9 » et le testeur resterait sur la rc.9.
        assertTrue(repo.isNewer("v1.18.0-rc.10", "1.18.0-rc.2"))
        assertFalse(repo.isNewer("v1.18.0-rc.2", "1.18.0-rc.10"))
    }

    @Test
    fun `beta precede rc, dans l'ordre alphabetique de semver`() {
        assertTrue(repo.isNewer("v1.18.0-rc.1", "1.18.0-beta.9"))
        assertFalse(repo.isNewer("v1.18.0-beta.9", "1.18.0-rc.1"))
    }

    /** Une réponse illisible ne doit pas déclencher de mise à jour. */
    @Test
    fun `une version illisible ne propose rien`() {
        assertFalse(repo.isNewer("nightly", "1.14.0"))
        assertFalse(repo.isNewer("v1.15.0", ""))
    }
}
