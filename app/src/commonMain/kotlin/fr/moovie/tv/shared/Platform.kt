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
