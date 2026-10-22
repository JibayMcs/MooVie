package fr.moovie.tv.shared

actual val platformName: String =
    System.getProperty("os.name")?.let { "Desktop ($it)" } ?: "Desktop"

actual val appVersionName: String = System.getProperty("moovie.version") ?: "0.0.0"

// Hors Gradle (IDE, lancement direct), moovie.build n'est pas posée : on se
// replie sur la version. Le cache de sources n'est alors invalidé qu'à chaque
// version, et non à chaque build.
actual val appBuildId: String = System.getProperty("moovie.build") ?: appVersionName

actual val openSubtitlesApiKey: String =
    System.getProperty("moovie.opensubtitles.key").orEmpty()

actual val deviceName: String
    get() = runCatching { java.net.InetAddress.getLocalHost().hostName }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: "Moo-vie"
