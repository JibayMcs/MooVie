package fr.moovie.tv.shared

/**
 * Animation de lancement, dans les ressources partagées.
 *
 * Déclarée ici plutôt que dupliquée dans chaque plateforme : Android la décode
 * avec Coil, le desktop avec le codec de Skia, mais les deux lisent le **même**
 * fichier — une copie par plateforme aurait fini par diverger.
 */
const val SPLASH_FILE = "files/moovie_splash.webp"
