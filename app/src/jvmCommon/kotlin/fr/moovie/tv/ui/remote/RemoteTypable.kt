package fr.moovie.tv.ui.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import fr.moovie.tv.data.remote.RemoteTyping
import kotlinx.coroutines.launch

/**
 * Annonce ce champ à la télécommande tant qu'il a le focus.
 *
 * ### Pourquoi chaque champ doit le demander
 *
 * L'application n'a pas de composant unique de saisie : quatre écrans posent
 * leur propre `BasicTextField`. On aurait pu détecter le focus globalement, mais
 * tous les champs ne se valent pas — le téléphone n'a rien à faire du champ qui
 * *reçoit* déjà sa saisie. Un opt-in explicite laisse ce choix à l'écran, et se
 * lit à l'endroit où il compte.
 *
 * ### Écrire, et non taper
 *
 * Le [onValueChange] est enregistré, si bien que la télécommande **remplace** le
 * contenu au lieu d'ajouter des caractères à la fin. L'injection clavier reste
 * en repli côté serveur, mais elle concaténait : corriger « inceptio » en
 * « inception » depuis le téléphone donnait « inceptioinception ».
 *
 * L'écriture est renvoyée sur le fil de la composition — l'ordre arrive d'une
 * socket, et un état Compose ne se modifie pas depuis n'importe où.
 */
@Composable
fun Modifier.remoteTypable(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    /**
     * Champ secret : on annonce **qu'il attend une saisie**, jamais ce qu'il
     * contient.
     *
     * Une clé d'API ou un mot de passe n'a pas à traverser le réseau local pour
     * s'afficher sur un téléphone. C'est la même règle que la page d'appairage,
     * qui dit si un réglage est renseigné sans jamais montrer sa valeur : la
     * commodité de saisie ne doit pas devenir une fuite.
     */
    secret: Boolean = false,
): Modifier {
    val id = remember { RemoteTyping.nextId() }
    val currentValue = rememberUpdatedState(if (secret) "" else value)
    val currentWrite = rememberUpdatedState(onValueChange)
    val currentLabel = rememberUpdatedState(label)
    val scope = rememberCoroutineScope()

    // Le contenu change aussi **sur le téléviseur** — au clavier à l'écran, ou
    // parce qu'on vient d'y écrire. Le téléphone doit voir la même chose, sinon
    // il réécrirait par-dessus une valeur qu'il croit encore vide.
    LaunchedEffect(value, secret) { RemoteTyping.updateValue(id, currentValue.value) }

    // Quitter l'écran sans passer par une perte de focus est le cas normal quand
    // on revient en arrière : sans ce retrait, le téléphone garderait un clavier
    // ouvert sur un champ qui n'existe plus.
    DisposableEffect(Unit) { onDispose { RemoteTyping.blur(id) } }

    return this.onFocusChanged { state ->
        if (state.isFocused) {
            RemoteTyping.focus(id, currentLabel.value, currentValue.value) { text ->
                scope.launch { currentWrite.value(text) }
            }
        } else {
            RemoteTyping.blur(id)
        }
    }
}
