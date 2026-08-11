package fr.moovie.tv.data.sources

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Le lien de fiche, dans les résultats de recherche d'anime-sama.
 *
 * Ce détail a coûté **tout le catalogue animé** : le site est passé des liens
 * relatifs aux liens absolus, la lecture n'a plus rien trouvé, et le provider
 * s'est mis à rendre une liste vide — indistinguable de « ce titre n'y est
 * pas ». Rien n'a échoué, rien n'a été journalisé ; le seul catalogue spécialisé
 * de l'application était muet depuis on ne sait quand.
 *
 * Le test tient sur les deux formes à la fois. Accepter la nouvelle en perdant
 * l'ancienne referait la même panne au prochain revirement du site, et rien ne
 * dit qu'il ne reviendra pas en arrière.
 */
class AnimeSamaSearchTest {

    /** Réponse réelle de `fetch.php`, abrégée à ce que la lecture regarde. */
    private fun result(href: String) =
        """<a href="$href" class="asn-search-result"><img src="x.webp" /><h3>Demon Slayer</h3></a>"""

    @Test
    fun `un lien absolu donne le chemin de la fiche`() {
        assertEquals(
            "/catalogue/demon-slayer/",
            AnimeSamaProvider.cataloguePath(result("https://anime-sama.to/catalogue/demon-slayer/")),
        )
    }

    /** La forme d'avant, que le site peut resservir sans prévenir. */
    @Test
    fun `un lien relatif marche toujours`() {
        assertEquals(
            "/catalogue/demon-slayer/",
            AnimeSamaProvider.cataloguePath(result("/catalogue/demon-slayer/")),
        )
    }

    /** Un autre domaine du jour ne doit rien changer : seul le chemin compte. */
    @Test
    fun `le domaine du jour est ignore`() {
        assertEquals(
            "/catalogue/one-piece/",
            AnimeSamaProvider.cataloguePath(result("https://anime-sama.org/catalogue/one-piece/")),
        )
    }

    @Test
    fun `une page sans resultat ne rend rien`() {
        assertEquals(null, AnimeSamaProvider.cataloguePath("<p>Aucun résultat</p>"))
    }
}
