package fr.moovie.tv.data.download

import java.io.File

/**
 * Racine des téléchargements.
 *
 * **Propre à l'application, donc effacée à la désinstallation.** C'est un choix,
 * pas un oubli : écrire ailleurs demande sur Android la permission *Accès à tous
 * les fichiers*, qui existe déjà pour les sauvegardes — mais un dossier de
 * plusieurs dizaines de gigaoctets abandonné au milieu du stockage de quelqu'un
 * qui a désinstallé l'app serait une bien pire surprise qu'un film à
 * retélécharger. La sauvegarde, elle, est petite et irremplaçable ; un film ne
 * l'est pas.
 *
 * Sur Android c'est le stockage *externe* propre à l'app et non `filesDir` :
 * la mémoire interne d'une box est souvent de huit gigaoctets, on y tiendrait
 * deux films.
 */
expect fun moovieDownloadsDir(): File

/** Dossier d'un téléchargement : ses segments, sa playlist et sa fiche. */
fun downloadDir(key: String): File = File(moovieDownloadsDir(), safeName(key))

/**
 * Une clé média (`tv:1396:s1e1`) porte des `:` que Windows refuse et que FAT32
 * n'aime pas davantage. On la transpose plutôt que de forger un identifiant de
 * plus : le dossier reste lisible à l'œil quand on va voir ce qui occupe le
 * disque.
 */
internal fun safeName(key: String): String = key.map {
    if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_'
}.joinToString("")
