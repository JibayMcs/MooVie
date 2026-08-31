package fr.moovie.tv.data.sources

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `resoudreRelatif` remplace `java.net.URI.resolve`, absent de Kotlin/Native.
 *
 * Un remplacement ne vaut que s'il rend la même chose que ce qu'il remplace :
 * ce test ne compare donc pas à des chaînes écrites à la main, qui ne
 * prouveraient que mon interprétation, mais à `URI` lui-même. Il vit dans
 * `jvmCommonTest` pour cette raison — c'est le seul source set où les deux
 * implémentations coexistent.
 */
class PortabiliteUrlTest {

    private val cas = listOf(
        // Les formes que `PackedJs.normalize` rencontre réellement.
        "https://hoster.example/embed/abc123" to "/stream/master.m3u8",
        "https://hoster.example/embed/abc123" to "./master.m3u8",
        "https://hoster.example/embed/abc123" to "../hls/master.m3u8",
        "https://hoster.example/embed/abc123" to "../../hls/master.m3u8",
        // Base avec barre finale : le répertoire courant n'est pas le même.
        "https://hoster.example/embed/" to "./master.m3u8",
        // Requête et fragment doivent traverser intacts.
        "https://hoster.example/e/xyz" to "/s/master.m3u8?token=42",
        "https://hoster.example/e/xyz" to "../s/master.m3u8?a=1&b=2",
        // Remontées au-delà de la racine : `URI` les absorbe, nous aussi.
        "https://hoster.example/a/b" to "../../../c.m3u8",
        // Base sans chemin du tout.
        "https://hoster.example" to "/master.m3u8",
        // Chemin profond, cas le plus courant chez les CDN à alias.
        "https://cdn.example/v/2024/05/file/index.m3u8" to "seg-1.ts",
    )

    @Test
    fun `resout comme java net URI`() {
        for ((base, reference) in cas) {
            val attendu = URI(base).resolve(reference).toString()
            val obtenu = resoudreRelatif(base, reference)
            assertEquals(attendu, obtenu, "base=$base reference=$reference")
        }
    }

    @Test
    fun `rend la reference telle quelle si elle est deja absolue`() {
        val absolue = "https://autre.example/master.m3u8"
        assertEquals(absolue, resoudreRelatif("https://hoster.example/e/x", absolue))
    }

    @Test
    fun `rend null sur une base sans schema`() {
        assertEquals(null, resoudreRelatif("pas-une-url", "/master.m3u8"))
    }
}
