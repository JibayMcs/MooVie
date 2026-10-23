package fr.moovie.tv.data.subtitles

import fr.moovie.tv.data.tmdb.TmdbApi
import fr.moovie.tv.data.tmdb.TmdbRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les réponses ci-dessous reprennent la forme de celles de l'addon public
 * `opensubtitles-v3.strem.io` (`/subtitles/movie/<imdb>.json` et
 * `/subtitles/series/<imdb>:<s>:<e>.json`) : un objet `subtitles` dont chaque
 * entrée porte `id`, `url`, `lang` (ISO-639-2) et `SubEncoding`. Elles sont
 * écrites à la main, faute d'accès réseau au moment de l'écriture.
 */
class StremioSubtitleCatalogTest {

    // TMDB injecté sur un client neutre : le client réel exige le contexte
    // Android, absent des tests unitaires (le catalogue ne l'appelle pas ici).
    private val tmdbNeutre = TmdbRepository(api = TmdbApi(HttpClient(OkHttp)))
    private val catalog = StremioSubtitleCatalog(tmdb = tmdbNeutre)

    private val movie = """
        {"subtitles":[
          {"id":"4573251","url":"https://subs5.strem.io/fre/sub1.srt","SubEncoding":"UTF-8","lang":"fre"},
          {"id":"4573252","url":"https://subs5.strem.io/eng/sub2.srt","SubEncoding":"UTF-8","lang":"eng"},
          {"id":"4573253","url":"https://subs5.strem.io/fre/sub3.srt","SubEncoding":"CP1252","lang":"fre"},
          {"id":"4573254","url":"https://subs5.strem.io/fre/sub1.srt","SubEncoding":"UTF-8","lang":"fre"}
        ]}
    """.trimIndent()

    private val episode = """
        {"subtitles":[
          {"id":"9100001","url":"https://subs5.strem.io/fre/s01e01.srt","SubEncoding":"UTF-8","lang":"fre"},
          {"id":"9100002","url":"https://subs5.strem.io/ger/s01e01.srt","SubEncoding":"UTF-8","lang":"ger"}
        ]}
    """.trimIndent()

    @Test
    fun decodesMovieResponseAndKeepsWantedLanguage() = runBlocking {
        val out = catalog.decode(movie, setOf("fr"))
        // Le doublon d'URL est écarté, l'anglais filtré.
        assertEquals(listOf("4573251", "4573253"), out.map { it.fileId })
        assertTrue(out.all { it.language == "fr" })
    }

    @Test
    fun decodesEpisodeResponse() = runBlocking {
        val out = catalog.decode(episode, setOf("fr", "de"))
        assertEquals(listOf("fr", "de"), out.map { it.language })
    }

    @Test
    fun garbageBodyGivesEmptyList() = runBlocking {
        assertTrue(catalog.decode("<html>", setOf("fr")).isEmpty())
    }

    @Test
    fun entryWithoutIdGetsDistinctPrefix() = runBlocking {
        val sansId = """{"subtitles":[{"url":"https://subs5.strem.io/fre/x.srt","lang":"fre"}]}"""
        val out = catalog.decode(sansId, setOf("fr"))
        assertEquals(1, out.size)
        assertTrue(out.single().fileId.startsWith("url-"))
    }

    @Test
    fun httpUrlIsNeverDownloaded() = runBlocking {
        // Refusé avant tout appel réseau : l'URL n'est pas en HTTPS.
        val enClair = """{"subtitles":[{"id":"77","url":"http://subs5.strem.io/fre/x.srt","lang":"fre"}]}"""
        val out = catalog.decode(enClair, setOf("fr"))
        assertNull(catalog.download(out.single()))
    }

    private val https = """{"subtitles":[{"id":"77","url":"https://subs5.strem.io/fre/x.srt","lang":"fre"}]}"""

    private suspend fun telecharge(statut: Int, corps: ByteArray): fr.moovie.tv.core.subtitles.model.DownloadedSubtitle? {
        val c = StremioSubtitleCatalog(tmdb = tmdbNeutre, lecteur = { statut to corps })
        val candidat = c.decode(https, setOf("fr")).single()
        return c.download(candidat)
    }

    @Test
    fun errorStatusWithHtmlBodyIsNotASubtitle() = runBlocking {
        val page = "<html><body>Not found</body></html>".encodeToByteArray()
        assertNull(telecharge(404, page))
        assertNull(telecharge(503, page))
    }

    @Test
    fun successfulDownloadIsDecoded() = runBlocking {
        val out = telecharge(200, "1\n00:00:01,000 --> 00:00:02,000\nSalut\n".encodeToByteArray())
        assertEquals("x.srt", out?.fileName)
    }

    @Test
    fun emptyOrOversizedBodyIsRejected() = runBlocking {
        assertNull(telecharge(200, ByteArray(0)))
        assertNull(telecharge(200, ByteArray((StremioSubtitleCatalog.TAILLE_MAX + 1).toInt())))
    }

    @Test
    fun throwingReaderGivesNull() = runBlocking {
        val c = StremioSubtitleCatalog(tmdb = tmdbNeutre, lecteur = { error("réseau") })
        assertNull(c.download(c.decode(https, setOf("fr")).single()))
    }

    /** cp1252 : œ (0x9C), ’ (0x92), € (0x80) et Ÿ (0x9F) se lisent correctement. */
    @Test
    fun cp1252DecodesTypographicPositions() {
        val octets = byteArrayOf(0x80.toByte(), 0x9C.toByte(), 0x92.toByte(), 0x9F.toByte())
        assertEquals("€œ’Ÿ", StremioSubtitleCatalog.cp1252(octets))
    }

    @Test
    fun cp1252TableHasEveryPosition() {
        assertEquals(32, StremioSubtitleCatalog.CP1252_HAUTS.length)
    }

    /** Un fichier qui n'est pas de l'UTF-8 tombe sur cp1252, sans exception. */
    @Test
    fun enUtf8FallsBackToCp1252() {
        // « Café œ » en cp1252 : é = 0xE9, œ = 0x9C. Invalide en UTF-8.
        val octets = byteArrayOf(0x43, 0x61, 0x66, 0xE9.toByte(), 0x20, 0x9C.toByte())
        assertEquals("Café œ", StremioSubtitleCatalog.enUtf8(octets))
    }

    /** Release et cadence : chemin Windows, nom de fichier seul, chemin POSIX, cadence absente ou nulle. */
    @Test
    fun releaseNameAndFpsComeFromTheEntry() = runBlocking {
        val avecRelease = """
            {"subtitles":[
              {"id":"1","url":"https://subs5.strem.io/fre/a.srt","lang":"fre",
               "movieReleaseName":"M:\\News\\Series\\Breaking.Bad.S01E02.DSR.XviD-0TV.avi","fpsMilli":23976},
              {"id":"2","url":"https://subs5.strem.io/fre/b.srt","lang":"fre",
               "subtitleFileName":"breaking.bad.s01e02.dvdrip.xvid-orpheus.FR.srt"},
              {"id":"3","url":"https://subs5.strem.io/fre/c.srt","lang":"fre",
               "movieReleaseName":"/mnt/series/Breaking.Bad.S01E02.720p.mkv","fpsMilli":25000},
              {"id":"4","url":"https://subs5.strem.io/fre/d.srt","lang":"fre","fpsMilli":0}
            ]}
        """.trimIndent()
        val out = catalog.decode(avecRelease, setOf("fr")).associateBy { it.fileId }

        assertEquals("Breaking.Bad.S01E02.DSR.XviD-0TV", out.getValue("1").release)
        assertEquals(23.976, out.getValue("1").fps)
        assertEquals("breaking.bad.s01e02.dvdrip.xvid-orpheus.FR", out.getValue("2").release)
        assertNull(out.getValue("2").fps)
        assertEquals("Breaking.Bad.S01E02.720p", out.getValue("3").release)
        assertEquals(25.0, out.getValue("3").fps)
        assertEquals("", out.getValue("4").release)
        assertNull(out.getValue("4").fps)
    }

    @Test
    fun shortLanguageCodes() {
        assertEquals("fr", StremioSubtitleCatalog.codeCourt("fre"))
        assertEquals("fr", StremioSubtitleCatalog.codeCourt("fra"))
        assertEquals("en", StremioSubtitleCatalog.codeCourt("eng"))
        assertEquals("fr", StremioSubtitleCatalog.codeCourt("fr"))
    }
}
