package fr.moovie.tv.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La lecture d'un lien d'appairage, partagée par les deux façons de l'obtenir.
 *
 * Le téléphone scanne le QR et reçoit une URI analysée par Android ; un
 * ordinateur n'a pas de caméra et ne reçoit que ce que quelqu'un a collé. Une
 * seule analyse pour les deux, sans quoi elle divergerait au premier paramètre
 * ajouté — et le symptôme serait un appairage qui ne marche que d'un côté.
 */
class RemoteLinkTest {

    private val lien = "moovie://remote?h=192.168.1.53&p=8099&t=rqpwgwmr&n=Salon"

    @Test
    fun `un lien complet donne une cible`() {
        val cible = parseRemoteLink(lien)

        assertEquals("192.168.1.53", cible?.host)
        assertEquals(8099, cible?.port)
        assertEquals("rqpwgwmr", cible?.token)
        assertEquals("Salon", cible?.name)
    }

    /** L'adresse écrite sous le QR est recopiée à la main : elle arrive sale. */
    @Test
    fun `un lien colle avec des espaces autour passe`() {
        assertEquals("rqpwgwmr", parseRemoteLink("  $lien\n")?.token)
    }

    /** Quelqu'un qui recopie s'arrête souvent au premier caractère utile. */
    @Test
    fun `un lien sans son schema passe`() {
        assertEquals("rqpwgwmr", parseRemoteLink("remote?h=192.168.1.53&p=8099&t=rqpwgwmr")?.token)
    }

    /**
     * **Le test qui compte.** Un lien amputé de son jeton n'est pas rattrapable :
     * le laisser passer donnerait une cible enregistrée qui répond 404 à chaque
     * appel, sans que rien ne dise pourquoi. Mieux vaut refuser l'appairage.
     */
    @Test
    fun `un lien sans jeton est refuse`() {
        assertNull(parseRemoteLink("moovie://remote?h=192.168.1.53&p=8099"))
        assertNull(parseRemoteLink("moovie://remote?h=192.168.1.53&p=8099&t="))
    }

    @Test
    fun `un lien sans adresse ou sans port est refuse`() {
        assertNull(parseRemoteLink("moovie://remote?p=8099&t=abc"))
        assertNull(parseRemoteLink("moovie://remote?h=192.168.1.53&t=abc"))
    }

    /** Un port hors bornes vient d'une faute de frappe, pas d'un téléviseur. */
    @Test
    fun `un port aberrant est refuse`() {
        assertNull(parseRemoteLink("moovie://remote?h=10.0.0.1&p=0&t=abc"))
        assertNull(parseRemoteLink("moovie://remote?h=10.0.0.1&p=70000&t=abc"))
        assertNull(parseRemoteLink("moovie://remote?h=10.0.0.1&p=huit&t=abc"))
    }

    /**
     * Le nom n'est qu'un libellé : son absence ne doit pas faire échouer un
     * appairage par ailleurs complet. L'adresse fait un repli lisible.
     */
    @Test
    fun `sans nom l adresse sert de libelle`() {
        assertEquals("192.168.1.53", parseRemoteLink("moovie://remote?h=192.168.1.53&p=8099&t=abc")?.name)
    }

    @Test
    fun `un nom accentue ou espace se decode`() {
        val cible = parseRemoteLink("moovie://remote?h=10.0.0.1&p=8099&t=abc&n=T%C3%A9l%C3%A9+du+salon")
        assertEquals("Télé du salon", cible?.name)
    }

    @Test
    fun `ce qui n est pas un lien d appairage est refuse`() {
        assertNull(parseRemoteLink(null))
        assertNull(parseRemoteLink(""))
        assertNull(parseRemoteLink("   "))
        assertNull(parseRemoteLink("https://moovie.fr/remote?h=1&p=2&t=3"))
        assertNull(parseRemoteLink("moovie://update?h=1&p=2&t=3"))
        assertNull(parseRemoteLink("moovie://remote"))
    }

    /** La cible ainsi lue doit former une base d'URL exploitable. */
    @Test
    fun `la cible lue s adresse au bon endroit`() {
        assertEquals("http://192.168.1.53:8099/rqpwgwmr", parseRemoteLink(lien)?.base())
    }
}
