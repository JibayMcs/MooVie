package fr.moovie.tv.ui.adaptive

import androidx.compose.runtime.Composable

/**
 * Le geste de retour du système, capté par l'écran qui a quelque chose à
 * refermer avant de céder la place.
 *
 * ## Pourquoi une abstraction
 *
 * Un écran commun ne peut pas nommer `BackHandler` : il vient de
 * `androidx.activity`, qui n'existe ni sur le bureau ni sur iOS. Jusqu'ici la
 * parade était de doubler l'écran d'une enveloppe Android — c'est ce que fait
 * `DetailsScreen.android.kt`. Cela vaut pour une page entière ; pour deux
 * lignes d'état interne, une enveloppe par écran serait plus lourde que ce
 * qu'elle porte.
 *
 * ## Ce que chaque plateforme en fait
 *
 * **Android** l'installe : c'est le seul système où un geste système
 * quelconque — la touche Retour, le glissement depuis le bord — remonte
 * jusqu'à l'application, et où ne pas le capter fait sortir de l'écran.
 *
 * **Le bureau** n'a pas de geste de retour. Échap y joue ce rôle, et il est
 * déjà traité au niveau de la fenêtre, qui sait dans quel ordre dépiler (voir
 * `Main.kt`). En ajouter un second ici ferait deux réponses au même appui.
 *
 * **iOS** n'a pas non plus de retour global : le glissement depuis le bord
 * appartient à `UINavigationController`, que cette application n'utilise pas —
 * elle porte sa propre pile. Les écrans y offrent un bouton.
 *
 * @param actif faux quand l'écran n'a rien à refermer. Le geste retombe alors
 *   sur le gestionnaire du dessus, qui dépile la navigation.
 */
@Composable
expect fun GesteRetour(actif: Boolean, onRetour: () -> Unit)
