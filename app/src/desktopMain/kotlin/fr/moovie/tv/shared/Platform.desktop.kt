package fr.moovie.tv.shared

actual val platformName: String =
    System.getProperty("os.name")?.let { "Desktop ($it)" } ?: "Desktop"

actual val isPointerUi: Boolean = true

actual val appVersionName: String = System.getProperty("moovie.version") ?: "0.0.0"

actual val openSubtitlesApiKey: String =
    System.getProperty("moovie.opensubtitles.key").orEmpty()
