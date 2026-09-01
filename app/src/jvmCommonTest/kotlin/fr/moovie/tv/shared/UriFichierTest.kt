package fr.moovie.tv.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `DashManifestStore` construisait son URI avec `File.toURI().rawPath` ; il
 * utilise désormais `enCheminUri`, `java.net.URI` n'existant pas en
 * Kotlin/Native.
 *
 * Le commentaire d'origine dit pourquoi c'est délicat : `rawPath` était choisi
 * parce qu'il **garde l'encodage**, seul à survivre à un nom d'utilisateur
 * accentué. Un remplacement qui n'encoderait pas produirait une URI que libVLC
 * refuse — et seulement chez les gens dont le chemin porte un accent ou une
 * espace, c'est-à-dire pas sur la machine de développement. Ce test compare
 * donc les deux encodages sur les chemins qui posent problème.
 */
class UriFichierTest {

    private val chemins = listOf(
        "/home/hugo/.cache/moovie/trailers/trailer-1a2b3c.mpd",
        // Le cas nommé dans le commentaire d'origine.
        "/home/renée/.cache/moovie/trailers/trailer-1a2b3c.mpd",
        "/home/josé maría/.cache/moovie/trailers/trailer-ff.mpd",
        // Windows : jpackage installe sous « Program Files », avec une espace.
        "/C:/Program Files/Moo-vie/cache/trailer-0.mpd",
        "/Users/Ana Lúcia/Library/Caches/moovie/trailers/trailer-9.mpd",
    )

    @Test
    fun `enCheminUri rend le meme chemin que URI rawPath`() {
        for (chemin in chemins) {
            assertEquals(
                File(chemin).toURI().rawPath,
                enCheminUri(chemin),
                "chemin=$chemin",
            )
        }
    }
}
