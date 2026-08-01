package fr.moovie.tv.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/**
 * Se souvient de la carte qui a ouvert un menu contextuel, pour lui rendre le
 * focus à la fermeture.
 *
 * Une popup Compose détruit le nœud focalisé en s'ouvrant. À sa fermeture, le
 * focus repartait donc sur le premier élément focalisable de l'écran — le bouton
 * de recherche de l'en-tête — et il fallait redescendre puis re-défiler jusqu'à
 * la carte qu'on venait de quitter. D'autant plus pénible que le menu s'ouvre
 * par un appui **long** : on visait précisément cette carte-là.
 *
 * Un seul emplacement suffit, et c'est volontaire : un seul menu contextuel peut
 * être ouvert à la fois. Le mémoriser au niveau de [MoovieCard] évite de faire
 * remonter un `FocusRequester` par carte à travers chaque rangée.
 */
class MoovieFocusMemory {

    private var pending: FocusRequester? = null

    /** Appelé par la carte au moment d'ouvrir son menu. */
    fun capture(requester: FocusRequester) {
        pending = requester
    }

    /**
     * Rend le focus à la carte mémorisée. Sans effet s'il n'y en a pas, ou si
     * elle a disparu entre-temps (entrée retirée de l'historique depuis le menu,
     * précisément l'un des cas d'usage) — d'où le `runCatching`.
     */
    fun restore() {
        val requester = pending ?: return
        pending = null
        runCatching { requester.requestFocus() }
    }
}

val LocalMoovieFocusMemory = staticCompositionLocalOf { MoovieFocusMemory() }
