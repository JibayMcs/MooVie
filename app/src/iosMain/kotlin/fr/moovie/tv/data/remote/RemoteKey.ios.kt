package fr.moovie.tv.data.remote

/**
 * Injection de touches : impossible sur iOS, et pas seulement non faite.
 *
 * L'implémentation Android passe par `Activity.dispatchKeyEvent`, c'est-à-dire
 * le chemin qu'emprunte une vraie télécommande — c'est ce qui fait que ni le
 * lecteur ni les rangées n'ont eu à changer. UIKit n'expose **aucun équivalent**
 * : il n'existe pas d'API publique pour fabriquer un événement clavier et
 * l'injecter dans sa propre application, et les contournements par
 * `UIApplication.sendEvent` sont privés, donc hors de question.
 *
 * Ce n'est de toute façon pas la moitié qui manque le plus : ces fonctions
 * servent au téléviseur qui **reçoit** les touches d'un téléphone. Un iPhone
 * est du côté qui les envoie, et ce côté-là passe par le réseau — voir
 * [RemoteBeacons].
 *
 * [remoteAvailable] rend donc false, et l'interface sait déjà quoi en faire :
 * elle n'affiche pas la télécommande plutôt que de proposer des touches sans
 * effet.
 */
actual fun sendRemoteKey(key: RemoteKey): Boolean = false

actual fun sendRemoteText(text: String): Boolean = false

actual fun remoteAvailable(): Boolean = false
