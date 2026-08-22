package fr.moovie.tv.ui.remote

import fr.moovie.tv.data.cast.CastDevice
import fr.moovie.tv.data.remote.RemoteTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ce qu'on propose comme destination, et surtout ce qu'on retire.
 *
 * Relevé sur le vrai banc : la Mi Box annonce `_moovie._tcp` sur
 * `192.168.1.53:8687` **et** répond au protocole Cast sur son port 8009. Elle
 * apparaissait donc deux fois, et le chemin Cast échouait — `connect ECHOUE`,
 * sans rien à l'écran pour l'expliquer.
 */
class CastTargetsTest {

    private val mibox = RemoteTarget(name = "Mi Box", host = "192.168.1.53", port = 8687, token = "t")
    private val salon = CastDevice(name = "Salon", host = "192.168.1.92")
    private val miboxEnCast = CastDevice(name = "Xiaomi MIBOX4", host = "192.168.1.53")

    /**
     * **Le test qui compte.** Le même appareil listé deux fois, dont une par un
     * chemin qui ne marche pas, c'est une chance sur deux de tomber sur le
     * mauvais — et rien pour s'en apercevoir avant l'échec.
     */
    @Test
    fun `un televiseur appaire n est pas propose aussi comme Chromecast`() {
        val cibles = castTargetsFor(paired = mibox, chromecasts = listOf(salon, miboxEnCast))

        assertEquals(2, cibles.size)
        assertTrue(cibles.any { it is CastTarget.Moovie })
        assertEquals(
            listOf("Salon"),
            cibles.filterIsInstance<CastTarget.Chromecast>().map { it.device.name },
        )
    }

    /**
     * Une box qui annonce Moo-vie sans être appairée est écartée du Cast : la
     * bonne réponse pour elle est l'appairage, pas un chemin dégradé qui
     * échouerait.
     */
    @Test
    fun `une box Moo-vie non appairee sort quand meme de la liste Cast`() {
        val cibles = castTargetsFor(
            paired = null,
            chromecasts = listOf(salon, miboxEnCast),
            moovieHosts = setOf("192.168.1.53"),
        )

        assertEquals(
            listOf("Salon"),
            cibles.filterIsInstance<CastTarget.Chromecast>().map { it.device.name },
        )
    }

    @Test
    fun `un vrai Chromecast reste propose`() {
        val cibles = castTargetsFor(paired = null, chromecasts = listOf(salon))

        assertEquals(1, cibles.size)
        assertTrue(cibles.single() is CastTarget.Chromecast)
    }

    /** Le téléviseur appairé passe en premier : c'est le meilleur chemin. */
    @Test
    fun `le televiseur Moo-vie est propose avant les Chromecast`() {
        val cibles = castTargetsFor(paired = mibox, chromecasts = listOf(salon))

        assertTrue(cibles.first() is CastTarget.Moovie)
    }

    @Test
    fun `sans rien sur le reseau il n y a rien a proposer`() {
        assertEquals(emptyList(), castTargetsFor(paired = null, chromecasts = emptyList()))
    }
}
