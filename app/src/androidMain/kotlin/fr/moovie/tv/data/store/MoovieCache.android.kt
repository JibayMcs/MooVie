package fr.moovie.tv.data.store

import java.io.File

// cacheDir : purgé par Android quand l'espace manque, exactement ce qu'on veut
// pour des données reconstructibles (réponses TMDB, affiches).
actual fun moovieCacheDir(name: String): File =
    File(appContext.cacheDir, name).apply { mkdirs() }
