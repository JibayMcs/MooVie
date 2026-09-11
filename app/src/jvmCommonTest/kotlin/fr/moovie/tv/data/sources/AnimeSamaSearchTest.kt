package fr.moovie.tv.data.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Le choix de la fiche, dans les résultats de recherche d'anime-sama.
 *
 * Deux pannes se sont succédé sur cette poignée de lignes, et elles tirent en
 * sens opposé — d'où les deux familles de cas ci-dessous.
 *
 * 1. **Le catalogue s'est tu.** Le site est passé des liens relatifs aux liens
 *    absolus, la lecture n'a plus rien trouvé, et le provider s'est mis à rendre
 *    une liste vide — indistinguable de « ce titre n'y est pas ». Rien n'a
 *    échoué, rien n'a été journalisé.
 *
 * 2. **Le catalogue a servi n'importe quoi.** `fetch.php` est une recherche
 *    floue : elle répond toujours quelque chose. Prendre son premier résultat
 *    faisait lancer l'animé `medalist` à qui demandait un épisode de
 *    *Mentalist*. Une liste vide se voit ; un mauvais épisode se regarde.
 *
 * Corriger l'une sans garder l'autre sous test, c'est refaire la première panne
 * en réparant la seconde. Les deux familles restent donc ici ensemble.
 */
class AnimeSamaSearchTest {

    /** Réponse réelle de `fetch.php`, abrégée à ce que la lecture regarde. */
    private fun resultat(href: String, titre: String, alternatives: String? = null) = buildString {
        append("""<a href="$href" class="asn-search-result">""")
        append("""<img class="asn-search-result-img" src="x.webp" />""")
        append("""<div class="asn-search-result-content">""")
        append("""<h3 class="asn-search-result-title">$titre</h3>""")
        if (alternatives != null) {
            append("""<p class="asn-search-result-subtitle">$alternatives</p>""")
        }
        append("</div></a>")
    }

    // ────────── 1. la fiche se trouve encore, quelle que soit la forme du lien

    @Test
    fun `un lien absolu donne le chemin de la fiche`() {
        assertEquals(
            "/catalogue/demon-slayer/",
            AnimeSamaProvider.cataloguePath(
                resultat("https://anime-sama.to/catalogue/demon-slayer/", "Demon Slayer"),
                "Demon Slayer",
            ),
        )
    }

    /** La forme d'avant, que le site peut resservir sans prévenir. */
    @Test
    fun `un lien relatif marche toujours`() {
        assertEquals(
            "/catalogue/demon-slayer/",
            AnimeSamaProvider.cataloguePath(
                resultat("/catalogue/demon-slayer/", "Demon Slayer"),
                "Demon Slayer",
            ),
        )
    }

    /** Un autre domaine du jour ne doit rien changer : seul le chemin compte. */
    @Test
    fun `le domaine du jour est ignore`() {
        assertEquals(
            "/catalogue/one-piece/",
            AnimeSamaProvider.cataloguePath(
                resultat("https://anime-sama.org/catalogue/one-piece/", "One Piece"),
                "One Piece",
            ),
        )
    }

    @Test
    fun `une page sans resultat ne rend rien`() {
        assertNull(AnimeSamaProvider.cataloguePath("<p>Aucun résultat</p>", "One Piece"))
    }

    // ────────── 2. la fiche servie est bien celle qu'on a demandée

    /**
     * Le cas rapporté : « Mentalist » et « Medalist » ne diffèrent que d'une
     * lettre, et c'est pourtant un animé de patinage qui se lançait.
     */
    @Test
    fun `un titre voisin mais different est rejete`() {
        val html = resultat("/catalogue/medalist", "Medalist", "Medarisuto") +
            resultat("/catalogue/noucome", "NouCome", "Ore no Nounai Sentakushi ga")

        assertNull(AnimeSamaProvider.cataloguePath(html, "Mentalist"))
    }

    /** Les autres intrus relevés le même jour, sur les titres de la sonde. */
    @Test
    fun `les series non animees ne ramenent aucune fiche`() {
        val cas = listOf(
            "Fight Club" to resultat("/catalogue/rymans-club", "Ryman's Club", "Salaryman's Club"),
            "Grey's Anatomy" to resultat("/catalogue/sk8rs", "SK8R'S", "Skaters"),
            "Intouchables" to resultat("/catalogue/anne-shirley", "Anne Shirley", "Akage no Anne"),
            "New York Unité Spéciale" to resultat("/catalogue/new-game", "New Game!", "Nyu Gemu"),
            "Breaking Bad" to resultat("/catalogue/bad-girl", "Bad Girl", "BADGIRL"),
        )
        for ((titre, html) in cas) {
            assertNull(AnimeSamaProvider.cataloguePath(html, titre), "« $titre » a ramené une fiche")
        }
    }

    /**
     * Le vrai animé doit survivre au resserrage — sinon on a troqué une panne
     * contre une autre.
     */
    @Test
    fun `le bon anime reste trouve`() {
        val html = resultat("/catalogue/one-piece/", "One Piece", "Wan Pisu, One Piece: Pirate King") +
            resultat("/catalogue/one-outs", "One Outs", "ONE OUTS Nobody wins, but I!")

        assertEquals("/catalogue/one-piece/", AnimeSamaProvider.cataloguePath(html, "One Piece"))
    }

    /**
     * La fiche retenue n'est pas forcément la première : pour « L'Attaque des
     * Titans », anime-sama place le spin-off « Junior High School » devant la
     * série, et seule la seconde porte ce titre exact dans ses alternatives.
     */
    @Test
    fun `on balaie tous les resultats et pas seulement le premier`() {
        val html = resultat(
            "/catalogue/shingeki-kyojin-chuugakkou/",
            "Attaque des Titans Junior High-school",
            "L&#039;Attaque des Titans - Junior High School, Attack on Titan Junior High",
        ) + resultat(
            "/catalogue/shingeki-no-kyojin/",
            "Shingeki no Kyojin",
            "L&#039;Attaque des Titans, SNK, Shingeki no Kyojin The Final Season",
        )

        assertEquals(
            "/catalogue/shingeki-no-kyojin/",
            AnimeSamaProvider.cataloguePath(html, "L'Attaque des Titans"),
        )
    }

    /**
     * L'apostrophe de TMDB arrive en entité HTML côté anime-sama. Retirer la
     * ponctuation avant l'entité laisserait `l039attaque` et raterait la fiche.
     */
    @Test
    fun `les entites HTML et les accents ne cassent pas la comparaison`() {
        assertEquals("lattaquedestitans", AnimeSamaProvider.normalise("L&#039;Attaque des Titans"))
        assertEquals("lattaquedestitans", AnimeSamaProvider.normalise("L'Attaque des Titans"))
        assertEquals("narutoshippuden", AnimeSamaProvider.normalise("Naruto Shippûden"))
        assertEquals(
            "demonslayerkimetsunoyaiba",
            AnimeSamaProvider.normalise("Demon Slayer : Kimetsu no Yaiba"),
        )
    }
}
