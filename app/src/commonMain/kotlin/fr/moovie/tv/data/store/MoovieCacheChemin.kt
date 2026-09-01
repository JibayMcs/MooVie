package fr.moovie.tv.data.store

/**
 * Répertoire de cache disque nommé ("tmdb-http", "subtitles", "trailers"…).
 *
 * Un chemin et non un `java.io.File`, que Kotlin/Native n'a pas. Les cibles JVM
 * le dérivent de `moovieCacheDir`, qui reste en place : le même répertoire est
 * servi qu'avant, les fichiers déjà en cache sont retrouvés.
 *
 * Distinct de [moovieDataStoreChemin] : ce contenu est **jetable**. Il vit dans
 * le cache de l'OS, que le système peut purger sans rien casser — contrairement
 * aux réglages et à la progression de lecture, qui doivent survivre.
 */
expect fun moovieCacheChemin(name: String): String
