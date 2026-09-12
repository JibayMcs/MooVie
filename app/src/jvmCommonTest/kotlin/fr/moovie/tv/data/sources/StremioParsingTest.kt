package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.port.HttpGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lecture d'une réponse d'addon Stremio — fonction pure, aucun réseau.
 *
 * Le corps ci-dessous reprend la forme réellement servie par Torrentio, telle
 * que `stremio-mpv` l'a mesurée : le poids et la source vivent dans le **texte**
 * de `title`, jamais dans un champ, et `name` porte le marqueur de mise en cache
 * sur sa première ligne, la définition sur la seconde.
 */
class StremioParsingTest {

    private val jamais = HttpGateway { null }

    /** Cinq flux : deux prêts, un magnet pur, un non mis en cache, un doublon. */
    private val reponse = """
        {"streams":[
          {"name":"[RD+] Torrentio\n1080p",
           "title":"Dune.2021.MULTi.1080p.BluRay.x264-Grp.mkv\n👤 432 💾 3.41 GB ⚙️ ThePirateBay",
           "url":"https://torrentio.strem.fun/realdebrid/CLE/aaa/0/Dune.mkv",
           "behaviorHints":{"filename":"Dune.2021.MULTi.1080p.BluRay.x264-Grp.mkv"}},
          {"name":"[RD+] Torrentio\n4k",
           "title":"Dune.2021.VOSTFR.2160p.REMUX.HEVC.mkv\n👤 12 💾 54.2 GB ⚙️ YTS",
           "url":"https://torrentio.strem.fun/realdebrid/CLE/bbb/0/Dune4k.mkv"},
          {"name":"Torrentio\n720p",
           "title":"Dune.2021.720p.WEBRip.mkv\n👤 3 💾 900 MB ⚙️ Rarbg",
           "infoHash":"ccc"},
          {"name":"Torrentio\n1080p",
           "title":"Dune.2021.1080p.WEB.mkv\n👤 88 💾 2.10 GB ⚙️ Rarbg",
           "url":"https://torrentio.strem.fun/realdebrid/CLE/ddd/0/DuneWeb.mkv"},
          {"name":"[RD+] Torrentio\n1080p",
           "title":"Dune.2021.MULTi.1080p.BluRay.x264-Grp.mkv\n👤 432 💾 3.41 GB ⚙️ ThePirateBay",
           "url":"https://torrentio.strem.fun/realdebrid/CLE/aaa/0/Dune.mkv"}
        ]}
    """.trimIndent()

    private fun liens(cachedOnly: Boolean = true) =
        StremioProvider(jamais, cachedOnly = cachedOnly).parse(reponse)

    @Test
    fun `un flux sans url est ecarte, car il n'est qu'un magnet`() {
        // Le lire supposerait un moteur torrent que l'application n'a pas : le
        // laisser passer produirait un bouton Lire qui échoue à l'ouverture.
        val urls = StremioProvider(jamais, cachedOnly = false).parse(reponse).map { it.url }
        assertTrue(urls.none { "ccc" in it }, "le magnet pur ne doit pas remonter")
    }

    @Test
    fun `sans marqueur de cache le flux est ecarte par defaut`() {
        // « DuneWeb » a une url mais pas de [RD+] : le debrid devrait d'abord
        // aller chercher le torrent, ce qui prend des minutes.
        assertTrue(liens().none { "DuneWeb" in it.url })
        assertTrue(liens(cachedOnly = false).any { "DuneWeb" in it.url })
    }

    @Test
    fun `le doublon d'url exacte ne compte qu'une fois`() {
        assertEquals(2, liens().size)
    }

    @Test
    fun `MULTi vaut VF, VOSTFR est lu tel quel`() {
        val parLangue = liens().associate { it.url.substringAfterLast('/') to it.language }
        assertEquals("VF", parLangue["Dune.mkv"])
        assertEquals("VOSTFR", parLangue["Dune4k.mkv"])
    }

    @Test
    fun `faute de marqueur de langue on conclut VO et pas VF`() {
        // Se tromper vers la VO fait manquer un doublage ; se tromper vers la VF
        // met un film anglais dans la section française, ce qui se voit à la
        // lecture. On préfère la première erreur.
        assertEquals("VO", StremioProvider.languageOf("Dune.2021.1080p.BluRay.x264.mkv"))
        assertEquals("VF", StremioProvider.languageOf("Dune.2021.TRUEFRENCH.1080p.mkv"))
    }

    @Test
    fun `la variante porte la definition et le poids, seuls criteres de choix`() {
        val variantes = liens().map { it.variant }
        assertEquals(listOf("1080p · 3.41 GB", "4k · 54.2 GB"), variantes)
    }

    @Test
    fun `le hoster est la jointure avec l'extracteur, pas le nom du debrid`() {
        // C'est `StreamResolution` règle 1 qui route sur ce nom : s'il dérive,
        // les liens retombent sur DirectStreamExtractor et repartent avec un
        // Referer qui n'a pas lieu d'être.
        assertTrue(liens().all { it.hoster == StremioExtractor.HOSTER })
    }

    @Test
    fun `un corps illisible rend une liste vide plutot que de lever`() {
        // Contrat du port : un catalogue en panne est un cas normal.
        assertEquals(emptyList(), StremioProvider(jamais).parse("pas du json"))
        assertEquals(emptyList(), StremioProvider(jamais).parse("""{"err":"nope"}"""))
    }

    /**
     * Forme réellement servie pour *Dune* : le premier flux en cache que l'addon
     * propose est un REMUX de 39,9 Go, et il arrive avant un web-dl de 2 Go.
     */
    private val commeDune = """
        {"streams":[
          {"name":"[RD+] Torrentio\n1080p",
           "title":"Dune.2021.1080p.BluRay.REMUX.AVC.DTS-HD.MA.TrueHD.7.1.Atmos-FGT.mkv\n👤 64 💾 39.96 GB ⚙️ TPB",
           "url":"https://torrentio.strem.fun/realdebrid/K/remux/0/a.mkv"},
          {"name":"[RD+] Torrentio\n2160p",
           "title":"Dune.2021.2160p.UHD.BluRay.x265.mkv\n👤 20 💾 22.1 GB ⚙️ TPB",
           "url":"https://torrentio.strem.fun/realdebrid/K/uhd/0/b.mkv"},
          {"name":"[RD+] Torrentio\n1080p",
           "title":"Dune.2021.1080p.WEB-DL.x264.mkv\n👤 300 💾 2.40 GB ⚙️ Rarbg",
           "url":"https://torrentio.strem.fun/realdebrid/K/web/0/c.mkv"},
          {"name":"[RD+] Torrentio\n720p",
           "title":"Dune.2021.720p.WEBRip.mkv\n👤 90 💾 1.10 GB ⚙️ Rarbg",
           "url":"https://torrentio.strem.fun/realdebrid/K/hd/0/d.mkv"}
        ]}
    """.trimIndent()

    @Test
    fun `le premier lien est celui que la tele sait jouer, pas le plus lourd`() {
        // Le bouton Lire joue le premier de la langue voulue. Sur un TCL sous
        // Android 9 en ARM 32 bits, un remux de 40 Go en TrueHD ne se décode
        // pas et ne se télécharge pas assez vite : le laisser en tête ferait
        // passer le catalogue pour cassé alors qu'il a rendu quatre flux.
        val ordre = StremioProvider(jamais).parse(commeDune).map { it.url.substringAfterLast('/') }
        assertEquals(listOf("c.mkv", "a.mkv", "d.mkv", "b.mkv"), ordre)
    }

    @Test
    fun `la 4K passe derriere la 720p, ce qui n'est pas une inversion fortuite`() {
        assertTrue(StremioProvider.qualityRank("1080p") < StremioProvider.qualityRank("720p"))
        assertTrue(StremioProvider.qualityRank("720p") < StremioProvider.qualityRank("2160p"))
        assertTrue(StremioProvider.qualityRank("480p") < StremioProvider.qualityRank("4k"))
    }

    @Test
    fun `ni le plus leger ni le plus lourd, mais le plus proche du poids vise`() {
        // Les deux extrêmes sont mauvais : 500 Mo pour un film de 2 h 35 se voit
        // à l'écran, 40 Go ne se décode pas sur la télé. Mesuré sur Dune, un tri
        // par poids croissant mettait le premier en tête.
        val corps = """
            {"streams":[
              {"name":"[RD+] T\n1080p","title":"a.mkv\n💾 500 MB","url":"https://x/leger.mkv"},
              {"name":"[RD+] T\n1080p","title":"b.mkv\n💾 39.96 GB","url":"https://x/remux.mkv"},
              {"name":"[RD+] T\n1080p","title":"c.mkv\n💾 4.20 GB","url":"https://x/juste.mkv"}
            ]}
        """.trimIndent()
        val premier = StremioProvider(jamais).parse(corps, StremioProvider.CIBLE_FILM).first()
        assertEquals("juste.mkv", premier.url.substringAfterLast('/'))
    }

    @Test
    fun `un flux au poids inconnu passe derriere ceux qu'on a pu mesurer`() {
        val corps = """
            {"streams":[
              {"name":"[RD+] T\n1080p","title":"sans poids","url":"https://x/inconnu.mkv"},
              {"name":"[RD+] T\n1080p","title":"b.mkv\n💾 12 GB","url":"https://x/gros.mkv"}
            ]}
        """.trimIndent()
        val ordre = StremioProvider(jamais).parse(corps).map { it.url.substringAfterLast('/') }
        assertEquals(listOf("gros.mkv", "inconnu.mkv"), ordre)
    }

    @Test
    fun `le poids se lit en octets pour pouvoir se comparer`() {
        assertEquals(3_410_000_000L, StremioProvider.sizeBytes("x\n👤 4 💾 3.41 GB ⚙️ y"))
        assertEquals(900_000_000L, StremioProvider.sizeBytes("x\n👤 4 💾 900 MB ⚙️ y"))
        assertEquals(null, StremioProvider.sizeBytes("aucun poids ici"))
    }
}
