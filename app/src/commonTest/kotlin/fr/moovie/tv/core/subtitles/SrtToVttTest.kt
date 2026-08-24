package fr.moovie.tv.core.subtitles

import fr.moovie.tv.core.subtitles.usecase.srtToVtt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La conversion SRT → WebVTT, exigée par le récepteur Cast.
 *
 * Un SRT servi tel quel est **accepté puis ignoré** par le récepteur de
 * Google : pas d'erreur, pas de piste, rien à l'écran. Ces tests protègent donc
 * une fonction dont l'échec est silencieux par nature.
 */
class SrtToVttTest {

    private val srt = """
        1
        00:00:01,000 --> 00:00:04,500
        Bonjour, ça va ?

        2
        00:01:12,250 --> 00:01:14,000
        Oui, merci.
    """.trimIndent()

    @Test
    fun `l en-tete WEBVTT est ajoute`() {
        assertTrue(srtToVtt(srt).startsWith("WEBVTT\n\n"))
    }

    @Test
    fun `les millisecondes passent de la virgule au point`() {
        val vtt = srtToVtt(srt)

        assertTrue("00:00:01.000 --> 00:00:04.500" in vtt)
        assertTrue("00:01:12.250 --> 00:01:14.000" in vtt)
        assertFalse("," in vtt.lines().first { "-->" in it })
    }

    /**
     * **Le test qui compte.** Remplacer toutes les virgules du fichier abîmerait
     * les répliques : une phrase française en contient à chaque ligne. Le
     * symptôme serait des sous-titres subtilement faux, ce qui est pire qu'une
     * absence — on ne le remarque pas tout de suite.
     */
    @Test
    fun `les virgules du texte ne sont pas touchees`() {
        assertTrue("Bonjour, ça va ?" in srtToVtt(srt))
    }

    /** Ré-encoder un WebVTT empilerait un second en-tête, ce qui l'invalide. */
    @Test
    fun `un fichier deja en WebVTT n est pas reconverti`() {
        val vtt = "WEBVTT\n\n1\n00:00:01.000 --> 00:00:02.000\nDéjà bon.\n"

        val rendu = srtToVtt(vtt)

        assertEquals(1, Regex("WEBVTT").findAll(rendu).count())
        assertEquals(vtt, rendu)
    }

    /**
     * La marque d'ordre d'octets est prise pour du texte avant l'en-tête, et le
     * récepteur refuse alors le fichier entier — sans rien dire.
     */
    @Test
    fun `la marque d ordre d octets est retiree`() {
        val avecBom = "﻿" + srt

        assertTrue(srtToVtt(avecBom).startsWith("WEBVTT"))
    }

    @Test
    fun `les fins de ligne Windows sont normalisees`() {
        val crlf = srt.replace("\n", "\r\n")

        assertFalse("\r" in srtToVtt(crlf))
    }

    /** Les balises de style sont communes aux deux formats : on n'y touche pas. */
    @Test
    fun `l italique survit`() {
        val avecBalise = "1\n00:00:01,000 --> 00:00:02,000\n<i>Murmure.</i>\n"

        assertTrue("<i>Murmure.</i>" in srtToVtt(avecBalise))
    }

    /** Un long métrage dépasse rarement 99 h, mais le motif ne doit pas caler. */
    @Test
    fun `les horodatages a trois chiffres d heures sont convertis`() {
        val long = "1\n100:00:01,000 --> 100:00:02,000\nTrès long.\n"

        assertTrue("100:00:01.000 --> 100:00:02.000" in srtToVtt(long))
    }
}
