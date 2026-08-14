package fr.moovie.tv.data.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le canal de mise à jour, sans réseau.
 *
 * ### Ce que ces tests documentent
 *
 * La règle attendue tient en une phrase — préversions activées, on reçoit les
 * rc **et** les versions finales ; désactivées, les finales seules — mais elle
 * se vérifie mal à l'œil : sur un appareil dont la version dépasse tout ce qui
 * est publié, un canal qui marche et un canal cassé répondent tous les deux
 * « à jour ». D'où ces cas, qui séparent les deux.
 *
 * La sonde voisine ([UpdateChannelProbeTest]) fait le trajet réel jusqu'à
 * GitHub ; celle-ci ne juge que la décision, et tourne partout.
 */
class UpdateChannelTest {

    private val repo = UpdateRepository()

    private fun release(tag: String, pre: Boolean = false, draft: Boolean = false) =
        GithubRelease(tagName = tag, prerelease = pre, draft = draft)

    // ── Éligibilité : ce que chaque canal accepte de regarder ────────────────

    @Test
    fun `le canal stable ignore les preversions`() {
        assertFalse(repo.isEligible(release("v1.21.0-rc.1", pre = true), prereleases = false))
        assertTrue(repo.isEligible(release("v1.20.0"), prereleases = false))
    }

    /** Le point de la demande : activer les rc n'exclut pas les finales. */
    @Test
    fun `le canal preversions accepte les deux`() {
        assertTrue(repo.isEligible(release("v1.21.0-rc.1", pre = true), prereleases = true))
        assertTrue(repo.isEligible(release("v1.20.0"), prereleases = true))
    }

    @Test
    fun `un brouillon n est publie pour personne`() {
        assertFalse(repo.isEligible(release("v9.9.9", draft = true), prereleases = true))
        assertFalse(repo.isEligible(release("v9.9.9", draft = true), prereleases = false))
    }

    // ── Comparaison : qui est plus récent que quoi ───────────────────────────

    @Test
    fun `une rc est proposee a qui vient d une version anterieure`() {
        assertTrue(repo.isNewer("v1.21.0-rc.1", "1.20.0"))
        assertTrue(repo.isNewer("v1.21.0-rc.1", "1.19.0"))
    }

    /**
     * Le cas qui a fait croire à une panne : un appareil portant `1.21.0` — un
     * build local jamais publié — voit `1.21.0-rc.1` comme **antérieure**, et
     * répond « à jour » à juste titre. La règle est voulue : sans elle, qui a
     * installé une rc resterait dessus pour toujours.
     */
    @Test
    fun `une rc n est pas proposee a qui a deja la version finale`() {
        assertFalse(repo.isNewer("v1.21.0-rc.1", "1.21.0"))
        assertFalse(repo.isNewer("v1.21.0-rc.1", "1.21.0-rc.1"))
    }

    @Test
    fun `la version finale est proposee a qui teste sa rc`() {
        assertTrue(repo.isNewer("v1.21.0", "1.21.0-rc.1"))
    }

    @Test
    fun `deux rc se departagent en nombres`() {
        assertTrue(repo.isNewer("v1.21.0-rc.2", "1.21.0-rc.1"))
        assertFalse(repo.isNewer("v1.21.0-rc.1", "1.21.0-rc.2"))
        // Comparées comme du texte, rc.10 passerait avant rc.2.
        assertTrue(repo.isNewer("v1.21.0-rc.10", "1.21.0-rc.2"))
    }

    @Test
    fun `une version plus ancienne n est jamais proposee`() {
        assertFalse(repo.isNewer("v1.19.0", "1.20.0"))
        assertFalse(repo.isNewer("v1.20.0", "1.20.0"))
    }
}
