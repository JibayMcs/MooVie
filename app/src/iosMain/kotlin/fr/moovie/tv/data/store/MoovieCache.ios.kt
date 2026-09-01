package fr.moovie.tv.data.store

import fr.moovie.tv.shared.systemeFichiers
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * `NSCachesDirectory`, et c'est le bon choix précisément parce qu'iOS s'autorise
 * à le vider quand l'espace manque : ce contenu est reconstructible. Le mettre
 * dans Application Support, à côté des réglages, le rendrait indestructible et
 * ferait grossir l'app sans raison aux yeux du système.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun moovieCacheChemin(name: String): String {
    val repertoire = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val base = repertoire?.path ?: error("répertoire Caches introuvable")
    // Même raison que pour le magasin de réglages : le sous-dossier n'existe
    // pas tant qu'on ne l'a pas créé, et okio lève à l'ouverture.
    val dossier = "$base/moovie/$name"
    runCatching { systemeFichiers.createDirectories(dossier.toPath()) }
    return dossier
}
