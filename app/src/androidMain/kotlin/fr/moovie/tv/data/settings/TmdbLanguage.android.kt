package fr.moovie.tv.data.settings

import fr.moovie.tv.data.store.appContext

actual fun currentTmdbLanguage(): String = LocaleManager.tmdbLanguage(appContext)
