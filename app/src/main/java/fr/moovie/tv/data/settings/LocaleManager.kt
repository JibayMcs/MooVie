package fr.moovie.tv.data.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Langues supportées par l'app. `tag == null` = suivre la langue système. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    FRENCH("fr"),
    ENGLISH("en"),
    SPANISH("es"),
}

/**
 * Gère la langue de l'app. Stockée en SharedPreferences (accès synchrone,
 * nécessaire dans `attachBaseContext` avant toute UI). Pilote à la fois la
 * locale des ressources (`strings.xml`) et la langue des requêtes TMDB.
 */
object LocaleManager {

    private const val PREF = "moovie_locale"
    private const val KEY = "app_language"

    fun current(context: Context): AppLanguage {
        val tag = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SYSTEM
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, language.tag).apply()
    }

    /** Enveloppe le contexte avec la locale choisie (no-op si SYSTEM). */
    fun wrap(base: Context): Context {
        val tag = current(base).tag ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /** Code langue TMDB (métadonnées) dérivé de la langue de l'app. */
    fun tmdbLanguage(context: Context): String = when (current(context)) {
        AppLanguage.FRENCH -> "fr-FR"
        AppLanguage.ENGLISH -> "en-US"
        AppLanguage.SPANISH -> "es-ES"
        AppLanguage.SYSTEM -> when (Locale.getDefault().language) {
            "fr" -> "fr-FR"
            "es" -> "es-ES"
            else -> "en-US"
        }
    }
}
