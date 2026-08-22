package fr.moovie.tv.ui.player

import android.content.Context
import android.content.Intent
import fr.moovie.tv.PlayerActivity
import fr.moovie.tv.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Le passage de relais entre l'écran principal et le lecteur détaché.
 *
 * ### Pourquoi le lecteur sort de l'Activity, sur téléphone seulement
 *
 * Android ne duplique pas une application pour la vignette : il y **déplace
 * l'Activity elle-même**. Avec une seule Activity, il ne reste donc rien
 * derrière, et la vignette *est* Moo-vie — impossible de continuer à naviguer
 * pendant qu'un épisode joue. La seule façon d'avoir les deux est d'empiler le
 * lecteur dans sa propre Activity : elle part en vignette, celle du dessous
 * revient au premier plan, et toucher la vignette la ramène en plein écran.
 * C'est ce que font YouTube et Netflix.
 *
 * **Le téléviseur n'y touche pas.** Il n'a pas de vignette, on n'y quitte pas
 * l'application en pleine lecture, et son lecteur est ce qu'il y a de plus
 * délicat dans cette application — focus D-pad, veille, enchaînement. Il
 * continue donc d'être rendu par `MainActivity`, exactement comme avant. La
 * séparation ne coûte pas un second écran : `PlayerScreen` reste unique, seule
 * change la fenêtre qui l'héberge.
 *
 * ### L'écran passe par ici, pas par l'intent
 *
 * [Screen.Player] porte des en-têtes, des sous-titres et une liste de sources de
 * repli. Les sérialiser pour les faire transiter par un `Intent` reviendrait à
 * inventer un format pour deux objets qui ne quittent jamais le processus. On
 * les dépose donc ici, et l'Activity les reprend.
 *
 * Le prix, assumé : si le système tue le processus, l'Activity recréée trouve
 * [enAttente] vide et se referme. C'est déjà ce qui arrivait — la lecture ne
 * survivait pas davantage à une mort de processus.
 */
object PlayerHost {

    /**
     * L'écran à jouer, déposé juste avant de lancer l'Activity.
     *
     * `@Volatile` par principe : l'écriture vient de la composition de
     * `MainActivity`, la lecture du `onCreate` de l'autre Activity.
     */
    @Volatile
    var enAttente: Screen.Player? = null
        private set

    /**
     * Ce que le lecteur détaché demande à l'écran principal.
     *
     * Il ne peut rien faire lui-même de ces trois-là : elles passent toutes par
     * le `DetailsViewModel`, qui a le scope de `MainActivity` et connaît la série
     * en cours. Une seconde Activity en obtiendrait une autre instance, vide.
     */
    sealed interface Demande {
        /** Prépare les sources de l'épisode suivant pendant que celui-ci joue. */
        data class Prefetch(val saison: Int, val episode: Int) : Demande

        /** Le flux a cassé une fois ouvert : la fiche reprend la cascade. */
        data object Echec : Demande

        /** Enchaîner : la fiche résout la source, puis relance le lecteur. */
        data class Episode(val tmdbId: Int, val saison: Int, val episode: Int) : Demande

        /**
         * La lecture est partie sur un Chromecast : montrer l'écran qui la pilote.
         *
         * **C'est la seule Activity qui puisse le faire.** Le lecteur détaché a
         * une fenêtre à lui, et se contenter de la refermer laissait retomber sur
         * la fiche — le film jouait sur la télé et rien ne le disait. La
         * télécommande de diffusion vit dans la navigation de `MainActivity`, donc
         * la demande doit y remonter, comme les trois autres.
         */
        data object Diffusion : Demande
    }

    // `extraBufferCapacity` plutôt qu'un canal : le lecteur émet au moment où il
    // se referme, et une émission qui suspendrait attendrait un collecteur que
    // l'Activity mourante ne peut plus servir.
    private val _demandes = MutableSharedFlow<Demande>(extraBufferCapacity = 8)
    val demandes: SharedFlow<Demande> = _demandes.asSharedFlow()

    fun demande(demande: Demande) {
        _demandes.tryEmit(demande)
    }

    /** Ouvre le lecteur détaché sur cet écran. */
    fun ouvre(context: Context, ecran: Screen.Player) {
        enAttente = ecran
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }

    /** Reprend l'écran déposé, et le retire : une lecture ne se rejoue pas seule. */
    fun preleve(): Screen.Player? = enAttente.also { enAttente = null }
}
