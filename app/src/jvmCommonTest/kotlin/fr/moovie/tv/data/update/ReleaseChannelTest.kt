package fr.moovie.tv.data.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le canal de mise à jour : qui reçoit quoi.
 *
 * Le réglage « recevoir les préversions » est resté **sans aucun effet** de sa
 * livraison en 1.18.0 jusqu'ici. Les deux ViewModels demandaient bien la liste
 * complète à GitHub, puis rejetaient toute release marquée préversion — la
 * règle était écrite en double, et la fonctionnalité n'en a corrigé aucune des
 * deux copies. Rien ne le signalait : l'app répondait « à jour », ce qui est la
 * réponse la plus crédible qui soit.
 *
 * D'où cette règle, unique et partagée, et ce test qui couvre les quatre cas.
 */
class ReleaseChannelTest {

    private val repo = UpdateRepository()

    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false) =
        GithubRelease(tagName = tag, prerelease = prerelease, draft = draft)

    @Test
    fun `le canal stable ne recoit que les versions finales`() {
        assertTrue(repo.isEligible(release("v1.19.0"), prereleases = false))
        assertFalse(
            repo.isEligible(release("v1.19.0-rc.3", prerelease = true), prereleases = false),
            "une préversion ne doit jamais atteindre le canal stable",
        )
    }

    @Test
    fun `le canal des preversions recoit les deux`() {
        assertTrue(
            repo.isEligible(release("v1.19.0-rc.3", prerelease = true), prereleases = true),
            "c'est exactement ce que le réglage promet, et ce qu'il ne faisait pas",
        )
        // Une version finale reste proposée : le testeur qui a installé une rc
        // doit recevoir la stable quand elle sort, sans changer de canal.
        assertTrue(repo.isEligible(release("v1.19.0"), prereleases = true))
    }

    @Test
    fun `un brouillon n'est publie pour personne`() {
        assertFalse(repo.isEligible(release("v1.20.0", draft = true), prereleases = false))
        assertFalse(repo.isEligible(release("v1.20.0", draft = true), prereleases = true))
    }

    /**
     * Le cas de l'utilisateur : 1.18.0 installée, canal préversions coché, une
     * rc de la 1.19 publiée. Éligibilité **et** comparaison de version doivent
     * conclure ensemble — l'une sans l'autre ne propose rien.
     */
    @Test
    fun `une rc plus recente est proposee a qui a coche le canal`() {
        val rc = release("v1.19.0-rc.3", prerelease = true)
        assertTrue(repo.isEligible(rc, prereleases = true))
        assertTrue(repo.isNewer(rc.tagName, "1.18.0"))
    }
}
