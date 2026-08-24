package fr.moovie.tv.core.sources.usecase

import fr.moovie.tv.core.sources.model.EmbedLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le filtre des hébergeurs illisibles.
 *
 * Il décide de ce que l'utilisateur voit dans le panneau des sources : trop
 * large, il masque des sources qui marchent ; trop étroit, il laisse revenir les
 * dix-neuf lignes « Ne répond pas » sur vingt qui ont motivé son écriture.
 */
class HosterSupportTest {

    @Test
    fun `un hebergeur jamais lisible est ecarte`() {
        assertFalse(isHosterSupported("netu"))
        assertFalse(isHosterSupported("vidara"))
        assertFalse(isHosterSupported("flemmix"))
    }

    /**
     * **Le test qui compte.** Les catalogues n'ont aucune convention de casse et
     * le même hébergeur s'y écrit de trois façons. Une comparaison sensible à la
     * casse laisserait passer `Netu` en écartant `netu` — donc un filtre qui
     * marche sur un catalogue et pas sur le voisin, ce qui est pire que pas de
     * filtre du tout : l'incohérence ne se diagnostique pas à l'écran.
     */
    @Test
    fun `la casse et les espaces ne font pas passer au travers`() {
        assertFalse(isHosterSupported("Netu"))
        assertFalse(isHosterSupported("NETU"))
        assertFalse(isHosterSupported("  netu  "))
    }

    @Test
    fun `les hebergeurs vivants restent proposes`() {
        listOf("swiftflow", "uqload", "playmogo", "voe", "dood", "vidapi", "premium")
            .forEach { assertTrue(isHosterSupported(it), "$it a été écarté à tort") }
    }

    /**
     * Un lien sans hébergeur nommé n'affirme rien. Le reniflage par structure de
     * page reste sa chance — et c'est par là que passent des hôtes vivants qui
     * n'ont pourtant aucun extracteur dédié (`hanerix`, `minochinos`, mesurés
     * jouables). L'écarter reviendrait à couper cette voie.
     */
    @Test
    fun `un lien sans hebergeur nomme n est pas ecarte`() {
        val liens = listOf(EmbedLink(url = "https://x.test/e/abc", hoster = ""))

        assertEquals(1, keepSupportedHosters(liens).size)
    }

    @Test
    fun `le filtre garde l ordre et ne retire que ce qu il doit`() {
        val liens = listOf(
            EmbedLink(url = "https://a.test/1", hoster = "swiftflow"),
            EmbedLink(url = "https://b.test/2", hoster = "netu"),
            EmbedLink(url = "https://c.test/3", hoster = "uqload"),
            EmbedLink(url = "https://d.test/4", hoster = "vidzy"),
        )

        val gardes = keepSupportedHosters(liens)

        assertEquals(listOf("swiftflow", "uqload"), gardes.map { it.hoster })
    }

    /**
     * Vidzy résout correctement — son URL signée est reconstruite, leurre
     * écarté — et le CDN répond 403 à tout. Il est donc dans la liste pour une
     * raison différente des autres, et c'est celle qui a le plus de chances de
     * changer : si l'hôte cesse de filtrer, il faudra l'en retirer.
     */
    @Test
    fun `un hote qui resout mais refuse est ecarte comme les autres`() {
        assertFalse(isHosterSupported("vidzy"))
        assertFalse(isHosterSupported("waaw"))
    }
}
