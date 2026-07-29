package fr.moovie.tv.data.store

import java.io.File

// Répertoire de cache standard par OS : %LOCALAPPDATA% (Windows),
// ~/Library/Caches (macOS), $XDG_CACHE_HOME ou ~/.cache (Linux).
actual fun moovieCacheDir(name: String): File {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val base = when {
        "win" in os -> System.getenv("LOCALAPPDATA")?.let(::File) ?: File(home, "AppData/Local")
        "mac" in os -> File(home, "Library/Caches")
        else -> System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(home, ".cache")
    }
    return File(base, "moovie/$name").apply { mkdirs() }
}
