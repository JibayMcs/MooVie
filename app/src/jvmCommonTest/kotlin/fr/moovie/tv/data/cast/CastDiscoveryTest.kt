package fr.moovie.tv.data.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le nom sous lequel un récepteur apparaît dans la liste.
 *
 * Relevés sur un vrai appareil le 21/08/2026 : le service s'annonce
 * `Chromecast-a5b1f58f89bccb0138437cb1de4c1f71`, et ses TXT portent
 * `fn=Salon`, `md=Chromecast`.
 */
class CastDiscoveryTest {

    private val serviceReel = "Chromecast-a5b1f58f89bccb0138437cb1de4c1f71"

    /**
     * **Le test qui compte.** Afficher le nom du service donnerait une liste de
     * chaînes hexadécimales où personne ne reconnaîtrait son salon.
     */
    @Test
    fun `le nom affiche est celui que l utilisateur a donne`() {
        val txt = mapOf("fn" to "Salon", "md" to "Chromecast", "rs" to "Google")

        assertEquals("Salon", castFriendlyName(txt, repli = serviceReel))
    }

    /** Sans `fn`, le modèle reste plus lisible qu'un identifiant. */
    @Test
    fun `sans nom choisi on retombe sur le modele`() {
        assertEquals("Chromecast", castFriendlyName(mapOf("md" to "Chromecast"), repli = serviceReel))
    }

    /**
     * Un appareil sans TXT exploitable reste **joignable** : l'écarter de la
     * liste serait pire qu'un nom laid.
     */
    @Test
    fun `un appareil muet reste dans la liste`() {
        assertEquals(serviceReel, castFriendlyName(emptyMap(), repli = serviceReel))
        assertEquals(serviceReel, castFriendlyName(mapOf("fn" to "", "md" to null), repli = serviceReel))
    }

    @Test
    fun `le type de service porte son point final`() {
        // Sans lui, NsdManager refuse la découverte sans expliquer pourquoi.
        assertTrue(CAST_SERVICE_TYPE.endsWith("."), "type mDNS incomplet : $CAST_SERVICE_TYPE")
        assertEquals("_googlecast._tcp.", CAST_SERVICE_TYPE)
    }

    @Test
    fun `un recepteur porte le port du protocole par defaut`() {
        assertEquals(8009, CastDevice(name = "Salon", host = "192.168.1.92").port)
    }
}
