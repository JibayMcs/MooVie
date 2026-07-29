package fr.moovie.tv.shared

actual val platformName: String =
    System.getProperty("os.name")?.let { "Desktop ($it)" } ?: "Desktop"

actual val isPointerUi: Boolean = true
