package fr.moovie.tv.data.download

import fr.moovie.tv.data.store.appContext
import java.io.File

// getExternalFilesDir : propre à l'app (aucune permission, effacé à la
// désinstallation) mais sur le volume partagé, seul endroit où plusieurs films
// tiennent. `filesDir` est la mémoire interne, souvent 8 Go sur une box.
fun moovieDownloadsFichier(): File =
    File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "downloads")
        .also { it.mkdirs() }

actual fun moovieDownloadsChemin(): String = moovieDownloadsFichier().absolutePath
