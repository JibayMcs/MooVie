package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le nom d'hébergeur tel que l'utilisateur le lit.
 *
 * Ce qui compte n'est pas la table — elle sera toujours en retard — mais le
 * **repli** : un hébergeur inconnu doit rester lisible sans qu'on ait touché au
 * code. C'est la propriété qui évite que ce travail devienne une liste à tenir
 * à jour, comme celles qui pourrissent chez les autres.
 */
class HosterLabelTest {

    @Test
    fun `un hebergeur connu garde sa casse d origine`() {
        assertEquals("DoodStream", hosterLabel("doodstream"))
        assertEquals("DoodStream", hosterLabel("dood"))
        assertEquals("SeekStreaming", hosterLabel("seekstreaming"))
        assertEquals("Uqload", hosterLabel("uqload"))
    }

    /** Le cœur du dispositif : rien à mettre à jour pour un nouvel hôte. */
    @Test
    fun `un hebergeur inconnu reste lisible`() {
        assertEquals("Bysebuho", hosterLabel("bysebuho"))
        assertEquals("Morencius", hosterLabel("morencius"))
    }

    @Test
    fun `un identifiant vide ne fait pas tomber l affichage`() {
        assertEquals("", hosterLabel(""))
    }

    /**
     * L'apport réel : les alias jetables de VOE deviennent « Voe ». La liste
     * vit dans `VoeExtractor`, où la *lecture* en dépend — on la consulte au
     * lieu d'en tenir une seconde.
     */
    @Test
    fun `un alias tournant est ramene a sa famille`() {
        val alias = EmbedLink(
            url = "https://jefferycontrolmodel.com/e/mu9osbhlx3zz",
            hoster = "jefferycontrolmodel",
        )

        assertEquals("Voe", hosterLabel(alias))
    }

    /**
     * Le garde-fou : `DirectStreamExtractor` revendique tout ce qui finit en
     * `.mp4`. Sans l'exclusion des extracteurs qui lisent une *forme*, la moitié
     * du panneau s'appellerait « Direct ».
     */
    @Test
    fun `un mp4 garde le nom de son hebergeur, pas celui du mecanisme`() {
        val mp4 = EmbedLink(url = "https://cdn.exemple.test/film.mp4", hoster = "swiftflow")

        val label = hosterLabel(mp4)

        assertEquals("SwiftFlow", label)
        assertTrue("Direct" !in label, "le mécanisme d'extraction a été pris pour un hébergeur")
    }
}
