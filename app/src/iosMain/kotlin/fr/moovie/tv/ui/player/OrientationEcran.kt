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
 * ## Le rappel plutôt qu'un sondage
 *
 * `MoovieApp.swift` pose [surChangement] au démarrage. Sans lui, cet objet ne
 * fait rien de visible : c'est voulu, il ne suppose pas qu'on l'ait branché.
 * Le rappel ne porte aucun argument — Swift relit [paysageForce] — parce qu'un
 * `Boolean` de Kotlin traverse la frontière emballé dans un `KotlinBoolean`,
 * et qu'une propriété se lit sans cet emballage.
 */
object OrientationEcran {

    /**
     * Posé par Swift au démarrage. Appelé après chaque changement de
     * [paysageForce], sur le fil qui a fait le changement — donc celui de la
     * composition.
     */
    var surChangement: (() -> Unit)? = null

    /**
     * Vrai tant qu'un lecteur est à l'écran.
     *
     * Le reste de l'application reste en portrait, comme sur le téléphone
     * Android : ses écrans sont des listes et des grilles, qu'un paysage
     * étirerait sans rien montrer de plus. Une vidéo, elle, n'a qu'une
     * orientation qui lui convienne.
     */
    var paysageForce: Boolean = false
        private set

    /** Entrée dans le lecteur. */
    fun forcerPaysage() {
        if (paysageForce) return
        paysageForce = true
        surChangement?.invoke()
    }

    /** Sortie du lecteur : le portrait redevient la seule orientation. */
    fun rendreLibre() {
        if (!paysageForce) return
        paysageForce = false
        surChangement?.invoke()
    }
}
