package fr.moovie.tv.exit

import fr.moovie.tv.ui.adaptive.UiFlavor

/**
 * Le double appui sur Retour qui ferme l'application, à la racine de la pile.
 *
 * Un seul appui fermait tout d'un coup : sur une télécommande, Retour est la
 * touche qu'on enfonce par réflexe, plusieurs fois de suite, pour « revenir »,
 * et la dernière de la série faisait quitter sans prévenir. Le premier appui
 * arme donc, et seul un second dans la fenêtre ferme.
 *
 * Hors d'Android, et sans horloge à elle : c'est l'appelant qui donne l'heure,
 * ce qui rend la fenêtre testable à la milliseconde près.
 */
class DoubleBackGate(private val windowMs: Long = WINDOW_MS) {

    private var armedAt: Long? = null

    /**
     * Un appui sur Retour à l'instant [nowMs]. Rend vrai s'il faut fermer ;
     * faux s'il vient seulement d'armer, et qu'il faut alors l'annoncer.
     *
     * Un second appui trop tardif ne ferme pas : il réarme, comme un premier.
     */
    fun press(nowMs: Long): Boolean {
        val armed = armedAt
        if (armed != null && nowMs - armed in 0..windowMs) {
            armedAt = null
            return true
        }
        armedAt = nowMs
        return false
    }

    /** Oublie l'appui en attente : la pile a bougé entre-temps. */
    fun reset() {
        armedAt = null
    }

    companion object {
        /** La fenêtre du second appui, calée sur la durée d'un Toast court. */
        const val WINDOW_MS = 2_000L
    }
}

/**
 * Faut-il fermer l'application quand elle quitte l'écran ?
 *
 * La question se pose pour **l'application**, pas pour une Activity : sur
 * téléphone, `MainActivity` lance `PlayerActivity`, et ce passage arrête la
 * première sans que rien ne soit quitté. On compte donc les Activity démarrées,
 * et seule la dernière à s'arrêter parle pour le processus, l'ordre d'Android
 * garantissant que la suivante démarre avant que la précédente ne s'arrête.
 *
 * Trois exceptions, qui ressemblent toutes à un départ sans en être un :
 *
 * - **Un changement de configuration** : l'Activity est recréée aussitôt.
 * - **Un écran système lancé par l'application elle-même**, l'installeur
 *   d'une mise à jour, la recherche vocale, une demande d'autorisation. On y
 *   part pour en revenir avec un résultat ; fermer pendant l'installeur
 *   risquerait même de casser l'installation. [expectReturn] est posé juste
 *   avant le lancement, et levé au retour (une reprise suffit : une boîte
 *   d'autorisation translucide ne fait que mettre en pause).
 *   **Borné dans le temps** : si le lancement n'ouvre aucun écran, personne ne
 *   revient jamais, et l'attente resterait levée jusqu'à la prochaine reprise,
 *   ce qui désactiverait la fermeture sans que rien ne le montre. Passé
 *   [ATTENTE_RETOUR_MS], la mise en attente ne compte plus.
 * - **Le téléphone**, entièrement : il garde l'application en mémoire, et la
 *   lecture part en vignette. Seul le téléviseur ferme.
 *
 * Non synchronisée : tous les rappels d'Activity arrivent sur le fil principal.
 */
class BackgroundExitPolicy {

    private var started = 0

    /** Échéance de l'attente de retour, en ms ; null s'il n'y a rien à attendre. */
    private var attenteJusqua: Long? = null

    fun activityStarted() {
        started++
    }

    fun activityResumed() {
        attenteJusqua = null
    }

    /** Un écran externe va s'ouvrir à l'instant [nowMs], et l'on attend d'en revenir. */
    fun expectReturn(nowMs: Long) {
        attenteJusqua = nowMs + ATTENTE_RETOUR_MS
    }

    /** Le lancement a échoué : personne ne reviendra, rien n'est à attendre. */
    fun cancelReturn() {
        attenteJusqua = null
    }

    /** Une Activity s'arrête à l'instant [nowMs]. Rend vrai s'il faut fermer l'application. */
    fun activityStopped(changingConfigurations: Boolean, flavor: UiFlavor, nowMs: Long): Boolean {
        started = (started - 1).coerceAtLeast(0)
        val attendu = attenteJusqua?.let { nowMs <= it } ?: false
        return started == 0 &&
            !changingConfigurations &&
            !attendu &&
            flavor == UiFlavor.TV
    }

    companion object {
        /**
         * Le délai entre le lancement d'un écran système et l'arrêt de
         * l'Activity qui l'ouvre. Quelques centaines de millisecondes sur une
         * box chargée ; dix secondes laissent une marge large, et bornent le
         * cas où rien ne s'ouvre.
         */
        const val ATTENTE_RETOUR_MS = 10_000L
    }
}
