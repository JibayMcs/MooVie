package fr.moovie.tv.ui.remote

import androidx.compose.ui.input.key.Key
import fr.moovie.tv.data.remote.RemoteKey

/**
 * Le clavier du poste de travail, traduit en touches de télécommande.
 *
 * ## Pourquoi c'est la vraie télécommande d'un ordinateur
 *
 * Le pavé directionnel de l'écran est un **geste au pouce** : on pose, on
 * maintient, on tourne, avec zone morte et hystérésis sur les diagonales. Rien
 * de tout ça n'a de sens avec un curseur — on ne tourne pas une souris, et viser
 * quatre flèches à la souris pour naviguer dans une grille est plus lent que la
 * vraie télécommande qu'on cherchait à remplacer.
 *
 * Or les touches existent déjà sous les doigts. Les flèches, Entrée et Espace
 * sont exactement ce que la box écoute — elles y arrivent par le même chemin
 * qu'une vraie télécommande, `dispatchKeyEvent`. Le meilleur écran de
 * télécommande sur un ordinateur est celui qu'on ne clique pas.
 *
 * ## Ce qui n'est délibérément pas traduit
 *
 * **Échap.** C'est la sortie de l'écran côté poste de travail, pas un ordre pour
 * la télé. L'envoyer là-bas enfermerait : le seul geste qui ressemble à « je
 * quitte » piloterait l'appareil d'en face, et l'écran ne se fermerait jamais.
 * Retour arrière porte donc le retour **du téléviseur**, et les deux ne se
 * confondent pas.
 *
 * **Les touches de volume.** Elles appartiennent au système bien avant qu'une
 * JVM les voie — voir [CaptureVolumeKeys].
 *
 * Une fonction pure, donc vérifiable sans fenêtre ni téléviseur : c'est le seul
 * endroit où la correspondance se décide.
 */
fun remoteKeyFor(key: Key): RemoteKey? = when (key) {
    Key.DirectionUp -> RemoteKey.UP
    Key.DirectionDown -> RemoteKey.DOWN
    Key.DirectionLeft -> RemoteKey.LEFT
    Key.DirectionRight -> RemoteKey.RIGHT
    // Le pavé numérique compte autant : rien ne dit sur quel Entrée on appuie.
    Key.Enter, Key.NumPadEnter -> RemoteKey.OK
    Key.Spacebar -> RemoteKey.PLAY_PAUSE
    Key.Backspace -> RemoteKey.BACK
    else -> null
}

/**
 * Cadence minimale entre deux répétitions envoyées à la box.
 *
 * **La répétition du système est conservée** — c'est elle qui fait défiler quand
 * on garde la flèche enfoncée — mais elle est amortie. Mesuré sur X11 : une
 * touche maintenue deux secondes produit **51 requêtes**, soit 25 par seconde
 * sur le réseau local, pour une navigation que l'œil ne suit même pas.
 *
 * Le problème et sa réponse sont déjà écrits ailleurs : `RemoteVolumeKeys.handle`
 * amortit à l'identique côté Android, avec la même justification. Le chemin
 * clavier du poste de travail l'avait simplement oublié.
 */
const val KEY_REPEAT_MS = 120L

/**
 * Cette répétition doit-elle partir, ou tomber ?
 *
 * Séparée du composant pour être vérifiable sans fenêtre : c'est une décision
 * sur deux nombres, pas sur un événement.
 *
 * @param now horloge **monotone** ([monotonicMs]) : une horloge système qui
 *   recule ferait passer toutes les répétitions, ou aucune.
 */
fun acceptKeyRepeat(now: Long, lastAt: Long): Boolean = now - lastAt >= KEY_REPEAT_MS
