package fr.moovie.tv.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Quand la sonde de diffusion doit se retirer, et à quel rythme elle relève.
 *
 * Une notification en avant-plan qui survit à ce qu'elle pilote est le défaut le
 * plus pénible de cette fonctionnalité : `ongoing`, elle ne se balaie pas, ses
 * boutons ne commandent plus rien, et il faut tuer l'application pour s'en
 * défaire. Personne n'ira le signaler comme un bug — on ferme l'app et on
 * passe à autre chose.
 *
 * Ces cas se joueraient autrement sur deux vrais appareils, une box à
 * débrancher, et une minute d'attente par scénario.
 */
class CastVigilTest {

    /** Déroule une suite de relevés, et rend le dernier verdict. */
    private fun run(vararg pulses: CastPulse): Pair<CastVigil, CastVerdict> {
        var vigil = CastVigil()
        var verdict: CastVerdict = CastVerdict.Watch(0)
        pulses.forEach { pulse ->
            val (next, out) = vigil.observe(pulse)
            vigil = next
            verdict = out
        }
        return vigil to verdict
    }

    private fun delayOf(verdict: CastVerdict): Long =
        assertIs<CastVerdict.Watch>(verdict).delayMs

    // ── Lecture de l'état ────────────────────────────────────────────────────

    @Test
    fun `un releve se lit en quatre cas distincts`() {
        val playing = NowPlaying(mediaKey = "movie:550", playing = true)
        val paused = NowPlaying(mediaKey = "movie:550", playing = false)

        assertEquals(CastPulse.SILENT, CastPulse.of(RemoteStatus.Unreachable))
        assertEquals(CastPulse.IDLE, CastPulse.of(RemoteStatus.Known(RemoteState())))
        assertEquals(
            CastPulse.PLAYING,
            CastPulse.of(RemoteStatus.Known(RemoteState(now = playing))),
        )
        assertEquals(
            CastPulse.PAUSED,
            CastPulse.of(RemoteStatus.Known(RemoteState(now = paused))),
        )
    }

    /**
     * **Le piège que le type existe pour éviter.** Une box muette et une box qui
     * ne joue rien ne se traitent pas pareil : la première peut être débranchée,
     * la seconde vient peut-être de finir un épisode. Les confondre ferait
     * retirer la session sur une coupure Wi-Fi passagère.
     */
    @Test
    fun `un silence n est pas une box qui ne joue rien`() {
        val (apresSilence, _) = CastVigil().observe(CastPulse.SILENT)
        val (apresVide, _) = CastVigil().observe(CastPulse.IDLE)

        assertEquals(1, apresSilence.silences)
        assertEquals(0, apresSilence.idles)
        assertEquals(0, apresVide.silences)
        assertEquals(1, apresVide.idles)
    }

    // ── Quand s'arrêter ──────────────────────────────────────────────────────

    /**
     * **Le test qui compte.** La box débranchée est le seul cas que rien d'autre
     * ne rattrape : personne n'ira fermer la notification depuis un téléviseur
     * éteint.
     */
    @Test
    fun `une box debranchee finit par liberer la notification`() {
        val silences = Array(CastVigil.SILENCES_BEFORE_RETIRE) { CastPulse.SILENT }
        val (_, verdict) = run(CastPulse.PLAYING, *silences)

        assertEquals(CastVerdict.Retire, verdict)
    }

    @Test
    fun `un silence isole ne retire rien`() {
        val (_, verdict) = run(CastPulse.PLAYING, CastPulse.SILENT)
        assertIs<CastVerdict.Watch>(verdict)
    }

    /** Une réponse efface l'ardoise : trois silences puis un relevé, et on repart. */
    @Test
    fun `une reponse remet les silences a zero`() {
        val presqueMort = Array(CastVigil.SILENCES_BEFORE_RETIRE - 1) { CastPulse.SILENT }
        val (vigil, verdict) = run(CastPulse.PLAYING, *presqueMort, CastPulse.PLAYING)

        assertEquals(0, vigil.silences)
        assertIs<CastVerdict.Watch>(verdict)

        // Et le budget est bien reparti de zéro, pas d'un cran avant la fin.
        val (_, suite) = run(
            CastPulse.PLAYING, *presqueMort, CastPulse.PLAYING, *presqueMort,
        )
        assertIs<CastVerdict.Watch>(suite)
    }

    @Test
    fun `une lecture terminee libere la notification`() {
        val fin = Array(CastVigil.IDLES_BEFORE_RETIRE) { CastPulse.IDLE }
        val (_, verdict) = run(CastPulse.PLAYING, *fin)

        assertEquals(CastVerdict.Retire, verdict)
    }

    /**
     * L'enchaînement automatique vers l'épisode suivant passe par un court
     * moment où plus rien ne joue. Le lâcher là ferait disparaître la
     * notification entre deux épisodes d'une même soirée.
     */
    @Test
    fun `un enchainement d episode ne retire pas la session`() {
        val creux = Array(CastVigil.IDLES_BEFORE_RETIRE - 1) { CastPulse.IDLE }
        val (vigil, verdict) = run(CastPulse.PLAYING, *creux, CastPulse.PLAYING)

        assertIs<CastVerdict.Watch>(verdict)
        assertEquals(0, vigil.idles)
    }

    /**
     * **Une résolution à froid prend 30 s sur la box** — mesuré. Pendant tout ce
     * temps elle répond « rien ne joue », son lecteur n'étant pas encore monté.
     * Un budget calibré sur la fin de lecture couperait donc la session au
     * milieu de presque chaque premier lancement.
     */
    @Test
    fun `la resolution a froid a le temps d aboutir`() {
        val attente = Array(CastVigil.IDLES_BEFORE_RETIRE + 1) { CastPulse.IDLE }
        val (_, verdict) = run(*attente)

        assertIs<CastVerdict.Watch>(verdict)

        // Le budget d'avant-démarrage doit couvrir les 30 s mesurées, avec de la
        // marge : c'est une mesure sur une box, pas une borne théorique.
        val couverture = CastVigil.IDLES_BEFORE_START * CastVigil.IDLE_POLL_MS
        assertTrue(couverture >= 45_000, "seulement ${couverture}ms avant d'abandonner")
    }

    /** Une diffusion qui n'aboutit jamais ne doit pas laisser une sonde éternelle. */
    @Test
    fun `une diffusion qui n aboutit jamais finit par abandonner`() {
        val attente = Array(CastVigil.IDLES_BEFORE_START) { CastPulse.IDLE }
        val (_, verdict) = run(*attente)

        assertEquals(CastVerdict.Retire, verdict)
    }

    /**
     * Le budget large ne vaut que **jusqu'à** la première image. Le garder
     * ensuite laisserait la notification une minute de trop après la fin.
     */
    @Test
    fun `la patience du demarrage ne survit pas a la premiere image`() {
        val fin = Array(CastVigil.IDLES_BEFORE_RETIRE) { CastPulse.IDLE }
        val (_, sansDemarrage) = run(*fin)
        val (_, apresDemarrage) = run(CastPulse.PLAYING, *fin)

        assertIs<CastVerdict.Watch>(sansDemarrage)
        assertEquals(CastVerdict.Retire, apresDemarrage)
        assertTrue(CastVigil.IDLES_BEFORE_START > CastVigil.IDLES_BEFORE_RETIRE)
    }

    /** Une pause prouve que la résolution a abouti, autant qu'une lecture. */
    @Test
    fun `une pause compte comme un demarrage`() {
        val (vigil, _) = CastVigil().observe(CastPulse.PAUSED)
        assertTrue(vigil.started)
    }

    // ── Ce que la notification montre ────────────────────────────────────────

    /**
     * **Le test qui manquait**, et l'émulateur l'a montré avant lui.
     *
     * `un enchainement d episode ne retire pas la session` passait déjà : la
     * vigie rendait bien `Watch`. Et dans l'application, la session mourait
     * quand même au premier creux — le lecteur tombait en `STATE_IDLE`, media3
     * annulait la notification, et le service s'arrêtait dessus. La politique
     * était juste, et personne ne l'écoutait.
     *
     * Il ne suffit donc pas de décider de continuer : il faut qu'il reste
     * quelque chose à montrer pendant qu'on continue.
     */
    @Test
    fun `un creux ne vide pas la notification`() {
        val joue = NowPlaying(title = "Blade Runner 2049", mediaKey = "movie:335984", playing = true)

        val pendantLeCreux = castDisplay(previous = joue, reading = null)

        assertEquals("Blade Runner 2049", pendantLeCreux?.title)
        assertEquals(false, pendantLeCreux?.playing, "le creux se dit, il ne s'efface pas")
    }

    @Test
    fun `un releve nomme remplace ce qu on montrait`() {
        val avant = NowPlaying(title = "Premier", mediaKey = "movie:1", playing = true)
        val apres = NowPlaying(title = "Second", mediaKey = "movie:2", playing = true)

        assertEquals(apres, castDisplay(avant, apres))
    }

    /**
     * Un téléviseur d'avant cette version joue sans dire quoi. Le montrer à la
     * place de ce qu'on affichait donnerait une notification anonyme, et ferait
     * perdre le média qu'on suivait.
     */
    @Test
    fun `un releve sans identite ne remplace rien`() {
        val connu = NowPlaying(title = "Connu", mediaKey = "movie:1", playing = true)
        val anonyme = NowPlaying(title = "", mediaKey = "", playing = true)

        assertEquals("Connu", castDisplay(connu, anonyme)?.title)
    }

    @Test
    fun `sans rien de connu il n y a rien a montrer`() {
        assertEquals(null, castDisplay(previous = null, reading = null))
    }

    // ── La cadence ───────────────────────────────────────────────────────────

    /**
     * En fond, personne ne lit la position : une seconde ne ferait que réveiller
     * la radio. Le relevé doit rester deux fois plus rapide que le pas
     * d'écriture de la reprise (10 s), sans quoi la progression sauterait.
     */
    @Test
    fun `le releve en lecture reste deux fois plus rapide que l ecriture`() {
        val (_, verdict) = CastVigil().observe(CastPulse.PLAYING)
        assertTrue(
            delayOf(verdict) <= 5_000,
            "un relevé plus lent que ça perdrait des morceaux de progression",
        )
    }

    @Test
    fun `une box en pause se releve plus lentement qu en lecture`() {
        val enPause = delayOf(CastVigil().observe(CastPulse.PAUSED).second)
        val enLecture = delayOf(CastVigil().observe(CastPulse.PLAYING).second)

        assertTrue(enPause > enLecture, "une pause n'a rien de neuf à raconter")
    }

    /**
     * Un silence se réessaie **vite** : toute la question est de distinguer un
     * paquet perdu d'une box débranchée, et y répondre tôt est ce qui borne la
     * durée de la notification fantôme.
     */
    @Test
    fun `un silence se reessaie vite et borne la notification fantome`() {
        val apresSilence = delayOf(CastVigil().observe(CastPulse.SILENT).second)
        val enLecture = delayOf(CastVigil().observe(CastPulse.PLAYING).second)

        assertTrue(apresSilence <= enLecture)

        val fantome = CastVigil.SILENCES_BEFORE_RETIRE * CastVigil.SILENT_POLL_MS
        assertTrue(fantome <= 20_000, "une box débranchée laisserait ${fantome}ms de fantôme")
    }
}
