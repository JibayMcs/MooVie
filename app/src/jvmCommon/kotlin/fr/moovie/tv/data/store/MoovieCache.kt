package fr.moovie.tv.data.store

import java.io.File

/**
 * Répertoire de cache disque nommé ("tmdb-http", "images"…), par plateforme.
 *
 * Distinct de [moovieDataStoreFile] : ce contenu est **jetable**. Il vit dans le
 * cache de l'OS, que le système peut purger sans rien casser — contrairement aux
 * réglages et à la progression de lecture, qui doivent survivre.
 */
expect fun moovieCacheDir(name: String): File
