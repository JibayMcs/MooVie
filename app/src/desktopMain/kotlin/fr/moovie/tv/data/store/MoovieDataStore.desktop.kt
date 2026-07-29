package fr.moovie.tv.data.store

import java.io.File

// Répertoire de configuration standard par OS : %APPDATA% (Windows),
// ~/Library/Application Support (macOS), $XDG_CONFIG_HOME ou ~/.config (Linux).
actual fun moovieDataStoreFile(name: String): File {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val base = when {
        "win" in os -> System.getenv("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")
        "mac" in os -> File(home, "Library/Application Support")
        else -> System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(home, ".config")
    }
    return File(base, "moovie/$name.preferences_pb")
}
