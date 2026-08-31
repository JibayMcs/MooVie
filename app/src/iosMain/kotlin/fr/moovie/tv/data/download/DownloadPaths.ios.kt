package fr.moovie.tv.data.download

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Application Support, et **pas** Caches : un film téléchargé pour être regardé
 * hors ligne est exactement ce qu'iOS purgerait en premier dans le cache, au
 * pire moment — dans l'avion, sans réseau pour le reprendre. C'est le même
 * raisonnement que côté Android, où le dossier est propre à l'application et
 * disparaît à la désinstallation.
 *
 * Ces fichiers ne sont pas non plus dans Documents : ce ne sont pas des
 * documents de l'utilisateur, et les exposer dans l'app Fichiers inviterait à
 * les manipuler à la main sous le dos de la bibliothèque.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun moovieDownloadsChemin(): String {
    val repertoire = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val base = repertoire?.path ?: error("répertoire Application Support introuvable")
    return "$base/moovie/downloads"
}
