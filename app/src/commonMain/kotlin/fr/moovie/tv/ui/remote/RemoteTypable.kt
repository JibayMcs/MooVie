package fr.moovie.tv.ui.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Annonce ce champ à la télécommande tant qu'il a le focus.
 *
 * ### Pourquoi c'est un `expect` et non une fonction commune
 *
 * Quatre écrans partagés le posent sur leur champ de saisie — réglages,
 * sous-titres, profils, synchronisation. Ces écrans sont maintenant communs aux
 * quatre plateformes ; ce que le modificateur *fait*, lui, ne peut pas l'être :
 * il inscrit le champ dans `RemoteTyping`, qui tient une socket ouverte vers le
 * téléphone appairé. Du `jvmCommon` par nature.
 *
 * Plutôt que d'entourer chaque appel d'une condition — quatre fois la même, et
 * une de plus à chaque champ ajouté — le modificateur existe partout et ne fait
 * rien là où il n'a rien à annoncer. C'est le sens d'un `Modifier` : s'ajouter
 * sans que l'appelant ait à savoir ce qu'il y a derrière.
 *
 * Sur iOS il rend `this` inchangé. Le portage y a écarté la télécommande : il
 * n'y a pas de téléviseur à piloter, donc aucun champ à annoncer.
 *
 * @param secret champ dont on annonce **qu'il attend une saisie**, jamais ce
 *   qu'il contient — une clé d'API n'a pas à traverser le réseau local.
 */
@Composable
expect fun Modifier.remoteTypable(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
): Modifier
