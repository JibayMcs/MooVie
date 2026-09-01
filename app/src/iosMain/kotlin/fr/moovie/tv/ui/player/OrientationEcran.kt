package fr.moovie.tv.ui.player

/**
 * L'orientation que l'application demande au système.
 *
 * ## Pourquoi ça ne peut pas être fait en Kotlin seul
 *
 * Sur iOS, l'orientation n'est pas un réglage qu'on pose : c'est une
 * **négociation**. Le système interroge le délégué d'application — et lui seul —
 * par `application(_:supportedInterfaceOrientationsFor:)`, à chaque fois qu'il
 * envisage une rotation. Une vue ne peut pas décider pour elle-même ; elle ne
 * peut que changer la réponse que fera le délégué, puis demander au système de
 * la reposer.
 *
 * Ce délégué appartient au côté Swift, qui est le point d'entrée de
 * l'application. Cet objet est donc un simple relais : Compose dit ce qu'il
 * veut, Swift le traduit en la danse UIKit qui va avec — laquelle diffère entre
 * iOS 15 et iOS 16, une raison de plus pour la laisser là-bas.
 *
 * ## Un état posé, et non des demandes appairées
 *
 * La version d'avant comptait les demandes : chaque écran voulant le paysage
 * appelait `demanderPaysage()` en entrant et `relacherPaysage()` en sortant. Sur
 * l'appareil, l'application restait en paysage après avoir refermé la
 * bande-annonce — un relâchement manquait à l'appel, et c'est le genre de
 * déséquilibre qu'une comptabilité par paires rend possible par construction :
 * il suffit d'un chemin de sortie qu'on n'a pas prévu.
 *
 * [definir] remplace ce compte par une valeur qu'un seul endroit calcule — la
 * racine, qui sait quel écran est affiché et si la bande-annonce est au premier
 * plan. Il n'y a plus de sortie à ne pas oublier : à chaque composition, la
 * réponse est recalculée entièrement.
 */
object OrientationEcran {

    /**
     * Posé par Swift au démarrage. Appelé après chaque changement de
     * [paysageForce], sur le fil qui a fait le changement — donc celui de la
     * composition.
     */
    var surChangement: (() -> Unit)? = null

    /**
     * Vrai quand l'écran affiché est une vidéo qui prend toute la place — le
     * lecteur, ou la bande-annonce passée au premier plan.
     *
     * Le reste de l'application reste en portrait, comme sur le téléphone
     * Android : ses écrans sont des listes et des grilles, qu'un paysage
     * étirerait sans rien montrer de plus.
     */
    var paysageForce: Boolean = false
        private set

    /**
     * Déclare l'orientation voulue. Sans effet si elle ne change pas — inutile
     * de faire tourner l'appareil pour lui redire ce qu'il fait déjà.
     */
    fun definir(paysage: Boolean) {
        if (paysage == paysageForce) return
        paysageForce = paysage
        surChangement?.invoke()
    }
}
