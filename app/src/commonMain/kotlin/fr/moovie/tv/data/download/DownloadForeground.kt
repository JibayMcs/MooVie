package fr.moovie.tv.data.download

/**
 * Maintient le processus en vie tant que la file tourne.
 *
 * ### Le défaut que ça corrige
 *
 * [DownloadQueue] vit dans un `CoroutineScope` d'application : **Android le tue
 * dès que Moo-vie passe en arrière-plan**. Un film de deux heures ne survivait
 * donc pas au premier appui sur la touche Accueil, et rien ne le disait — la
 * reprise repartait plus tard, silencieusement.
 *
 * Poser une notification de progression sans régler ça aurait été pire que rien
 * : une barre figée pour toujours sur un téléchargement mort.
 *
 * ### Pourquoi une abstraction plutôt qu'un appel direct
 *
 * La file est en `jvmCommon`, elle ne peut pas nommer un `Service` Android. Le
 * desktop, lui, n'a pas le problème : son processus vit tant que la fenêtre est
 * ouverte, d'où une implémentation vide plutôt qu'un mécanisme inventé.
 */
expect object DownloadForeground {
    /** Appelé quand un premier titre entre en file. Idempotent. */
    fun start()

    /** Appelé quand la file se vide. Idempotent. */
    fun stop()
}
