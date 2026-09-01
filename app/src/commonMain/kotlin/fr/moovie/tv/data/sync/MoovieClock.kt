package fr.moovie.tv.data.sync

import fr.moovie.tv.shared.Verrou
import fr.moovie.tv.shared.avec
import fr.moovie.tv.shared.maintenantMs
import kotlin.concurrent.Volatile

/**
 * L'horloge qui date les décisions synchronisables.
 *
 * Trois garanties, dans cet ordre d'importance :
 *
 * 1. **Corrigée** — l'écart mesuré avec l'horloge du dépôt ([SyncReport.clockOffset])
 *    est ajouté au temps physique. Deux appareils mal réglés produisent alors des
 *    valeurs comparables, sans que personne n'ait eu à se mettre à l'heure.
 * 2. **Monotone** — on n'émet jamais deux fois la même valeur, ni une valeur
 *    inférieure à la précédente. Deux décisions dans la même milliseconde
 *    restent donc ordonnées.
 * 3. **Causale** — [observe] hisse le plancher sur tout horodatage distant qu'on
 *    lit. Après avoir fusionné un fichier venu d'un appareil en avance, nos
 *    écritures suivantes le dépassent forcément, même si notre horloge physique,
 *    elle, est encore derrière.
 *
 * C'est une horloge logique hybride, avec le compteur **replié dans la
 * milliseconde** au lieu d'être un second champ. Le couple `(physique, compteur)`
 * du manuel imposerait d'encoder deux nombres dans un `Long`, donc de changer
 * l'échelle de toutes les valeurs déjà écrites — et de casser au passage les
 * champs qui sont aussi des dates affichées. Ici un horodatage reste un epoch en
 * millisecondes : rien à migrer, rien à réinterpréter.
 *
 * **Volontairement pas persistée.** Au redémarrage le plancher retombe au temps
 * physique corrigé, ce qui est correct : la correction est, elle, persistée.
 */
object MoovieClock {

    /**
     * Au-delà, un horodatage distant est tenu pour aberrant.
     *
     * Sans ce garde-fou, un seul appareil dont l'horloge est en 2038 tirerait
     * l'horloge de tous les autres avec lui, définitivement. Le prix est
     * qu'une avance réelle de plus d'un jour serait ignorée, ce qui n'arrive
     * pas sans une horloge fausse.
     */
    private const val MAX_DRIFT_MS = 24L * 60 * 60 * 1000

    @Volatile
    private var offset = 0L

    @Volatile
    private var floor = 0L

    private val lock = Verrou()

    /** Applique l'écart mesuré face au dépôt. */
    fun correctBy(offset: Long) {
        this.offset = offset
    }

    /** Horodate une décision locale. Jamais deux fois la même valeur. */
    fun now(): Long = lock.avec {
        val physical = maintenantMs() + offset
        val next = if (physical > floor) physical else floor + 1
        floor = next
        next
    }

    /**
     * Prend acte d'un horodatage lu ailleurs.
     *
     * À appeler sur ce qu'on fusionne : c'est ce qui garantit qu'une décision
     * prise *après* avoir vu celle d'en face la dépasse, quelles que soient les
     * horloges physiques.
     */
    fun observe(remote: Long) {
        if (remote <= 0) return
        lock.avec {
            val physical = maintenantMs() + offset
            if (remote > physical + MAX_DRIFT_MS) return
            if (remote > floor) floor = remote
        }
    }

    /** Pour les tests : repart d'une horloge vierge. */
    internal fun reset() = lock.avec {
        offset = 0
        floor = 0
    }
}
