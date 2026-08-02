package fr.moovie.tv.shared

// Point d'entrée du code partagé : chaque cible fournit son nom de plateforme.
// Sert aussi de validation du câblage expect/actual du squelette KMP.
expect val platformName: String

/**
 * Vrai quand l'UI est pilotée au pointeur (desktop), faux au D-pad (Android TV).
 * Sert à n'activer que côté desktop les affordances souris/clavier qui
 * perturberaient la télécommande : flèches de défilement des rangées et
 * capture des touches gauche/droite.
 */
expect val isPointerUi: Boolean

/**
 * Version de l'app telle qu'affichée à l'utilisateur (« 1.10.0 »).
 *
 * Elle vient du `versionName` sur Android et de la propriété système
 * `moovie.version` sur desktop, mais les deux sortent du même `appVersion` du
 * build : ce pont évite d'avoir à choisir lequel lire depuis le code partagé.
 */
expect val appVersionName: String
