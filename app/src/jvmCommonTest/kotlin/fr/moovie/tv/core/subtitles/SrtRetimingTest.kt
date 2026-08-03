package fr.moovie.tv.core.subtitles

import fr.moovie.tv.core.subtitles.usecase.SubtitleTiming
import fr.moovie.tv.core.subtitles.usecase.retimeSrt
import fr.moovie.tv.core.subtitles.usecase.timingFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SrtRetimingTest {

    /** Extrait réel, tel que téléchargé par la sonde. */
    private val sample = """
        1
        00:00:33,636 --> 00:00:34,785
        Nous y sommes, Reet.

        2
        00:00:35,000 --> 00:00:37,500
        Deuxième réplique.
    """.trimIndent()

    @Test
    fun `un decalage constant deplace les deux bornes`() {
        val out = retimeSrt(sample, SubtitleTiming(offsetMs = 2_000))

        assertTrue(out.contains("00:00:35,636 --> 00:00:36,785"), out)
        assertTrue(out.contains("00:00:37,000 --> 00:00:39,500"), out)
    }

    @Test
    fun `la correction de cadence etire les horodatages`() {
        val out = retimeSrt(sample, timingFor(subtitleFps = 25.0, streamFps = 23.976))

        // 33 636 ms × 25 / 23,976 = 35 072,6 ms, arrondi à 35 073.
        assertTrue(out.contains("00:00:35,073"), out)
    }

    /** Le fichier n'est pas seulement des horodatages : le reste doit survivre. */
    @Test
    fun `le texte, la numerotation et la ponctuation sont intacts`() {
        val out = retimeSrt(sample, SubtitleTiming(offsetMs = 1_000))

        assertTrue(out.contains("Nous y sommes, Reet."), out)
        assertTrue(out.contains("Deuxième réplique."), out)
        assertTrue(out.trimStart().startsWith("1"), out)
    }

    /** Certains SRT portent des coordonnées de position après les bornes. */
    @Test
    fun `les coordonnees de position en fin de ligne sont conservees`() {
        val withCoords = "1\n00:00:10,000 --> 00:00:12,000 X1:40 X2:600 Y1:20 Y2:50\nTexte"

        val out = retimeSrt(withCoords, SubtitleTiming(offsetMs = 1_000))

        assertTrue(out.contains("00:00:11,000 --> 00:00:13,000 X1:40 X2:600 Y1:20 Y2:50"), out)
    }

    /** Les fichiers viennent souvent de Windows : ne pas leur retirer leurs \r. */
    @Test
    fun `les fins de ligne Windows survivent`() {
        val crlf = "1\r\n00:00:10,000 --> 00:00:12,000\r\nTexte\r\n"

        val out = retimeSrt(crlf, SubtitleTiming(offsetMs = 500))

        assertTrue(out.contains("00:00:10,500 --> 00:00:12,500\r\n"), out.replace("\r", "\\r"))
    }

    /** WebVTT sépare les millisecondes par un point : ne pas le convertir. */
    @Test
    fun `le separateur d origine est preserve`() {
        val vtt = "00:00:10.000 --> 00:00:12.000\nTexte"

        val out = retimeSrt(vtt, SubtitleTiming(offsetMs = 1_000))

        assertTrue(out.contains("00:00:11.000 --> 00:00:13.000"), out)
    }

    /**
     * Un décalage négatif peut rejeter une réplique avant le début du média.
     * Aucun lecteur ne lit un horodatage négatif.
     */
    @Test
    fun `un horodatage rejete avant zero est ramene a zero`() {
        val early = "1\n00:00:01,000 --> 00:00:03,000\nTexte"

        val out = retimeSrt(early, SubtitleTiming(offsetMs = -5_000))

        assertTrue(out.contains("00:00:00,000 --> 00:00:00,000"), out)
    }

    /** 46 Ko n'ont aucune raison d'être réécrits pour ne rien changer. */
    @Test
    fun `sans reglage le fichier n est pas touche`() {
        assertSame(sample, retimeSrt(sample, SubtitleTiming.None))
    }

    @Test
    fun `les heures et les minutes se propagent`() {
        val late = "1\n01:59:59,500 --> 02:00:00,500\nTexte"

        val out = retimeSrt(late, SubtitleTiming(offsetMs = 1_000))

        assertTrue(out.contains("02:00:00,500 --> 02:00:01,500"), out)
    }

    @Test
    fun `un fichier sans horodatage ressort tel quel`() {
        val junk = "ceci n'est pas un sous-titre"

        assertEquals(junk, retimeSrt(junk, SubtitleTiming(offsetMs = 1_000)))
    }
}
