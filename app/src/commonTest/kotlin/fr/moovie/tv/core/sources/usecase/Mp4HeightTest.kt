package fr.moovie.tv.core.sources.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lecture de la définition dans l'en-tête d'un MP4.
 *
 * Les cas sont fabriqués octet par octet plutôt que capturés : un vrai fichier
 * pèse trop pour vivre dans le dépôt, et surtout il ne contiendrait qu'**un**
 * des cas à couvrir. Ce qui casse ici n'est pas la lecture d'un fichier sain,
 * c'est tout le reste — une piste audio à zéro, une taille de boîte aberrante,
 * les quatre lettres « tkhd » tombées au milieu de données binaires.
 */
class Mp4HeightTest {

    /** `[taille][type][charge]`, la seule forme qu'ait une boîte MP4. */
    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        return byteArrayOf(
            (size ushr 24).toByte(), (size ushr 16).toByte(),
            (size ushr 8).toByte(), size.toByte(),
        ) + type.encodeToByteArray() + payload
    }

    /**
     * Charge d'un `tkhd` version 0 : 76 octets de champs, puis largeur et
     * hauteur en 16.16.
     */
    private fun tkhd(width: Int, height: Int): ByteArray =
        ByteArray(76) + fixed1616(width) + fixed1616(height)

    private fun fixed1616(value: Int) = byteArrayOf(
        (value ushr 8).toByte(), value.toByte(), 0, 0,
    )

    private fun moovWith(vararg tracks: ByteArray): ByteArray =
        box("moov", tracks.fold(ByteArray(0)) { acc, t -> acc + box("trak", box("tkhd", t)) })

    @Test
    fun `une piste video rend sa hauteur`() {
        val header = box("ftyp", ByteArray(16)) + moovWith(tkhd(1920, 1080))

        assertEquals(1080, mp4Height(header))
    }

    /** Une piste audio porte un tkhd à zéro : il ne doit pas gagner. */
    @Test
    fun `une piste audio a zero est ignoree`() {
        val header = moovWith(tkhd(0, 0), tkhd(1280, 720))

        assertEquals(720, mp4Height(header))
    }

    @Test
    fun `la plus grande piste l emporte`() {
        val header = moovWith(tkhd(640, 360), tkhd(1920, 1080), tkhd(854, 480))

        assertEquals(1080, mp4Height(header))
    }

    /**
     * Le cas qui rendait la lecture par chaîne dangereuse : une hauteur dont les
     * octets sortent de l'ASCII. 2160 = `0x0870`, et la largeur 3840 = `0x0F00`
     * porte un octet nul au milieu.
     */
    @Test
    fun `une definition aux octets non ASCII se lit correctement`() {
        val header = moovWith(tkhd(3840, 2160))

        assertEquals(2160, mp4Height(header))
    }

    /** Les quatre lettres peuvent tomber dans des données : la taille arbitre. */
    @Test
    fun `un tkhd fortuit dans des donnees ne trompe pas`() {
        val leurre = byteArrayOf(0x00, 0x00, 0x00, 0x02) + "tkhd".encodeToByteArray() + ByteArray(40)

        assertNull(mp4Height(leurre))
    }

    @Test
    fun `un en tete tronque ne rend rien plutot qu un chiffre faux`() {
        val complet = moovWith(tkhd(1920, 1080))

        assertNull(mp4Height(complet.copyOfRange(0, 20)))
    }

    @Test
    fun `un fragment vide ou minuscule ne fait pas tomber la lecture`() {
        assertNull(mp4Height(ByteArray(0)))
        assertNull(mp4Height(ByteArray(3)))
        assertNull(mp4Height("pas du tout un mp4".encodeToByteArray()))
    }

    /**
     * La nuance qui décide d'une seconde requête : « pas de hauteur » et « pas
     * encore lu le moov » sont deux réponses différentes.
     */
    @Test
    fun `la presence du moov se distingue de son absence`() {
        assertTrue(mp4HeaderComplete(moovWith(tkhd(1280, 720))))
        assertFalse(mp4HeaderComplete(box("ftyp", ByteArray(32))))
        assertFalse(mp4HeaderComplete(ByteArray(0)))
    }

    /** Une hauteur aberrante est écartée : mieux vaut rien qu'un libellé faux. */
    @Test
    fun `une hauteur hors bornes est refusee`() {
        assertNull(mp4Height(moovWith(tkhd(99999, 99999))))
    }
}
