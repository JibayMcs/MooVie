package fr.moovie.tv.data.settings

import android.content.Context
import android.content.Intent
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
        // commit() synchrone (pas apply()) : la préférence doit être flushée sur
        // disque AVANT le redémarrage du process, sinon elle est perdue.
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, language.tag).commit()
    }

    /**
     * Enregistre la langue puis redémarre l'app à froid. Un simple recreate()
     * ne suffit pas : les ViewModels (scope Activity) survivent et gardent leurs
     * données déjà chargées (titres de rangées, métadonnées TMDB) dans l'ancienne
     * langue. Un redémarrage complet recharge tout dans la nouvelle langue.
     *
     * NB : on utilise l'intent Leanback (app TV, catégorie LEANBACK_LAUNCHER) —
     * `getLaunchIntentForPackage` cherche CATEGORY_LAUNCHER et renverrait null,
     * ce qui tuerait le process sans le relancer (= crash apparent).
     */
    fun applyAndRestart(context: Context, language: AppLanguage) {
        set(context, language)
        val app = context.applicationContext
        val pm = app.packageManager
        val launch = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
        val component = launch?.component
        if (component != null) {
            app.startActivity(Intent.makeRestartActivityTask(component))
            Runtime.getRuntime().exit(0)
        } else {
            // Garde-fou : jamais tuer le process sans chemin de relance fiable.
            (context as? android.app.Activity)?.recreate()
        }
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
