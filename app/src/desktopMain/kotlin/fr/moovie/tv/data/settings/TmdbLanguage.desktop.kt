package fr.moovie.tv.data.settings

import java.util.Locale

actual fun currentTmdbLanguage(): String = when (Locale.getDefault().language) {
    "fr" -> "fr-FR"
    "es" -> "es-ES"
    else -> "en-US"
}
