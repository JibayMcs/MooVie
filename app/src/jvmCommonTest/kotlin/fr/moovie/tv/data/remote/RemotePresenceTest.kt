package fr.moovie.tv.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Le choix de la balise, c'est-à-dire « à quel téléviseur je réapprends à
 * parler ». Se tromper ici envoie les touches chez le voisin, sans rien dire.
 */
class RemotePresenceTest {

    private fun beacon(name: String, host: String) = RemoteBeacon(name, host, 8687)

    @Test
    fun `le nom exact l'emporte sur un autre televiseur`() {
        val found = listOf(beacon("Chambre", "192.168.1.20"), beacon("Salon", "192.168.1.10"))
        assertEquals("192.168.1.10", pickBeacon(found, "Salon")?.host)
    }

    @Test
    fun `un nom suffixe par Android reste le meme appareil`() {
        val found = listOf(beacon("Salon (2)", "192.168.1.11"))
        assertEquals("192.168.1.11", pickBeacon(found, "Salon")?.host)
    }

    @Test
    fun `le nom exact passe avant le nom suffixe`() {
        val found = listOf(beacon("Salon (2)", "192.168.1.11"), beacon("Salon", "192.168.1.10"))
        assertEquals("192.168.1.10", pickBeacon(found, "Salon")?.host)
    }

    @Test
    fun `un candidat unique rattrape un televiseur renomme`() {
        val found = listOf(beacon("Séjour", "192.168.1.10"))
        assertEquals("192.168.1.10", pickBeacon(found, "Salon")?.host)
    }

    /**
     * L'invariant qui compte. Deux inconnus, c'est une ambiguïté ; la trancher
     * au hasard reviendrait à piloter un appareil qu'on n'a jamais appairé.
     */
    @Test
    fun `deux inconnus ne designent personne`() {
        val found = listOf(beacon("Séjour", "192.168.1.10"), beacon("Cuisine", "192.168.1.20"))
        assertNull(pickBeacon(found, "Salon"))
    }

    @Test
    fun `rien trouve, rien choisi`() {
        assertNull(pickBeacon(emptyList(), "Salon"))
    }
}
