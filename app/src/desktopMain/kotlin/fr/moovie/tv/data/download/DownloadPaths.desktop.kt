package fr.moovie.tv.data.download

import java.io.File

// Répertoire de *données* et non de configuration : $XDG_DATA_HOME (Linux),
// ~/Library/Application Support (macOS), %LOCALAPPDATA% (Windows). Des
// gigaoctets n'ont rien à faire dans %APPDATA%, qui est parfois synchronisé
// avec le profil itinérant d'un domaine.
actual fun moovieDownloadsDir(): File {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val base = when {
        "win" in os -> System.getenv("LOCALAPPDATA")?.let(::File)
            ?: File(home, "AppData/Local")
        "mac" in os -> File(home, "Library/Application Support")
        else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(home, ".local/share")
    }
    return File(base, "moovie/downloads").also { it.mkdirs() }
}
