package fr.moovie.tv.data.download

/**
 * Rien à faire sur desktop : le processus vit tant que la fenêtre est ouverte,
 * et fermer l'application est un geste explicite dont on n'a pas à protéger le
 * téléchargement. Aucun équivalent du service de premier plan n'est nécessaire.
 */
actual object DownloadForeground {
    actual fun start() = Unit
    actual fun stop() = Unit
}
