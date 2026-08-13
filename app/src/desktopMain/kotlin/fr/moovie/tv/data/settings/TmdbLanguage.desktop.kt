package fr.moovie.tv.data.settings

import java.util.Locale

/**
 * La langue des métadonnées TMDB, dérivée du **choix** de l'utilisateur.
 *
 * Elle ne lisait que la locale du système, si bien que choisir une langue dans
 * les réglages — quand le sélecteur a existé — n'aurait rien changé aux titres
 * de rangées ni aux résumés : l'interface aurait basculé et pas le contenu.
 * C'est exactement ce que fait `LocaleManager.tmdbLanguage` côté Android.
 */
actual fun currentTmdbLanguage(): String = when (DesktopLocale.current()) {
    DesktopLanguage.FRENCH -> "fr-FR"
    DesktopLanguage.ENGLISH -> "en-US"
    DesktopLanguage.SPANISH -> "es-ES"
    DesktopLanguage.SYSTEM -> when (Locale.getDefault().language) {
        "fr" -> "fr-FR"
        "es" -> "es-ES"
        else -> "en-US"
    }
}
