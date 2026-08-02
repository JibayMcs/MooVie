package fr.moovie.tv.data.backup

import fr.moovie.tv.data.watch.HistoryEntry
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.TitleMeta
import fr.moovie.tv.data.watch.WatchlistEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupJsonTest {

    private val full = MoovieBackup(
        exportedAt = 1_700_000_000_000,
        appVersion = "1.10.0",
        platform = "Android TV",
        resume = listOf(
            ResumeEntry(key = "tv:1396:s1e1", tmdbId = 1396, isTv = true, positionMs = 900_000),
        ),
        watchlist = listOf(WatchlistEntry(key = "movie:27205", tmdbId = 27205, isTv = false)),
        watched = listOf("movie:603", "tv:1396:s1e2"),
        history = listOf(HistoryEntry(key = "movie:603", tmdbId = 603, isTv = false, watchedAt = 42)),
        audioTracks = mapOf("tv:1396" to "French"),
        titles = mapOf("tv:1396" to TitleMeta(title = "Breaking Bad", genres = listOf("Drame"))),
        tmdbApiKey = "cle",
        settings = BackupSettings(streamLanguage = "VF", dohEnabled = true),
    )

    @Test
    fun `un aller-retour ne perd rien`() {
        assertEquals(full, BackupJson.decode(BackupJson.encode(full)))
    }

    /** Le fichier doit rester relisible à l'œil dans un éditeur de texte. */
    @Test
    fun `le fichier est indente et nomme ses champs`() {
        val raw = BackupJson.encode(full)

        assertTrue(raw.contains("\n    \"version\""), raw.take(80))
        assertTrue(raw.contains("\"watchlist\""))
    }

    /** Un import qui n'est pas une sauvegarde ne doit pas passer en silence. */
    @Test
    fun `un fichier etranger est refuse`() {
        assertNull(BackupJson.decode("ceci n'est pas du json"))
        assertNull(BackupJson.decode("""{"foo": 1}""".trimIndent().let { "[$it]" }))
    }

    /**
     * Une sauvegarde d'une version future peut avoir changé le sens d'un champ :
     * on refuse plutôt que d'en importer la moitié.
     */
    @Test
    fun `un format plus recent est refuse`() {
        val future = BackupJson.encode(full).replace("\"version\": 1", "\"version\": 99")

        assertNull(BackupJson.decode(future))
    }

    /** Un champ ajouté par une version ultérieure ne doit pas casser la lecture. */
    @Test
    fun `un champ inconnu est ignore`() {
        val extra = BackupJson.encode(full).replaceFirst("{", """{"futurChamp": "x",""")

        assertEquals("1.10.0", BackupJson.decode(extra)?.appVersion)
    }

    /** Sans clé TMDB, le fichier ne contient rien de secret. */
    @Test
    fun `une sauvegarde sans cle ne la mentionne pas`() {
        val raw = BackupJson.encode(full.copy(tmdbApiKey = null))

        assertTrue(!raw.contains("\"cle\""), raw)
        assertNull(BackupJson.decode(raw)?.tmdbApiKey)
    }

    @Test
    fun `une sauvegarde vide se relit`() {
        val backup = BackupJson.decode(BackupJson.encode(MoovieBackup()))

        assertEquals(MoovieBackup.FORMAT_VERSION, backup?.version)
        assertEquals(0, backup?.summary()?.watched)
    }
}
