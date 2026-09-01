package fr.moovie.tv.shared

import okio.FileSystem

/**
 * Le système de fichiers réel.
 *
 * `FileSystem.SYSTEM` existe pour la JVM comme pour Kotlin/Native, mais okio le
 * déclare dans un source set `nonJsMain` que le nôtre ne voit pas — le commun
 * d'okio doit rester compilable pour JavaScript, qui n'a pas de disque. Ce pont
 * rend la valeur visible sans rien changer à ce qu'elle est.
 */
expect val systemeFichiers: FileSystem
