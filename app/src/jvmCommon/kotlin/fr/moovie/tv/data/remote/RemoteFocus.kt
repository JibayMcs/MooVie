package fr.moovie.tv.data.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Repli de déplacement du focus pour la télécommande virtuelle.
 *
 * ### Le défaut qu'il corrige
 *
 * Injecter la touche par `dispatchKeyEvent` traverse bien la hiérarchie de vues,
 * mais **court-circuite `ViewRootImpl`**. Or c'est lui qui, sur une vraie
 * télécommande, *attribue un focus initial* quand plus rien n'en détient : il
 * fait la recherche de focus une fois que la hiérarchie a décliné l'événement.
 *
 * Sans focus de départ, Compose n'a rien à déplacer et la flèche ne fait rien.
 * C'est exactement ce qui se produisait sur l'accueil au retour des réglages :
 * aucun élément focalisé, donc haut/bas/gauche/droite sans effet — alors que OK
 * et Retour marchaient, l'un consommé par l'élément actif, l'autre traité par
 * l'Activity elle-même. Le symptôme désignait le mauvais coupable.
 *
 * ### Pourquoi un repli et non un remplacement
 *
 * On garde l'injection en premier : c'est elle qui donne leur chance aux
 * `onPreviewKeyEvent` des neuf écrans, au lecteur et aux champs de texte. Le
 * déplacement de focus n'intervient **que si personne n'a consommé la touche**,
 * ce qui est précisément l'ordre que suit `ViewRootImpl`. Remplacer l'injection
 * par `moveFocus` aurait au contraire ignoré tous ces gestionnaires.
 */
object RemoteFocus {

    private var manager: FocusManager? = null

    /**
     * Déplace le focus, ou rend false si la touche n'est pas directionnelle.
     *
     * `moveFocus` sait partir de rien : sans élément actif, il donne le focus au
     * premier candidat dans la direction demandée. C'est ce qui débloque le cas
     * observé.
     */
    fun move(key: RemoteKey): Boolean {
        val direction = when (key) {
            RemoteKey.UP -> FocusDirection.Up
            RemoteKey.DOWN -> FocusDirection.Down
            RemoteKey.LEFT, RemoteKey.REWIND -> FocusDirection.Left
            RemoteKey.RIGHT, RemoteKey.FORWARD -> FocusDirection.Right
            else -> return false
        }
        return manager?.moveFocus(direction) ?: false
    }

    /**
     * À poser une fois à la racine de la composition, sur chaque plateforme.
     *
     * Le `FocusManager` est propre à la composition : le prendre ailleurs
     * viserait un autre arbre, ou rien du tout.
     */
    @Composable
    fun Register() {
        val current = LocalFocusManager.current
        DisposableEffect(current) {
            manager = current
            onDispose { manager = null }
        }
    }
}
