package fr.moovie.tv.data.store

import java.io.File

/**
 * Fichier de persistance d'un DataStore Preferences nommé (chemin par
 * plateforme).
 *
 * `expect` ici et non dans `commonMain` : le type de retour est un
 * `java.io.File`, qui n'existe que sur la JVM. Android et desktop gardent donc
 * leurs implémentations mot pour mot, et `DesktopLocale` — qui remonte au
 * `parentFile` pour y poser son propre fichier — continue de fonctionner sans
 * modification.
 */
expect fun moovieDataStoreFile(name: String): File

/**
 * Le chemin que réclame le code commun, dérivé du fichier ci-dessus.
 *
 * Cette dérivation est ce qui garantit qu'aucun utilisateur ne perd ses
 * données : le magasin commun ouvre exactement le fichier que la version
 * précédente ouvrait.
 */
actual fun moovieDataStoreChemin(name: String): String = moovieDataStoreFile(name).absolutePath
