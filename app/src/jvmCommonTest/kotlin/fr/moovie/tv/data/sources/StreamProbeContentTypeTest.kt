package fr.moovie.tv.data.sources

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Filtre de type de contenu de la sonde de jouabilité — fonction pure.
 *
 * Née d'un vrai faux positif : l'extracteur DoodStream produisait une URL que
 * l'hébergeur servait en `text/html`. La sonde la déclarait jouable (HTTP 200),
 * la cascade s'arrêtait dessus, et ExoPlayer terminait en
 * `UnrecognizedInputFormatException` — « lecture impossible » sur une source
 * annoncée bonne.
 */
class StreamProbeContentTypeTest {

    @Test
    fun `les types de flux réels sont acceptés`() {
        listOf(
            "application/vnd.apple.mpegurl", // HLS, ce que rendent LuLu et uqload
            "application/x-mpegURL",
            "video/mp4",
            "video/MP2T",
            "audio/mpeg",
            "application/octet-stream",
            "binary/octet-stream",
            "application/dash+xml",
        ).forEach { assertTrue(isPlayableContentType(it), "doit accepter « $it »") }
    }

    @Test
    fun `les pages d'erreur sont rejetées`() {
        listOf(
            "text/html",
            "text/html; charset=UTF-8",
            "text/plain",
            "application/json",
            "application/xhtml+xml",
        ).forEach { assertFalse(isPlayableContentType(it), "doit rejeter « $it »") }
    }

    @Test
    fun `l'absence d'en-tête ne fait pas rejeter`() {
        // Beaucoup de CDN n'envoient pas de Content-Type sur un HEAD. Rejeter par
        // défaut écarterait des sources parfaitement valides — on n'écarte que ce
        // qui est manifestement autre chose qu'un flux.
        assertTrue(isPlayableContentType(null))
        assertTrue(isPlayableContentType(""))
        assertTrue(isPlayableContentType("   "))
    }

    @Test
    fun `les paramètres et la casse ne changent rien`() {
        assertTrue(isPlayableContentType("VIDEO/MP4; charset=binary"))
        assertFalse(isPlayableContentType("TEXT/HTML; charset=utf-8"))
    }
}
