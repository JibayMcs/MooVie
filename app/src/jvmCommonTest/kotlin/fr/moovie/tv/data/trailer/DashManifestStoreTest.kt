package fr.moovie.tv.data.trailer

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DashManifestStoreTest {

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "moovie-test-${javaClass.simpleName}")
            .apply { deleteRecursively(); mkdirs() }

    @Test
    fun `l'URI rendue porte trois barres obliques`() {
        val uri = DashManifestStore(tempDir()).write("<MPD/>")
        assertNotNull(uri)
        // `File.toURI()` rend `file:/home/…`, que libVLC prend pour un chemin
        // relatif : il la colle derrière le répertoire courant et n'ouvre rien.
        // Constaté sur le desktop, corrigé ici — ne pas revenir à toURI().
        assertTrue(uri.startsWith("file:///"), "URI inattendue : $uri")
    }

    @Test
    fun `l'URI designe un fichier qui existe et contient le manifeste`() {
        val uri = DashManifestStore(tempDir()).write("<MPD>bonjour</MPD>")
        assertNotNull(uri)
        val file = File(uri.removePrefix("file://"))
        assertTrue(file.isFile, "fichier absent : $file")
        assertEquals("<MPD>bonjour</MPD>", file.readText())
        assertTrue(file.name.endsWith(".mpd"), "extension manquante : ${file.name}")
    }

    @Test
    fun `le meme manifeste ne seme pas un fichier par ouverture`() {
        val dir = tempDir()
        val store = DashManifestStore(dir)
        repeat(3) { store.write("<MPD/>") }
        assertEquals(1, dir.listFiles()?.size)
    }

    @Test
    fun `les manifestes perimes sont effaces`() {
        val dir = tempDir()
        val vieux = File(dir, "trailer-vieux.mpd").apply {
            writeText("<MPD/>")
            // Sept heures : au-delà des six heures d'expiration annoncées par
            // YouTube, donc plus qu'un fichier d'URLs mortes.
            setLastModified(System.currentTimeMillis() - 7L * 60 * 60 * 1000)
        }
        DashManifestStore(dir).write("<MPD>neuf</MPD>")
        assertTrue(!vieux.exists(), "le manifeste périmé aurait dû être effacé")
    }
}
