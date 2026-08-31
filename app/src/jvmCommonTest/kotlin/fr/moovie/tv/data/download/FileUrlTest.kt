package fr.moovie.tv.data.download

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verrouille la forme de l'URL d'un fichier téléchargé.
 *
 * Le défaut qu'il empêche ne se voyait que sur desktop : `File.toURI()` rend
 * `file:/home/…`, à une seule barre oblique. Android l'accepte, libVLC y voit un
 * chemin relatif et le résout contre le répertoire de travail — la lecture hors
 * ligne échouait donc sur un chemin absurde, avec le dossier du projet collé
 * devant celui du téléchargement.
 */
class FileUrlTest {

    @Test
    fun `rend trois barres obliques, pas une`() {
        val url = fileUrl("/tmp/moovie/tv_1_s1e1/stream.m3u8".toPath())
        assertTrue(url.startsWith("file:///"), url)
        assertTrue(url.endsWith("/tv_1_s1e1/stream.m3u8"), url)
    }

    /** Une URL avec autorité vide est absolue : rien ne peut la préfixer. */
    @Test
    fun `produit une URI absolue`() {
        val uri = java.net.URI(fileUrl("/tmp/a/b.m3u8".toPath()))
        assertTrue(uri.isAbsolute, uri.toString())
        assertTrue(uri.path == "/tmp/a/b.m3u8", uri.path)
    }

    /** Un espace dans le chemin ne doit pas casser l'URL. */
    @Test
    fun `echappe les espaces`() {
        val url = fileUrl("/tmp/mes videos/x.m3u8".toPath())
        assertTrue(" " !in url, url)
        assertTrue(java.net.URI(url).path == "/tmp/mes videos/x.m3u8")
    }
}
