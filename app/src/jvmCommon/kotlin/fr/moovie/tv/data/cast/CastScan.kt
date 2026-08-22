package fr.moovie.tv.data.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ce qu'un balayage mDNS a donné, dans le détail.
 *
 * ## Pourquoi on garde tout ça
 *
 * « Aucun récepteur » est le symptôme, jamais la cause, et trois causes très
 * différentes le produisent :
 *
 * - la pile mDNS a **refusé de démarrer** ([demarre] faux) — l'appareil a autre
 *   chose en cours, ou le service NSD n'est pas joignable ;
 * - elle a démarré et **rien n'a répondu** ([annonces] à zéro) — plus personne
 *   sur le réseau, ou le multicast est filtré, ou le routeur isole ses clients ;
 * - des appareils se sont annoncés mais **aucun n'a pu être résolu**
 *   ([annonces] > 0 et [resolus] à zéro) — la collision de résolutions que
 *   [fr.moovie.tv.data.net.NsdGate] corrige, sur les appareils d'avant
 *   Android 12.
 *
 * Sans cette distinction, les trois se présentent comme une liste vide, et une
 * liste vide ressemble exactement à un réseau sans Chromecast. C'est la même
 * leçon que la sonde de couverture des catalogues : un échec silencieux est
 * indistinguable d'un succès sans résultat.
 */
data class CastScanReport(
    /** Faux si `discoverServices` n'a même pas pu démarrer. */
    val demarre: Boolean = true,
    /** Annonces mDNS vues pendant la fenêtre, avant résolution. */
    val annonces: Int = 0,
    /** Appareils effectivement résolus, donc joignables. */
    val resolus: Int = 0,
    /** Nombre de balayages menés depuis le lancement. */
    val passages: Int = 0,
    /** Vrai si la plateforme ne sait pas balayer du tout (bureau). */
    val supporte: Boolean = true,
) {

    /**
     * Le diagnostic en un mot, pour l'afficher sans le recalculer partout.
     *
     * `RESOLUTION` est le seul cas qui accuse **l'application** ; les autres
     * disent que le problème est ailleurs, ce qui est une information à part
     * entière quand on cherche pourquoi un bouton n'apparaît pas.
     */
    val verdict: CastScanVerdict
        get() = when {
            !supporte -> CastScanVerdict.NON_SUPPORTE
            passages == 0 -> CastScanVerdict.JAMAIS
            !demarre -> CastScanVerdict.PILE_REFUSE
            resolus > 0 -> CastScanVerdict.TROUVE
            annonces > 0 -> CastScanVerdict.RESOLUTION
            else -> CastScanVerdict.RESEAU_MUET
        }
}

enum class CastScanVerdict { JAMAIS, TROUVE, RESEAU_MUET, RESOLUTION, PILE_REFUSE, NON_SUPPORTE }

/**
 * Le compte rendu du dernier balayage Cast, lisible de partout.
 *
 * Un objet global plutôt qu'une valeur de retour : la découverte est appelée
 * depuis plusieurs écrans, et le compte rendu intéresse surtout **celui qui n'a
 * rien demandé** — les réglages, où l'on va voir pourquoi rien n'apparaît.
 */
object CastScan {

    private val _dernier = MutableStateFlow(CastScanReport(passages = 0))
    val dernier: StateFlow<CastScanReport> = _dernier.asStateFlow()

    fun rapporte(demarre: Boolean, annonces: Int, resolus: Int, supporte: Boolean = true) {
        _dernier.value = CastScanReport(
            demarre = demarre,
            annonces = annonces,
            resolus = resolus,
            passages = _dernier.value.passages + 1,
            supporte = supporte,
        )
    }
}
