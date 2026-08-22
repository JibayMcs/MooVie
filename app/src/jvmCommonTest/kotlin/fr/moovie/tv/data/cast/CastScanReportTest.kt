package fr.moovie.tv.data.cast

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Le diagnostic d'un balayage mDNS — ce qui sépare « rien sur le réseau » de
 * « la recherche a échoué ».
 *
 * ## Ce que ces tests protègent
 *
 * Un utilisateur a signalé que le bouton de diffusion n'apparaissait **jamais**
 * chez lui, sur le même Wi-Fi que son Chromecast. Impossible de l'aider : la
 * liste était vide, et une liste vide était tout ce que l'application savait
 * dire. Trois causes très différentes se présentaient sous ce seul visage.
 *
 * Chaque verdict ci-dessous mène à un conseil différent, et se tromper de
 * verdict, c'est envoyer chercher la panne au mauvais endroit — vérifier son
 * routeur quand c'est la pile du téléphone qui a refusé, par exemple.
 */
class CastScanReportTest {

    @Test
    fun `avant tout balayage on ne conclut rien`() {
        assertEquals(CastScanVerdict.JAMAIS, CastScanReport(passages = 0).verdict)
    }

    @Test
    fun `un appareil resolu est un appareil trouve`() {
        val rapport = CastScanReport(annonces = 1, resolus = 1, passages = 1)

        assertEquals(CastScanVerdict.TROUVE, rapport.verdict)
    }

    /**
     * Personne n'a répondu : la cause est **hors de l'application** — mauvais
     * réseau, isolation des clients par le routeur, multicast filtré. C'est le
     * seul cas où il est juste de renvoyer l'utilisateur vers son Wi-Fi.
     */
    @Test
    fun `aucune annonce accuse le reseau, pas l application`() {
        val rapport = CastScanReport(annonces = 0, resolus = 0, passages = 3)

        assertEquals(CastScanVerdict.RESEAU_MUET, rapport.verdict)
    }

    /**
     * **Le test qui compte.** Des appareils se sont annoncés et aucun n'a pu
     * être joint : c'est le symptôme de la collision de résolutions que
     * [fr.moovie.tv.data.net.NsdGate] corrige. Le confondre avec un réseau muet
     * ferait chercher une panne de Wi-Fi qui n'existe pas, alors que relancer
     * suffit.
     */
    @Test
    fun `des annonces sans resolution designent la resolution`() {
        val rapport = CastScanReport(annonces = 2, resolus = 0, passages = 1)

        assertEquals(CastScanVerdict.RESOLUTION, rapport.verdict)
    }

    /**
     * La pile a refusé de démarrer. Rien n'a été cherché — dire « aucun
     * récepteur » serait faux, et c'est pourtant ce que l'application faisait :
     * `onStartDiscoveryFailed` était un `Unit`, et on attendait quatre secondes
     * avant de rendre une liste vide indiscernable d'un réseau vide.
     */
    @Test
    fun `un refus de la pile prime sur le compte des annonces`() {
        val rapport = CastScanReport(demarre = false, annonces = 0, resolus = 0, passages = 1)

        assertEquals(CastScanVerdict.PILE_REFUSE, rapport.verdict)
    }

    /**
     * Le bureau n'a pas de pile mDNS : « pas encore disponible ici » et non
     * « aucun récepteur », qui enverrait chercher un problème de réseau sur une
     * fonctionnalité qui n'est simplement pas écrite.
     */
    @Test
    fun `une plateforme sans mDNS le dit plutot que de se taire`() {
        val rapport = CastScanReport(demarre = false, supporte = false, passages = 1)

        assertEquals(CastScanVerdict.NON_SUPPORTE, rapport.verdict)
    }

    /** Un appareil trouvé prime : peu importe combien d'autres ont échoué. */
    @Test
    fun `un seul resolu suffit a conclure au succes`() {
        val rapport = CastScanReport(annonces = 4, resolus = 1, passages = 2)

        assertEquals(CastScanVerdict.TROUVE, rapport.verdict)
    }
}
