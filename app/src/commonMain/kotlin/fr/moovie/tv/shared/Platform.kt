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

/**
 * Clé de consumer OpenSubtitles, injectée à la compilation. Vide si le build
 * n'en a pas — les sous-titres se désactivent alors proprement.
 *
 * Elle identifie **l'application**, jamais l'utilisateur : OpenSubtitles impose
 * une clé unique par application et bannit l'accès de ceux qui demandent la leur
 * à leurs utilisateurs. C'est l'inverse de TMDB, dont la clé est propre à chaque
 * personne et se saisit donc dans les réglages. Ce qui est propre à
 * l'utilisateur ici, c'est son compte, et lui seul.
 */
expect val openSubtitlesApiKey: String
