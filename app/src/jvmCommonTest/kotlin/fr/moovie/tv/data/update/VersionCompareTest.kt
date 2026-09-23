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

    /**
     * L'annuaire des domaines est publié comme une release — et ne doit jamais
     * être pris pour une version.
     *
     * Le dépôt porte un tag `v0.0.0-annuaire`, qui n'est pas une version de
     * l'application mais le support d'un fichier de configuration (voir
     * `DomainBook`). Deux protections se cumulent : la release est une
     * préversion, donc `releases/latest` l'ignore, et son numéro est nul, donc
     * le **canal de test** ne peut pas davantage la proposer.
     *
     * C'est la seconde que ce test verrouille, parce qu'elle tient au seul choix
     * du tag. Le renommer en quelque chose de non numérique — `annuaire` tout
     * court — rendrait `isNewer` faux dans les deux sens, et le comparateur de
     * `latestIncludingPrereleases` n'est pas transitif dans ce cas : la release
     * retenue dépendrait alors de sa position dans la liste. Une panne
     * silencieuse, et seulement pour ceux qui ont activé les préversions.
     */
    @Test
    fun `l'annuaire des domaines n'est jamais proposé comme mise à jour`() {
        assertFalse(repo.isNewer("v0.0.0-annuaire", "1.24.3"))
        // …et il ne doit pas non plus écraser une vraie version dans l'autre
        // sens : une vraie version lui est bien postérieure.
        assertTrue(repo.isNewer("v1.24.3", "0.0.0-annuaire"))
    }
}
