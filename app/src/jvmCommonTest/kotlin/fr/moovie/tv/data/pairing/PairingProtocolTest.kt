package fr.moovie.tv.data.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le protocole de la page d'appairage, sans ouvrir de socket.
 *
 * Ce qui casse ici ne se voit pas à l'écran : un corps mal découpé donne une clé
 * silencieusement tronquée, et l'utilisateur cherchera le défaut du côté de
 * TMDB.
 */
class PairingProtocolTest {

    // --- Analyse de la requête ------------------------------------------------

    private fun stream(text: String) = text.toByteArray(Charsets.ISO_8859_1).inputStream()

    @Test
    fun `lit la methode et le chemin`() {
        val line = readRequestLine(stream("POST /ab3xk9zq HTTP/1.1\r\n"))
        assertEquals("POST", line?.method)
        assertEquals("/ab3xk9zq", line?.path)
    }

    /** La chaîne de requête ne fait pas partie du chemin comparé au jeton. */
    @Test
    fun `retire la chaine de requete du chemin`() {
        assertEquals("/ab3xk9zq", readRequestLine(stream("GET /ab3xk9zq?x=1 HTTP/1.1\r\n"))?.path)
    }

    @Test
    fun `rend null sur une requete tronquee`() {
        assertNull(readRequestLine(stream("")))
        assertNull(readRequestLine(stream("BLARG\r\n")))
    }

    /**
     * Le point qui justifie de lire octet par octet : après les en-têtes, le
     * corps doit être **intact** dans le flux. Un lecteur tamponné en aurait
     * avalé une partie, et la dernière valeur du formulaire serait rognée.
     */
    @Test
    fun `laisse le corps intact apres les en-tetes`() {
        val input = stream("POST /t HTTP/1.1\r\nHost: x\r\nContent-Length: 9\r\n\r\ntmdb=abcd")
        readRequestLine(input)
        val headers = readHeaders(input)
        assertEquals("9", headers["content-length"])
        assertEquals("tmdb=abcd", String(readExactly(input, 9), Charsets.UTF_8))
    }

    /**
     * Le corps arrive rarement d'un bloc. Un flux qui rend un octet à la fois
     * reproduit ce que fait le réseau, et ce qu'une lecture unique raterait :
     * la clé se retrouverait tronquée sans que rien ne le signale.
     */
    @Test
    fun `rassemble un corps livre en morceaux`() {
        val goutteAGoutte = object : java.io.InputStream() {
            private val data = "introdb=abcdef".toByteArray()
            private var at = 0
            override fun read(): Int = if (at < data.size) data[at++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (at >= data.size) return -1
                b[off] = data[at++]
                return 1 // toujours un seul octet, quoi qu'on demande
            }
        }
        assertEquals("introdb=abcdef", String(readExactly(goutteAGoutte, 14), Charsets.UTF_8))
    }

    /** Flux plus court qu'annoncé : on rend ce qu'on a, sans octets nuls en trop. */
    @Test
    fun `tolere un corps plus court qu annonce`() {
        assertEquals("abc", String(readExactly(stream("abc"), 10), Charsets.UTF_8))
    }

    @Test
    fun `normalise les noms d en-tete en minuscules`() {
        val input = stream("GET /t HTTP/1.1\r\nContent-Length: 4\r\n\r\n")
        readRequestLine(input)
        assertEquals("4", readHeaders(input)["content-length"])
    }

    // --- Décodage du formulaire ----------------------------------------------

    @Test
    fun `decode un formulaire percent-encode`() {
        val values = decodeForm("tmdb=abc123&sync.b2_app_key=K005%2Fx%2By")
        assertEquals("abc123", values["tmdb"])
        // `%2F` et `%2B` sont fréquents dans une clé B2 : la laisser passer
        // encodée produirait une clé refusée par le service.
        assertEquals("K005/x+y", values["sync.b2_app_key"])
    }

    /** Dans un formulaire, `+` est une espace — et pas dans une URL. */
    @Test
    fun `traite le plus comme une espace`() {
        assertEquals("mot de passe", decodeForm("os_pass=mot+de+passe")["os_pass"])
    }

    @Test
    fun `garde une valeur vide et ignore le bruit`() {
        val values = decodeForm("tmdb=&introdb=x&&=orphelin")
        assertEquals("", values["tmdb"])
        assertEquals("x", values["introdb"])
        assertEquals(2, values.size)
    }

    /** Une phrase secrète a le droit de contenir un `=`. */
    @Test
    fun `ne coupe pas sur un egal interne`() {
        assertEquals("a=b=c", decodeForm("passphrase=a%3Db%3Dc")["passphrase"])
    }

    // --- Rendu de la page ----------------------------------------------------

    private val texts = PairingTexts(
        title = "Saisie",
        intro = "Laisser vide pour ne pas modifier.",
        submit = "Enregistrer",
        done = "Enregistré",
        doneDetail = "Terminé.",
    )

    @Test
    fun `rend un champ par reglage, poste sur le jeton`() {
        val html = pairingPage(
            listOf(
                PairingField("tmdb", "Clé API TMDB", "API & Clés", "22f163f9cd"),
                PairingField("passphrase", "Phrase de passe", "Synchro", ""),
            ),
            texts,
            "/ab3xk9zq",
        )
        assertTrue("""name="tmdb"""" in html, html)
        assertTrue("""name="passphrase"""" in html)
        assertTrue("""action="/ab3xk9zq"""" in html)
        assertTrue("""value="22f163f9cd"""" in html)
    }

    /**
     * Le cœur du sujet : la correction automatique du téléphone transformerait
     * une clé hexadécimale en mot capitalisé. Sans ces attributs, la
     * fonctionnalité produit des clés fausses sans prévenir.
     */
    @Test
    fun `desarme le clavier du telephone sur chaque champ`() {
        val html = pairingPage(listOf(PairingField("tmdb", "Clé", "API & Clés", "")), texts, "/t")
        assertTrue("""autocapitalize="off"""" in html)
        assertTrue("""autocorrect="off"""" in html)
        assertTrue("""spellcheck="false"""" in html)
    }

    /**
     * Le formulaire est pré-rempli avec ce que porte le téléviseur.
     *
     * C'est l'inverse du premier jet, qui n'annonçait qu'un « déjà renseigné » :
     * l'écran de réglages affiche déjà ces valeurs en clair sur la TV, les
     * masquer ici ne protégeait rien et interdisait de relire ou de corriger une
     * clé sans la retaper entièrement.
     */
    @Test
    fun `pre-remplit avec la valeur du televiseur`() {
        val html = pairingPage(listOf(PairingField("tmdb", "Clé", "API & Clés", "secret")), texts, "/t")
        assertTrue("""value="secret"""" in html, html)
    }

    /**
     * Un guillemet dans une phrase de passe fermerait l'attribut : la valeur
     * serait amputée à l'affichage, puis renvoyée tronquée à l'envoi — une
     * corruption silencieuse du secret même qui doit être identique partout.
     */
    @Test
    fun `echappe un guillemet dans une valeur`() {
        val html = pairingPage(
            listOf(PairingField("passphrase", "Phrase", "Synchro", """mon "vrai" secret""")),
            texts,
            "/t",
        )
        assertTrue("""value="mon &quot;vrai&quot; secret"""" in html, html)
    }

    /**
     * Les champs sont regroupés sous le service qui les réclame.
     *
     * « Identifiant » et « Mot de passe » ne veulent rien dire seuls au milieu
     * d'un formulaire : c'est le titre de section qui dit de quel compte il
     * s'agit. Une section par service, dans l'ordre de saisie.
     */
    @Test
    fun `groupe les champs par service, sans dupliquer un titre`() {
        val html = pairingPage(
            listOf(
                PairingField("os_user", "Identifiant", "Sous-titres · OpenSubtitles", ""),
                PairingField("os_pass", "Mot de passe", "Sous-titres · OpenSubtitles", ""),
                PairingField("sync.b2_key_id", "Identifiant de clé", "Synchro · Backblaze B2", ""),
            ),
            texts,
            "/t",
        )
        assertEquals(2, Regex("<section>").findAll(html).count(), html)
        assertEquals(1, Regex("OpenSubtitles").findAll(html).count())
        assertTrue("<h2>Synchro · Backblaze B2</h2>" in html)
    }

    /** Un libellé traduit reste du texte, jamais du balisage. */
    @Test
    fun `echappe les libelles`() {
        val html = pairingPage(listOf(PairingField("x", "<script>a&b", "S", "")), texts, "/t")
        assertTrue("&lt;script&gt;a&amp;b" in html)
        assertTrue("<script>a&b" !in html)
    }

    @Test
    fun `la page est autonome`() {
        val html = pairingPage(listOf(PairingField("tmdb", "Clé", "API & Clés", "")), texts, "/t")
        // Rien à charger ailleurs : le téléviseur sert la page, il ne renvoie
        // pas le téléphone sur un CDN pour afficher six champs.
        assertTrue("http://" !in html && "https://" !in html, html)
        assertTrue("<meta name=\"viewport\"" in html)
    }
}
