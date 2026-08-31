package fr.moovie.tv.data.store

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Application Support et non Documents : ces fichiers sont l'état interne de
 * l'app, pas des documents de l'utilisateur. Documents est exposé dans
 * l'application Fichiers et sauvegardé par iTunes ; y déposer des
 * `.preferences_pb` les donnerait à voir et à supprimer.
 *
 * `create = true` fait naître le répertoire s'il manque, ce qui est le cas au
 * premier lancement : contrairement à Documents, Application Support n'est pas
 * fourni d'office dans le bac à sable.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun moovieDataStoreChemin(name: String): String {
    val repertoire = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val base = repertoire?.path
        ?: error("répertoire Application Support introuvable")
    return "$base/moovie/$name.preferences_pb"
}
