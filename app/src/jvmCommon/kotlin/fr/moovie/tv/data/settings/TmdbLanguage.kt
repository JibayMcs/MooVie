package fr.moovie.tv.data.settings

/**
 * Code langue TMDB (métadonnées) courant. Android : dérivé de la langue de
 * l'app (LocaleManager) ; desktop : locale système.
 */
expect fun currentTmdbLanguage(): String
