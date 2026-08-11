package fr.moovie.tv.data.pairing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La page télécommande, sur ce qui ne se voit pas à la relecture.
 *
 * Une page servie par un téléviseur ne se teste pas au navigateur en CI, mais
 * trois choses s'y vérifient sans en ouvrir un, et chacune a déjà cassé quelque
 * chose ici ou ailleurs : qu'aucune ressource externe ne soit référencée, que
 * chaque touche porte un nom accessible — elles n'ont plus de texte — et que
 * les codes envoyés soient ceux que le serveur accepte.
 *
 * Avec `-Dmoovie.probe=1`, elle écrit aussi la page dans `/tmp` pour l'ouvrir
 * dans un vrai navigateur. C'est ce qui a rattrapé un formulaire dont les
 * champs étaient invisibles alors que le code se lisait bien.
 */
class RemotePageTest {

    private val texts = PairingTexts(
        title = "Réglages",
        intro = "intro",
        submit = "Envoyer",
        done = "Fait",
        doneDetail = "détail",
        remoteTitle = "Télécommande",
        remoteIntro = "Pilote Moo-vie",
        remoteType = "Texte à saisir",
        remoteSend = "Envoyer",
        remoteToSettings = "Réglages",
        remoteToRemote = "Télécommande",
        remoteBack = "Retour",
        keyUp = "Haut",
        keyDown = "Bas",
        keyLeft = "Gauche",
        keyRight = "Droite",
        keyOk = "Valider",
        keyBack = "Retour",
        keyPlayPause = "Lecture ou pause",
        keyRewind = "Reculer",
        keyForward = "Avancer",
        keyKeyboard = "Clavier",
    )

    private val page = remotePage(texts, "http://192.168.1.20:8080/abc")

    @Test
    fun `la page ne charge rien depuis l'extérieur`() {
        // Servie par un téléviseur sur un réseau qui n'a pas forcément Internet :
        // la moindre ressource distante arriverait sans style, ou pas du tout.
        listOf("<script src", "<link", "@import", "//fonts.", "cdn.").forEach {
            assertTrue(it !in page, "la page ne doit rien charger d'externe ($it)")
        }
    }

    @Test
    fun `chaque touche porte un nom accessible`() {
        // Sans texte visible, l'aria-label est le seul nom de la touche. Une
        // icône muette n'est pas un bouton pour qui n'y voit pas.
        val buttons = Regex("""<button[^>]*>""").findAll(page).map { it.value }.toList()
        assertTrue(buttons.size >= 10, "attendu au moins dix touches, vu ${buttons.size}")
        buttons.forEach {
            assertTrue("aria-label=\"" in it, "touche sans nom accessible : $it")
        }
    }

    @Test
    fun `les codes envoyés sont ceux que le serveur connaît`() {
        val sent = Regex("""data-k="([A-Z_]+)"""").findAll(page).map { it.groupValues[1] }.toSet()
        val known = fr.moovie.tv.data.remote.RemoteKey.entries.map { it.name }.toSet()

        assertEquals(emptySet(), sent - known, "codes inconnus du serveur")
        assertEquals(emptySet(), known - sent, "touches du serveur absentes de la page")
    }

    /**
     * L'API de vibration n'existe pas partout — iOS ne l'implémente pas. Elle
     * doit donc être testée avant d'être appelée, sinon la première pression
     * lève une exception et le code qui envoie la touche derrière elle ne part
     * jamais : la télécommande cesserait de fonctionner là où elle se contente
     * aujourd'hui de ne pas vibrer.
     */
    @Test
    fun `la vibration est testée avant d'être appelée`() {
        // La présence est relevée une fois puis consultée à chaque appel : ce
        // qui compte est qu'aucun `navigator.vibrate(` ne soit atteint sans
        // qu'elle ait été vérifiée.
        assertTrue("'vibrate' in navigator" in page, "présence non relevée")
        assertTrue("if (haptics)" in page, "appel non gardé")
    }

    /**
     * Le geste du joystick est suivi sur le disque, pas sur les secteurs : eux
     * ne reçoivent plus de pointeur. Le vérifier ici évite de re-régler la
     * géométrie et de la reperdre au prochain remaniement du style.
     */
    @Test
    fun `le disque prend le geste, pas les secteurs`() {
        assertTrue("touch-action:none" in page, "le disque doit garder le geste")
        assertTrue("pointer-events:none" in page, "les secteurs ne prennent pas le pointeur")
        assertTrue("setPointerCapture" in page, "le doigt doit rester suivi hors du disque")
    }

    @Test
    fun `écrit la page pour inspection`() {
        if (System.getProperty("moovie.probe") == null) return
        val out = File("/tmp/moovie-remote.html")
        out.writeText(page)
        println("[sonde] page écrite : ${out.absolutePath}")
    }

    /**
     * Le mini-lecteur de la page web doit offrir ce qu'offre l'écran natif.
     *
     * C'est la seule télécommande dont disposent les téléphones sans
     * l'application — un iPhone, notamment. Une page en retard d'une
     * fonctionnalité, ce sont des utilisateurs qui n'y ont simplement pas droit.
     */
    @Test
    fun `la page offre le mini-lecteur et le deplacement`() {
        listOf("id=\"np\"", "id=\"npbar\"", "id=\"nptitle\"", "id=\"npart\"").forEach {
            assertTrue(it in page, "le mini-lecteur doit exposer $it")
        }
        assertTrue("'/state'" in page, "la page doit relever l'état du téléviseur")
        assertTrue("'/seek'" in page, "la barre doit pouvoir déplacer la lecture")
    }

    /**
     * Les deux icônes sont dans le balisage, et c'est voulu : le bouton bascule
     * par une classe. Réécrire son HTML à chaque relevé referait le calque et
     * ferait clignoter le néon une fois par seconde.
     */
    @Test
    fun `le bouton central porte les deux icones`() {
        assertTrue("class=\"ip\"" in page && "class=\"ia\"" in page)
        assertTrue(".rk.hero.playing .ia" in page, "la bascule doit se faire en CSS")
    }

    /**
     * La jaquette vient de TMDB, donc d'Internet — mais **à l'exécution**, et
     * seulement si le téléviseur en annonce une. Aucune URL n'est figée dans la
     * page : sans réseau, l'image manque et le reste fonctionne, ce qui est
     * exactement la promesse que garde le test d'au-dessus.
     */
    @Test
    fun `aucune image n'est figee dans le balisage`() {
        assertTrue("image.tmdb.org" !in page, "la jaquette est fournie par le téléviseur")
        assertTrue("<img class=\"npart\" id=\"npart\" alt=\"\" hidden>" in page)
    }

    /** Le champ suit le focus du téléviseur : sans libellé, on remplit à l'aveugle. */
    @Test
    fun `le champ de saisie s'annonce avec son libelle`() {
        assertTrue("id=\"kblabel\"" in page)
        assertTrue("setTyping" in page)
    }

}
