package fr.moovie.tv.data.download

/**
 * Vide sur iOS, et ce n'est **pas** le même vide que sur desktop.
 *
 * Le desktop ne fait rien parce qu'il n'a pas le problème : son processus vit
 * tant que la fenêtre est ouverte. iOS l'a, et plus durement qu'Android — le
 * système suspend l'application quelques secondes après le passage en
 * arrière-plan, et rien de ce que l'app exécute elle-même n'y survit. Une
 * notification de progression n'y changerait rien : il n'existe pas
 * d'équivalent du service de premier plan.
 *
 * La réponse d'iOS à ce besoin est ailleurs — une session de transfert en
 * arrière-plan `NSURLSession`, que le **système** exécute pour le compte de
 * l'app, y compris pendant qu'elle est suspendue. C'est une autre architecture
 * de téléchargement, pas une variante de celle-ci : le transfert n'est plus
 * piloté par une coroutine mais délégué avec des rappels au réveil.
 *
 * Tant que cette architecture n'est pas écrite, un téléchargement iOS ne
 * progresse **que tant que l'écran est allumé sur l'application**. C'est une
 * limite réelle, documentée ici plutôt que découverte par un utilisateur qui
 * verrait sa barre figée.
 */
actual object DownloadForeground {
    actual fun start() = Unit
    actual fun stop() = Unit
}
