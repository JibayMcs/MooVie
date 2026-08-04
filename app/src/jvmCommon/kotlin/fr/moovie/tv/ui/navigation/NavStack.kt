package fr.moovie.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/**
 * Pile de navigation partagée TV + desktop.
 *
 * Avant, chaque écran câblait son retour en dur sur [Screen.Home] (Android) ou
 * sur une unique fiche mémorisée (desktop) : revenir du lecteur perdait l'écran
 * d'où l'on venait, et le retour suivant sautait par-dessus la liste des
 * saisons. Une pile explicite rend le chemin inverse fidèle à l'aller.
 *
 * Ce n'est volontairement pas un NavHost : le contrôle explicite du focus et du
 * retour reste plus simple à maîtriser sur TV.
 */
class NavStack(root: Screen = Screen.Home) {

    private val entries = mutableStateListOf(root)

    /** Écran affiché. */
    val current: Screen get() = entries.last()

    /** Vrai s'il reste un écran en dessous (le retour a quelque chose à faire). */
    val canGoBack: Boolean get() = entries.size > 1

    /** Empile un écran : le retour ramènera sur l'écran courant. */
    fun push(screen: Screen) {
        entries.add(screen)
    }

    /**
     * Remplace l'écran courant sans creuser la pile. Utilisé pour l'enchaînement
     * d'épisodes : sinon dix épisodes d'affilée laisseraient dix entrées à
     * remonter une par une pour revenir à la fiche.
     */
    fun replace(screen: Screen) {
        entries[entries.lastIndex] = screen
    }

    /** Dépile. Retourne faux si on est déjà à la racine (rien à faire). */
    fun pop(): Boolean {
        if (entries.size <= 1) return false
        entries.removeAt(entries.lastIndex)
        return true
    }

    /**
     * Bascule vers une destination de premier niveau, sans creuser la pile.
     *
     * C'est le geste d'une barre d'onglets : passer de l'accueil au catalogue
     * puis à l'historique ne doit pas laisser trois retours à remonter. On
     * revient donc à la racine avant d'empiler la destination — et le bouton
     * retour du système ramène toujours à l'accueil en un appui, quel que soit
     * le nombre d'onglets visités.
     */
    fun switchTop(screen: Screen) {
        popToRoot()
        if (entries.first() != screen) push(screen)
    }

    /** Retour direct à la racine (accueil). */
    fun popToRoot() {
        while (entries.size > 1) entries.removeAt(entries.lastIndex)
    }

    /**
     * Remonte jusqu'à la dernière entrée satisfaisant [predicate], sans dépiler
     * si aucune ne correspond. Sert à revenir sur la fiche d'un titre depuis le
     * lecteur quand l'enchaînement a remplacé l'entrée courante.
     */
    fun popUpTo(predicate: (Screen) -> Boolean): Boolean {
        val index = entries.indexOfLast(predicate)
        if (index < 0 || index == entries.lastIndex) return false
        while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
        return true
    }
}

/** Pile mémorisée pour la durée de la composition. */
@Composable
fun rememberNavStack(root: Screen = Screen.Home): NavStack = remember { NavStack(root) }
