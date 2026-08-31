package fr.moovie.tv.data.net

import kotlinx.coroutines.flow.StateFlow

/**
 * L'appareil a-t-il un accès Internet **utilisable** ?
 *
 * ### « Connecté » ne veut rien dire
 *
 * La question n'est pas de savoir si une interface est active : un Wi-Fi
 * associé à une box débranchée, un portail captif d'hôtel, un partage de
 * connexion sans forfait sont tous « connectés » et tous inutiles. C'est
 * pourquoi Android est interrogé sur `NET_CAPABILITY_VALIDATED` — le système a
 * lui-même vérifié qu'il sortait — et pourquoi le desktop, qui n'a aucun
 * équivalent en JVM pure, ouvre périodiquement une connexion vers l'hôte dont
 * l'application a réellement besoin.
 *
 * ### Optimiste au démarrage
 *
 * L'état initial est **en ligne**, sur les deux plateformes, et c'est délibéré :
 * la sonde met un instant à répondre, et démarrer sur « hors ligne » ferait
 * clignoter la bibliothèque locale à chaque lancement avant que l'accueil
 * n'arrive. Se tromper une seconde dans ce sens ne coûte qu'une requête qui
 * échoue ; se tromper dans l'autre remplace l'application entière.
 *
 * ### Ce que la réponse déclenche
 *
 * Tout ce qui sort du réseau s'arrête — synchro, mises à jour, TMDB, résolution
 * de sources — et l'accueil devient la bibliothèque hors ligne. Voir
 * `fr.moovie.tv.ui.offline.OfflineScreen`.
 */
expect object Connectivity {

    /** Vrai tant qu'un accès Internet vérifié existe. */
    val online: StateFlow<Boolean>

    /**
     * Met la sonde en marche. Idempotent : les points d'entrée des deux
     * plateformes l'appellent sans avoir à savoir si l'autre l'a déjà fait.
     */
    fun start()

    /**
     * Re-teste immédiatement, sans attendre le prochain relevé.
     *
     * C'est ce que fait le bouton « Réessayer » de l'écran hors ligne : sans
     * lui, quelqu'un qui vient de rebrancher son câble attendrait la fin d'un
     * cycle devant un écran qui a déjà tort.
     */
    fun recheck()
}
