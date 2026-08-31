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
     * Vrai tant qu'au moins un écran réclame le paysage.
     *
     * Le reste de l'application reste en portrait, comme sur le téléphone
     * Android : ses écrans sont des listes et des grilles, qu'un paysage
     * étirerait sans rien montrer de plus. Une vidéo, elle, n'a qu'une
     * orientation qui lui convienne.
     */
    var paysageForce: Boolean = false
        private set

    /**
     * **Un compteur, et non un drapeau.** Deux écrans réclament le paysage — le
     * lecteur, et la fiche quand sa bande-annonce passe au premier plan — et
     * l'un mène à l'autre : on lance un film depuis la fiche. Avec un simple
     * booléen, la fiche relâcherait en se démontant juste après que le lecteur
     * a demandé, et l'écran retomberait en portrait sur le film.
     *
     * Le compteur rend l'ordre indifférent : le portrait ne revient qu'au
     * dernier relâchement.
     */
    private var demandes = 0

    /** Entrée dans un écran qui veut le paysage. */
    fun demanderPaysage() {
        demandes++
        appliquer()
    }

    /** Sortie. Sans effet si un autre écran le réclame encore. */
    fun relacherPaysage() {
        if (demandes > 0) demandes--
        appliquer()
    }

    private fun appliquer() {
        val voulu = demandes > 0
        if (voulu == paysageForce) return
        paysageForce = voulu
        surChangement?.invoke()
    }
}
