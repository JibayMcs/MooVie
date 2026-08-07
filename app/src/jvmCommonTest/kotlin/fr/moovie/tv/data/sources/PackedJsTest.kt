package fr.moovie.tv.data.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verrouille la dé-obfuscation fsvid / vidzy sur des pages réelles.
 *
 * Ces hébergeurs font tourner leur obfuscation : ce sont les seules sources dont
 * le format a déjà changé trois fois. Les échantillons ci-dessous sont capturés
 * tels quels sur les pages servies, sans réseau à l'exécution — un test qui
 * irait les chercher échouerait le jour où le lien expire, ce qui n'apprendrait
 * rien sur le décodeur.
 */
class PackedJsTest {

    /**
     * Charge utile relevée sur `https://vidzy.cc/embed-8xx0mtz9p5vy.html`
     * (Dune, VF).
     */
    private val payload =
        "shSgBDj1QiOHCC6aYc2QfM//Zte+De60G+wdOfZaIIlROIZvyZRxgLwAxeBBkulYsD5DoB1Q6jF/8xyc8gKj1zjR" +
            "yUOJxGWXPGukDGL/K33HCnSRc4Gje5+iVfH0SKAIQ6RfeeEceoYqONI7m608k6oW1LYe5UwM+0E3jRVh3HRh" +
            "yn6O5CGF5RTVuVr5Uh64Am20Dw=="

    private fun rollingPage(payload: String) = """
        <script>var _fsvHls="https://s1.fsvid.lol/troll/master.m3u8";
        player=videojs('vjsplayer',{sources:[{src:(function(s){
        var h=(location&&location.hostname)||"",H=0;for(var j=0;j<h.length;j++){H=(H+h.charCodeAt(j))&255}
        var b=atob(s),a=b.split("").reverse().join(""),r="";
        for(var i=0;i<a.length;i++){var kk=(0x3d+i*89+H)&255;r+=String.fromCharCode(a.charCodeAt(i)^kk)}
        return/^https?:/.test(r)?r:"https://s1.fsvid.lol/troll/master.m3u8"})("$payload"),
        type:"application/x-mpegURL"}]});</script>
    """.trimIndent()

    /** Le cas nominal : clé glissante, octets inversés, graine dépendant du nom d'hôte. */
    @Test
    fun `decode la cle glissante de vidzy`() {
        val url = PackedJs.findM3u8(rollingPage(payload), "https://vidzy.cc/embed-8xx0mtz9p5vy.html")
        assertEquals(
            "https://u14.vidzy.cc/hls2/01/00014/8xx0mtz9p5vy_n/master.m3u8" +
                "?t=6tSuvOBtclJFDIAWtY8zLWOTcDBuRVEsgcGbsmOwo-A&s=1786132470&e=172800&f=74075&i=0.0&sp=0",
            url,
        )
    }

    /**
     * Le nom d'hôte sert de graine côté site, mais nous ne le lisons pas : la clé
     * est retrouvée depuis le clair connu. Décoder la même page servie sous un
     * autre alias doit donner le même résultat — c'est tout l'intérêt.
     */
    @Test
    fun `ne depend pas du nom d hote de la page`() {
        val url = PackedJs.findM3u8(rollingPage(payload), "https://vidzy.org/embed-8xx0mtz9p5vy.html")
        assertTrue(url!!.startsWith("https://u14.vidzy.cc/hls2/"), url)
    }

    /**
     * Le piège qui a coûté le plus cher : sans ce rejet, l'échec du décodage ne
     * rend pas « pas de source » mais l'URL leurre de la page, qui répond 200 et
     * lit vingt secondes de vidéo à la place du film.
     */
    @Test
    fun `ne renvoie jamais l URL leurre`() {
        val html = """<script>var _fsvHls="https://s1.fsvid.lol/troll/master.m3u8";</script>"""
        assertNull(PackedJs.findM3u8(html, "https://vidzy.cc/embed-x.html"))
    }

    /** Une charge utile tronquée ne doit rien produire plutôt que du bruit. */
    @Test
    fun `rejette une charge utile illisible`() {
        assertNull(PackedJs.findM3u8(rollingPage(payload.take(40)), "https://vidzy.cc/embed-x.html"))
    }

    /** Le format hérité (URL en clair dans le script) reste pris en charge. */
    @Test
    fun `decode encore une URL en clair`() {
        val html = """<script>jwplayer("v").setup({sources:[{file:"https://cdn.example.com/a/master.m3u8"}]});</script>"""
        assertEquals(
            "https://cdn.example.com/a/master.m3u8",
            PackedJs.findM3u8(html, "https://vidzy.cc/embed-x.html"),
        )
    }

    /** Le XOR à clé fixe (format 07/2026) ne doit pas régresser. */
    @Test
    fun `decode encore le XOR a cle fixe`() {
        val target = "https://cdn.example.com/hls/master.m3u8"
        val key = listOf(37, 91, 12, 200)
        val cipher = target.encodeToByteArray()
            .mapIndexed { i, b -> (b.toInt() xor key[i % key.size]).toByte() }
            .toByteArray()
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val b64 = kotlin.io.encoding.Base64.Default.encode(cipher)
        val html = """
            <script>(function(s){var k=[${key.joinToString(",")}],b=atob(s),r="";
            for(var i=0;i<b.length;i++){r+=String.fromCharCode(b.charCodeAt(i)^k[i%k.length])}
            return r})("$b64")</script>
        """.trimIndent()
        assertEquals(target, PackedJs.findM3u8(html, "https://vidzy.cc/embed-x.html"))
    }
}
